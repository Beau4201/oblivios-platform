// SPDX-License-Identifier: Apache-2.0
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.freeholdos.exit.Choices;
import org.freeholdos.exit.Consent;
import org.freeholdos.exit.ControlCodec;
import org.freeholdos.exit.CompanyNames;
import org.freeholdos.exit.Ladder;
import org.freeholdos.exit.Policy;
import org.freeholdos.exit.Presenter;
import org.freeholdos.exit.ProfileRows;
import org.freeholdos.exit.TunNet;
import org.freeholdos.exit.UidRanges;

/** Host checks of the pure Java of the exit app (no Android class). Exits 1 on the first failure. */
public final class HostChecks {
    private static void check(boolean ok, String what) {
        if (!ok) {
            System.out.println("FAIL: " + what);
            System.exit(1);
        }
    }

    private static String str(List<int[]> ranges) {
        StringBuilder b = new StringBuilder();
        for (int[] r : ranges) {
            b.append('[').append(r[0]).append('-').append(r[1]).append(']');
        }
        return b.toString();
    }

    private static String str(List<Policy.Cls> classes, boolean kinds) {
        StringBuilder b = new StringBuilder();
        for (Policy.Cls c : classes) {
            b.append(c.kind.name().charAt(0)).append('[').append(c.start).append('-').append(c.end).append(']');
        }
        return b.toString();
    }

    private static void throwsIae(Runnable r, String what) {
        try {
            r.run();
        } catch (IllegalArgumentException e) {
            return;
        }
        check(false, what + " must throw");
    }

    // ---- UidRanges ------------------------------------------------------------------------------------------------

    static void uidRanges() {
        check(Arrays.equals(UidRanges.forUser(0), new int[] {10000, 19999}), "user 0 range");
        check(Arrays.equals(UidRanges.forUser(10), new int[] {1010000, 1019999}), "user 10 range");
        check(Arrays.equals(UidRanges.forUser(21474), new int[] {2147410000, 2147419999}), "largest user");
        throwsIae(() -> UidRanges.forUser(-1), "negative user");
        throwsIae(() -> UidRanges.forUser(21475), "user past int range");

        check(str(UidRanges.coalesce(new int[] {})).equals(""), "no uids");
        check(str(UidRanges.coalesce(new int[] {10005})).equals("[10005-10005]"), "one uid");
        check(str(UidRanges.coalesce(new int[] {10003, 10001, 10002})).equals("[10001-10003]"), "adjacent uids, unsorted");
        check(str(UidRanges.coalesce(new int[] {10001, 10001, 10002})).equals("[10001-10002]"), "duplicates are one");
        check(str(UidRanges.coalesce(new int[] {10001, 10003})).equals("[10001-10001][10003-10003]"), "a gap stays");
        int[] input = {10003, 10001};
        UidRanges.coalesce(input);
        check(input[0] == 10003, "the input array is not sorted in place");

        List<int[]> all = Arrays.asList(new int[] {10000, 19999});
        check(str(UidRanges.subtract(all, Arrays.asList(new int[] {10500, 10600}))).equals("[10000-10499][10601-19999]"), "cut from the middle");
        check(str(UidRanges.subtract(all, Arrays.asList(new int[] {10000, 10000}))).equals("[10001-19999]"), "cut at the start");
        check(str(UidRanges.subtract(all, Arrays.asList(new int[] {19999, 19999}))).equals("[10000-19998]"), "cut at the end");
        check(str(UidRanges.subtract(all, Arrays.asList(new int[] {10000, 19999}))).equals(""), "cut all");
        check(str(UidRanges.subtract(all, Arrays.asList(new int[] {0, 30000}))).equals(""), "cut more than all");
        check(str(UidRanges.subtract(all, Arrays.asList(new int[] {20000, 30000}))).equals("[10000-19999]"), "cut outside");
        check(str(UidRanges.subtract(all, Arrays.asList(new int[] {10100, 10100}, new int[] {10102, 10103}))).equals("[10000-10099][10101-10101][10104-19999]"), "two cuts");
        check(str(UidRanges.subtract(Arrays.asList(new int[] {10000, 10010}, new int[] {20000, 20010}), Arrays.asList(new int[] {10005, 20005}))).equals("[10000-10004][20006-20010]"), "a cut across two ranges");
        check(str(UidRanges.subtract(Arrays.asList(new int[] {2147410000, 2147419999}), Arrays.asList(new int[] {2147419999, 2147419999}))).equals("[2147410000-2147419998]"), "no int overflow at the top");
    }

    // ---- Choices --------------------------------------------------------------------------------------------------

    static void choices() {
        Choices c = new Choices();
        check(c.choiceOf("org.example.app") == Choices.Choice.DIRECT, "default is Direct");
        check(c.set("org.example.app", Choices.Choice.TOR), "set Tor");
        check(c.choiceOf("org.example.app") == Choices.Choice.TOR, "Tor taken");
        check(c.setShowsAddress("org.example.app", 7, true) && c.setShowsAddress("org.example.app", 300, true), "show taken");
        check(Arrays.equals(c.shownCompanies("org.example.app"), new int[] {7, 300}), "companies sorted");
        check(c.showsAddress("org.example.app", 7) && !c.showsAddress("org.example.app", 8), "showsAddress");
        check(!c.setShowsAddress("org.example.app", 0, true) && !c.setShowsAddress("org.example.app", 65536, true), "company range");
        check(!c.set("not a package", Choices.Choice.TOR) && !c.set("single", Choices.Choice.TOR) && !c.set("1a.b", Choices.Choice.TOR), "bad names refused");
        check(!c.set(null, Choices.Choice.TOR), "null name refused");

        Choices back = Choices.parse(c.serialize());
        check(!back.damaged(), "a written file reads clean");
        check(back.serialize().equals(c.serialize()), "round trip");
        check(back.choiceOf("org.example.app") == Choices.Choice.TOR && back.showsAddress("org.example.app", 300), "round trip content");

        c.set("org.example.app", Choices.Choice.DIRECT);
        check(c.choiceOf("org.example.app") == Choices.Choice.DIRECT, "back to Direct");
        c.setShowsAddress("org.example.app", 7, false);
        c.setShowsAddress("org.example.app", 300, false);
        check(c.packagesWithOverrides().isEmpty(), "no overrides left");

        // Nothing there yet is not damage; anything unreadable is, and gives the defaults.
        check(!Choices.parse("").damaged() && !Choices.parse(null).damaged(), "no file is not damage");
        for (String junk : new String[] {"garbage", "exit-choices\t2\ntor\torg.a.b\n", "\u0000\u0001", "exit-choices 1\n", "tor\torg.a.b\n"}) {
            Choices j = Choices.parse(junk);
            check(j.damaged() && j.torApps().isEmpty(), "junk is damage with defaults: " + junk.length());
        }
        Choices part = Choices.parse(Choices.HEADER + "\ntor\torg.good.app\ntor\tbad name\nshow\torg.good.app\tx\nshow\torg.good.app\t7\nunknown\tx\n");
        check(part.damaged(), "a bad line is damage");
        check(part.choiceOf("org.good.app") == Choices.Choice.TOR && part.showsAddress("org.good.app", 7), "the good lines are kept");

        // Bounds.
        Choices full = new Choices();
        for (int i = 0; i < Choices.MAX_TOR_APPS; i++) {
            check(full.set("org.app.p" + i, Choices.Choice.TOR), "tor app " + i);
        }
        check(!full.set("org.app.extra", Choices.Choice.TOR), "the 49th Tor app is refused");
        check(full.set("org.app.p0", Choices.Choice.TOR), "an app already on Tor can be set again");
        check(full.set("org.app.extra", Choices.Choice.DIRECT), "Direct is always taken");
        Choices shown = new Choices();
        for (int i = 0; i < Choices.MAX_SHOWN; i++) {
            check(shown.setShowsAddress("org.app.p" + (i / 200), 1 + i % 200, true), "show " + i);
        }
        check(!shown.setShowsAddress("org.app.other", 1, true), "the table of shown addresses is bounded");
        check(shown.setShowsAddress("org.app.p0", 1, true), "an existing entry can be set again");
        StringBuilder big = new StringBuilder(Choices.HEADER).append('\n');
        while (big.length() <= Choices.MAX_FILE_CHARS) {
            big.append("tor\torg.a.b\n");
        }
        check(Choices.parse(big.toString()).damaged(), "a file over the size limit is damaged");
        check(!Choices.validPackage("a.b.") && !Choices.validPackage("a..b") && !Choices.validPackage("a.b-c") && Choices.validPackage("a.b_c9"), "package grammar");
        check(!Choices.validPackage("a." + "x".repeat(260)) && Choices.validPackage("a." + "x".repeat(253)), "package length 255");
        check(!Choices.validPackage("a." + "x".repeat(254)), "package length 256 is refused");
        check(Choices.MAX_TOR_APPS == 48 && Choices.MAX_OPEN_APPS == 24 && Choices.MAX_SHOWN == 256 && Choices.MAX_FILE_CHARS == 65536, "the limits of Choices");

        // The third stand: never masked (ADR-188). One app has one stand.
        Choices o = new Choices();
        check(o.set("org.o.app", Choices.Choice.OPEN) && o.choiceOf("org.o.app") == Choices.Choice.OPEN && o.openApps().contains("org.o.app"), "set Open");
        check(o.torApps().isEmpty(), "an open app is not on Tor");
        check(o.set("org.o.app", Choices.Choice.TOR) && o.choiceOf("org.o.app") == Choices.Choice.TOR && o.openApps().isEmpty(), "Open to Tor leaves the open list");
        check(o.set("org.o.app", Choices.Choice.OPEN) && o.torApps().isEmpty() && o.choiceOf("org.o.app") == Choices.Choice.OPEN, "Tor to Open leaves the Tor list");
        check(o.set("org.o.app", Choices.Choice.DIRECT) && o.choiceOf("org.o.app") == Choices.Choice.DIRECT && o.openApps().isEmpty(), "back to Direct");
        check(!o.set("bad name", Choices.Choice.OPEN) && !o.accepts("bad name", Choices.Choice.OPEN) && !o.accepts(null, Choices.Choice.DIRECT), "bad names are refused for Open too");
        Choices openFull = new Choices();
        for (int i = 0; i < Choices.MAX_OPEN_APPS; i++) {
            check(openFull.set("org.open.p" + i, Choices.Choice.OPEN), "open app " + i);
        }
        check(!openFull.set("org.open.extra", Choices.Choice.OPEN) && !openFull.accepts("org.open.extra", Choices.Choice.OPEN), "the 25th open app is refused");
        check(openFull.openApps().size() == 24 && openFull.choiceOf("org.open.extra") == Choices.Choice.DIRECT, "and changes nothing");
        check(openFull.set("org.open.p0", Choices.Choice.OPEN) && openFull.accepts("org.open.p0", Choices.Choice.OPEN), "an open app can be set again");
        check(openFull.set("org.open.extra", Choices.Choice.TOR) && openFull.set("org.open.extra", Choices.Choice.DIRECT), "the full open list does not stop Tor or Direct");
        check(openFull.accepts("org.open.extra", Choices.Choice.DIRECT) && openFull.accepts("org.open.extra", Choices.Choice.TOR), "accepts agrees");
        // A refused move leaves the old stand: the 49th Tor app that is Open stays Open.
        Choices torFull = new Choices();
        for (int i = 0; i < Choices.MAX_TOR_APPS; i++) {
            torFull.set("org.t.p" + i, Choices.Choice.TOR);
        }
        torFull.set("org.t.open", Choices.Choice.OPEN);
        check(!torFull.set("org.t.open", Choices.Choice.TOR) && torFull.choiceOf("org.t.open") == Choices.Choice.OPEN, "a refused move keeps the old stand");
        Choices oback = Choices.parse(openFull.serialize());
        check(!oback.damaged() && oback.serialize().equals(openFull.serialize()) && oback.openApps().size() == 24, "open lines round trip");
        check(Choices.parse(Choices.HEADER + "\ntor\torg.a.b\nopen\torg.a.b\n").damaged(), "an app on a tor line and an open line is damage");
        check(Choices.parse(Choices.HEADER + "\nopen\torg.a.b\ntor\torg.a.b\n").damaged(), "and the other way round");
        check(!Choices.parse(Choices.HEADER + "\nopen\torg.a.b\nopen\torg.a.b\n").damaged(), "the same open line twice is one open app");
        check(Choices.parse(Choices.HEADER + "\nopen\tbad name\n").damaged(), "a bad package on an open line is damage");
        StringBuilder many = new StringBuilder(Choices.HEADER).append('\n');
        for (int i = 0; i <= Choices.MAX_OPEN_APPS; i++) {
            many.append("open\torg.p.p").append(i).append('\n');
        }
        Choices pm = Choices.parse(many.toString());
        check(pm.damaged() && pm.openApps().size() == 24, "a file with 25 open apps is damaged and keeps 24");
    }

