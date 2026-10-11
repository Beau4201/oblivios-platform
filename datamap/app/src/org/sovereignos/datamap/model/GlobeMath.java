// SPDX-License-Identifier: Apache-2.0
package org.sovereignos.datamap.model;

/**
 * Orthographic projection of the globe. Pure arithmetic, no Android class, so it is checked on the host.
 *
 * <p>The view direction is the +z axis. The globe is turned by a longitude {@code lon0} (about the
 * polar axis) and a latitude {@code lat0} (tilt), both in degrees. A point is visible when its
 * {@code depth} is positive.
 */
public final class GlobeMath {
    public static final double MAX_TILT = 80.0;
    public static final double MIN_ZOOM = 1.0;
    public static final double MAX_ZOOM = 6.0;

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

    /**
     * The point of the globe under a screen position, or null if the position is off the globe.
     * Inverse of {@link #project}: {@code (x, y)} are in globe radii, y up.
     *
     * @return {latitude, longitude} in degrees
     */
    public static double[] unproject(double x, double y, double lat0Deg, double lon0Deg) {
        double r2 = x * x + y * y;
        if (r2 > 1.0) {
            return null;
        }
        double z = Math.sqrt(1.0 - r2);
        double lat0 = Math.toRadians(lat0Deg);
        double sinLat = y * Math.cos(lat0) + z * Math.sin(lat0);
        double lat = Math.asin(Math.max(-1.0, Math.min(1.0, sinLat)));
        double dLon = Math.atan2(x, z * Math.cos(lat0) - y * Math.sin(lat0));
        double lon = Math.toDegrees(dLon) + lon0Deg;
        return new double[] {Math.toDegrees(lat), wrapLon(lon)};
    }

    /**
     * A point behind the globe, moved to the rim along the line from the centre, so that an outline
     * that passes over the horizon is filled up to the rim instead of being cut off or turned inside
     * out. Points in front are returned unchanged. Returns {x, y}.
     */
    public static double[] toRim(P p) {
        if (p.depth > 0) {
            return new double[] {p.x, p.y};
        }
        double len = Math.hypot(p.x, p.y);
        if (len < 1e-9) {
            return new double[] {1.0, 0.0};   // straight behind the centre: any rim point will do
        }
        return new double[] {p.x / len, p.y / len};
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

    /**
     * Points along the great circle between two places, lifted off the surface in the middle so that a
     * line between two far places arches over the globe instead of cutting through it. The lift is
     * zero at both ends. Each result is {latitude, longitude, lift} with lift in globe radii.
     */
    public static double[][] arc(double lat1, double lon1, double lat2, double lon2, int steps,
            double maxLift) {
        double[] a = unit(lat1, lon1);
        double[] b = unit(lat2, lon2);
        double dot = a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
        double cx = a[1] * b[2] - a[2] * b[1];
        double cy = a[2] * b[0] - a[0] * b[2];
        double cz = a[0] * b[1] - a[1] * b[0];
        // atan2 of the cross product keeps the angle exact for places that are close together
        double omega = Math.atan2(Math.sqrt(cx * cx + cy * cy + cz * cz), dot);
        double[][] out = new double[steps + 1][];
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            double[] p;
            if (omega < 1e-9) {
                p = a;
            } else {
                double s = Math.sin(omega);
                double ka = Math.sin((1 - t) * omega) / s;
                double kb = Math.sin(t * omega) / s;
                p = new double[] {ka * a[0] + kb * b[0], ka * a[1] + kb * b[1], ka * a[2] + kb * b[2]};
            }
            double lat = Math.toDegrees(Math.asin(Math.max(-1.0, Math.min(1.0, p[2]))));
            double lon = Math.toDegrees(Math.atan2(p[1], p[0]));
            double lift = maxLift * Math.sin(Math.PI * t) * (omega / Math.PI);
            out[i] = new double[] {lat, lon, lift};
        }
        return out;
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

    /** The zoom that shows both ends of a relation that lie {@code angle} radians apart: 1.5 to 2.6. */
    public static double zoomForSpan(double angle) {
        return Math.max(1.5, Math.min(2.6, 1.7 / Math.max(angle, 0.5)));
    }

    private static double[] unit(double latDeg, double lonDeg) {
        double lat = Math.toRadians(latDeg);
        double lon = Math.toRadians(lonDeg);
        return new double[] {Math.cos(lat) * Math.cos(lon), Math.cos(lat) * Math.sin(lon), Math.sin(lat)};
    }
}
