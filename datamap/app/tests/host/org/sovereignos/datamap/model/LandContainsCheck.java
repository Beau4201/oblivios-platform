// SPDX-License-Identifier: Apache-2.0
package org.sovereignos.datamap.model;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;

public final class LandContainsCheck {
    private static void check(boolean ok, String what) {
        if (!ok) {
            throw new AssertionError(what);
        }
    }

    private static void ring(DataOutputStream o, float... lonLat) throws Exception {
        o.writeShort(lonLat.length / 2);
        for (float v : lonLat) {
            o.writeShort(Math.round(v * 100));
        }
    }

    public static void main(String[] args) throws Exception {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        DataOutputStream o = new DataOutputStream(b);
        o.writeBytes("DMLD");
        o.writeByte(1);
        o.writeShort(2);
        o.writeShort(2);   // a square with a hole
        ring(o, 0, 0, 10, 0, 10, 10, 0, 10, 0, 0);
        ring(o, 2, 2, 4, 2, 4, 4, 2, 4, 2, 2);
        o.writeShort(1);   // a triangle
        ring(o, 20, 0, 30, 0, 25, 8, 20, 0);
        LandData d = LandData.read(new ByteArrayInputStream(b.toByteArray()));
        check(d.contains(5, 5), "inside the square");
        check(!d.contains(3, 3), "inside the hole");
        check(!d.contains(11, 5), "outside");
        check(!d.contains(-1, 5), "outside the west edge");
        check(d.contains(1, 25), "inside the triangle");
        check(!d.contains(9, 25), "above the triangle");
        check(!d.contains(50, 50), "far away");
        check(d.shapeAt(5, 5) == 0 && d.shapeAt(1, 25) == 1 && d.shapeAt(50, 50) == -1, "which shape");
        float[] tri = d.bounds(1);
        check(tri[0] == 20 && tri[1] == 30 && tri[2] == 0 && tri[3] == 8, "bounds of the triangle");
        System.out.println("PASS LandContainsCheck");
    }
}
