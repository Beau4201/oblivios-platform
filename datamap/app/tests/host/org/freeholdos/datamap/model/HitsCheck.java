// SPDX-License-Identifier: Apache-2.0
package org.freeholdos.datamap.model;

import java.util.ArrayList;
import java.util.List;

public final class HitsCheck {
    private static void check(boolean ok, String what) {
        if (!ok) {
            throw new AssertionError(what);
        }
    }

    private static Hits.Card card(String id, float l, float t, float r, float b, float a) {
        Hits.Card c = new Hits.Card();
        c.id = id;
        c.left = l;
        c.top = t;
        c.right = r;
        c.bottom = b;
        c.alpha = a;
        return c;
    }

    private static Hits.Point point(String id, float x, float y, float reach, float a) {
        Hits.Point p = new Hits.Point();
        p.id = id;
        p.x = x;
        p.y = y;
        p.reach = reach;
        p.alpha = a;
        return p;
    }

    public static void main(String[] args) {
        List<Hits.Card> cards = new ArrayList<>();
        List<Hits.Point> points = new ArrayList<>();
        cards.add(card("a", 100, 100, 220, 146, 1f));
        points.add(point("a", 160, 170, 24, 1f));
        points.add(point("b", 300, 170, 24, 1f));

        check("a".equals(Hits.pick(150, 120, cards, points)), "a touch on a card chooses its place");
        check("a".equals(Hits.pick(165, 175, cards, points)), "a touch near the ring point chooses its place");
        check("b".equals(Hits.pick(290, 180, cards, points)), "the other point");
        check(Hits.pick(250, 300, cards, points) == null, "a touch on the globe away from every place chooses none");
        check("a".equals(Hits.pick(160 + 24, 170, cards, points)), "exactly the reach counts");
        check(Hits.pick(160 + 25, 170, cards, points) == null, "one more does not");

        // a card is above a point: a touch on the card never chooses the place under it
        points.add(point("c", 150, 120, 24, 1f));
        check("a".equals(Hits.pick(150, 120, cards, points)), "the card wins over a point under it");

        // cards drawn later stand on top: where two overlap, the touch chooses the one drawn last
        List<Hits.Card> over = new ArrayList<>();
        over.add(card("under", 0, 0, 100, 46, 1f));
        over.add(card("top", 50, 0, 150, 46, 1f));
        check("top".equals(Hits.pick(75, 20, over, new ArrayList<Hits.Point>())), "the card drawn last wins");
        check("under".equals(Hits.pick(20, 20, over, new ArrayList<Hits.Point>())), "outside the overlap");
        // a faded top card lets the touch through to the one below
        over.get(1).alpha = 0.3f;
        check("under".equals(Hits.pick(75, 20, over, new ArrayList<Hits.Point>())), "a faded card is skipped");
        // the nearest point wins when two reach a touch
        List<Hits.Point> two = new ArrayList<>();
        two.add(point("p", 100, 100, 24, 1f));
        two.add(point("q", 110, 100, 24, 1f));
        check("q".equals(Hits.pick(106, 100, new ArrayList<Hits.Card>(), two)), "nearest");
        check("p".equals(Hits.pick(103, 100, new ArrayList<Hits.Card>(), two)), "nearest, the other way");

        // the limb rule: a place faded under half is not a target, a card or a point
        List<Hits.Card> faded = new ArrayList<>();
        faded.add(card("f", 0, 0, 100, 46, 0.4f));
        List<Hits.Point> fadedPoints = new ArrayList<>();
        fadedPoints.add(point("f", 50, 80, 24, 0.4f));
        check(Hits.pick(50, 20, faded, fadedPoints) == null, "a faded card");
        check(Hits.pick(50, 80, faded, fadedPoints) == null, "a faded point");
        fadedPoints.set(0, point("f", 50, 80, 24, 0.5f));
        check("f".equals(Hits.pick(50, 80, faded, fadedPoints)), "half is the first opacity that counts");
        System.out.println("PASS HitsCheck");
    }
}
