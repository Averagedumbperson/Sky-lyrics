package com.skylyrics.core;

import java.io.IOException;
import java.util.Locale;

/** Opens a URL in the default browser without any AWT (which is unreliable inside Minecraft). */
public final class BrowserOpener {
    private BrowserOpener() {
    }

    public static boolean open(String url) {
        try {
            String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            ProcessBuilder pb;
            if (os.contains("win")) {
                pb = new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url);
            } else if (os.contains("mac")) {
                pb = new ProcessBuilder("open", url);
            } else {
                pb = new ProcessBuilder("xdg-open", url);
            }
            pb.redirectErrorStream(true);
            pb.start();
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
