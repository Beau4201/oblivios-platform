// SPDX-License-Identifier: Apache-2.0
package com.android.server.datamap;

public final class ClassifierCheck {
    /** A small, valid file. The names are fixtures for the checks, not data of the product. */
    static final String FIXTURE = String.join("\n",
            "# fixture",
            "datamap-classifier\t1",
            "source\tS-1\tFixture source one\tCC0-1.0\thttps://example.invalid/one",
            "source\tS-2\tFixture source two\tCC0-1.0\thttps://example.invalid/two",
            "owner\tmeta\tMeta\tMeta Platforms\tUS\tMenlo Park\t37.45\t-122.18\tS-1",
            "owner\tgoog\tGoogle\tAlphabet\tUS\tMountain View\t37.42\t-122.08\tS-1",
            "owner\tapl\tAppLovin\tAppLovin Corp.\tUS\tPalo Alto\t37.44\t-122.14\tS-2",
            "owner\tadj\tAdjust\tAdjust GmbH\tDE\tBerlin\t52.52\t13.40\tS-2",
            "service\tfacebook.com\tmeta\tanalytics\tS-1",
            "service\tdoubleclick.net\tgoog\tadvertising\tS-1",
            "service\tgoogle-analytics.com\tgoog\tanalytics\tS-1",
            "service\tadjust.com\tadj\tattribution\tS-2",
            "service\tcloudflare.com\t-\tcdn\tS-2",
            "service\tgstatic.com\tgoog\tcore\tS-1",
            "publisher\tcom.example.photos\tmeta\tS-1",
            "relation\tapl\towns\tadj\tsince 2021\tS-2",
            "relation\tgoog\tpartner_of\tmeta\t\tS-1",
            "");

    private static void bad(String why, String text) {
        Check.throwsA(ClassifierException.class, () -> Classifier.parse(text), why);
    }