    // ---- Consent --------------------------------------------------------------------------------------------------

    static void consent() {
        for (Consent c : Consent.values()) {
            check(Consent.parse(c.serialize()) == c, "consent round trip " + c);
        }
        for (String junk : new String[] {null, "", "ON", "OFF", "Off", "yes", "true", "1", "on ", "onn"}) {
            check(Consent.parse(junk) == Consent.UNSET, "unreadable consent is UNSET: " + junk);
        }
    }

    // ---- Policy ---------------------------------------------------------------------------------------------------

    private static Policy.App app(String pkg, int uid) {
        return new Policy.App(pkg, uid);
    }

    private static Choices tor(String... pkgs) {
        Choices c = new Choices();
        for (String p : pkgs) {
            check(c.set(p, Choices.Choice.TOR), "tor " + p);
        }
        return c;
    }

    static void policy() {
        List<Policy.App> apps = Arrays.asList(
                app("org.a.one", 10100), app("org.a.two", 10101), app("org.a.three", 10200),
                app("org.freeholdos.exit", 10050), app("app.seamlessupdate.client", 10060));
        int[] users = {0};

        // No consent: nothing masked, no steering. Tor does not wait for the consent: it is the user's own choice.
        Policy.Out out = Policy.compute(tor("org.a.one"), apps, users, Consent.UNSET, false, false);
        check(str(out.classes, true).equals("E[10050-10050]E[10060-10060]T[10100-10100]"), "no consent: Tor and Exempt only: " + str(out.classes, true));
        check(out.steer.isEmpty(), "no consent: no steering");
        check(str(out.guard).equals("[10100-10100]"), "no consent: the Tor guard stays");
        check(out.oemPrefs.size() == 1 && out.oemPrefs.get("org.a.one") == Policy.OEM_PRIVATE_ONLY && Policy.OEM_PRIVATE_ONLY == 4, "preference for the Tor app");
        for (Consent c : new Consent[] {Consent.OFF}) {
            check(Policy.compute(tor(), apps, users, c, false, false).steer.isEmpty(), "consent OFF: no steering");
        }

        // Consent ON, two Tor apps: Tor (two ranges, adjacent uids join), Masked is the user range minus them and the
        // exempt apps, Exempt are the exit app and the updater.
        out = Policy.compute(tor("org.a.one", "org.a.two", "org.a.three"), apps, users, Consent.ON, false, false);
        check(str(out.classes, true).equals(
                "M[10000-10049]E[10050-10050]M[10051-10059]E[10060-10060]M[10061-10099]T[10100-10101]M[10102-10199]T[10200-10200]M[10201-19999]"),
                "classes: " + str(out.classes, true));
        check(str(out.guard).equals("[10100-10101][10200-10200]"), "guard: " + str(out.guard));
        check(str(out.steer).equals("[10000-10049][10051-10059][10061-10099][10102-10199][10201-19999]"), "steer: " + str(out.steer));
        // Sorted and disjoint, as the daemon requires.
        int next = 10000;
        for (Policy.Cls c : out.classes) {
            check(c.start >= next && c.start <= c.end, "sorted and disjoint at " + c.start);
            next = c.end + 1;
        }

        // The exit app and the updater are never Tor or Masked, even when chosen.
        out = Policy.compute(tor("org.freeholdos.exit", "app.seamlessupdate.client"), apps, users, Consent.ON, false, false);
        check(out.guard.isEmpty() && out.oemPrefs.isEmpty(), "exempt apps are never Tor");
        for (Policy.Cls c : out.classes) {
            if (c.kind == Policy.Kind.MASKED) {
                check(!(c.start <= 10050 && 10050 <= c.end) && !(c.start <= 10060 && 10060 <= c.end), "exempt uid is never masked");
            }
        }

        // A package that shares its uid with the exit app (a shared user id) is exempt, whatever else is chosen for it.
        List<Policy.App> shared = Arrays.asList(app("org.freeholdos.exit", 10050), app("org.shared.tor", 10050), app("org.a.one", 10100));
        out = Policy.compute(tor("org.shared.tor", "org.a.one"), shared, users, Consent.ON, false, false);
        check(str(out.guard).equals("[10100-10100]"), "a shared uid with an exempt app is never Tor: " + str(out.guard));

        // A package installed later extends the Tor range only when chosen; the masked range already covers its uid.
        List<Policy.App> later = new ArrayList<>(apps);
        later.add(app("org.a.new", 10102));
        Policy.Out before = Policy.compute(tor("org.a.one"), later, users, Consent.ON, false, false);
        check(str(before.guard).equals("[10100-10100]"), "an unchosen new package is not Tor");
        Policy.Out chosen = Policy.compute(tor("org.a.one", "org.a.new"), later, users, Consent.ON, false, false);
        check(str(chosen.guard).equals("[10100-10100][10102-10102]"), "a chosen new package is Tor: " + str(chosen.guard));

        // Several users: each has its own app range, and a package on Tor is Tor in every user where it is installed.
        List<Policy.App> two = Arrays.asList(app("org.a.one", 10100), app("org.a.one", 1010100), app("org.freeholdos.exit", 10050), app("org.freeholdos.exit", 1010050));
        out = Policy.compute(tor("org.a.one"), two, new int[] {10, 0}, Consent.ON, false, false);
        check(str(out.guard).equals("[10100-10100][1010100-1010100]"), "Tor in both users: " + str(out.guard));
        check(str(out.steer).equals("[10000-10049][10051-10099][10101-19999][1010000-1010049][1010051-1010099][1010101-1019999]"), "masked in both users: " + str(out.steer));
        out = Policy.compute(tor("org.a.one"), two, new int[] {0, 0, 10}, Consent.ON, false, false);
        check(str(out.steer).equals("[10000-10049][10051-10099][10101-19999][1010000-1010049][1010051-1010099][1010101-1019999]"), "a repeated user is one");

        // A package with a system uid has no place in the table.
        out = Policy.compute(tor("org.sys.app", "org.hi.app"), Arrays.asList(app("org.sys.app", 1000), app("org.neg.app", -5), app("org.hi.app", 20000)),
                users, Consent.ON, false, false);
        check(out.guard.isEmpty() && out.oemPrefs.isEmpty(), "a system uid and a uid above the app ids are never in a range");
        check(out.classes.size() == 1 && out.classes.get(0).start == 10000 && out.classes.get(0).end == 19999, "the whole range is masked");

        // Warnings.
        out = Policy.compute(tor(), apps, users, Consent.ON, true, true);
        check(out.warnings.contains(Policy.Warning.VPN_ACTIVE) && out.warnings.contains(Policy.Warning.PRIVATE_DNS_LIMITED), "both warnings");
        out = Policy.compute(tor(), apps, users, Consent.UNSET, true, true);
        check(out.warnings.contains(Policy.Warning.VPN_ACTIVE) && !out.warnings.contains(Policy.Warning.PRIVATE_DNS_LIMITED), "the DNS limit matters only for the masked tier");
        out = Policy.compute(tor(), apps, users, Consent.ON, false, false);
        check(out.warnings.isEmpty(), "no warning without a reason");

        // Limits of the daemon (255 classes) and the helper (128 ranges per list).
        List<Policy.App> many = new ArrayList<>();
        Choices manyTor = new Choices();
        for (int i = 0; i < 48; i++) {
            many.add(app("org.m.p" + i, 10100 + 2 * i)); // every second uid: no two ranges join
            manyTor.set("org.m.p" + i, Choices.Choice.TOR);
        }
        out = Policy.compute(manyTor, many, users, Consent.ON, false, false);
        check(out.guard.size() == 48 && !out.warnings.contains(Policy.Warning.MASK_TIER_OFF_TOO_MANY_APPS), "48 Tor apps fit");
        check(out.classes.size() <= Policy.MAX_CLASSES && out.steer.size() <= Policy.MAX_RANGES, "within the limits");
        // Three users with 48 non-adjacent Tor apps each: 144 guard ranges, past 128.
        List<Policy.App> three = new ArrayList<>();
        for (int u = 0; u < 3; u++) {
            for (int i = 0; i < 48; i++) {
                three.add(app("org.m.p" + i, u * 100000 + 10100 + 2 * i));
            }
        }
        out = Policy.compute(manyTor, three, new int[] {0, 1, 2}, Consent.ON, false, false);
        check(out.warnings.contains(Policy.Warning.TOO_MANY_TOR_APPS), "144 guard ranges raise the warning");
        check(out.guard.size() == Policy.MAX_RANGES, "the guard is cut at 128");
        check(out.steer.isEmpty() && out.warnings.contains(Policy.Warning.MASK_TIER_OFF_TOO_MANY_APPS), "and the masked tier goes off");
        // The table the daemon gets is within its limit.
        check(out.classes.size() <= Policy.MAX_CLASSES, "classes within limit");
        // The steer limit is 128 ranges. Three users, every app in each: 42 Tor apps give 126 guard ranges and 129 masked
        // ranges (one more than each Tor uid, plus the stretch above the last in each user): the masked tier goes off. With
        // two apps in two users only, 125 guard ranges give 128 masked ranges: it stays on.
        Choices edge = new Choices();
        List<Policy.App> edgeApps = new ArrayList<>();
        for (int i = 0; i < 42; i++) {
            edge.set("org.e.p" + i, Choices.Choice.TOR);
            for (int u = 0; u < 3; u++) {
                edgeApps.add(app("org.e.p" + i, u * 100000 + 10100 + 2 * i));
            }
        }
        out = Policy.compute(edge, edgeApps, new int[] {0, 1, 2}, Consent.ON, false, false);
        check(out.guard.size() == 126 && out.steer.isEmpty() && out.warnings.contains(Policy.Warning.MASK_TIER_OFF_TOO_MANY_APPS), "129 masked ranges: off");
        edgeApps.remove(edgeApps.size() - 1);
        edgeApps.remove(edgeApps.size() - 1);
        edgeApps.remove(edgeApps.size() - 1);
        edgeApps.add(app("org.e.p41", 10100 + 82));
        edgeApps.add(app("org.e.p41", 100000 + 10100 + 82));
        out = Policy.compute(edge, edgeApps, new int[] {0, 1, 2}, Consent.ON, false, false);
        check(out.guard.size() == 125 && out.steer.size() == 128 && out.warnings.isEmpty(), "128 masked ranges: on: guard=" + out.guard.size() + " steer=" + out.steer.size());

        // 48 Tor apps in two users: 96 guard ranges and 98 masked ranges, both inside the limits, so the tier stays on.
        Choices c2 = new Choices();
        List<Policy.App> holes = new ArrayList<>();
        for (int i = 0; i < 48; i++) {
            c2.set("org.h.p" + i, Choices.Choice.TOR);
            holes.add(app("org.h.p" + i, 10100 + 2 * i));
            holes.add(app("org.h.p" + i, 100000 + 10100 + 2 * i));
        }
        out = Policy.compute(c2, holes, new int[] {0, 1}, Consent.ON, false, false);
        check(out.guard.size() == 96 && out.steer.size() == 98 && out.warnings.isEmpty() && out.classes.size() == 194,
                "guard=" + out.guard.size() + " steer=" + out.steer.size() + " classes=" + out.classes.size());
    }

