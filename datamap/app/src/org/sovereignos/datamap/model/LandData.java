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

    private LandData(float[][][] shapes) {
        this.shapes = shapes;
        centreLat = new double[shapes.length];
        centreLon = new double[shapes.length];
        radius = new double[shapes.length];
        for (int s = 0; s < shapes.length; s++) {
            float[] ring = shapes[s][0];
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
