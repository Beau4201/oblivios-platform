// SPDX-License-Identifier: Apache-2.0
package org.oblivios.datamap.model;

public final class CardPlacerCheck {
    private static void check(boolean ok, String what) {
        if (!ok) {
            throw new AssertionError(what);
        }
    }

    private static boolean overlap(float[] p, float[] w, int i, int j, float h) {
        return p[2 * i] < p[2 * j] + w[j] && p[2 * i] + w[i] > p[2 * j]
                && p[2 * i + 1] < p[2 * j + 1] + h && p[2 * i + 1] + h > p[2 * j + 1];
    }

    public static void main(String[] args) {
        // one card: above the point, its left edge 22 left of it (the first candidate of the page)
        float[] pos = CardPlacer.place(new float[] {200}, new float[] {300}, new float[] {120}, 46, 412, 766);
        check(pos[0] == 178 && pos[1] == 242, "first candidate: " + pos[0] + "," + pos[1]);

        // the card stays on the screen: 6 from the sides, 14 from the top and 110 from the bottom
        pos = CardPlacer.place(new float[] {5}, new float[] {5}, new float[] {120}, 46, 412, 766);
        check(pos[0] == 6 && pos[1] == 14, "clamped to the top left: " + pos[0] + "," + pos[1]);
        pos = CardPlacer.place(new float[] {410}, new float[] {760}, new float[] {120}, 46, 412, 766);
        check(pos[0] <= 412 - 120 - 6 && pos[1] <= 766 - 110 - 46 + 46, "clamped to the bottom right");
        check(pos[1] + 0 <= 766 - 110, "above the bottom margin: " + pos[1]);
        // a card wider than the screen is pushed to the left edge, not off it
        pos = CardPlacer.place(new float[] {200}, new float[] {300}, new float[] {500}, 46, 412, 766);
        check(pos[0] == 6, "wider than the screen: " + pos[0]);

        // two places at the same spot: the second takes the next candidate and the cards do not overlap
        float[] xs = {200, 200};
        float[] ys = {300, 300};
        float[] ws = {120, 120};
        pos = CardPlacer.place(xs, ys, ws, 46, 412, 766);
        check(!overlap(pos, ws, 0, 1, 46), "two cards overlap");
        check(pos[0] == 178 && pos[1] == 242 && !(pos[2] == 178 && pos[3] == 242), "the second moved");

        // a card does not cover the ring point of another place
        xs = new float[] {200, 215};
        ys = new float[] {300, 250};
        ws = new float[] {120, 120};
        pos = CardPlacer.place(xs, ys, ws, 46, 412, 766);
        boolean covers = xs[1] > pos[0] - 6 && xs[1] < pos[0] + 120 + 6 && ys[1] > pos[1] - 6 && ys[1] < pos[1] + 46 + 6;
        check(!covers, "the first card covers the point of the second");

        // nothing to place
        check(CardPlacer.place(new float[0], new float[0], new float[0], 46, 412, 766).length == 0, "empty");
        System.out.println("PASS CardPlacerCheck");
    }
}
