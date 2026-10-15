// SPDX-License-Identifier: Apache-2.0
package org.freeholdos.datamap.model;

/**
 * Where the globe stands on the map tab, in the numbers of the design page (ADR-160): 192 dp from the top with a
 * radius of 160 dp, and in full screen the middle of the space above the tab bar with a radius of 198 dp. It does
 * not depend on the sheet, which stands over the globe; so a tall sheet can never leave it without a radius.
 */
public final class GlobeLayout {
    public static final int CONTROL_ZOOM = 0;
    public static final int CONTROL_CENTRE = 1;
    public static final int CONTROL_FULL = 2;

    private static final float TOP_DP = 192;
    private static final float RADIUS_DP = 160;
    private static final float FS_RADIUS_DP = 198;
    /** In full screen the globe is centred in the height less this: the tab bar and its margin. */
    private static final float FS_FREE_BELOW_DP = 76;
    private static final float[] CONTROL_TOP_DP = {262, 346, 316};
    private static final float FS_CONTROL_SHIFT_DP = 104;
    private static final float SHEET_DP = 422;
    private static final float SHEET_TOP_MARGIN_DP = 60;
    /** "Your apps" stands this share of the radius below the centre of the globe. */
    private static final float ANCHOR_OF_RADIUS = 0.93f;

    /** The centre and radius in pixels. */
    public final float centerY;
    public final float radius;

    private GlobeLayout(float centerY, float radius) {
        this.centerY = centerY;
        this.radius = radius;
    }

    public static GlobeLayout of(float widthPx, float heightPx, boolean fullScreen, float density) {
        float wd = widthPx / density;
        float hd = heightPx / density;
        float cy = fullScreen ? (hd - FS_FREE_BELOW_DP) / 2 : TOP_DP;
        float r = Math.min(fullScreen ? FS_RADIUS_DP : RADIUS_DP, wd / 2 - 8);
        if (fullScreen) {
            r = Math.min(r, (hd - FS_FREE_BELOW_DP) / 2 - 8);
        }
        return new GlobeLayout(cy * density, Math.max(r * density, 1f));
    }

    /** Between two layouts, for the flight into and out of full screen. */
    public static GlobeLayout lerp(GlobeLayout a, GlobeLayout b, float t) {
        return new GlobeLayout(a.centerY + (b.centerY - a.centerY) * t, a.radius + (b.radius - a.radius) * t);
    }

    /** The y of the point "Your apps", in pixels. */
    public float anchorY() {
        return centerY + radius * ANCHOR_OF_RADIUS;
    }

    public static float controlTopDp(int control, boolean fullScreen) {
        return CONTROL_TOP_DP[control] + (fullScreen ? FS_CONTROL_SHIFT_DP : 0);
    }

    /** The height of the sheet over the map in dp: 422, or the screen less 60 when tall; never above the screen. */
    public static float sheetHeightDp(float screenHeightDp, boolean tall) {
        float cap = Math.max(0f, screenHeightDp - SHEET_TOP_MARGIN_DP);
        return tall ? cap : Math.min(SHEET_DP, cap);
    }
}
