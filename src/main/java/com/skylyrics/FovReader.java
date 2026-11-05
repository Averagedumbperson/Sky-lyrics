package com.skylyrics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;

/**
 * Reads the player's FOV setting (Options.fov().get()) by reflection, so the mod still compiles and runs even if
 * that accessor changes in some Minecraft version. Falls back to 70 (the default) if it can't be read.
 */
final class FovReader {
    private static final Logger LOG = LoggerFactory.getLogger("skylyrics");
    private static final float DEFAULT_FOV = 70f;

    private static Method fovMethod;
    private static Method getMethod;
    private static boolean failed;

    private FovReader() {
    }

    static float read(Object options) {
        if (failed || options == null) {
            return DEFAULT_FOV;
        }
        try {
            if (fovMethod == null) {
                fovMethod = options.getClass().getMethod("fov");
            }
            Object instance = fovMethod.invoke(options);
            if (getMethod == null) {
                getMethod = instance.getClass().getMethod("get");
            }
            Object value = getMethod.invoke(instance);
            if (value instanceof Number n) {
                return n.floatValue();
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            failed = true;
            LOG.warn("Could not read the FOV option, assuming {}: {}", DEFAULT_FOV, e.toString());
        }
        return DEFAULT_FOV;
    }
}
