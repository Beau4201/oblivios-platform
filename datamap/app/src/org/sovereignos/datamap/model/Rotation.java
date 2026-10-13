// SPDX-License-Identifier: Apache-2.0
package org.sovereignos.datamap.model;

/**
 * The turn of the globe as a 3 by 3 matrix, so that a point given as a unit vector is moved to the screen with nine
 * multiplications and no trigonometry. It is the same orientation as {@link GlobeMath#project}: x to the right,
 * y up, depth toward the viewer; the view centre {@code (lat0, lon0)} lands in the middle of the disk.
 */
public final class Rotation {
    private final double m0;
    private final double m2;
    private final double m3;
    private final double m4;
    private final double m5;
    private final double m6;
    private final double m7;
    private final double m8;

    public Rotation(double lonDeg, double latDeg) {
        double cl = Math.cos(Math.toRadians(lonDeg));
        double sl = Math.sin(Math.toRadians(lonDeg));
        double cp = Math.cos(Math.toRadians(latDeg));
        double sp = Math.sin(Math.toRadians(latDeg));
        m0 = cl;
        m2 = -sl;
        m3 = -sp * sl;
        m4 = cp;
        m5 = -sp * cl;
        m6 = cp * sl;
        m7 = sp;
        m8 = cp * cl;
    }

    /** Only the depth: whether a point is in front, without the other two numbers. */
    public float depth(float x, float y, float z) {
        return (float) (m6 * x + m7 * y + m8 * z);
    }

    /** @param out receives x, y and depth */
    public void apply(float x, float y, float z, float[] out) {
        out[0] = (float) (m0 * x + m2 * z);
        out[1] = (float) (m3 * x + m4 * y + m5 * z);
        out[2] = (float) (m6 * x + m7 * y + m8 * z);
    }
}
