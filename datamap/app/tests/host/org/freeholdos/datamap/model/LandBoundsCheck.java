// SPDX-License-Identifier: Apache-2.0
package org.freeholdos.datamap.model;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public final class LandBoundsCheck {
    private static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError(what);
    }

    private static void ring(DataOutputStream o, double... lonLat) throws IOException {
        o.writeShort(lonLat.length / 2);
        for (double v : lonLat) {
            o.writeShort((int) Math.round(v * 100));
        }
    }

    public static void main(String[] args) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        DataOutputStream o = new DataOutputStream(b);
        o.writeBytes("DMLD");
        o.writeByte(1);
        o.writeShort(4);
        o.writeShort(1);   // a square of 20 degrees around (0, 0)
        ring(o, -10, -10, 10, -10, 10, 10, -10, 10);
        o.writeShort(1);   // a square of 20 degrees around the date line
        ring(o, 170, -10, -170, -10, -170, 10, 170, 10);
        o.writeShort(1);   // a square in the north, around (60, 0)
        ring(o, -10, 50, 10, 50, 10, 70, -10, 70);
        o.writeShort(1);   // four points around the globe: their average is the middle of it, no centre
        ring(o, 0, 0, 180, 0, 90, 0, -90, 0);
        LandData d = LandData.read(new ByteArrayInputStream(b.toByteArray()));
        check(d.mayBeVisible(0, 0, 0), "the shape in the middle is seen from the middle");
        check(!d.mayBeVisible(1, 0, 0), "the shape on the date line is on the far side");
        check(d.mayBeVisible(1, 0, 180), "the shape on the date line is seen from there");
        check(!d.mayBeVisible(0, 0, 180), "and the first is not");
        check(d.mayBeVisible(0, 0, 100), "a shape whose edge is still over the horizon may show (100 < 90 + 14)");
        check(!d.mayBeVisible(0, 0, 120), "a shape wholly beyond the horizon is skipped (120 > 90 + 14)");
        check(d.mayBeVisible(1, 0, 120), "the date line shape from 120 is 60 degrees away");
        check(d.mayBeVisible(0, 80, 0), "seen from the north, the shape at the equator is near");
        check(d.mayBeVisible(2, 60, 0), "the northern shape is seen from the north");
        check(!d.mayBeVisible(2, -60, 0), "and is on the far side from the south");
        check(d.mayBeVisible(3, 0, 0) && d.mayBeVisible(3, 0, 180) && d.mayBeVisible(3, 80, 0),
                "a ring with no usable centre may be seen from anywhere");
        System.out.println("PASS LandBoundsCheck");
    }
}
