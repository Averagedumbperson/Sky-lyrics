package com.skylyrics;

import com.skylyrics.core.Config;
import com.skylyrics.core.LrcParser;
import com.skylyrics.core.LyricAnim;
import com.skylyrics.core.LyricLine;
import com.skylyrics.core.LyricsService;
import com.skylyrics.core.NowPlaying;
import com.skylyrics.core.SkyProjection;
import com.skylyrics.core.TextWrap;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.joml.Matrix3x2fStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Draws the "now playing" pill and the lyrics floating in the sky.
 *
 * The sky text is drawn on the 2D HUD layer: each lyric line is anchored to a yaw/pitch direction in the world,
 * projected to the screen with the player's camera angles and FOV (see SkyProjection), then drawn rotated and
 * scaled to match. That gives a "3D in the sky" look without needing the world-render pipeline.
 */
public final class LyricsHud {
    private static final int ACCENT = 0xFFC9B8FF;

    // World-anchored mode: where each line of the current track was placed (yaw, pitch).
    private static final Map<Integer, float[]> ANCHORS = new HashMap<>();
    private static String anchorTrack = "";

    private LyricsHud() {
    }

    public static void render(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        Config cfg = SkyLyricsClient.config();
        LyricsService svc = SkyLyricsClient.service();
        NowPlaying np = svc.nowPlaying();
        if (np == null) {
            return;
        }

        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        long pos = np.positionMs(System.nanoTime()) + cfg.offsetMs;

        LyricsService.LyricsState ls = svc.lyrics();
        List<LyricLine> lines = ls.trackId().equals(np.trackId()) ? ls.lines() : List.of();
        int idx = lines.isEmpty() ? -1 : LrcParser.indexAt(lines, pos);

        if (!np.trackId().equals(anchorTrack)) {
            ANCHORS.clear();
            anchorTrack = np.trackId();
        }

        if (cfg.showPill) {
            drawPill(g, mc, np, lines, idx, pos, sw);
        }

        if (cfg.showLyrics && np.playing() && idx >= 0) {
            float camYaw = mc.player.getYRot();
            float camPitch = mc.player.getXRot();
            float fov = FovReader.read(mc.options);
            // Previous line first so the current one is drawn on top while they cross-fade.
            if (idx > 0) {
                drawSkyLine(g, mc, cfg, np, lines, idx - 1, pos, camYaw, camPitch, fov, sw, sh);
            }
            drawSkyLine(g, mc, cfg, np, lines, idx, pos, camYaw, camPitch, fov, sw, sh);
        }
    }

