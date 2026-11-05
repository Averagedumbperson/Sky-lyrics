package com.skylyrics.core;

/**
 * Projects a direction on the "sky sphere" (yaw/pitch in Minecraft's convention) onto the screen,
 * using a plain perspective camera. Pure math: no Minecraft classes.
 *
 * Minecraft convention: yaw 0 = south (+Z), 90 = west (-X); pitch +90 = straight down, -90 = straight up.
 */
public final class SkyProjection {

    public record Result(boolean visible, float x, float y, float angleRad, float pxPerRad) {
        public static final Result HIDDEN = new Result(false, 0f, 0f, 0f, 0f);
    }

    private static final double MIN_DEPTH = 0.12;
    private static final double EPS = 0.02; // radians, used to measure local orientation/scale

    private SkyProjection() {
    }

    /**
     * @param fovDeg vertical field of view in degrees (Minecraft's FOV option is vertical)
     * @return screen position (GUI pixels), baseline angle (radians, screen y points down) and how many
     *         pixels one radian of sky spans at that spot (use it to size text by angular height)
     */
    public static Result project(double camYawDeg, double camPitchDeg, double yawDeg, double pitchDeg,
                                 double fovDeg, double screenW, double screenH) {
        double[] f = dir(camYawDeg, camPitchDeg);
        double[] r = right(camYawDeg);
        double[] u = cross(r, f);
        double focal = (screenH / 2.0) / Math.tan(Math.toRadians(fovDeg) / 2.0);

        double[] a = dir(yawDeg, pitchDeg);
        double[] p = toScreen(a, r, u, f, focal, screenW, screenH);
        if (p == null) {
            return Result.HIDDEN;
        }

        double[] t = right(yawDeg); // horizontal tangent pointing to the right of the anchor
        double[] a1 = normalize(add(a, scale(t, EPS)));
        double[] a0 = normalize(sub(a, scale(t, EPS)));
        double[] p1 = toScreen(a1, r, u, f, focal, screenW, screenH);
        double[] p0 = toScreen(a0, r, u, f, focal, screenW, screenH);
        if (p1 == null || p0 == null) {
            return Result.HIDDEN;
        }

        double angle = Math.atan2(p1[1] - p0[1], p1[0] - p0[0]);
        double between = Math.acos(Math.max(-1.0, Math.min(1.0, dot(a0, a1))));
        double pxPerRad = Math.hypot(p1[0] - p0[0], p1[1] - p0[1]) / Math.max(between, 1e-6);
        return new Result(true, (float) p[0], (float) p[1], (float) angle, (float) pxPerRad);
    }

    private static double[] toScreen(double[] a, double[] r, double[] u, double[] f,
                                     double focal, double w, double h) {
        double cx = dot(a, r);
        double cy = dot(a, u);
        double cz = dot(a, f);
        if (cz < MIN_DEPTH) {
            return null;
        }
        return new double[]{w / 2.0 + cx / cz * focal, h / 2.0 - cy / cz * focal};
    }

    static double[] dir(double yawDeg, double pitchDeg) {
        double y = Math.toRadians(yawDeg);
        double p = Math.toRadians(pitchDeg);
        return new double[]{-Math.sin(y) * Math.cos(p), -Math.sin(p), Math.cos(y) * Math.cos(p)};
    }

    static double[] right(double yawDeg) {
        double y = Math.toRadians(yawDeg);
        return new double[]{-Math.cos(y), 0.0, -Math.sin(y)};
    }

    private static double[] cross(double[] a, double[] b) {
        return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    private static double dot(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    private static double[] add(double[] a, double[] b) {
        return new double[]{a[0] + b[0], a[1] + b[1], a[2] + b[2]};
    }

    private static double[] sub(double[] a, double[] b) {
        return new double[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]};
    }

    private static double[] scale(double[] a, double s) {
        return new double[]{a[0] * s, a[1] * s, a[2] * s};
    }

    private static double[] normalize(double[] a) {
        double len = Math.sqrt(dot(a, a));
        return new double[]{a[0] / len, a[1] / len, a[2] / len};
    }
}
