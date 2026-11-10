// SPDX-License-Identifier: Apache-2.0
package org.oblivios.datamap.model;

/**
 * Places the cards of the places so that they do not cover each other or the ring point of another place, and stay
 * on the screen: the rule of the design page. Each card takes the first of 15 positions around its point that is
 * free; if none is, the first one. All numbers are dp.
 */
public final class CardPlacer {
    private static final float MARGIN_X = 6;
    private static final float MARGIN_TOP = 14;
    private static final float MARGIN_BOTTOM = 110;
    private static final float GAP = 4;
    private static final float POINT_GAP = 6;

    private CardPlacer() {}

    private static float clampX(float x, float w, float areaW) {
        return Math.max(MARGIN_X, Math.min(areaW - w - MARGIN_X, x));
    }

    private static float clampY(float y, float areaH) {
        return Math.max(MARGIN_TOP, Math.min(areaH - MARGIN_BOTTOM, y));
    }

    /**
     * @param px x of each place's ring point, in the order the cards are placed (top to bottom)
     * @param py y of each point
     * @param w width of each card
     * @param h height of every card
     * @return x and y of the top left of each card, two numbers per card
     */
    public static float[] place(float[] px, float[] py, float[] w, float h, float areaW, float areaH) {
        int n = px.length;
        float[] out = new float[2 * n];
        for (int i = 0; i < n; i++) {
            float mx = px[i];
            float my = py[i];
            float wi = w[i];
            float[][] cand = {
                {mx - 22, my - h - 12}, {mx - wi + 22, my - h - 12}, {mx + 14, my - h / 2}, {mx - wi - 14, my - h / 2},
                {mx - 22, my + 13}, {mx - wi + 22, my + 13}, {mx - wi / 2, my - h - 16}, {mx + 14, my - h - 8},
                {mx - wi - 14, my - h - 8}, {mx + 14, my + 6}, {mx - wi - 14, my + 6}, {mx - 22, my - 2 * h - 18},
                {mx - wi + 22, my - 2 * h - 18}, {mx - 22, my + h + 19}, {mx - wi + 22, my + h + 19},
            };
            float bx = cand[0][0];
            float by = cand[0][1];
            for (float[] c : cand) {
                float x = clampX(c[0], wi, areaW);
                float y = clampY(c[1], areaH);
                boolean hit = false;
                for (int j = 0; j < i && !hit; j++) {
                    float ox = out[2 * j];
                    float oy = out[2 * j + 1];
                    hit = x < ox + w[j] + GAP && x + wi + GAP > ox && y < oy + h + GAP && y + h + GAP > oy;
                }
                for (int j = 0; j < n && !hit; j++) {
                    boolean own = Math.abs(px[j] - mx) < 1 && Math.abs(py[j] - my) < 1;
                    hit = !own && px[j] > x - POINT_GAP && px[j] < x + wi + POINT_GAP
                            && py[j] > y - POINT_GAP && py[j] < y + h + POINT_GAP;
                }
                if (!hit) {
                    bx = x;
                    by = y;
                    break;
                }
            }
            out[2 * i] = clampX(bx, wi, areaW);
            out[2 * i + 1] = clampY(by, areaH);
        }
        return out;
    }
}
