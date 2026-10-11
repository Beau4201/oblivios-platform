// SPDX-License-Identifier: Apache-2.0
package com.android.server.datamap;

import java.util.ArrayList;
import java.util.List;

public final class DataMapStoreCheck {
    private static DataMapStore.Row find(List<DataMapStore.Row> rows, int uid, String key) {
        for (DataMapStore.Row r : rows) {
            if (r.uid == uid && r.key.equals(key)) {
                return r;
            }
        }
        return null;
    }

    public static void main(String[] args) {
        // counts, first and last, foreground and background
        DataMapStore s = new DataMapStore(256, 4000);
        s.record(0, 10100, "com.a", "x.com", false, true, 1000);
        s.record(0, 10100, "com.a", "x.com", true, true, 3000);
        s.record(0, 10100, "com.a", "x.com", false, false, 2000);
        DataMapStore.Row r = find(s.snapshot(0), 10100, "x.com");
        Check.eq(3, r.count, "three contacts");
        Check.eq(1, r.fg, "one in the foreground");
        Check.eq(2, r.bg, "two in the background");
        Check.eq(1000L, r.firstWall, "first seen");
        Check.eq(3000L, r.lastWall, "last seen is the latest, not the last call");
        Check.isTrue(r.viaDns, "one contact came through a DNS answer");
        Check.eq("com.a", r.pkg, "the package");

        // users do not see each other
        s.record(10, 1010100, "com.a", "y.com", false, true, 5000);
        Check.eq(1, s.snapshot(0).size(), "user 0 sees only its own");
        Check.eq(1, s.snapshot(10).size(), "user 10 sees only its own");
        Check.eq(null, find(s.snapshot(0), 1010100, "y.com"), "user 0 has nothing of user 10");
        Check.eq(0, s.snapshot(99).size(), "an unknown user has nothing");

        // per-uid cap: a full uid evicts its own least recently seen entry
        DataMapStore c = new DataMapStore(3, 100);
        c.record(0, 10100, "com.a", "a1.com", false, true, 1);
        c.record(0, 10100, "com.a", "a2.com", false, true, 2);
        c.record(0, 10100, "com.a", "a3.com", false, true, 3);
        c.record(0, 10100, "com.a", "a1.com", false, true, 4);        // a1 is fresh again
        c.record(0, 10100, "com.a", "a4.com", false, true, 5);        // evicts a2
        Check.eq(3, c.sizeFor(0, 10100), "capped at three");
        Check.eq(null, find(c.snapshot(0), 10100, "a2.com"), "the least recently seen goes");
        Check.isTrue(find(c.snapshot(0), 10100, "a1.com") != null, "a fresh entry stays");

        // flooding: one app cannot push another app's entries out (MAP-009)
        DataMapStore f = new DataMapStore(256, 4000);
        f.record(0, 10200, "com.b", "b1.com", false, true, 1);
        f.record(0, 10200, "com.b", "b2.com", false, true, 1);
        f.record(0, 10200, "com.b", "b3.com", true, true, 1);
        for (int i = 0; i < 20000; i++) {
            f.record(0, 10100, "com.a", "flood" + i + ".example", false, true, 10 + i);
        }
        Check.eq(256, f.sizeFor(0, 10100), "the flooder is held at the per-uid cap");
        Check.eq(3, f.sizeFor(0, 10200), "the other app keeps all of its entries");
        Check.eq(259, f.size(), "the total is the sum");

        // total cap: a full store evicts from the uid that holds most, so a small app is not touched
        DataMapStore t = new DataMapStore(50, 60);
        for (int i = 0; i < 50; i++) {
            t.record(0, 10100, "com.a", "a" + i + ".com", false, true, i);
        }
        for (int i = 0; i < 10; i++) {
            t.record(0, 10200, "com.b", "b" + i + ".com", false, true, i);
        }
        Check.eq(60, t.size(), "the store is full");
        t.record(0, 10300, "com.c", "c0.com", false, true, 99);       // a new app arrives
        Check.eq(60, t.size(), "still at the cap");
        Check.eq(10, t.sizeFor(0, 10200), "the small app lost nothing");
        Check.eq(49, t.sizeFor(0, 10100), "the largest app gave one up");
        Check.eq(1, t.sizeFor(0, 10300), "the newcomer is in");

        // invalid names are counted and never stored
        f.countInvalid(0);
        f.countInvalid(0);
        Check.eq(2, f.invalidCount(0), "invalid count");
        Check.eq(0, f.invalidCount(10), "per user");

        // versions grow with a change and are per user
        long v0 = s.version(0);
        s.record(0, 10100, "com.a", "x.com", false, true, 9000);
        Check.isTrue(s.version(0) > v0, "a change raises the version");
        Check.eq(1L, s.version(10), "the other user is untouched");

        // restore merges and respects the caps
        DataMapStore m = new DataMapStore(256, 4000);
        m.record(0, 10100, "com.a", "x.com", true, false, 5000);
        List<DataMapStore.Row> old = new ArrayList<>();
        old.add(new DataMapStore.Row(10100, "com.a", "x.com", 1000, 4000, 5, 1, 4, true));
        old.add(new DataMapStore.Row(10100, "com.a", "z.com", 2000, 2500, 2, 0, 2, true));
        m.restore(0, old);
        DataMapStore.Row mx = find(m.snapshot(0), 10100, "x.com");
        Check.eq(6, mx.count, "counts add up");
        Check.eq(2, mx.fg, "foreground adds up");
        Check.eq(4, mx.bg, "background adds up");
        Check.eq(1000L, mx.firstWall, "the earlier first seen wins");
        Check.eq(5000L, mx.lastWall, "the later last seen wins");
        Check.isTrue(mx.viaDns, "dns is sticky");
        Check.eq(2, m.size(), "one merged, one added");

        // clear removes a user and frees the room
        s.clearUser(10);
        Check.eq(0, s.snapshot(10).size(), "cleared");
        Check.eq(1, s.size(), "the total follows");
        Check.eq(0L, s.version(10), "the counter is gone too");
        s.clearUser(10);
        s.clearUser(77);
        Check.eq(1, s.size(), "clearing nothing changes nothing");

        // the snapshot is in a fixed order
        DataMapStore o = new DataMapStore(256, 4000);
        o.record(0, 10300, "com.c", "b.com", false, true, 1);
        o.record(0, 10100, "com.a", "z.com", false, true, 1);
        o.record(0, 10100, "com.a", "a.com", false, true, 1);
        List<DataMapStore.Row> rows = o.snapshot(0);
        Check.eq("a.com", rows.get(0).key, "uid first, then key");
        Check.eq("z.com", rows.get(1).key, "uid first, then key (2)");
        Check.eq(10300, rows.get(2).uid, "uid first, then key (3)");

        // an empty day restored into a user that has nothing yet must not fail: a cleared history
        // leaves an empty day in the file, and the next unlock restores it
        DataMapStore empty = new DataMapStore(256, 4000);
        empty.restore(7, new ArrayList<DataMapStore.Row>());
        Check.eq(0, empty.snapshot(7).size(), "an empty restore adds nothing");

        // the reply is bounded: the newest rows are kept, in their order
        List<DataMapStore.Row> many = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            many.add(new DataMapStore.Row(1, "p", "k" + i, i, 100 + i, 1, 1, 0, true));
        }
        List<DataMapStore.Row> top = DataMapStore.newest(many, 3);
        Check.eq(3, top.size(), "capped to three");
        Check.eq("k7", top.get(0).key, "the newest three, oldest of them first (1)");
        Check.eq("k9", top.get(2).key, "the newest three, oldest of them first (2)");
        Check.eq(10, DataMapStore.newest(many, 50).size(), "under the cap nothing is dropped");
        Check.passed("DataMapStoreCheck");
    }
}
