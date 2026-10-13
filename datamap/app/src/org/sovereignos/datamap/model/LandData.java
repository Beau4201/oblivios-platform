// SPDX-License-Identifier: Apache-2.0
package org.sovereignos.datamap.model;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * The land outlines of the globe: the file res/raw/land.bin that tools/make-land.py writes. A shape is
 * one polygon, drawn with the even-odd rule; its first ring is the outline and the others are holes.
 * The file is the app's own, but the reader still refuses a file that claims more than the globe can
 * use, so a damaged asset ends in an error and not in an out-of-memory crash.
 */
public final class LandData {
    static final int MAX_SHAPES = 2000;
    static final int MAX_RINGS = 100;
    static final int MAX_POINTS = 200_000;

    /** shapes[shape][ring] = longitude, latitude, longitude, latitude ... in degrees */
    public final float[][][] shapes;

    private final double[] centreLat;
    private final double[] centreLon;
    private final double[] radius;   // radians: the farthest point of the outline from the centre
    private final float[][] box;     // minLon, maxLon, minLat, maxLat of the outline of each shape

    private LandData(float[][][] shapes) {
        this.shapes = shapes;
        centreLat = new double[shapes.length];
        centreLon = new double[shapes.length];
        radius = new double[shapes.length];
        box = new float[shapes.length][];
        for (int s = 0; s < shapes.length; s++) {
            float[] ring = shapes[s][0];
            float[] bb = {Float.MAX_VALUE, -Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE};
            for (int i = 0; i < ring.length; i += 2) {
                bb[0] = Math.min(bb[0], ring[i]);
                bb[1] = Math.max(bb[1], ring[i]);
                bb[2] = Math.min(bb[2], ring[i + 1]);
                bb[3] = Math.max(bb[3], ring[i + 1]);
            }
            box[s] = bb;
            double x = 0;
            double y = 0;
            double z = 0;
            for (int i = 0; i < ring.length; i += 2) {
                double lon = Math.toRadians(ring[i]);
                double lat = Math.toRadians(ring[i + 1]);
                x += Math.cos(lat) * Math.cos(lon);
                y += Math.cos(lat) * Math.sin(lon);
                z += Math.sin(lat);
            }
            double len = Math.sqrt(x * x + y * y + z * z);
            if (len < 1e-9) {
                radius[s] = Math.PI;   // no usable centre: always possibly visible
                continue;
            }
            centreLat[s] = Math.toDegrees(Math.asin(z / len));
            centreLon[s] = Math.toDegrees(Math.atan2(y, x));
            double far = 0;
            for (int i = 0; i < ring.length; i += 2) {
                far = Math.max(far, GlobeMath.angle(centreLat[s], centreLon[s], ring[i + 1], ring[i]));
            }
            radius[s] = far;
        }
    }

    /**
     * Whether any part of a shape can be on the near half of the globe turned to {@code (lat0, lon0)}. A
     * shape for which this is false need not be projected: it is on the far side.
     */
    public boolean mayBeVisible(int shape, double lat0, double lon0) {
        double angle = GlobeMath.angle(centreLat[shape], centreLon[shape], lat0, lon0);
        return angle < Math.PI / 2 + radius[shape];
    }

    /**
     * Whether a place is on land: inside the outline of a shape and not inside one of its holes (even-odd, in the
     * plane of longitude and latitude; the land file splits what crosses the date line).
     */
    public boolean contains(double lat, double lon) {
        return shapeAt(lat, lon) >= 0;
    }

    /** The shape a place is inside, or -1. */
    public int shapeAt(double lat, double lon) {
        for (int s = 0; s < shapes.length; s++) {
            float[] bb = box[s];
            if (lon < bb[0] || lon > bb[1] || lat < bb[2] || lat > bb[3]) {
                continue;
            }
            boolean inside = false;
            for (float[] ring : shapes[s]) {
                int n = ring.length / 2;
                for (int i = 0, j = n - 1; i < n; j = i++) {
                    double xi = ring[2 * i];
                    double yi = ring[2 * i + 1];
                    double xj = ring[2 * j];
                    double yj = ring[2 * j + 1];
                    if ((yi > lat) != (yj > lat) && lon < (xj - xi) * (lat - yi) / (yj - yi) + xi) {
                        inside = !inside;
                    }
                }
            }
            if (inside) {
                return s;
            }
        }
        return -1;
    }

