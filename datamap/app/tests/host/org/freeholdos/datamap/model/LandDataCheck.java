// SPDX-License-Identifier: Apache-2.0
package org.freeholdos.datamap.model;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

/** usage: LandDataCheck MINI.bin  where MINI.bin is made by make-land.py --tolerance 0 from tests/fixtures/mini.geojson */
public final class LandDataCheck {
    private static void check(boolean ok, String what) {
        if (!ok) {
            throw new AssertionError(what);
        }
    }

    private static void refuses(byte[] bytes, String what) {
        try {
            LandData.read(new ByteArrayInputStream(bytes));
        } catch (IOException e) {
            return;
        }
        throw new AssertionError("accepted: " + what);
    }

    /** A complete file of {@code shapes} polygons, each one ring of {@code points} points. */
    private static byte[] shapesFile(int shapes, int points) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        DataOutputStream o = new DataOutputStream(b);
        o.writeBytes("DMLD");
        o.writeByte(1);
        o.writeShort(shapes);
        for (int s = 0; s < shapes; s++) {
            o.writeShort(1);
            o.writeShort(points);
            for (int i = 0; i < points; i++) {
                o.writeShort(i * 100);
                o.writeShort(i % 2 == 0 ? 0 : 100);
            }
        }
        return b.toByteArray();
    }

    public static void main(String[] args) throws Exception {
        byte[] file = Files.readAllBytes(Paths.get(args[0]));
        LandData d = LandData.read(new ByteArrayInputStream(file));
        // AAA: one polygon with a hole; BBB: two polygons; ATA: left out
        check(d.shapes.length == 3, "three shapes: " + d.shapes.length);
        check(d.shapes[0].length == 2, "the first shape has an outline and a hole");
        check(d.shapes[0][0].length == 8, "the outline has four points (the closing point is dropped)");
        check(d.shapes[0][0][2] == 10f && d.shapes[0][0][3] == 0f, "the second point is 10, 0");
        check(d.shapes[0][1][0] == 2f && d.shapes[0][1][1] == 2f, "the hole starts at 2, 2");
        check(d.shapes[1].length == 1 && d.shapes[1][0].length == 6, "BBB first polygon");
        check(d.shapes[2][0][0] == -170.5f && d.shapes[2][0][1] == -60.25f, "negative and fractional values");

        refuses(new byte[0], "an empty file");
        refuses("XXXX".getBytes("US-ASCII"), "a wrong magic");
        byte[] wrongVersion = file.clone();
        wrongVersion[4] = 2;
        refuses(wrongVersion, "a wrong version");
        byte[] cut = java.util.Arrays.copyOf(file, file.length - 1);
        refuses(cut, "a truncated file");
        byte[] extra = java.util.Arrays.copyOf(file, file.length + 1);
        refuses(extra, "trailing bytes");
        byte[] manyShapes = file.clone();
        manyShapes[5] = (byte) 0xFF;
        manyShapes[6] = (byte) 0xFF;
        refuses(manyShapes, "65535 shapes");
        // the limits of a file that is complete: a truncated file would be refused anyway and prove nothing
        LandData.read(new ByteArrayInputStream(shapesFile(LandData.MAX_SHAPES, 3)));
        refuses(shapesFile(LandData.MAX_SHAPES + 1, 3), "one shape more than the limit");
        LandData.read(new ByteArrayInputStream(shapesFile(1, 3)));
        refuses(shapesFile(1, 2), "a ring of two points");
        if (args.length > 1) {   // the real data: every point is a valid place and every ring a polygon
            LandData real = LandData.read(new ByteArrayInputStream(Files.readAllBytes(Paths.get(args[1]))));
            check(real.shapes.length > 200, "the real data has more than 200 shapes");
            for (float[][] shape : real.shapes) {
                for (float[] ring : shape) {
                    check(ring.length >= 6, "a ring has three points at least");
                    for (int i = 0; i < ring.length; i += 2) {
                        check(Math.abs(ring[i]) <= 180f && Math.abs(ring[i + 1]) <= 90f, "a point is on the globe");
                    }
                }
            }
        }
        System.out.println("PASS LandDataCheck");
    }
}