    static void policyOpen() {
        List<Policy.App> apps = Arrays.asList(
                app("org.a.one", 10100), app("org.a.two", 10101), app("org.a.three", 10200),
                app("org.freeholdos.exit", 10050), app("app.seamlessupdate.client", 10060));
        int[] users = {0};
        Choices c = new Choices();
        c.set("org.a.two", Choices.Choice.OPEN);
        Policy.Out out = Policy.compute(c, apps, users, Consent.ON, false, false);
        check(str(out.classes, true).equals("M[10000-10049]E[10050-10050]M[10051-10059]E[10060-10060]M[10061-10100]E[10101-10101]M[10102-19999]"),
                "an open app is Exempt: " + str(out.classes, true));
        check(str(out.steer).equals("[10000-10049][10051-10059][10061-10100][10102-19999]"), "and is not steered: " + str(out.steer));
        check(out.guard.isEmpty() && out.oemPrefs.isEmpty() && out.warnings.isEmpty(), "no Tor, no warning");
        // Without the consent nothing is masked, and an open app is still Exempt (the daemon does nothing for it).
        out = Policy.compute(c, apps, users, Consent.UNSET, false, false);
        check(str(out.classes, true).equals("E[10050-10050]E[10060-10060]E[10101-10101]") && out.steer.isEmpty(), "no consent: Exempt only: " + str(out.classes, true));
        // Tor, Open and Direct together; the table is sorted and disjoint.
        c.set("org.a.one", Choices.Choice.TOR);
        out = Policy.compute(c, apps, users, Consent.ON, false, false);
        check(str(out.classes, true).equals("M[10000-10049]E[10050-10050]M[10051-10059]E[10060-10060]M[10061-10099]T[10100-10100]E[10101-10101]M[10102-19999]"),
                "Tor, Open and Masked: " + str(out.classes, true));
        int next = 10000;
        for (Policy.Cls k : out.classes) {
            check(k.start >= next && k.start <= k.end, "sorted and disjoint at " + k.start);
            next = k.end + 1;
        }
        // An open app that shares its uid with the exit app or with an app on Tor does not change that uid.
        List<Policy.App> shared = Arrays.asList(app("org.freeholdos.exit", 10050), app("org.shared.open", 10050), app("org.b.tor", 10100), app("org.shared.open2", 10100));
        Choices s2 = new Choices();
        s2.set("org.shared.open", Choices.Choice.OPEN);
        s2.set("org.shared.open2", Choices.Choice.OPEN);
        s2.set("org.b.tor", Choices.Choice.TOR);
        out = Policy.compute(s2, shared, users, Consent.ON, false, false);
        check(str(out.guard).equals("[10100-10100]") && out.classes.stream().filter(k -> k.kind == Policy.Kind.EXEMPT).count() == 1, "a shared uid keeps its stronger stand: " + str(out.classes, true));
        // Several users: each user's open app is Exempt in its own range.
        List<Policy.App> two = Arrays.asList(app("org.a.one", 10100), app("org.a.one", 1010100));
        Choices o2 = new Choices();
        o2.set("org.a.one", Choices.Choice.OPEN);
        out = Policy.compute(o2, two, new int[] {0, 10}, Consent.ON, false, false);
        check(str(out.classes, true).equals("M[10000-10099]E[10100-10100]M[10101-19999]M[1010000-1010099]E[1010100-1010100]M[1010101-1019999]"), "open in both users: " + str(out.classes, true));
        // The limits: the open ranges are cut at the room that the system packages leave, and the table stays inside 256.
        Choices lots = new Choices();
        List<Policy.App> lotsApps = new ArrayList<>();
        lotsApps.add(app("org.freeholdos.exit", 10050));
        for (int u = 0; u < 3; u++) {
            for (int i = 0; i < 24; i++) {
                lots.set("org.l.p" + i, Choices.Choice.OPEN);
                lotsApps.add(app("org.l.p" + i, u * 100000 + 10100 + 2 * i));
            }
        }
        out = Policy.compute(lots, lotsApps, new int[] {0, 1, 2}, Consent.ON, false, false);
        int exemptRanges = 0;
        for (Policy.Cls k : out.classes) {
            if (k.kind == Policy.Kind.EXEMPT) {
                exemptRanges++;
            }
        }
        check(Policy.MAX_EXEMPT_RANGES == 64 && exemptRanges == 64, "72 open ranges are cut at 64 together with the system packages: " + exemptRanges);
        check(out.warnings.contains(Policy.Warning.TOO_MANY_OPEN_APPS), "and the app says so");
        check(out.classes.size() <= Policy.MAX_CLASSES, "inside the daemon's table: " + out.classes.size());
        // 24 open apps in one user fit without a warning.
        Choices fit = new Choices();
        List<Policy.App> fitApps = new ArrayList<>();
        for (int i = 0; i < 24; i++) {
            fit.set("org.f.p" + i, Choices.Choice.OPEN);
            fitApps.add(app("org.f.p" + i, 10100 + 2 * i));
        }
        out = Policy.compute(fit, fitApps, new int[] {0}, Consent.ON, false, false);
        check(!out.warnings.contains(Policy.Warning.TOO_MANY_OPEN_APPS), "24 open apps in one user: no warning");
        // The worst case together: 48 Tor apps, 24 open apps, three users. The masked tier goes off before the table overflows.
        Choices worst = new Choices();
        List<Policy.App> worstApps = new ArrayList<>();
        for (int u = 0; u < 3; u++) {
            for (int i = 0; i < 48; i++) {
                worst.set("org.w.t" + i, Choices.Choice.TOR);
                worstApps.add(app("org.w.t" + i, u * 100000 + 10100 + 2 * i));
            }
            for (int i = 0; i < 24; i++) {
                worst.set("org.w.o" + i, Choices.Choice.OPEN);
                worstApps.add(app("org.w.o" + i, u * 100000 + 11100 + 2 * i));
            }
        }
        out = Policy.compute(worst, worstApps, new int[] {0, 1, 2}, Consent.ON, false, false);
        check(out.classes.size() <= Policy.MAX_CLASSES && out.steer.size() <= Policy.MAX_RANGES, "the worst case fits the daemon: " + out.classes.size());
        // A table that would be 257 long turns the masked tier off instead of being refused by the daemon.
        Choices edge = new Choices();
        List<Policy.App> edgeApps = new ArrayList<>();
        for (int i = 0; i < 48; i++) {
            edge.set("org.x.t" + i, Choices.Choice.TOR);
            for (int u = 0; u < 2; u++) {
                edgeApps.add(app("org.x.t" + i, u * 100000 + 10100 + 4 * i));
            }
        }
        for (int i = 0; i < 24; i++) {
            edge.set("org.x.o" + i, Choices.Choice.OPEN);
            for (int u = 0; u < 2; u++) {
                edgeApps.add(app("org.x.o" + i, u * 100000 + 10102 + 4 * i));
            }
        }
        out = Policy.compute(edge, edgeApps, new int[] {0, 1}, Consent.ON, false, false);
        check(out.classes.size() <= Policy.MAX_CLASSES, "never more than the daemon accepts: " + out.classes.size());

        // The table limit is reached with 128 Tor ranges, 61 open ranges and 3 system ranges around 69 masked ranges (every Tor
        // uid, open uid, Tor uid in a row keeps its masked gap): 261 classes, with the steer list inside its own limit.
        // The masked tier goes off instead of the daemon refusing the table.
        Choices rows = new Choices();
        List<Policy.App> rowApps = new ArrayList<>();
        for (int u = 0; u < 3; u++) {
            int base = u * 100000;
            rowApps.add(app("org.freeholdos.exit", base + 10050));
            for (int k = 0; k < 21; k++) {
                int x = base + 10100 + 4 * k;
                rows.set("org.q.t" + (2 * k), Choices.Choice.TOR);
                rows.set("org.q.t" + (2 * k + 1), Choices.Choice.TOR);
                rows.set("org.q.o" + k, Choices.Choice.OPEN);
                rowApps.add(app("org.q.t" + (2 * k), x));
                rowApps.add(app("org.q.o" + k, x + 1));
                rowApps.add(app("org.q.t" + (2 * k + 1), x + 2));
            }
            rows.set("org.q.t42", Choices.Choice.TOR);
            rowApps.add(app("org.q.t42", base + 10300));
        }
        out = Policy.compute(rows, rowApps, new int[] {0, 1, 2}, Consent.ON, false, false);
        check(out.warnings.contains(Policy.Warning.TOO_MANY_TOR_APPS) && out.warnings.contains(Policy.Warning.TOO_MANY_OPEN_APPS), "both cuts apply: " + out.warnings);
        check(out.steer.isEmpty() && out.warnings.contains(Policy.Warning.MASK_TIER_OFF_TOO_MANY_APPS),
                "the masked tier goes off when the table would be too long: steer=" + out.steer.size() + " classes=" + out.classes.size());
        check(out.classes.size() <= Policy.MAX_CLASSES, "and the table stays inside the daemon's limit: " + out.classes.size());

        // Exactly one open range more than the room: 63 open ranges fit next to one system range, the 64th is cut and said.
        Choices cut = new Choices();
        List<Policy.App> cutApps = new ArrayList<>();
        cutApps.add(app("org.freeholdos.exit", 10050));
        for (int u = 0; u < 3; u++) {
            for (int k = 0; k < 21; k++) {
                cut.set("org.c.o" + k, Choices.Choice.OPEN);
                cutApps.add(app("org.c.o" + k, u * 100000 + 10100 + 2 * k));
            }
        }
        out = Policy.compute(cut, cutApps, new int[] {0, 1, 2}, Consent.ON, false, false);
        check(!out.warnings.contains(Policy.Warning.TOO_MANY_OPEN_APPS), "63 open ranges and one system range fit");
        cut.set("org.c.extra", Choices.Choice.OPEN);
        cutApps.add(app("org.c.extra", 10400));
        out = Policy.compute(cut, cutApps, new int[] {0, 1, 2}, Consent.ON, false, false);
        int exempts = 0;
        for (Policy.Cls k : out.classes) {
            if (k.kind == Policy.Kind.EXEMPT) {
                exempts++;
            }
        }
        check(out.warnings.contains(Policy.Warning.TOO_MANY_OPEN_APPS) && exempts == Policy.MAX_EXEMPT_RANGES, "the 64th open range is cut and said: " + exempts);
    }

