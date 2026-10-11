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
        Check.passed("RecentAnswersCheck");
    }
}
