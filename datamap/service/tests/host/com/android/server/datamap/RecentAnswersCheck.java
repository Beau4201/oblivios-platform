// SPDX-License-Identifier: Apache-2.0
package com.android.server.datamap;

public final class RecentAnswersCheck {
    public static void main(String[] args) {
        RecentAnswers r = new RecentAnswers(3);
        r.put(10001, "198.51.100.1", "a.example");
        Check.eq("a.example", r.get(10001, "198.51.100.1"), "an answer is found for its app");
        Check.eq(null, r.get(10002, "198.51.100.1"), "another app does not see it");
        Check.eq(null, r.get(10001, "198.51.100.2"), "another address");
        r.put(10001, "198.51.100.2", "b.example");
        r.put(10001, "198.51.100.3", "c.example");
        r.get(10001, "198.51.100.1");                       // a read keeps it fresh
        r.put(10001, "198.51.100.4", "d.example");          // the oldest unread one goes: .2
        Check.eq(3, r.size(), "bounded");
        Check.eq("a.example", r.get(10001, "198.51.100.1"), "the fresh one stays");
        Check.eq(null, r.get(10001, "198.51.100.2"), "the oldest goes");

        // an IPv4 answer is found by the mapped IPv6 form of the same address
        RecentAnswers m = new RecentAnswers(3);
        m.put(10001, "198.51.100.1", "a.example");
        Check.eq("a.example", m.get(10001, "::ffff:198.51.100.1"), "mapped form finds the answer");
        m.put(10001, "::ffff:198.51.100.2", "b.example");
        Check.eq("b.example", m.get(10001, "198.51.100.2"), "and the other way round");

        // one app cannot push the answers of another app out
        RecentAnswers f = new RecentAnswers(3);
        f.put(10002, "198.51.100.9", "other.example");
        for (int i = 0; i < 100; i++) {
            f.put(10001, "198.51.100." + i, "h" + i + ".example");
        }
        Check.eq("other.example", f.get(10002, "198.51.100.9"), "the answer of another app stays");
        Check.eq(4, f.size(), "three of the flooding app and one of the other");

        // the names of a user go when that user is cleared or stopped, and only that user's
        RecentAnswers u = new RecentAnswers(3);
        u.put(10001, "198.51.100.1", "a.example");
        u.put(1010001, "198.51.100.2", "b.example");
        u.clearUser(0);
        Check.eq(null, u.get(10001, "198.51.100.1"), "user 0 is gone");
        Check.eq("b.example", u.get(1010001, "198.51.100.2"), "user 10 stays");
        Check.eq(1, u.size(), "one left");
        Check.passed("RecentAnswersCheck");
    }
}
