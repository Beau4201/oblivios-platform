// SPDX-License-Identifier: Apache-2.0
package com.android.server.datamap;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class LinkDeriverCheck {
    private static DataMapStore.Row row(String pkg, String key, long first, long last) {
        return new DataMapStore.Row(Math.abs(pkg.hashCode()), pkg, key, first, last, 3, 1, 2, true);
    }

    public static void main(String[] args) throws Exception {
        Classifier c = Classifier.parse(ClassifierCheck.FIXTURE);
        long hour = 3_600_000L;
        long window = 5 * 60_000L;

        // two apps that talk to the same tracker could be linked
        List<DataMapStore.Row> rows = new ArrayList<>();
        rows.add(row("com.a", "facebook.com", 1000, 2000));
        rows.add(row("com.b", "facebook.com", 1500, 2500));
        rows.add(row("com.c", "adjust.com", 1000, 2000));
        List<LinkDeriver.Link> links = LinkDeriver.derive(rows, c, window);
        Check.eq(1, links.size(), "one pair shares a tracker");
        Check.eq("com.a", links.get(0).pkgA, "the pair is ordered");
        Check.eq("com.b", links.get(0).pkgB, "the pair is ordered (2)");
        Check.eq(LinkDeriver.SHARED_COMPANY, links.get(0).kind, "a shared company");
        Check.eq("facebook.com", links.get(0).evidence.get(0), "the evidence names the service");
        Check.isTrue(links.get(0).stronger, "close in time is stronger");

        // far apart in time: still a link, not a stronger one
        rows = new ArrayList<>();
        rows.add(row("com.a", "facebook.com", 0, hour));
        rows.add(row("com.b", "facebook.com", 10 * hour, 11 * hour));
        links = LinkDeriver.derive(rows, c, window);
        Check.eq(1, links.size(), "still linkable");
        Check.isTrue(!links.get(0).stronger, "far apart in time is not stronger");

        // a service that cannot link two apps never makes a link: cdn, core and unknown
        rows = new ArrayList<>();
        rows.add(row("com.a", "cloudflare.com", 1, 2));
        rows.add(row("com.b", "cloudflare.com", 1, 2));
        rows.add(row("com.a", "gstatic.com", 1, 2));
        rows.add(row("com.b", "gstatic.com", 1, 2));
        rows.add(row("com.a", "mystery.example", 1, 2));
        rows.add(row("com.b", "mystery.example", 1, 2));
        rows.add(row("com.a", "ip:203.0.113.9", 1, 2));
        rows.add(row("com.b", "ip:203.0.113.9", 1, 2));
        Check.eq(0, LinkDeriver.derive(rows, c, window).size(), "cdn, core, unknown and fixed addresses make no link");

        // one app alone has no partner
        rows = new ArrayList<>();
        rows.add(row("com.a", "facebook.com", 1, 2));
        Check.eq(0, LinkDeriver.derive(rows, c, window).size(), "one app, no link");
        Check.eq(0, LinkDeriver.derive(new ArrayList<>(), c, window).size(), "no rows, no link");

        // different services of the same owner link two apps by owner
        rows = new ArrayList<>();
        rows.add(row("com.a", "doubleclick.net", 1, 2));
        rows.add(row("com.b", "google-analytics.com", 1, 2));
        links = LinkDeriver.derive(rows, c, window);
        Check.eq(1, links.size(), "the same owner behind two services");
        Check.eq(LinkDeriver.SAME_OWNER, links.get(0).kind, "by owner");
        Check.eq("goog", links.get(0).evidence.get(0), "the evidence names the owner");
        Check.isTrue(!links.get(0).stronger, "an owner link is never stronger");

        // a shared service and the same owner in one pair: one link, by the shared service
        rows = new ArrayList<>();
        rows.add(row("com.a", "doubleclick.net", 1, 2));
        rows.add(row("com.b", "doubleclick.net", 1, 2));
        rows.add(row("com.a", "google-analytics.com", 1, 2));
        rows.add(row("com.b", "google-analytics.com", 1, 2));
        links = LinkDeriver.derive(rows, c, window);
        Check.eq(1, links.size(), "one link per pair");
        Check.eq(2, links.get(0).evidence.size(), "both shared services are the evidence");

        // the pair with more evidence comes first
        rows = new ArrayList<>();
        rows.add(row("com.a", "facebook.com", 1, 2));
        rows.add(row("com.a", "adjust.com", 1, 2));
        rows.add(row("com.b", "facebook.com", 1, 2));
        rows.add(row("com.b", "adjust.com", 1, 2));
        rows.add(row("com.c", "facebook.com", 1, 2));
        links = LinkDeriver.derive(rows, c, window);
        Check.eq(3, links.size(), "three pairs");
        Check.eq("com.a", links.get(0).pkgA, "the strongest pair first");
        Check.eq("com.b", links.get(0).pkgB, "the strongest pair first (2)");
        Check.eq(2, links.get(0).evidence.size(), "two services shared");

        // relevant relations: at least one end appears; the other end may be a company nobody contacts
        Set<String> appear = new HashSet<>();
        appear.add("adj");
        List<Classifier.Relation> rel = LinkDeriver.relevantRelations(c, appear);
        Check.eq(1, rel.size(), "the owner of Adjust is relevant");
        Check.eq("apl", rel.get(0).a, "the parent is on the other end");
        appear.add("goog");
        Check.eq(2, LinkDeriver.relevantRelations(c, appear).size(), "and Google's partner relation");
        Check.eq(0, LinkDeriver.relevantRelations(c, new HashSet<>()).size(), "nothing appears, nothing is relevant");

        // the reply is bounded: the strongest links are kept
        rows = new ArrayList<>();
        for (String p : new String[] {"com.a", "com.b", "com.c", "com.d", "com.e"}) {
            rows.add(row(p, "facebook.com", 1000, 2000));
        }
        List<LinkDeriver.Link> all = LinkDeriver.derive(rows, c, window);
        List<LinkDeriver.Link> some = LinkDeriver.derive(rows, c, window, 3);
        Check.eq(10, all.size(), "five apps make ten pairs");
        Check.eq(3, some.size(), "capped to three");
        Check.eq(all.get(0).pkgA + all.get(0).pkgB, some.get(0).pkgA + some.get(0).pkgB, "the same order as without the cap");
        Check.eq(10, LinkDeriver.derive(rows, c, window, 50).size(), "under the cap nothing is dropped");

        // the evidence of a link is bounded too: a reply is one Binder transaction, and two ad-heavy apps share hundreds
        // of service keys. The first pieces are kept, in order; the number of links is not changed.
        List<String> evidence = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            evidence.add("s" + i + ".example");
        }
        List<LinkDeriver.Link> big = new ArrayList<>();
        big.add(new LinkDeriver.Link("com.a", "com.b", LinkDeriver.SAME_OWNER, evidence, true));
        big.add(new LinkDeriver.Link("com.a", "com.c", LinkDeriver.SHARED_COMPANY, evidence.subList(0, 3), false));
        List<LinkDeriver.Link> capped = LinkDeriver.capEvidence(big, 16);
        Check.eq(2, capped.size(), "no link is dropped");
        Check.eq(16, capped.get(0).evidence.size(), "the evidence is cut to 16");
        Check.eq("s0.example", capped.get(0).evidence.get(0), "the first pieces are kept");
        Check.eq("s15.example", capped.get(0).evidence.get(15), "up to the 16th");
        Check.eq(3, capped.get(1).evidence.size(), "a link under the cap is as it was");
        Check.isTrue(capped.get(0).stronger && capped.get(0).kind == LinkDeriver.SAME_OWNER && capped.get(0).pkgB.equals("com.b"),
                "the rest of the link is as it was");
        Check.eq(40, big.get(0).evidence.size(), "the input is not changed");
        Check.eq(16, LinkDeriver.capEvidence(big, 16).get(0).evidence.size() + 0, "again");
        Check.eq(40, LinkDeriver.capEvidence(big, 40).get(0).evidence.size(), "a cap of exactly the size keeps all");
        Check.passed("LinkDeriverCheck");
    }
}
