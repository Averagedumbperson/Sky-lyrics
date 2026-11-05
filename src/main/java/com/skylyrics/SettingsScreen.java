package com.skylyrics;

import com.skylyrics.core.Config;
import com.skylyrics.core.LyricsService;
import com.skylyrics.core.NowPlaying;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Settings menu (default key: K). */
public final class SettingsScreen extends Screen {
    private static final int COL_W = 150;
    private static final int GAP = 6;
    private static final int ROW_STEP = 22;

    private final Screen parent;
    private final Config cfg = SkyLyricsClient.config();
    private final LyricsService svc = SkyLyricsClient.service();

    private Button lyricsBtn;
    private Button pillBtn;
    private Button whereBtn;
    private Button showBtn;
    private Button colorBtn;
    private Button sizeBtn;
    private Button sourceBtn;
    private Button spotifyBtn;

    public SettingsScreen(Screen parent) {
        super(Component.literal("Sky Lyrics"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int left = cx - COL_W - GAP / 2;
        int right = cx + GAP / 2;
        int top = Math.max(24, this.height / 2 - 95);

        lyricsBtn = addCol(left, top, () -> {
            cfg.showLyrics = !cfg.showLyrics;
            changed();
        });
        pillBtn = addCol(right, top, () -> {
            cfg.showPill = !cfg.showPill;
            changed();
        });
        whereBtn = addCol(left, top + ROW_STEP, () -> {
            cfg.followView = !cfg.followView;
            changed();
        });
        showBtn = addCol(right, top + ROW_STEP, () -> {
            cfg.wordByWord = !cfg.wordByWord;
            changed();
        });
        colorBtn = addCol(left, top + ROW_STEP * 2, () -> {
            cfg.colorIndex = (cfg.colorIndex + 1) % Config.COLOR_NAMES.length;
            changed();
        });
        sizeBtn = addCol(right, top + ROW_STEP * 2, () -> {
            cfg.sizeIndex = (cfg.sizeIndex + 1) % Config.SIZE_NAMES.length;
            changed();
        });

        addRenderableWidget(Button.builder(Component.literal("Lyrics earlier (-100 ms)"), b -> {
            cfg.offsetMs = Math.max(-5000, cfg.offsetMs - 100);
            changed();
        }).bounds(left, top + ROW_STEP * 3, COL_W, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Lyrics later (+100 ms)"), b -> {
            cfg.offsetMs = Math.min(5000, cfg.offsetMs + 100);
            changed();
        }).bounds(right, top + ROW_STEP * 3, COL_W, 20).build());

        sourceBtn = addCol(left, top + ROW_STEP * 4, () -> {
            cfg.useSpotifyAccount = !cfg.useSpotifyAccount;
            changed();
        });
        spotifyBtn = addCol(right, top + ROW_STEP * 4, () -> {
            if (svc.isSpotifyConnected()) {
                svc.disconnect();
            } else {
                svc.connect();
            }
        });

        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(left, top + ROW_STEP * 5, COL_W * 2 + GAP, 20).build());

        refreshLabels();
    }

    private Button addCol(int x, int y, Runnable onPress) {
        Button b = Button.builder(Component.literal(""), btn -> onPress.run())
                .bounds(x, y, COL_W, 20).build();
        addRenderableWidget(b);
        return b;
    }

    private void changed() {
        cfg.save();
        refreshLabels();
    }

    private void refreshLabels() {
        lyricsBtn.setMessage(Component.literal("Sky lyrics: " + onOff(cfg.showLyrics)));
        pillBtn.setMessage(Component.literal("Now-playing pill: " + onOff(cfg.showPill)));
        whereBtn.setMessage(Component.literal("Where: " + (cfg.followView ? "Floating (follows view)" : "Fixed in sky")));
        showBtn.setMessage(Component.literal("Show: " + (cfg.wordByWord ? "Word by word" : "Whole line")));
        colorBtn.setMessage(Component.literal("Colour: " + Config.COLOR_NAMES[cfg.colorIndex]));
        sizeBtn.setMessage(Component.literal("Size: " + Config.SIZE_NAMES[cfg.sizeIndex]));
        sourceBtn.setMessage(Component.literal("Source: " + (cfg.useSpotifyAccount ? "Spotify account" : "This computer")));
        updateSpotifyButton();
    }

    /** The Spotify button only matters in "Spotify account" mode; its label follows the connection state. */
    private void updateSpotifyButton() {
        spotifyBtn.active = cfg.useSpotifyAccount;
        spotifyBtn.setMessage(Component.literal(svc.isSpotifyConnected() ? "Disconnect Spotify" : "Connect Spotify"));
    }

    private static String onOff(boolean v) {
        return v ? "ON" : "OFF";
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        updateSpotifyButton();
        super.extractRenderState(graphics, mouseX, mouseY, delta);

        centered(graphics, "Sky Lyrics", Math.max(24, this.height / 2 - 95) - 14, 0xFFC9B8FF);

        int y = Math.max(24, this.height / 2 - 95) + ROW_STEP * 6 + 4;
        centered(graphics, connectionLine(), y, 0xFFFFFFFF);

        NowPlaying np = svc.nowPlaying();
        if (np != null) {
            String track = np.artist().isEmpty() ? np.title() : np.title() + " - " + np.artist();
            centered(graphics, "Now playing: " + track, y + 11, 0xFFD0D0D0);
            LyricsService.LyricsState ls = svc.lyrics();
            if (ls.trackId().equals(np.trackId()) && !ls.note().isEmpty()) {
                centered(graphics, "Lyrics: " + ls.note(), y + 22, 0xFFD0D0D0);
            }
        }
        centered(graphics, "Lyrics offset: " + (cfg.offsetMs >= 0 ? "+" : "") + cfg.offsetMs + " ms", y + 33, 0xFFA0A0A0);
    }

    private String connectionLine() {
        return switch (svc.status()) {
            case LOCAL -> svc.localStatus();
            case NO_CLIENT_ID -> "Add your Spotify Client ID to config/skylyrics.json, then click Connect";
            case DISCONNECTED -> svc.message().isEmpty() ? "Spotify: not connected" : svc.message();
            case CONNECTING -> svc.message().isEmpty() ? "Spotify: waiting for browser login..." : svc.message();
            case CONNECTED -> "Spotify: connected" + (svc.nowPlaying() == null ? " (nothing playing)" : "");
            case ERROR -> svc.message();
        };
    }

    private void centered(GuiGraphicsExtractor graphics, String text, int y, int color) {
        graphics.text(this.font, text, this.width / 2 - this.font.width(text) / 2, y, color, true);
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(this.parent);
    }
}
