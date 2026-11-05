package com.skylyrics;

import com.mojang.blaze3d.platform.InputConstants;
import com.skylyrics.core.Config;
import com.skylyrics.core.LyricsService;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.nio.file.Path;

public final class SkyLyricsClient implements ClientModInitializer {
    public static final String MOD_ID = "skylyrics";

    private static Config config;
    private static LyricsService service;
    private static KeyMapping openSettingsKey;
    private boolean noticeShown;

    public static Config config() {
        return config;
    }

    public static LyricsService service() {
        return service;
    }

    @Override
    public void onInitializeClient() {
        Path configDir = FabricLoader.getInstance().getConfigDir();
        config = Config.load(configDir.resolve("skylyrics.json"));
        service = new LyricsService(config, configDir.resolve("skylyrics-spotify.json"));
        service.start();

        KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));
        openSettingsKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.skylyrics.open_settings",
                InputConstants.Type.KEYSYM,
                InputConstants.KEY_K,
                category));

        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(MOD_ID, "lyrics"), LyricsHud::render);

        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
    }

    private void onTick(Minecraft client) {
        while (openSettingsKey.consumeClick()) {
            if (client.player != null && client.gui.screen() == null) {
                client.gui.setScreen(new SettingsScreen(null));
            }
        }

        // One-time hint after joining a world.
        if (!noticeShown && client.player != null) {
            noticeShown = true;
            if (config.useSpotifyAccount && service.status() == LyricsService.Status.NO_CLIENT_ID) {
                client.player.sendSystemMessage(Component.literal(
                        "[Sky Lyrics] Spotify account mode needs a Client ID in config/skylyrics.json "
                                + "(press K to switch back to no-setup mode)."));
            } else if (!config.welcomeShown) {
                config.welcomeShown = true;
                config.save();
                client.player.sendSystemMessage(Component.literal(
                        "[Sky Lyrics] Ready! Play a song in the Spotify app and lyrics will appear. "
                                + "Press K for settings."));
            }
        }
    }
}
