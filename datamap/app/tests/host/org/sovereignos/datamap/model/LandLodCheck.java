// SPDX-License-Identifier: Apache-2.0
package org.sovereignos.datamap.model;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public final class LandLodCheck {
    private static void check(boolean ok, String what) {
        if (!ok) {
            throw new AssertionError(what);
        }
    }

    /** Writes one shape per ring list; a ring is lon, lat pairs in degrees (hundredths in the file). */
    private static LandData land(float[][][] shapes) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        DataOutputStream o = new DataOutputStream(b);
        o.writeBytes("DMLD");
        o.writeByte(1);
        o.writeShort(shapes.length);
        for (float[][] shape : shapes) {
            o.writeShort(shape.length);
            for (float[] ring : shape) {
                o.writeShort(ring.length / 2);
                for (float v : ring) {
                    o.writeShort(Math.round(v * 100));
                }
            }
        }
        return LandData.read(new ByteArrayInputStream(b.toByteArray()));
    }

    public static void main(String[] args) throws Exception {
        // a square with 8 points on every side: the corners stay, the points on the sides go
        float[] square = new float[(4 * 8 + 1) * 2];
        int k = 0;
        for (int i = 0; i < 8; i++) {
            square[k++] = i * 10f / 8;
            square[k++] = 0;
        }
        for (int i = 0; i < 8; i++) {
            square[k++] = 10;
            square[k++] = i * 10f / 8;
        }
        for (int i = 0; i < 8; i++) {
            square[k++] = 10 - i * 10f / 8;
            square[k++] = 10;
        }
        for (int i = 0; i < 8; i++) {
            square[k++] = 0;
            square[k++] = 10 - i * 10f / 8;
        }
        square[k++] = 0;
        square[k++] = 0;
        // a small island that is smaller than the tolerance, and a hole that stays
        float[] island = {50, 50, 50.1f, 50, 50.1f, 50.1f, 50, 50.1f, 50, 50};
        float[] hole = {3, 3, 6, 3, 6, 6, 3, 6, 3, 3};
        LandData full = land(new float[][][] {{square, hole}, {island}});
        LandData lod = full.decimated(0.5);
        check(lod.shapes.length == 1, "the island is gone: " + lod.shapes.length);
        check(lod.shapes[0].length == 2, "the hole stays");
        check(lod.shapes[0][0].length / 2 <= 6, "the outline keeps its corners only: " + lod.shapes[0][0].length / 2);
        check(lod.shapes[0][0].length / 2 >= 4, "all four corners stay");
        // tolerance 0 changes nothing that matters: every point that is not on a straight line stays
        LandData same = full.decimated(0);
        check(same.shapes.length == 2, "tolerance 0 keeps the island");
        // a corner larger than the tolerance stays: a triangle of 3 points is never reduced further
        LandData tri = land(new float[][][] {{{0, 0, 10, 0, 5, 8, 0, 0}}}).decimated(1);
        check(tri.shapes.length == 1 && tri.shapes[0][0].length / 2 >= 3, "a triangle stays");
        // the reduced land is smaller on the real data: checked in the plan against land.bin
        System.out.println("PASS LandLodCheck");
    }
}
