// SPDX-License-Identifier: Apache-2.0
package org.freeholdos.datamap.model;

import java.util.List;

/**
 * Which place a touch on the map chooses. A card counts where it is drawn; a ring point counts within its reach, the
 * nearest first. A place faded to under half opacity at the horizon is not a target (ADR-159 limb rule). A touch that
 * chooses nothing closes the choice.
 */
public final class Hits {
    private Hits() {}

    public static final class Card {
        public String id;
        public float left;
        public float top;
        public float right;
        public float bottom;
        public float alpha = 1f;
    }

    public static final class Point {
        public String id;
        public float x;
        public float y;
        public float reach;
        public float alpha = 1f;
    }

    /** @return the id of the place, or null */
    public static String pick(float x, float y, List<Card> cards, List<Point> points) {
        // the cards are listed in the order they are drawn; the last one is on top
        for (int i = cards.size() - 1; i >= 0; i--) {
            Card c = cards.get(i);
            if (GlobeMath.tappable(c.alpha) && x >= c.left && x <= c.right && y >= c.top && y <= c.bottom) {
                return c.id;
            }
        }
        String best = null;
        float bestD = Float.MAX_VALUE;
        for (Point p : points) {
            if (!GlobeMath.tappable(p.alpha)) {
                continue;
            }
            float dx = p.x - x;
            float dy = p.y - y;
            float d = dx * dx + dy * dy;
            if (d <= p.reach * p.reach && d < bestD) {
                bestD = d;
                best = p.id;
            }
        }
        return best;
    }
}