    /** minLon, maxLon, minLat, maxLat of the outline of a shape. */
    public float[] bounds(int shape) {
        return box[shape].clone();
    }

    /**
     * A copy with fewer points, for the frames in which the globe moves: every ring is simplified so that no point
     * of the original is farther than {@code toleranceDeg} degrees from the result (Douglas and Peucker). A ring
     * left with fewer than three points is dropped, and a shape whose outline is dropped goes with it.
     */
    public LandData decimated(double toleranceDeg) {
        java.util.List<float[][]> out = new java.util.ArrayList<>();
        for (float[][] shape : shapes) {
            float[] outline = simplify(shape[0], toleranceDeg);
            if (outline == null) {
                continue;
            }
            java.util.List<float[]> rings = new java.util.ArrayList<>();
            rings.add(outline);
            for (int r = 1; r < shape.length; r++) {
                float[] hole = simplify(shape[r], toleranceDeg);
                if (hole != null) {
                    rings.add(hole);
                }
            }
            out.add(rings.toArray(new float[0][]));
        }
        return new LandData(out.toArray(new float[0][][]));
    }

    private static double distance(float[] r, int k, int a, int b) {
        double px = r[2 * k];
        double py = r[2 * k + 1];
        double ax = r[2 * a];
        double ay = r[2 * a + 1];
        double dx = r[2 * b] - ax;
        double dy = r[2 * b + 1] - ay;
        double len2 = dx * dx + dy * dy;
        double t = len2 == 0 ? 0 : Math.max(0, Math.min(1, ((px - ax) * dx + (py - ay) * dy) / len2));
        return Math.hypot(px - (ax + t * dx), py - (ay + t * dy));
    }

    /** @return the simplified ring, or null when fewer than three points remain */
    private static float[] simplify(float[] ring, double tol) {
        int n = ring.length / 2;
        boolean[] keep = new boolean[n];
        keep[0] = true;
        keep[n - 1] = true;
        int[] stack = new int[2 * n + 2];
        int top = 0;
        stack[top++] = 0;
        stack[top++] = n - 1;
        while (top > 0) {
            int b = stack[--top];
            int a = stack[--top];
            double far = -1;
            int at = -1;
            for (int k = a + 1; k < b; k++) {
                double d = distance(ring, k, a, b);
                if (d > far) {
                    far = d;
                    at = k;
                }
            }
            if (at >= 0 && far > tol) {
                keep[at] = true;
                stack[top++] = a;
                stack[top++] = at;
                stack[top++] = at;
                stack[top++] = b;
            }
        }
        int count = 0;
        for (boolean k : keep) {
            if (k) {
                count++;
            }
        }
        if (count < 3) {
            return null;
        }
        float[] out = new float[2 * count];
        int w = 0;
        for (int i = 0; i < n; i++) {
            if (keep[i]) {
                out[w++] = ring[2 * i];
                out[w++] = ring[2 * i + 1];
            }
        }
        return out;
    }

    public static LandData read(InputStream raw) throws IOException {
        DataInputStream in = new DataInputStream(raw);
        byte[] magic = new byte[4];
        in.readFully(magic);
        if (magic[0] != 'D' || magic[1] != 'M' || magic[2] != 'L' || magic[3] != 'D') {
            throw new IOException("not a land file");
        }
        if (in.readUnsignedByte() != 1) {
            throw new IOException("unknown land file version");
        }
        int nShapes = in.readUnsignedShort();
        if (nShapes > MAX_SHAPES) {
            throw new IOException("too many shapes");
        }
        float[][][] shapes = new float[nShapes][][];
        int total = 0;
        for (int s = 0; s < nShapes; s++) {
            int nRings = in.readUnsignedShort();
            if (nRings == 0 || nRings > MAX_RINGS) {
                throw new IOException("bad ring count");
            }
            shapes[s] = new float[nRings][];
            for (int r = 0; r < nRings; r++) {
                int n = in.readUnsignedShort();
                total += n;
                if (n < 3 || total > MAX_POINTS) {
                    throw new IOException("bad point count");
                }
                float[] ring = new float[n * 2];
                for (int i = 0; i < n; i++) {
                    ring[2 * i] = in.readShort() / 100f;
                    ring[2 * i + 1] = in.readShort() / 100f;
                }
                shapes[s][r] = ring;
            }
        }
        if (in.read() != -1) {
            throw new IOException("trailing bytes");
        }
        return new LandData(shapes);
    }
}
