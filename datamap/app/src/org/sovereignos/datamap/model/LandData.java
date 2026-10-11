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

    private LandData(float[][][] shapes) {
        this.shapes = shapes;
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