    // ---- Ladder ---------------------------------------------------------------------------------------------------

    static void ladder() {
        Ladder l = new Ladder();
        check(l.shouldNotify(10100, 7, 1000), "the first report notifies");
        check(!l.shouldNotify(10100, 7, 2000), "the second inside the window does not");
        check(l.shouldNotify(10100, 8, 2000), "another company does");
        check(l.shouldNotify(10101, 7, 2000), "another app does");
        check(!l.shouldNotify(10100, 7, 1000 + Ladder.WINDOW_MS), "exactly at the end of the window it is still the same window");
        check(l.shouldNotify(10100, 7, 1000 + Ladder.WINDOW_MS + 1), "after the window it notifies again");
        check(!l.shouldNotify(10100, 7, 1000 + Ladder.WINDOW_MS + 2), "and the new window holds");
        check(l.size() == 3, "keys of an old window are forgotten: " + l.size());
        check(Ladder.WINDOW_MS == 600000 && Ladder.MAX_KEYS == 4096, "the numbers of ladder.rs");
        // A flood of keys cannot grow the table, and the oldest key goes first.
        Ladder f = new Ladder();
        for (int i = 0; i < Ladder.MAX_KEYS; i++) {
            check(f.shouldNotify(10000 + i, 1, i), "key " + i);
        }
        check(f.size() == Ladder.MAX_KEYS, "full");
        check(f.shouldNotify(99999, 1, 5000), "a new key at the limit notifies");
        check(f.size() == Ladder.MAX_KEYS, "still bounded");
        check(f.shouldNotify(10000, 1, 5001), "the oldest key was evicted, so it notifies again");
        check(!f.shouldNotify(10001 + 1, 1, 5002), "a younger key is still held");
    }