    private static void drawSkyLine(GuiGraphicsExtractor g, Minecraft mc, Config cfg, NowPlaying np,
                                    List<LyricLine> lines, int i, long pos,
                                    float camYaw, float camPitch, float fov, int sw, int sh) {
        LyricLine line = lines.get(i);
        String text = line.text();
        if (text.isBlank()) {
            return;
        }

        long start = line.timeMs();
        long end = LyricAnim.lineEnd(lines, i, np.durationMs());
        float alpha = LyricAnim.alpha(pos, start, end);
        if (alpha <= 0.02f) {
            return;
        }

        float[] place = LyricAnim.placement(np.trackId().hashCode(), i);
        float aYaw;
        float aPitch;
        if (cfg.followView) {
            // Floating: offsets are relative to wherever you're looking.
            aYaw = camYaw + place[0];
            aPitch = camPitch - place[1];
        } else {
            // Sky: fixed in the world, in the band above the horizon, where the line first appeared.
            float[] fixed = ANCHORS.computeIfAbsent(i, k -> new float[]{camYaw + place[0], -place[1] - 6f});
            aYaw = fixed[0];
            aPitch = fixed[1];
        }
        aPitch = Math.max(-80f, Math.min(80f, aPitch));

        SkyProjection.Result p = SkyProjection.project(camYaw, camPitch, aYaw, aPitch, fov, sw, sh);
        if (!p.visible()) {
            return;
        }

        Font font = mc.font;
        float heightRad = (float) Math.toRadians(Config.SIZE_DEG[cfg.sizeIndex]);
        float scale = Math.min(p.pxPerRad() * heightRad / font.lineHeight, 14f);
        if (scale < 0.3f) {
            return;
        }

        String[] words = text.trim().split("\\s+");
        int maxBaseWidth = Math.max(40, (int) (sw * 0.6f / scale));
        List<List<String>> rows = TextWrap.wrapWords(words, maxBaseWidth, s -> font.width(s));
        long[] reveal = cfg.wordByWord ? LyricAnim.wordRevealMs(words, start, end) : null;

        int rgb = Config.COLOR_RGB[cfg.colorIndex];
        int rowH = font.lineHeight + 2;
        float totalH = rows.size() * rowH;
        float s = scale * LyricAnim.pop(pos - start);

        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        pose.translate(p.x(), p.y());
        pose.rotate(p.angleRad());
        pose.scale(s, s);

        int wordIndex = 0;
        for (int r = 0; r < rows.size(); r++) {
            List<String> row = rows.get(r);
            float x0 = -font.width(String.join(" ", row)) / 2f;
            float y0 = -totalH / 2f + r * rowH;
            StringBuilder prefix = new StringBuilder();
            for (String w : row) {
                float wa = alpha;
                if (reveal != null) {
                    wa *= LyricAnim.clamp01((pos - reveal[wordIndex]) / 180f);
                }
                int a255 = Math.round(wa * 255f);
                if (a255 >= 8) { // vanilla treats very low alpha as opaque, so skip those
                    int px = Math.round(x0 + font.width(prefix.toString()));
                    g.text(font, w, px, Math.round(y0), (a255 << 24) | rgb, true);
                }
                prefix.append(w).append(' ');
                wordIndex++;
            }
        }
        pose.popMatrix();
    }

    private static void drawPill(GuiGraphicsExtractor g, Minecraft mc, NowPlaying np,
                                 List<LyricLine> lines, int idx, long pos, int sw) {
        Font font = mc.font;
        String label = null;
        if (np.playing() && idx >= 0 && !lines.get(idx).text().isBlank()) {
            label = lines.get(idx).text();
        }
        if (label == null) {
            label = np.artist().isEmpty() ? np.title() : np.title() + " - " + np.artist();
        }

        int w = Math.min(280, sw - 40);
        int h = 17;
        int x = (sw - w) / 2;
        int y = 4;
        int bg = 0xC8120F1C;

        // Rounded-looking pill from non-overlapping rectangles (so translucent areas don't double up).
        g.fill(x + 2, y, x + w - 2, y + h, bg);
        g.fill(x + 1, y + 1, x + 2, y + h - 1, bg);
        g.fill(x + w - 2, y + 1, x + w - 1, y + h - 1, bg);
        g.fill(x, y + 2, x + 1, y + h - 2, bg);
        g.fill(x + w - 1, y + 2, x + w, y + h - 2, bg);

        // Little equaliser bars (flat when paused).
        long t = System.nanoTime() / 1_000_000L;
        for (int k = 0; k < 3; k++) {
            int bh = np.playing() ? 3 + (int) (7 * Math.abs(Math.sin(t * 0.006 + k * 1.9))) : 3;
            int bx = x + 7 + k * 3;
            g.fill(bx, y + h - 4 - bh, bx + 2, y + h - 4, ACCENT);
        }

        String shown = fit(font, label, w - 21 - 8);
        g.text(font, shown, x + 21, y + (h - font.lineHeight) / 2 + 1, 0xFFFFFFFF, false);

        if (np.durationMs() > 0) {
            int x0 = x + 5;
            int x1 = x + w - 5;
            float prog = Math.max(0f, Math.min(1f, pos / (float) np.durationMs()));
            g.fill(x0, y + h - 2, x1, y + h - 1, 0x40FFFFFF);
            g.fill(x0, y + h - 2, x0 + Math.round((x1 - x0) * prog), y + h - 1, ACCENT);
        }
    }

    private static String fit(Font font, String s, int maxW) {
        if (font.width(s) <= maxW) {
            return s;
        }
        String ellipsis = "...";
        int end = s.length();
        while (end > 1 && font.width(s.substring(0, end) + ellipsis) > maxW) {
            end--;
        }
        return s.substring(0, end).stripTrailing() + ellipsis;
    }
}
