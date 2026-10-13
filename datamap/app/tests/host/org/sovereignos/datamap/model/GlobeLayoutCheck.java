// SPDX-License-Identifier: Apache-2.0
package org.sovereignos.datamap.model;

public final class GlobeLayoutCheck {
    private static void near(double expected, double actual, String what) {
        if (!(Math.abs(expected - actual) <= 1e-3)) {
            throw new AssertionError(what + ": expected <" + expected + "> but was <" + actual + ">");
        }
    }

    public static void main(String[] args) {
        // the page at 412 x 828 dp: the globe is 192 from the top with radius 160, full screen 376 and 198
        GlobeLayout n = GlobeLayout.of(412, 828, false, 1f);
        near(192, n.centerY, "centre");
        near(160, n.radius, "radius");
        GlobeLayout f = GlobeLayout.of(412, 828, true, 1f);
        near(376, f.centerY, "full screen: the middle of the free space above the tab bar");
        near(198, f.radius, "full screen radius");
        // a taller phone keeps the top and the radius; full screen follows the free space
        near(192, GlobeLayout.of(412, 900, false, 1f).centerY, "taller: same top");
        near(412, GlobeLayout.of(412, 900, true, 1f).centerY, "taller: the middle of the free space");
        // the numbers are dp
        GlobeLayout d = GlobeLayout.of(1080, 2174, false, 2.625f);
        near(192 * 2.625, d.centerY, "dp to pixels: centre");
        near(160 * 2.625, d.radius, "dp to pixels: radius");
        // a narrow screen never lets the globe run off it, and a tiny one never gives a radius of 0 or less
        near(142, GlobeLayout.of(300, 828, false, 1f).radius, "narrow: half the width less 8");
        if (!(GlobeLayout.of(1, 1, false, 1f).radius >= 1f) || !(GlobeLayout.of(1, 1, true, 1f).radius >= 1f)) {
            throw new AssertionError("radius of a tiny view");
        }
        if (!(GlobeLayout.of(0, 0, false, 1f).radius >= 1f)) {
            throw new AssertionError("radius of no view");
        }
        // "Your apps" stands at 0.93 of the base radius below the centre, whatever the zoom
        near(192 + 160 * 0.93, n.anchorY(), "anchor");
        // the controls of the page: top 262 (zoom), 334 (centre), 316 (full screen); 104 lower in full screen
        near(262, GlobeLayout.controlTopDp(GlobeLayout.CONTROL_ZOOM, false), "zoom control");
        near(334, GlobeLayout.controlTopDp(GlobeLayout.CONTROL_CENTRE, false), "centre control");
        near(316, GlobeLayout.controlTopDp(GlobeLayout.CONTROL_FULL, false), "full screen control");
        near(366, GlobeLayout.controlTopDp(GlobeLayout.CONTROL_ZOOM, true), "zoom control in full screen");
        // the sheet: 422 dp, or the height less 60 when it is taller; never taller than the screen
        near(422, GlobeLayout.sheetHeightDp(828, false), "sheet");
        near(768, GlobeLayout.sheetHeightDp(828, true), "tall sheet");
        near(422, GlobeLayout.sheetHeightDp(450, true), "tall never shrinks the sheet");
        near(340, GlobeLayout.sheetHeightDp(400, false), "a short screen: the sheet leaves 60 at the top");
        System.out.println("PASS GlobeLayoutCheck");
    }
}