    // ---- Presenter and CompanyNames -------------------------------------------------------------------------------

    static void presenter() {
        List<Presenter.Listed> apps = Arrays.asList(
                new Presenter.Listed("org.z.zeta", "Zeta", 10102), new Presenter.Listed("org.a.also", "alpha", 10103), new Presenter.Listed("org.a.alpha", "alpha", 10100),
                new Presenter.Listed("org.b.beta", "Beta Reader", 10101), new Presenter.Listed("org.freeholdos.exit", "Compartments", 10050),
                new Presenter.Listed("app.seamlessupdate.client", "Updater", 10060)
        );
        Choices c = new Choices();
        List<Presenter.AppRow> rows = Presenter.appRows(c, apps, "");
        StringBuilder order = new StringBuilder();
        for (Presenter.AppRow r : rows) {
            order.append(r.pkg).append(r.tor ? "+" : "").append(' ');
        }
        check(order.toString().equals("org.a.alpha org.a.also org.b.beta org.z.zeta "), "sorted by label, then package, without the exempt apps: " + order);
        check(Presenter.appRows(c, apps, "ALPH").size() == 2, "search by label in any case");
        check(Presenter.appRows(c, apps, "  org.b ").size() == 1, "search by package, trimmed");
        check(Presenter.appRows(c, apps, "READER").size() == 1, "search by a word of the label that has capitals");
        check(Presenter.appRows(c, apps, "nothing").isEmpty() && Presenter.appRows(c, apps, null).size() == 4, "no match, null query");
        check(Presenter.appRows(c, apps, "compart").isEmpty(), "the exit app is not listed");
        // A package with a system uid is not listed: Policy.compute would skip it, so a Tor choice would never be applied (C2-03).
        List<Presenter.Listed> withSystem = new ArrayList<>(apps);
        withSystem.add(new Presenter.Listed("android.sys.tool", "System Tool", 1000));
        withSystem.add(new Presenter.Listed("android.sys.low", "Low Tool", 9999));
        withSystem.add(new Presenter.Listed("org.u1.app", "Second User App", 110050));
        check(Presenter.appRows(c, withSystem, "").size() == 5 && Presenter.appRows(c, withSystem, "tool").isEmpty()
                && Presenter.appRows(c, withSystem, "second").size() == 1, "system uids are not listed, an app of another user is");
        check(!UidRanges.isAppUid(-1) && !UidRanges.isAppUid(1000) && !UidRanges.isAppUid(9999) && UidRanges.isAppUid(10000)
                && UidRanges.isAppUid(19999) && !UidRanges.isAppUid(20000) && !UidRanges.isAppUid(100999) && UidRanges.isAppUid(110100)
                && !UidRanges.isAppUid(120000), "the app uid range of every user");

        // A tap on an app row, then the policy: the app is Tor and its uid is in the guard.
        check(Arrays.equals(Presenter.options(), new Choices.Choice[] {Choices.Choice.DIRECT, Choices.Choice.TOR, Choices.Choice.OPEN}), "the three choices of the dialog");
        check(Presenter.appRows(c, apps, "").get(0).choice == Choices.Choice.DIRECT && !Presenter.appRows(c, apps, "").get(0).tor, "the row starts at Direct");
        c.set("org.a.alpha", Choices.Choice.TOR);
        check(Presenter.appRows(c, apps, "").get(0).tor && Presenter.appRows(c, apps, "").get(0).choice == Choices.Choice.TOR, "the row shows Tor");
        // The exit follows the place of an Isolated app only when the app is on Tor (ADR-208).
        check(Presenter.exitFor("ES", "NL", Choices.Choice.DIRECT) == Presenter.Exit.MISMATCH, "a place abroad on Direct disagrees");
        check(Presenter.exitFor("ES", "NL", Choices.Choice.OPEN) == Presenter.Exit.MISMATCH, "a place abroad on Open disagrees");
        check(Presenter.exitFor("ES", "NL", Choices.Choice.TOR) == Presenter.Exit.COUNTRY, "a place abroad on Tor moves the exit");
        check(Presenter.exitFor("NL", "NL", Choices.Choice.DIRECT) == Presenter.Exit.NONE, "a place in the own country needs nothing");
        check(Presenter.exitFor("NL", "NL", Choices.Choice.TOR) == Presenter.Exit.NONE, "nor on Tor");
        check(Presenter.exitFor("", "NL", Choices.Choice.TOR) == Presenter.Exit.NONE && Presenter.exitFor(null, "NL", Choices.Choice.TOR) == Presenter.Exit.NONE,
                "no place, no change");
        check(Presenter.exitFor("es", "NL", Choices.Choice.TOR) == Presenter.Exit.NONE && Presenter.exitFor("ESP", "NL", Choices.Choice.TOR) == Presenter.Exit.NONE,
                "a code that is not two capitals is no country");
        check(Presenter.exitFor("ES", "", Choices.Choice.TOR) == Presenter.Exit.COUNTRY, "an unknown own country still follows the place");
        c.set("org.b.beta", Choices.Choice.OPEN);
        check(Presenter.appRows(c, apps, "beta").get(0).choice == Choices.Choice.OPEN && !Presenter.appRows(c, apps, "beta").get(0).tor, "the row shows Open");
        c.set("org.b.beta", Choices.Choice.DIRECT);
        List<Policy.App> installed = new ArrayList<>();
        for (Presenter.Listed l : apps) {
            installed.add(new Policy.App(l.pkg, l.uid));
        }
        Policy.Out out = Policy.compute(c, installed, new int[] {0}, Consent.ON, false, false);
        check(str(out.guard).equals("[10100-10100]") && out.oemPrefs.containsKey("org.a.alpha"), "selecting Tor gives the expected policy: " + str(out.guard));

        // The hosts that see the address.
        CompanyNames names = CompanyNames.parse("exit-mask-list\t1\nads.example.com\t7\tExample Ads\nx.example\t0\tprotected\nbad\tx\tN\nlong.example\t9\t" + "n".repeat(65) + "\ny.example\t300\tTwo\tcolumns\ne.example\t11\t\nbig.example\t70000\tBig\nd.example\t4\tFirst\nd2.example\t4\tSecond\n");
        check("Example Ads".equals(names.nameOf(7)) && names.nameOf(0) == null && names.nameOf(9) == null && names.nameOf(11) == null && names.nameOf(70000) == null && "First".equals(names.nameOf(4)) && names.size() == 2, "company names: " + names.size());
        check(CompanyNames.parse("a.example\t7\tName\n").size() == 0 && CompanyNames.parse("not a header\na.example\t7\tName\n").size() == 0,
                "rows without the header are not read");
        check(CompanyNames.parse("not a list\n").size() == 0 && CompanyNames.parse(null).size() == 0, "not a list: no names");
        check(CompanyNames.parse("exit-mask-list\t1\na.example\t70000\tBig\nb.example\t5\tA\u0001B\n").nameOf(5).equals("AB"), "control characters are dropped, ids past 16 bits are not names");
        c.setShowsAddress("org.b.beta", 7, true);
        c.setShowsAddress("org.b.beta", 8, true);
        c.setShowsAddress("org.a.alpha", 7, true);
        List<Presenter.OverrideRow> o = Presenter.overrideRows(c, names);
        check(o.size() == 3 && o.get(0).pkg.equals("org.a.alpha") && o.get(0).company == 7 && "Example Ads".equals(o.get(0).companyName)
                && o.get(2).company == 8 && o.get(2).companyName == null, "override rows sorted by package and company");
        check(Presenter.overrideRows(new Choices(), names).isEmpty(), "no overrides");
    }

    // ---- ControlCodec ---------------------------------------------------------------------------------------------

    private static Map<String, byte[]> vectors() throws Exception {
        String path = System.getProperty("vectors");
        check(path != null, "-Dvectors=<file> is required");
        Map<String, byte[]> m = new HashMap<>();
        for (String line : Files.readAllLines(Paths.get(path), StandardCharsets.UTF_8)) {
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            String[] f = line.split("\t");
            check(f.length == 2 && f[1].length() % 2 == 0, "vector line: " + line);
            byte[] b = new byte[f[1].length() / 2];
            for (int i = 0; i < b.length; i++) {
                b[i] = (byte) Integer.parseInt(f[1].substring(2 * i, 2 * i + 2), 16);
            }
            check(m.put(f[0], b) == null, "vector twice: " + f[0]);
        }
        return m;
    }