    public static void main(String[] args) throws Exception {
        Classifier c = Classifier.parse(FIXTURE);
        Check.eq(5 + 1, c.serviceCount(), "six services");
        Check.eq(4, c.owners().size(), "four owners");
        Check.eq(2, c.relations().size(), "two relations");
        Check.eq("meta", c.publisherOf("com.example.photos"), "a publisher by package");
        Check.eq(null, c.publisherOf("com.example.other"), "an unlisted package");
        Check.eq("Menlo Park", c.owner("meta").city, "an owner's base");
        Check.eq(-122.18, c.owner("meta").lon, "an owner's longitude");
        Check.eq("CC0-1.0", c.source("S-1").licence, "a source has a licence");
        Check.eq("since 2021", c.relations().get(0).note, "a relation note");
        Check.eq("", c.relations().get(1).note, "an empty note is allowed");

        Check.eq("facebook.com", c.lookup("facebook.com").suffix, "exact");
        Check.eq("facebook.com", c.lookup("graph.facebook.com").suffix, "a subdomain");
        Check.eq(Category.ANALYTICS, c.lookup("a.b.facebook.com").category, "deeper");
        Check.eq(null, c.lookup("notfacebook.com"), "no match inside a label");
        Check.eq(null, c.lookup("com"), "a bare tld is not a service");
        Check.eq("-", c.lookup("x.cloudflare.com").ownerId, "a service with no single owner");
        Check.eq(null, c.lookup(null), "null host");
        Check.eq(0, Classifier.empty().serviceCount(), "the empty classifier");
        Check.eq(null, Classifier.empty().lookup("facebook.com"), "the empty classifier knows nothing");

        String head = "datamap-classifier\t1\n";
        String src = "source\tS-1\tt\tCC0-1.0\tu\n";
        String own = "owner\tmeta\tMeta\tMeta Platforms\tUS\tMenlo Park\t37.45\t-122.18\tS-1\n";
        String own2 = "owner\tgoog\tGoogle\tAlphabet\tUS\tMountain View\t37.42\t-122.08\tS-1\n";
        bad("an empty file", "");
        bad("no header", src + own);
        bad("a wrong header", "datamap-classifier\t2\n" + src);
        bad("an unknown record", head + src + "thing\tx\n");
        bad("an owner without a source record", head + own);
        bad("an owner with a source id that does not exist", head + src
                + "owner\tmeta\tMeta\tMeta Platforms\tUS\tMenlo Park\t37.45\t-122.18\tS-9\n");
        bad("an owner with an empty source", head + src
                + "owner\tmeta\tMeta\tMeta Platforms\tUS\tMenlo Park\t37.45\t-122.18\t\n");
        bad("a country of three letters", head + src
                + "owner\tmeta\tMeta\tMeta Platforms\tUSA\tMenlo Park\t37.45\t-122.18\tS-1\n");
        bad("a lower-case country", head + src
                + "owner\tmeta\tMeta\tMeta Platforms\tus\tMenlo Park\t37.45\t-122.18\tS-1\n");
        bad("a latitude out of range", head + src
                + "owner\tmeta\tMeta\tMeta Platforms\tUS\tMenlo Park\t91\t-122.18\tS-1\n");
        bad("a longitude out of range", head + src
                + "owner\tmeta\tMeta\tMeta Platforms\tUS\tMenlo Park\t37\t-181\tS-1\n");
        bad("a coordinate that is not a number", head + src
                + "owner\tmeta\tMeta\tMeta Platforms\tUS\tMenlo Park\tNaN\t-122.18\tS-1\n");
        bad("an owner id with a capital", head + src
                + "owner\tMeta\tMeta\tMeta Platforms\tUS\tMenlo Park\t37.45\t-122.18\tS-1\n");
        bad("a duplicate owner", head + src + own + own);
        bad("a duplicate source", head + src + src);
        bad("a source without a licence", head + "source\tS-1\tt\t\tu\n");
        bad("a source with too few fields", head + "source\tS-1\tt\tCC0-1.0\n");
        bad("a service of an unknown owner", head + src + "service\tx.com\tnobody\tanalytics\tS-1\n");
        bad("a service with an unknown category", head + src + own + "service\tx.com\tmeta\tspyware\tS-1\n");
        bad("a service without a source", head + src + own + "service\tx.com\tmeta\tanalytics\t\n");
        bad("a service suffix in capitals", head + src + own + "service\tX.com\tmeta\tanalytics\tS-1\n");
        bad("a service suffix that is not a name", head + src + own + "service\tx y.com\tmeta\tanalytics\tS-1\n");
        bad("a duplicate service", head + src + own + "service\tx.com\tmeta\tanalytics\tS-1\nservice\tx.com\tmeta\tadvertising\tS-1\n");
        bad("a publisher of an unknown owner", head + src + "publisher\tcom.a\tnobody\tS-1\n");
        bad("a publisher without a source", head + src + own + "publisher\tcom.a\tmeta\t\n");
        bad("a package name with a slash", head + src + own + "publisher\tcom/a\tmeta\tS-1\n");
        bad("a relation without a source", head + src + own + own2 + "relation\tmeta\towns\tgoog\t\t\n");
        bad("a relation with an unknown source", head + src + own + own2 + "relation\tmeta\towns\tgoog\t\tS-9\n");
        bad("a relation of a company to itself", head + src + own + "relation\tmeta\towns\tmeta\t\tS-1\n");
        bad("a relation with an unknown type", head + src + own + own2 + "relation\tmeta\tstole_from\tgoog\t\tS-1\n");
        bad("a relation to an unknown owner", head + src + own + "relation\tmeta\towns\tnobody\t\tS-1\n");
        bad("a duplicate relation", head + src + own + own2
                + "relation\tmeta\towns\tgoog\t\tS-1\nrelation\tmeta\towns\tgoog\tagain\tS-1\n");
        bad("a line that is far too long", head + src + "#" + "x".repeat(Classifier.MAX_LINE + 1) + "\n");
        StringBuilder many = new StringBuilder(head + src);
        for (int i = 0; i < Classifier.MAX_LINES; i++) {
            many.append("#\n");
        }
        bad("more lines than allowed", many.toString());
        Check.isTrue(Classifier.parse(head + src + own + own2 + "relation\tmeta\towns\tgoog\t\tS-1\r\n") != null,
                "windows line endings are accepted");
        Check.passed("ClassifierCheck");
    }
}
