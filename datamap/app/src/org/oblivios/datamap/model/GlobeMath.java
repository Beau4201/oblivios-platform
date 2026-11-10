// SPDX-License-Identifier: Apache-2.0
package org.oblivios.datamap.model;

/**
 * Orthographic projection of the globe, and the limits of the view. Pure arithmetic, no Android class, so it is
 * checked on the host.
 *
 * <p>The view direction is the +z axis. The globe is turned by a longitude {@code lon0} (about the
 * polar axis) and a latitude {@code lat0} (tilt), both in degrees. A point is visible when its
 * {@code depth} is positive.
 */
public final class GlobeMath {
    /** The limits of the design page: the view is tilted at most 70 degrees and zoomed from 0.8 to 2.4. */
    public static final double MAX_TILT = 70.0;
    public static final double MIN_ZOOM = 0.8;
    public static final double MAX_ZOOM = 2.4;

    /** A place is fully visible up to this angle from the view centre and fades to nothing at the horizon (90). */
    public static final double LIMB_FULL_DEG = 70.0;

    /** x to the right and y up, in units of the globe radius; depth toward the viewer. */
    public static final class P {
        public final double x;
        public final double y;
        public final double depth;

        P(double x, double y, double depth) {
            this.x = x;
            this.y = y;
            this.depth = depth;
        }

        public boolean visible() {
            return depth > 0;
        }
    }

    private GlobeMath() {}

    public static P project(double latDeg, double lonDeg, double lat0Deg, double lon0Deg) {
        double lat = Math.toRadians(latDeg);
        double dLon = Math.toRadians(lonDeg - lon0Deg);
        double lat0 = Math.toRadians(lat0Deg);
        double cosLat = Math.cos(lat);
        double x = cosLat * Math.sin(dLon);
        double y = Math.cos(lat0) * Math.sin(lat) - Math.sin(lat0) * cosLat * Math.cos(dLon);
        double depth = Math.sin(lat0) * Math.sin(lat) + Math.cos(lat0) * cosLat * Math.cos(dLon);
        return new P(x, y, depth);
    }

    public static double wrapLon(double lon) {
        double w = (lon + 180.0) % 360.0;
        if (w < 0) {
            w += 360.0;
        }
        return w - 180.0;
    }

    public static double clampTilt(double lat0) {
        return Math.max(-MAX_TILT, Math.min(MAX_TILT, lat0));
    }

    public static double clampZoom(double zoom) {
        return Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom));
    }

    /** The angle in radians between two places as seen from the middle of the globe. */
    public static double angle(double lat1, double lon1, double lat2, double lon2) {
        double[] a = unit(lat1, lon1);
        double[] b = unit(lat2, lon2);
        double cx = a[1] * b[2] - a[2] * b[1];
        double cy = a[2] * b[0] - a[0] * b[2];
        double cz = a[0] * b[1] - a[1] * b[0];
        return Math.atan2(Math.sqrt(cx * cx + cy * cy + cz * cz), a[0] * b[0] + a[1] * b[1] + a[2] * b[2]);
    }

    /**
     * The opacity of a place from its depth: 1 up to {@link #LIMB_FULL_DEG} from the view centre, then falling in a
     * straight line of the angle to 0 at the horizon.
     */
    public static double limbAlpha(double depth) {
        double deg = Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, depth))));
        if (deg < LIMB_FULL_DEG) {
            return 1.0;
        }
        return Math.max(0.0, 1.0 - (deg - LIMB_FULL_DEG) / (90.0 - LIMB_FULL_DEG));
    }

    /** A faded place is not a target: a touch only counts from this opacity. */
    public static boolean tappable(double alpha) {
        return alpha >= 0.5;
    }

    private static double[] unit(double latDeg, double lonDeg) {
        double lat = Math.toRadians(latDeg);
        double lon = Math.toRadians(lonDeg);
        return new double[] {Math.cos(lat) * Math.cos(lon), Math.cos(lat) * Math.sin(lon), Math.sin(lat)};
    }
}