    private static void same(Map<String, byte[]> v, String name, byte[] built) {
        check(Arrays.equals(v.get(name), built), "vector " + name);
    }

    private static ControlCodec.Reply daemon(Map<String, byte[]> v, String name) throws Exception {
        byte[] b = v.get(name);
        ControlCodec.Reply r = ControlCodec.decodeDaemon(b, b.length);
        check(r != null && r.used == b.length, "decoded whole: " + name);
        for (int cut = 0; cut < b.length; cut++) {
            check(ControlCodec.decodeDaemon(b, cut) == null, "a frame cut at " + cut + " is incomplete: " + name);
        }
        return r;
    }

    private static ControlCodec.Reply route(Map<String, byte[]> v, String name) throws Exception {
        byte[] b = v.get(name);
        ControlCodec.Reply r = ControlCodec.decodeRoute(b, b.length);
        check(r != null && r.used == b.length, "decoded whole: " + name);
        for (int cut = 0; cut < b.length; cut++) {
            check(ControlCodec.decodeRoute(b, cut) == null, "a frame cut at " + cut + " is incomplete: " + name);
        }
        return r;
    }

    private static void refuses(boolean daemon, byte[] frame, String what) {
        try {
            if (daemon) {
                ControlCodec.decodeDaemon(frame, frame.length);
            } else {
                ControlCodec.decodeRoute(frame, frame.length);
            }
        } catch (ControlCodec.CodecException e) {
            return;
        }
        check(false, "must refuse " + what);
    }

    static void codec() throws Exception {
        Map<String, byte[]> v = vectors();
        List<Policy.Cls> classes = Arrays.asList(
                new Policy.Cls(10000, 10000, Policy.Kind.TOR), new Policy.Cls(10001, 10230, Policy.Kind.MASKED),
                new Policy.Cls(10231, 10231, Policy.Kind.EXEMPT));
        same(v, "to_daemon.hello", ControlCodec.hello());
        same(v, "to_daemon.set_memory_max", ControlCodec.setMemoryMax(7, 64L * 1024 * 1024));
        same(v, "to_daemon.refresh_app", ControlCodec.refreshApp(3, 10123));
        same(v, "to_daemon.refresh_all", ControlCodec.refreshAll(4));
        same(v, "to_daemon.shutdown", ControlCodec.shutdown(5));
        same(v, "to_daemon.get_state", ControlCodec.getState(9));
        same(v, "to_daemon.set_policy", ControlCodec.setPolicy(1, classes));
        same(v, "to_daemon.set_overrides", ControlCodec.setOverrides(2, 10123, new int[] {7, 300}));
        same(v, "to_daemon.release", ControlCodec.release(11, 10123, 300));
        same(v, "to_daemon.set_country", ControlCodec.setCountry(12, 10123, "ES"));
        same(v, "to_daemon.clear_country", ControlCodec.setCountry(13, 10123, null));
        check(Arrays.equals(ControlCodec.setCountry(13, 10123, ""), v.get("to_daemon.clear_country")), "an empty country is no choice");
        check(ControlCodec.validCountry("AZ") && ControlCodec.validCountry("ZA") && !ControlCodec.validCountry("A@")
                && !ControlCodec.validCountry("A[") && !ControlCodec.validCountry("a") && !ControlCodec.validCountry(null), "the capital letters, and only they");
        same(v, "route.hello", ControlCodec.routeHello());
        same(v, "route.apply", ControlCodec.routeApply(5, "exit0", 0x01020304L,
                Arrays.asList(new int[] {10000, 10230}), Arrays.asList(new int[] {10231, 10231})));
        same(v, "route.clear", ControlCodec.routeClear(3));
        same(v, "route.get_applied", ControlCodec.routeGetApplied(4));

        ControlCodec.Reply r = daemon(v, "from_daemon.ready");
        check(r.type == ControlCodec.Type.READY && r.version == 4 && "exit0".equals(r.iface), "ready");
        r = daemon(v, "from_daemon.state_up");
        check(r.type == ControlCodec.Type.STATE && r.state == ControlCodec.STATE_UP, "state");
        r = daemon(v, "from_daemon.failures");
        check(r.type == ControlCodec.Type.FAILURES && r.uid == 10123 && r.company == 7 && r.count == 3, "failures");
        r = daemon(v, "from_daemon.promoted");
        check(r.type == ControlCodec.Type.PROMOTED && r.uid == 10123 && r.company == 7 && r.fresh, "promoted");
        // The fresh flag is zero or one; a promotion of a company above 255 keeps both bytes of its id.
        r = ControlCodec.decodeDaemon(new byte[] {0, 0, 0, 8, (byte) 0x86, 0, 0, 0x27, (byte) 0xf7, 1, 0x2c, 0}, 12);
        check(r != null && r.type == ControlCodec.Type.PROMOTED && r.company == 300 && !r.fresh && r.uid == 10231, "promoted, not fresh, company 300");
        refuses(true, new byte[] {0, 0, 0, 8, (byte) 0x86, 0, 0, 0, 1, 0, 7, 2}, "a fresh flag of 2");
        refuses(true, new byte[] {0, 0, 0, 7, (byte) 0x86, 0, 0, 0, 1, 0, 7}, "a Promoted without the flag");
        refuses(true, new byte[] {0, 0, 0, 9, (byte) 0x86, 0, 0, 0, 1, 0, 7, 1, 1}, "a Promoted with a trailing byte");
        refuses(false, v.get("from_daemon.promoted"), "a Promoted from the helper");
        throwsIae(() -> ControlCodec.release(1, 10123, 0), "a release of company 0");
        throwsIae(() -> ControlCodec.release(1, 10123, 65536), "a release of company 65536");
        throwsIae(() -> ControlCodec.release(256, 10123, 7), "a release with a sequence number of two bytes");
        check(ControlCodec.release(1, 10123, 65535).length == 4 + 1 + 7, "company 65535 is written");
        r = daemon(v, "from_daemon.ack");
        check(r.type == ControlCodec.Type.ACK && r.seq == 9, "ack");
        r = daemon(v, "from_daemon.error");
        check(r.type == ControlCodec.Type.ERROR && r.seq == 2 && r.code == ControlCodec.ERR_TOO_MANY, "error");
        r = route(v, "route_reply.ready");
        check(r.type == ControlCodec.Type.READY && r.version == 1 && r.iface == null, "route ready");
        r = route(v, "route_reply.applied");
        check(r.type == ControlCodec.Type.APPLIED && r.seq == 9 && r.rules == 0x0102, "applied");
        r = route(v, "route_reply.error");
        check(r.type == ControlCodec.Type.ERROR && r.seq == 9 && r.code == ControlCodec.ERR_FAILED, "route error");

        // The numbers that the vectors do not reach.
        check(ControlCodec.DAEMON_PROTOCOL == 4 && ControlCodec.ROUTE_PROTOCOL == 1 && ControlCodec.MAX_FRAME == 4096 && ControlCodec.IFNAME_MAX == 15,
                "the numbers of the protocols");
        check(ControlCodec.STATE_BOOTSTRAPPING == 0 && ControlCodec.STATE_UP == 1 && ControlCodec.STATE_FAILED == 2, "state values");
        check(ControlCodec.ERR_BAD_VALUE == 1 && ControlCodec.ERR_TOO_MANY == 2 && ControlCodec.ERR_BUSY == 3
                && ControlCodec.ERR_UNSUPPORTED == 4 && ControlCodec.ERR_FAILED == 5, "error code values");
        // Every state and every error code is read, and the name of an interface may use every character it may use.
        for (int s = 0; s <= 2; s++) {
            r = ControlCodec.decodeDaemon(new byte[] {0, 0, 0, 2, (byte) 0x82, (byte) s}, 6);
            check(r != null && r.state == s, "state " + s);
        }
        for (int code = 1; code <= 5; code++) {
            r = ControlCodec.decodeDaemon(new byte[] {0, 0, 0, 3, (byte) 0x85, 4, (byte) code}, 7);
            check(r != null && r.code == code && r.seq == 4, "error code " + code);
        }
        byte[] name = "Ab_c-d.e9".getBytes(StandardCharsets.US_ASCII);
        byte[] ready = new byte[4 + 2 + name.length];
        ready[3] = (byte) (2 + name.length);
        ready[4] = (byte) 0x81;
        ready[5] = 2;
        System.arraycopy(name, 0, ready, 6, name.length);
        r = ControlCodec.decodeDaemon(ready, ready.length);
        check(r != null && "Ab_c-d.e9".equals(r.iface), "an interface name with upper case, underscore, dash, dot and digit");
        for (String bad : new String[] {".", "..", "a b", "a/b", "a:b", "aé"}) {
            byte[] n = bad.getBytes(StandardCharsets.ISO_8859_1);
            byte[] f = new byte[4 + 2 + n.length];
            f[3] = (byte) (2 + n.length);
            f[4] = (byte) 0x81;
            f[5] = 2;
            System.arraycopy(n, 0, f, 6, n.length);
            refuses(true, f, "the interface name " + bad.length());
        }
        // A frame that claims exactly the largest size is waited for; one byte more is refused at once.
        check(ControlCodec.decodeDaemon(new byte[] {0, 0, 0x10, 0, (byte) 0x84, 1}, 6) == null, "4096 claimed: incomplete");
        refuses(true, new byte[] {0, 0, 0x10, 1, (byte) 0x84, 1}, "4097 claimed");
        // The limits are inclusive: 255 classes, 32 companies and 128 ranges are written.
        List<Policy.Cls> max = new ArrayList<>();
        for (int i = 0; i < 255; i++) {
            max.add(new Policy.Cls(10000 + 2 * i, 10000 + 2 * i, Policy.Kind.TOR));
        }
        byte[] full = ControlCodec.setPolicy(1, max);
        check(full.length == 4 + 1 + 2 + 255 * 9, "255 classes");
        check(full[6] == (byte) 255, "the count of a full table is 255 and not 0");
        check(Policy.MAX_CLASSES == 255, "the count is one byte on the wire");
        max.add(new Policy.Cls(10000 + 2 * 255, 10000 + 2 * 255, Policy.Kind.TOR));
        throwsIae(() -> ControlCodec.setPolicy(1, max), "256 classes cannot be written");
        check(ControlCodec.setOverrides(1, 1, new int[32]).length == 4 + 1 + 1 + 4 + 1 + 64, "32 companies");
        List<int[]> r128 = Collections129();
        r128.remove(128);
        check(ControlCodec.routeApply(1, "exit0", 0, r128, r128).length == 4 + 1 + 1 + 1 + 5 + 4 + 2 * (1 + 128 * 8), "128 ranges in each list");
        check(ControlCodec.routeApply(1, "fifteen_chars_x", 0, new ArrayList<int[]>(), new ArrayList<int[]>()).length > 0, "a 15-byte name");

        // Replies of the other protocol and malformed frames are refused, never read as something else.
        refuses(true, v.get("route_reply.applied"), "an Applied from the daemon");
        refuses(false, v.get("from_daemon.state_up"), "a State from the helper");
        refuses(false, v.get("from_daemon.failures"), "Failures from the helper");
        refuses(true, new byte[] {0, 0, 0, 0}, "an empty frame");
        refuses(true, new byte[] {0, 0, 0x10, 1, (byte) 0x84}, "a frame past MAX_FRAME");
        refuses(true, new byte[] {0, 0, 0, 2, 0x7f, 1}, "an unknown tag");
        refuses(true, new byte[] {0, 0, 0, 3, (byte) 0x84, 9, 9}, "an Ack with a trailing byte");
        refuses(true, new byte[] {0, 0, 0, 1, (byte) 0x84}, "an Ack without a sequence number");
        refuses(true, new byte[] {0, 0, 0, 2, (byte) 0x82, 3}, "a state of 3");
        refuses(true, new byte[] {0, 0, 0, 3, (byte) 0x85, 1, 0}, "error code 0");
        refuses(true, new byte[] {0, 0, 0, 3, (byte) 0x85, 1, 6}, "error code 6");
        refuses(true, new byte[] {0, 0, 0, 2, (byte) 0x81, 2}, "a Ready without an interface name");
        refuses(true, new byte[] {0, 0, 0, 4, (byte) 0x81, 2, '.', '.'}, "a Ready with the name ..");
        refuses(true, new byte[] {0, 0, 0, 4, (byte) 0x81, 2, 'a', ' '}, "a Ready with a space in the name");
        byte[] longName = new byte[4 + 1 + 1 + 16];
        longName[3] = 18;
        longName[4] = (byte) 0x81;
        Arrays.fill(longName, 6, longName.length, (byte) 'a');
        refuses(true, longName, "a 16-byte interface name");
        refuses(false, new byte[] {0, 0, 0, 3, (byte) 0x81, 1, 1}, "a helper Ready with a trailing byte");
        // The addresses of the tunnel are those the route helper writes (the Rust tests check the same lines).
        check(Arrays.equals(v.get("const.tun_v4"), concat(TunNet.V4, new byte[] {(byte) TunNet.V4_PREFIX})), "tun v4 address");
        check(Arrays.equals(v.get("const.tun_v6"), concat(TunNet.V6, new byte[] {(byte) TunNet.V6_PREFIX})), "tun v6 address");
        check(Arrays.equals(v.get("const.dns_v4"), TunNet.DNS_V4), "dns address");
        // The client may not emit what the other side refuses.
        for (Runnable bad : new Runnable[] {
                () -> ControlCodec.getState(256), () -> ControlCodec.getState(-1),
                () -> ControlCodec.setOverrides(1, 1, new int[33]),
                () -> ControlCodec.setCountry(1, 1, "E"), () -> ControlCodec.setCountry(1, 1, "ESP"),
                () -> ControlCodec.setCountry(1, 1, "es"), () -> ControlCodec.setCountry(1, 1, "E1"),
                () -> ControlCodec.setCountry(1, 1, "ÉS"), () -> ControlCodec.setCountry(256, 1, "ES"),
                () -> ControlCodec.setCountry(1, -1, "ES"), () -> ControlCodec.setCountry(1, 0x1_0000_0000L, "ES"),
                () -> ControlCodec.routeApply(1, "", 0, new ArrayList<int[]>(), new ArrayList<int[]>()),
                () -> ControlCodec.routeApply(1, "sixteen_chars_xx", 0, new ArrayList<int[]>(), new ArrayList<int[]>()),
                () -> ControlCodec.routeApply(1, "exit0", 0, Collections129(), new ArrayList<int[]>()),
                () -> ControlCodec.routeApply(1, "exit0", 0, new ArrayList<int[]>(), Collections129()),
                () -> ControlCodec.setPolicy(1, Collections257())}) {
            throwsIae(bad, "an out-of-limit request");
        }
        // Two frames in one buffer: the first is read and its length says where the second begins.
        byte[] two = new byte[v.get("from_daemon.ack").length * 2];
        System.arraycopy(v.get("from_daemon.ack"), 0, two, 0, v.get("from_daemon.ack").length);
        System.arraycopy(v.get("from_daemon.ack"), 0, two, v.get("from_daemon.ack").length, v.get("from_daemon.ack").length);
        r = ControlCodec.decodeDaemon(two, two.length);
        check(r != null && r.used == two.length / 2, "the first of two frames");
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] r = Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, r, a.length, b.length);
        return r;
    }

    private static List<int[]> Collections129() {
        List<int[]> l = new ArrayList<>();
        for (int i = 0; i < 129; i++) {
            l.add(new int[] {10000 + 2 * i, 10000 + 2 * i});
        }
        return l;
    }

    private static List<Policy.Cls> Collections257() {
        List<Policy.Cls> l = new ArrayList<>();
        for (int i = 0; i < 257; i++) {
            l.add(new Policy.Cls(10000 + 2 * i, 10000 + 2 * i, Policy.Kind.TOR));
        }
        return l;
    }

    // ---- ProfileRows (ADR-208) ---------------------------------------------------------------------------------------

    private static String rowText(ProfileRows.Row r) {
        return r.cls + ":" + Arrays.toString(r.values) + "=" + r.current;
    }

    static void profileRows() {
        // the numbers are those of ICompartmentsPolicy and are pinned by ProfilesTest of the policy core
        check(ProfileRows.PROFILE_TRUSTED == 0 && ProfileRows.PROFILE_PROTECTED == 1 && ProfileRows.PROFILE_ISOLATED == 2, "profile numbers");
        check(ProfileRows.CLASS_SENSORS == 0 && ProfileRows.CLASS_LOOPBACK == 1 && ProfileRows.CLASS_SETTINGS == 2 && ProfileRows.CLASS_LOCATION == 3, "class numbers");
        check(ProfileRows.VALUE_REAL == 0 && ProfileRows.VALUE_SUBSTITUTE == 1 && ProfileRows.VALUE_BLOCKED == 2, "value numbers");
        check(Arrays.equals(ProfileRows.profileChoices(true), new int[] {1, 2}), "the defaults are Protected or Isolated");
        check(Arrays.equals(ProfileRows.profileChoices(false), new int[] {0, 1, 2}), "an app may be any profile");
        // the arrays that come back are copies: a caller cannot change what the next call returns
        ProfileRows.profileChoices(true)[0] = 9;
        check(Arrays.equals(ProfileRows.profileChoices(true), new int[] {1, 2}), "profileChoices returns a copy");

        List<ProfileRows.Row> rows = ProfileRows.rowsFor(ProfileRows.PROFILE_PROTECTED, 0, 2, 2, 0);
        // the page order (ADR-209): location, sensors, other apps on this phone, phone settings; the place is no class row
        check(rows.size() == 4, "four classes: " + rows.size());
        check(rowText(rows.get(0)).equals("3:[0, 1, 2]=0"), "location row: " + rowText(rows.get(0)));
        check(rowText(rows.get(1)).equals("0:[0, 1, 2]=0"), "sensors row: " + rowText(rows.get(1)));
        check(rowText(rows.get(2)).equals("1:[0, 2]=2"), "loopback row: " + rowText(rows.get(2)));
        check(rowText(rows.get(3)).equals("2:[0, 2]=2"), "settings row: " + rowText(rows.get(3)));
        for (int p = 0; p < 3; p++) {
            List<ProfileRows.Row> order = ProfileRows.rowsFor(p, 0, 0, 0, 1);
            check(order.size() == 4 && order.get(0).cls == 3 && order.get(1).cls == 0 && order.get(2).cls == 1
                    && order.get(3).cls == 2, "class order for profile " + p);
        }
        List<ProfileRows.Row> iso = ProfileRows.rowsFor(ProfileRows.PROFILE_ISOLATED, 1, 2, 2, 1);
        check(iso.size() == 4, "substitute location adds no row, the place has its own row type: " + iso.size());
        check(rowText(iso.get(0)).equals("3:[0, 1, 2]=1"), "location row of an isolated app: " + rowText(iso.get(0)));
        check(ProfileRows.rowsFor(ProfileRows.PROFILE_PROTECTED, 0, 2, 2, 2).size() == 4, "blocked location has four rows");

        // the value each profile gives a class (the same table as Profiles.defaultValue of the core)
        for (int p = 0; p < 3; p++) {
            check(ProfileRows.profileDefault(p, ProfileRows.CLASS_SENSORS) == (p == 2 ? 1 : 0), "sensors default of " + p);
            check(ProfileRows.profileDefault(p, ProfileRows.CLASS_LOCATION) == (p == 2 ? 1 : 0), "location default of " + p);
            check(ProfileRows.profileDefault(p, ProfileRows.CLASS_LOOPBACK) == (p == 0 ? 0 : 2), "loopback default of " + p);
            check(ProfileRows.profileDefault(p, ProfileRows.CLASS_SETTINGS) == (p == 0 ? 0 : 2), "settings default of " + p);
        }
        throwsIae(() -> ProfileRows.profileDefault(3, 0), "default of profile 3");
        throwsIae(() -> ProfileRows.profileDefault(-1, 0), "default of profile -1");
        throwsIae(() -> ProfileRows.profileDefault(1, 4), "default of class 4");
        throwsIae(() -> ProfileRows.profileDefault(1, -1), "default of class -1");

        // the identity of an app: its profile, or Custom when one detail differs from what the profile gives
        check(ProfileRows.identityOf(1, 0, 2, 2, 0) == 1, "Protected as set");
        check(ProfileRows.identityOf(2, 1, 2, 2, 1) == 2, "Isolated as set");
        check(ProfileRows.identityOf(0, 0, 0, 0, 0) == 0, "Trusted as set");
        check(ProfileRows.identityOf(1, 0, 2, 2, 1) == ProfileRows.IDENTITY_CUSTOM, "location substituted under Protected is Custom");
        check(ProfileRows.identityOf(1, 0, 0, 2, 0) == ProfileRows.IDENTITY_CUSTOM, "loopback real under Protected is Custom");
        check(ProfileRows.identityOf(2, 0, 2, 2, 1) == ProfileRows.IDENTITY_CUSTOM, "sensors real under Isolated is Custom");
        check(ProfileRows.identityOf(1, 1, 2, 2, 0) == ProfileRows.IDENTITY_CUSTOM, "sensors substituted under Protected is Custom");
        check(ProfileRows.identityOf(1, 0, 2, 0, 0) == ProfileRows.IDENTITY_CUSTOM, "settings real under Protected is Custom");
        throwsIae(() -> ProfileRows.identityOf(3, 0, 0, 0, 0), "identity of profile 3");
        throwsIae(() -> ProfileRows.identityOf(1, 0, 1, 0, 0), "identity with substitute loopback");

        // only an app without an entry follows the default
        check(ProfileRows.selectedChoice(false, 2) == ProfileRows.CHOICE_FOLLOW_DEFAULT, "no entry follows the default");
        check(ProfileRows.selectedChoice(true, 1) == 1 && ProfileRows.selectedChoice(true, 0) == 0, "an entry shows its profile");

        // the place row: hidden unless the location is substitute data, not available without a place
        check(ProfileRows.placeRow(0, 0) == ProfileRows.PlaceRow.HIDDEN, "real location has no place row");
        check(ProfileRows.placeRow(2, 0) == ProfileRows.PlaceRow.HIDDEN, "blocked location has no place row");
        check(ProfileRows.placeRow(1, 0) == ProfileRows.PlaceRow.NOT_AVAILABLE, "no place can be made");
        check(ProfileRows.placeRow(1, -1) == ProfileRows.PlaceRow.NOT_AVAILABLE, "persona place unknown");
        check(ProfileRows.placeRow(1, 5) == ProfileRows.PlaceRow.SHOWN, "a place exists");
        throwsIae(() -> ProfileRows.rowsFor(1, 0, 0, 0, 3), "location value 3");
        throwsIae(() -> ProfileRows.rowsFor(1, 0, 0, 0, -1), "location value -1");
        ProfileRows.Row r = ProfileRows.rowsFor(ProfileRows.PROFILE_TRUSTED, 1, 0, 0, 0).get(1);
        check(r.cls == ProfileRows.CLASS_SENSORS && r.current == 1, "sensors may be substitute data");
        r.values[0] = 9;
        check(ProfileRows.rowsFor(ProfileRows.PROFILE_TRUSTED, 1, 0, 0, 0).get(1).values[0] == 0, "row values are copies");
        for (int p = 0; p < 3; p++) {
            for (ProfileRows.Row row : ProfileRows.rowsFor(p, 0, 0, 0, 0)) {
                boolean substitute = false;
                for (int v : row.values) {
                    substitute |= v == ProfileRows.VALUE_SUBSTITUTE;
                }
                check(substitute == (row.cls == ProfileRows.CLASS_SENSORS || row.cls == ProfileRows.CLASS_LOCATION),
                        "only the sensors and the location offer substitute data");
            }
        }
        throwsIae(() -> ProfileRows.rowsFor(3, 0, 0, 0, 0), "profile 3");
        throwsIae(() -> ProfileRows.rowsFor(-1, 0, 0, 0, 0), "profile -1");
        throwsIae(() -> ProfileRows.rowsFor(1, 3, 0, 0, 0), "sensors value 3");
        throwsIae(() -> ProfileRows.rowsFor(1, -1, 0, 0, 0), "sensors value -1");
        throwsIae(() -> ProfileRows.rowsFor(1, 0, 3, 0, 0), "loopback value 3");
        throwsIae(() -> ProfileRows.rowsFor(1, 0, 1, 0, 0), "loopback cannot be substitute data");
        throwsIae(() -> ProfileRows.rowsFor(1, 0, 0, 3, 0), "settings value 3");
        throwsIae(() -> ProfileRows.rowsFor(1, 0, 0, 1, 0), "settings cannot be substitute data");

        List<ProfileRows.Row> defaults = ProfileRows.defaultRows(ProfileRows.PROFILE_ISOLATED, 1, 1);
        check(defaults.size() == 2 && rowText(defaults.get(0)).equals("0:[0, 1, 2]=1"), "the defaults hold the sensors row first");
        check(rowText(defaults.get(1)).equals("3:[1]=1"), "and a location row that offers only its current value: " + rowText(defaults.get(1)));
        check(rowText(ProfileRows.defaultRows(ProfileRows.PROFILE_PROTECTED, 0, 0).get(1)).equals("3:[0]=0"), "defaults of Protected");
        throwsIae(() -> ProfileRows.defaultRows(1, 0, 3), "defaults with location value 3");
        throwsIae(() -> ProfileRows.defaultRows(1, 0, -1), "defaults with location value -1");
        throwsIae(() -> ProfileRows.defaultRows(ProfileRows.PROFILE_TRUSTED, 0, 0), "the defaults are never Trusted");
        throwsIae(() -> ProfileRows.defaultRows(3, 0, 0), "defaults of profile 3");
        throwsIae(() -> ProfileRows.defaultRows(1, 3, 0), "defaults with sensors value 3");
    }

    public static void main(String[] args) throws Exception {
        profileRows();
        uidRanges();
        choices();
        consent();
        policy();
        policyOpen();
        ladder();
        presenter();
        codec();
        org.freeholdos.exit.ControllerChecks.run();
        System.out.println("exit app host checks passed");
    }
}
