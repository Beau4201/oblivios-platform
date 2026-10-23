// SPDX-License-Identifier: Apache-2.0
package org.freeholdos.exit;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Host checks of the controller and of the two control clients, with fakes for the daemon, the helper and the platform.
 * The log of the fakes is one list, so the order of the calls across all of them is what the checks read.
 */
public final class ControllerChecks {
    private ControllerChecks() {}

    private static void check(boolean ok, String what) {
        if (!ok) {
            System.out.println("FAIL: " + what);
            System.exit(1);
        }
    }

    // ---- fakes ----------------------------------------------------------------------------------------------------

    private static final class World {
        final List<String> log = new ArrayList<>();
        final Choices choices = new Choices();
        Consent consent = Consent.ON;
        List<Policy.App> apps = new ArrayList<>();
        int[] users = {0};
        boolean vpn;
        boolean strictDns;
        long netId = 101;
        /** Method names ("daemon.connect", "daemon.poll", ...) that throw once or for ever. */
        final Map<String, Integer> failures = new TreeMap<>();
        List<DaemonApi.Failure> nextFailures = new ArrayList<>();
        List<DaemonApi.Promotion> nextPromotions = new ArrayList<>();
        Status last;

        void maybeFail(String what) throws IOException {
            Integer n = failures.get(what);
            if (n != null && n != 0) {
                if (n > 0) {
                    failures.put(what, n - 1);
                }
                throw new IOException("injected " + what);
            }
        }

        final DaemonApi daemon = new DaemonApi() {
            @Override
            public String connect() throws IOException {
                log.add("daemon.connect");
                maybeFail("daemon.connect");
                return "exit0";
            }

            @Override
            public void setPolicy(List<Policy.Cls> classes) throws IOException {
                log.add("daemon.setPolicy:" + classes.size());
                maybeFail("daemon.setPolicy");
            }

            @Override
            public void setOverrides(long uid, int[] companies) throws IOException {
                log.add("daemon.setOverrides:" + uid + Arrays.toString(companies));
                maybeFail("daemon.setOverrides");
            }

            @Override
            public Poll poll() throws IOException {
                log.add("daemon.poll");
                maybeFail("daemon.poll");
                List<Failure> f = nextFailures;
                nextFailures = new ArrayList<>();
                List<Promotion> q = nextPromotions;
                nextPromotions = new ArrayList<>();
                return new Poll(ControlCodec.STATE_UP, f, q);
            }

            @Override
            public void release(long uid, int company) throws IOException {
                log.add("daemon.release:" + uid + "/" + company);
                maybeFail("daemon.release");
            }

            @Override
            public void shutdown() throws IOException {
                log.add("daemon.shutdown");
                maybeFail("daemon.shutdown");
            }

            @Override
            public void close() {
                log.add("daemon.close");
            }
        };

        final RouteApi route = new RouteApi() {
            @Override
            public void connect() throws IOException {
                log.add("route.connect");
                maybeFail("route.connect");
            }

            @Override
            public void apply(String iface, long net, List<int[]> steer, List<int[]> guard) throws IOException {
                log.add("route.apply:net=" + net + " steer=" + steer.size() + " guard=" + guard.size());
                maybeFail("route.apply");
            }

            @Override
            public void clear() throws IOException {
                log.add("route.clear");
                maybeFail("route.clear");
            }

            @Override
            public void close() {
                log.add("route.close");
            }
        };

        final PlatformApi platform = new PlatformApi() {
            @Override
            public int[] users() {
                return users;
            }

            @Override
            public List<Policy.App> installedApps() {
                return apps;
            }

            @Override
            public boolean vpnActive() {
                return vpn;
            }

            @Override
            public boolean privateDnsStrict() {
                return strictDns;
            }

            @Override
            public long registerTorNetwork(String iface) throws IOException {
                log.add("net.register:" + iface);
                maybeFail("net.register");
                return netId;
            }

            @Override
            public void unregisterTorNetwork() {
                log.add("net.unregister");
            }

            @Override
            public void setOemPreferences(Map<String, Integer> prefs) throws IOException {
                log.add("prefs:" + new TreeMap<>(prefs));
                maybeFail("prefs");
            }

            @Override
            public void notifyFailure(long uid, int company) {
                log.add("notify:" + uid + "/" + company);
            }

            @Override
            public void notifyPromotion(long uid, int company) {
                log.add("promoted:" + uid + "/" + company);
            }

            @Override
            public void publish(Status s) {
                log.add("status:" + s.phase);
                last = s;
            }
        };

        final Store store = new Store() {
            @Override
            public Choices choices() {
                return choices;
            }

            @Override
            public Consent consent() {
                return consent;
            }
        };

        final Ladder ladder = new Ladder();
        final ExitController controller = new ExitController(daemon, route, platform, store, ladder);

        int count(String prefix) {
            int n = 0;
            for (String l : log) {
                if (l.startsWith(prefix)) {
                    n++;
                }
            }
            return n;
        }

        int indexOf(String prefix, int from) {
            for (int i = from; i < log.size(); i++) {
                if (log.get(i).startsWith(prefix)) {
                    return i;
                }
            }
            return -1;
        }
    }

    private static World world() {
        World w = new World();
        w.apps.add(new Policy.App("org.a.one", 10100));
        w.apps.add(new Policy.App("org.a.two", 10101));
        w.apps.add(new Policy.App("org.freeholdos.exit", 10050));
        return w;
    }

    /** Asserts that each prefix occurs after the one before it, starting at {@code from}; returns the last index. */
    private static int inOrder(World w, int from, String... prefixes) {
        int at = from;
        for (String p : prefixes) {
            int i = w.indexOf(p, at);
            check(i >= 0, "'" + p + "' must come after index " + at + " in " + w.log);
            at = i + 1;
        }
        return at - 1;
    }

    // ---- the controller -------------------------------------------------------------------------------------------

    private static void bootOrder() {
        World w = world();
        w.choices.set("org.a.one", Choices.Choice.TOR);
        w.controller.start(0);
        // Deny by default: the preference is set and the daemon is asked for before anything connects.
        int up = inOrder(w, 0, "prefs:{org.a.one=4}", "status:CONNECTING");
        check(w.indexOf("daemon.connect", 0) < 0, "nothing connects inside start(): " + w.log);
        w.controller.tick(0);
        int end = inOrder(w, up + 1, "daemon.connect", "route.connect", "prefs:{org.a.one=4}", "route.apply:net=0",
                "daemon.setPolicy:", "net.register:exit0", "route.apply:net=101", "status:RUNNING");
        // The "ready for apps" status is the last thing, and it comes once.
        check(w.count("status:RUNNING") == 1 && w.log.get(w.log.size() - 1).equals("status:RUNNING") && end == w.log.size() - 1,
                "RUNNING is published last: " + w.log);
        // Nothing but the poll happens until the poll time.
        int before = w.log.size();
        w.controller.tick(4999);
        check(w.log.size() == before, "no poll before 5 s");
        w.controller.tick(5000);
        check(w.log.size() > before && w.log.get(before).equals("daemon.poll"), "poll at 5 s: " + w.log);
        check(w.last.daemonState == ControlCodec.STATE_UP, "the daemon state of the poll reaches the status");
    }

    private static void daemonRestart() {
        World w = world();
        w.choices.set("org.a.one", Choices.Choice.TOR);
        w.choices.setShowsAddress("org.a.one", 7, true);
        w.controller.start(0);
        w.controller.tick(0);
        check(w.last.phase == Status.Phase.RUNNING, "running");
        inOrder(w, 0, "route.apply:net=0", "daemon.setPolicy", "daemon.setOverrides:10100[7]", "net.register", "route.apply:net=101");
        // The daemon dies: the next poll fails.
        w.failures.put("daemon.poll", 1);
        int mark = w.log.size();
        w.controller.tick(5000);
        check(w.last.phase == Status.Phase.PAUSED && "IOException".equals(w.last.lastError), "paused after the lost poll: " + w.last);
        check(w.indexOf("net.unregister", mark) >= 0, "the Tor network goes when the daemon goes");
        check(w.indexOf("prefs:{}", mark) < 0, "the preference stays: the apps on Tor have no network");
        // Retried after one second, not before.
        int after = w.log.size();
        w.controller.tick(5999);
        check(w.log.size() == after, "no retry before 1 s");
        w.controller.tick(6000);
        // The same order again, and the overrides are sent again (a fresh daemon knows nothing).
        inOrder(w, after, "daemon.connect", "route.connect", "prefs:{org.a.one=4}", "route.apply:net=0", "daemon.setPolicy",
                "daemon.setOverrides:10100[7]", "net.register", "route.apply:net=101", "status:RUNNING");
        check(w.last.phase == Status.Phase.RUNNING && w.last.lastError == null, "running again");
        // A run in between starts the schedule again from one second.
        w.failures.put("daemon.poll", 1);
        w.controller.tick(11000);
        int again = w.log.size();
        w.controller.tick(11999);
        check(w.log.size() == again, "no retry before 1 s after the second loss");
        w.controller.tick(12000);
        check(w.last.phase == Status.Phase.RUNNING, "one second after the second loss, not two: " + w.last);
    }

    private static void backoff() {
        World w = world();
        w.choices.set("org.a.one", Choices.Choice.TOR);
        w.failures.put("daemon.connect", -1); // for ever
        w.controller.start(0);
        long[] at = {0, 1000, 3000, 7000, 15000, 31000, 61000, 91000, 121000};
        int expected = 0;
        for (int i = 0; i < at.length; i++) {
            // One millisecond before the attempt nothing happens (except for the first, which is at 0).
            if (i > 0) {
                w.controller.tick(at[i] - 1);
                check(w.count("daemon.connect") == expected, "no attempt at " + (at[i] - 1) + ": " + expected + " vs " + w.count("daemon.connect"));
            }
            w.controller.tick(at[i]);
            expected++;
            check(w.count("daemon.connect") == expected, "attempt " + i + " at " + at[i]);
            check(w.last.phase == Status.Phase.CONNECTING, "still connecting, never ran: " + w.last.phase);
        }
        check(ExitController.BACKOFF_MS[5] == 30000 && ExitController.POLL_MS == 5000, "the schedule");
    }

    private static void idleWithoutConsentOrTorApps() {
        World w = world();
        w.consent = Consent.UNSET;
        w.controller.start(0);
        // The first call after a start cleans up whatever the platform kept, then the daemon stays off.
        inOrder(w, 0, "prefs:{}", "net.unregister", "route.connect", "route.clear", "route.close", "daemon.close", "status:IDLE");
        check(w.indexOf("daemon.connect", 0) < 0 && w.indexOf("daemon.shutdown", 0) < 0, "a daemon nobody asked for is left alone");
        int n = w.log.size();
        w.controller.tick(0);
        w.controller.tick(100000);
        check(w.log.size() == n, "nothing happens while idle");
        check(w.last.phase == Status.Phase.IDLE, "idle");
        // A helper that cannot be reached does not stop the cleanup.
        World v = world();
        v.consent = Consent.OFF;
        v.failures.put("route.connect", -1);
        v.controller.start(0);
        check(v.last.phase == Status.Phase.IDLE && v.indexOf("route.close", 0) >= 0, "idle although the helper is down");
        // A daemon that cannot be reached for the shutdown does not keep the app from going idle either.
        World u = world();
        u.controller.start(0);
        u.controller.tick(0);
        u.failures.put("daemon.connect", -1);
        u.consent = Consent.OFF;
        u.controller.changed(1000);
        check(u.last.phase == Status.Phase.IDLE, "idle although the daemon cannot be asked to exit");
    }

    private static void torOnlyWithoutConsent() {
        World w = world();
        w.consent = Consent.UNSET;
        w.choices.set("org.a.one", Choices.Choice.TOR);
        w.controller.start(0);
        w.controller.tick(0);
        check(w.last.phase == Status.Phase.RUNNING && w.indexOf("route.apply:net=101 steer=0 guard=1", 0) >= 0, "Tor does not wait for the consent: " + w.log);
        // A second Tor app changes the guard although the steer list stays empty.
        w.choices.set("org.a.two", Choices.Choice.TOR);
        int mark = w.log.size();
        w.controller.changed(1000);
        check(w.indexOf("route.apply:net=101 steer=0 guard=1", mark) >= 0, "the rules follow a change of the guard alone: " + w.log.subList(mark, w.log.size()));
        check(w.indexOf("daemon.setPolicy", mark) >= 0 && w.indexOf("prefs:{org.a.one=4, org.a.two=4}", mark) >= 0, "table and preferences follow");
        // The consent comes later: the steer list changes and the guard does not, and the rules follow.
        w.consent = Consent.ON;
        mark = w.log.size();
        w.controller.changed(2000);
        check(w.indexOf("route.apply:net=101 steer=3 guard=1", mark) >= 0, "the rules follow a change of the steer list alone: " + w.log.subList(mark, w.log.size()));
    }

    private static void maskedOnly() {
        World w = world();
        w.controller.start(0);
        w.controller.tick(0);
        check(w.indexOf("daemon.connect", 0) >= 0 && w.last.phase == Status.Phase.RUNNING, "consent alone brings up the daemon");
        check(w.indexOf("net.register", 0) < 0, "no Tor network without an app on Tor");
        check(w.indexOf("route.apply:net=0 steer=2 guard=0", 0) >= 0, "masked ranges are steered: " + w.log);
        check(w.indexOf("prefs:{}", 0) >= 0, "no preference");
    }

    private static void changesWhileRunning() {
        World w = world();
        w.controller.start(0);
        w.controller.tick(0);
        // The first app goes to Tor: the network appears and the rules get its id.
        w.choices.set("org.a.one", Choices.Choice.TOR);
        int mark = w.log.size();
        w.controller.changed(1000);
        inOrder(w, mark, "prefs:{org.a.one=4}", "route.apply:net=0", "daemon.setPolicy", "net.register", "route.apply:net=101", "status:RUNNING");
        // A second app: only the rules, the table and the preferences change; the network stays.
        w.choices.set("org.a.two", Choices.Choice.TOR);
        mark = w.log.size();
        w.controller.changed(2000);
        inOrder(w, mark, "prefs:{org.a.one=4, org.a.two=4}", "route.apply:net=101", "daemon.setPolicy");
        check(w.indexOf("net.register", mark) < 0, "one network for all");
        // Nothing changed: nothing is sent.
        mark = w.log.size();
        w.controller.changed(3000);
        check(w.indexOf("route.apply", mark) < 0 && w.indexOf("daemon.setPolicy", mark) < 0 && w.indexOf("prefs", mark) < 0, "no resend of the same: " + w.log.subList(mark, w.log.size()));
        // The last app leaves Tor: the preference, then the network, then the id in the rules.
        w.choices.set("org.a.one", Choices.Choice.DIRECT);
        w.choices.set("org.a.two", Choices.Choice.DIRECT);
        mark = w.log.size();
        w.controller.changed(4000);
        inOrder(w, mark, "prefs:{}", "net.unregister", "route.apply:net=0");
        // An override is added, changed and removed.
        w.choices.setShowsAddress("org.a.one", 7, true);
        mark = w.log.size();
        w.controller.changed(5000);
        check(w.indexOf("daemon.setOverrides:10100[7]", mark) >= 0, "override sent");
        mark = w.log.size();
        w.controller.changed(5100);
        check(w.indexOf("daemon.setOverrides", mark) < 0, "an unchanged override is not sent again");
        w.choices.setShowsAddress("org.a.one", 8, true);
        mark = w.log.size();
        w.controller.changed(5200);
        check(w.indexOf("daemon.setOverrides:10100[7, 8]", mark) >= 0, "override changed");
        w.choices.setShowsAddress("org.a.one", 7, false);
        w.choices.setShowsAddress("org.a.one", 8, false);
        mark = w.log.size();
        w.controller.changed(5300);
        check(w.indexOf("daemon.setOverrides:10100[]", mark) >= 0, "override removed with an empty list: " + w.log.subList(mark, w.log.size()));
        // Everything off: idle.
        w.consent = Consent.OFF;
        mark = w.log.size();
        w.controller.changed(6000);
        inOrder(w, mark, "prefs:{}", "net.unregister", "route.connect", "route.clear", "route.close", "daemon.connect", "daemon.shutdown", "daemon.close", "status:IDLE");
        mark = w.log.size();
        w.controller.tick(60000);
        check(w.log.size() == mark, "idle again");
        // And on again.
        w.consent = Consent.ON;
        w.controller.changed(70000);
        check(w.indexOf("status:CONNECTING", mark) >= 0, "back on");
    }

    /** F11: a choice made while the daemon is being reached or is down closes the app at once; it must not wait for the link. */
    private static void choiceWhileDaemonIsNotRunning() {
        // CONNECTING: start() was called, nothing connected yet.
        World w = world();
        w.controller.start(0);
        check(w.last.phase == Status.Phase.CONNECTING, "connecting");
        w.choices.set("org.a.one", Choices.Choice.TOR);
        int mark = w.log.size();
        w.controller.changed(10);
        check(w.indexOf("prefs:{org.a.one=4}", mark) >= 0, "Tor in CONNECTING reaches the platform: " + w.log.subList(mark, w.log.size()));
        check(w.indexOf("daemon.", mark) < 0 && w.indexOf("route.", mark) < 0 && w.indexOf("net.", mark) < 0,
                "no daemon, helper or network call while it is not running: " + w.log.subList(mark, w.log.size()));
        check(w.last.phase == Status.Phase.CONNECTING, "still connecting");
        mark = w.log.size();
        w.controller.changed(20);
        check(w.indexOf("prefs", mark) < 0, "the same preference is not set again: " + w.log.subList(mark, w.log.size()));

        // PAUSED: the daemon was running and the poll failed.
        w = world();
        w.controller.start(0);
        w.controller.tick(0);
        w.failures.put("daemon.poll", 1);
        w.controller.tick(5000);
        check(w.last.phase == Status.Phase.PAUSED, "paused");
        w.choices.set("org.a.two", Choices.Choice.TOR);
        mark = w.log.size();
        w.controller.changed(5100);
        check(w.indexOf("prefs:{org.a.two=4}", mark) >= 0, "Tor in PAUSED reaches the platform: " + w.log.subList(mark, w.log.size()));
        check(w.indexOf("daemon.", mark) < 0, "the daemon is not touched before the retry");
        w.choices.set("org.a.two", Choices.Choice.DIRECT);
        mark = w.log.size();
        w.controller.changed(5200);
        check(w.indexOf("prefs:{}", mark) >= 0, "leaving Tor in PAUSED reaches the platform too: " + w.log.subList(mark, w.log.size()));
        // The reconnect still sends everything.
        w.choices.set("org.a.one", Choices.Choice.TOR);
        w.controller.changed(5300);
        mark = w.log.size();
        w.controller.tick(6000);
        inOrder(w, mark, "daemon.connect", "route.connect", "prefs:{org.a.one=4}", "net.register", "status:RUNNING");
    }

    /** Review of 2026-10-23, C2-01: a preference the platform or a step before it refused is not lost while the daemon is down. */
    private static void preferenceFailuresAreRetried() {
        // A choice for Tor while RUNNING: a later step fails and the daemon never comes back. The preference is the first step,
        // so the app is closed in spite of it.
        World w = world();
        w.controller.start(0);
        w.controller.tick(0);
        check(w.last.phase == Status.Phase.RUNNING, "running");
        w.failures.put("daemon.setPolicy", 1);
        w.failures.put("daemon.connect", -1);
        w.choices.set("org.a.one", Choices.Choice.TOR);
        int mark = w.log.size();
        w.controller.changed(1000);
        check(w.last.phase == Status.Phase.PAUSED, "the failed step pauses: " + w.last);
        check(w.indexOf("prefs:{org.a.one=4}", mark) >= 0, "the preference is set before the step that failed: " + w.log.subList(mark, w.log.size()));
        for (long t = 2000; t <= 120000; t += 1000) {
            w.controller.tick(t);
        }
        check(w.count("prefs:{org.a.one=4}") == 1, "and is not set again while nothing changed: " + w.log);

        // The platform refuses the preference inside the push of a running controller: the lost link asks it once more at
        // once (a refusal inside push() throws, the controller pauses), so the app is not left on the Wi-Fi.
        w = world();
        w.controller.start(0);
        w.controller.tick(0);
        w.failures.put("prefs", 1);
        w.failures.put("daemon.connect", -1);
        w.choices.set("org.a.one", Choices.Choice.TOR);
        mark = w.log.size();
        w.controller.changed(1000);
        check(w.last.phase == Status.Phase.PAUSED, "paused after the refusal: " + w.last);
        check(w.count("prefs:{org.a.one=4}") == 2, "refused in push, set by the lost link: " + w.log.subList(mark, w.log.size()));

        // The platform refuses the preference twice: it is asked again every PREFS_RETRY_MS, not before (the lost link of a
        // failed connect does not ask earlier), and no more once it worked.
        w = world();
        w.choices.set("org.a.one", Choices.Choice.TOR);
        w.failures.put("prefs", 2);
        w.failures.put("daemon.connect", -1);
        w.controller.start(0);
        w.controller.tick(0);
        w.controller.tick(4999);
        check(w.count("prefs:{org.a.one=4}") == 1, "refused at start(), no retry before the delay of 5 s: " + w.log);
        w.controller.tick(5000);
        check(w.count("prefs:{org.a.one=4}") == 2, "retried after the delay, with the daemon down: " + w.log);
        w.controller.tick(10000);
        check(w.count("prefs:{org.a.one=4}") == 3, "and once more: " + w.log);
        for (long t = 11000; t <= 120000; t += 1000) {
            w.controller.tick(t);
        }
        check(w.count("prefs:{org.a.one=4}") == 3, "and not again once it worked: " + w.log);

        // A refusal that never ends is retried, but no faster than the delay.
        w = world();
        w.choices.set("org.a.one", Choices.Choice.TOR);
        w.failures.put("prefs", -1);
        w.failures.put("daemon.connect", -1);
        w.controller.start(0);
        for (long t = 0; t <= 60000; t += 1000) {
            w.controller.tick(t);
        }
        check(w.count("prefs:{org.a.one=4}") <= 3 + 60000 / ExitController.PREFS_RETRY_MS + 20, "a permanent refusal is retried at the delay, not every tick: " + w.count("prefs:"));

        // The last app leaves Tor and the platform refuses to clear the preference: the clearing is retried, so an app set
        // back to Direct does not stay without a network until the next reboot.
        w = world();
        w.choices.set("org.a.one", Choices.Choice.TOR);
        w.controller.start(0);
        w.controller.tick(0);
        w.choices.set("org.a.one", Choices.Choice.DIRECT);
        w.consent = Consent.OFF;
        w.failures.put("prefs", 1);
        mark = w.log.size();
        w.controller.changed(1000);
        check(w.last.phase == Status.Phase.IDLE && w.count("prefs:{}") == 1, "idle, the clearing was refused: " + w.log.subList(mark, w.log.size()));
        w.controller.tick(1000 + ExitController.PREFS_RETRY_MS - 1);
        check(w.count("prefs:{}") == 1, "no retry before the delay");
        w.controller.tick(1000 + ExitController.PREFS_RETRY_MS);
        check(w.count("prefs:{}") == 2, "the clearing is retried while idle: " + w.log);
        w.controller.tick(60000);
        check(w.count("prefs:{}") == 2, "and not again once it worked");

        // A change of the choices or the packages is an event, not a tick: it asks again at once.
        w = world();
        w.choices.set("org.a.one", Choices.Choice.TOR);
        w.controller.start(0);
        w.controller.tick(0);
        w.choices.set("org.a.one", Choices.Choice.DIRECT);
        w.consent = Consent.OFF;
        w.failures.put("prefs", 1);
        w.controller.changed(1000);
        check(w.count("prefs:{}") == 1, "the clearing was refused: " + w.log);
        w.controller.changed(1500);
        check(w.count("prefs:{}") == 2, "a sync retries the clearing: " + w.log);
        w.controller.changed(1600);
        check(w.count("prefs:{}") == 2, "and not again once it worked: " + w.log);
    }

    private static void failuresAndLadder() {
        World w = world();
        w.choices.set("org.a.one", Choices.Choice.TOR);
        w.controller.start(0);
        w.controller.tick(0);
        w.nextFailures.add(new DaemonApi.Failure(10100, 7, 3));
        w.controller.tick(5000);
        check(w.count("notify:10100/7") == 1, "one notification");
        w.nextFailures.add(new DaemonApi.Failure(10100, 7, 4));
        w.nextFailures.add(new DaemonApi.Failure(10100, 8, 3));
        w.controller.tick(10000);
        check(w.count("notify:10100/7") == 1 && w.count("notify:10100/8") == 1, "the same key not again in its window, another company yes");
        w.nextFailures.add(new DaemonApi.Failure(10100, 7, 3));
        w.controller.tick(5000 + Ladder.WINDOW_MS + 5000);
        check(w.count("notify:10100/7") == 2, "again after the window");
    }

    private static void promotions() {
        World w = world();
        w.controller.start(0);
        w.controller.tick(0);
        check(w.last.promotions.isEmpty(), "no promotions at first");
        // A fresh promotion notifies once and is listed in the status.
        w.nextPromotions.add(new DaemonApi.Promotion(10100, 7, true));
        w.controller.tick(5000);
        check(w.count("promoted:10100/7") == 1, "one notification for a fresh promotion: " + w.log);
        check(w.last.promotions.size() == 1 && w.last.promotions.get(0).uid == 10100 && w.last.promotions.get(0).company == 7, "listed: " + w.last);
        // The daemon lists it again, not fresh: no second notification, still listed.
        w.nextPromotions.add(new DaemonApi.Promotion(10100, 7, false));
        w.controller.tick(10000);
        check(w.count("promoted:") == 1 && w.last.promotions.size() == 1, "listed again, not notified again");
        // Not listed once: kept (a frame can arrive with the next reply). Not listed twice: over.
        w.controller.tick(15000);
        check(w.last.promotions.size() == 1, "one poll without it keeps it: " + w.last);
        w.controller.tick(20000);
        check(w.last.promotions.isEmpty(), "two polls without it end it: " + w.last);
        // Sorted by uid, then company.
        w.nextPromotions.add(new DaemonApi.Promotion(10101, 3, true));
        w.nextPromotions.add(new DaemonApi.Promotion(10100, 9, true));
        w.nextPromotions.add(new DaemonApi.Promotion(10100, 7, false));
        w.controller.tick(25000);
        check(w.last.promotions.toString().equals("[10100/7, 10100/9, 10101/3]"), "sorted: " + w.last.promotions);
        check(w.count("promoted:10101/3") == 1 && w.count("promoted:10100/9") == 1 && w.count("promoted:10100/7") == 1, "only the fresh ones notify (10100/7 was notified at 5 s, not again)");
        // "Hide again": the daemon is told, the entry goes at once and nothing notifies.
        int mark = w.log.size();
        w.controller.release(10100, 9, 26000);
        check(w.indexOf("daemon.release:10100/9", mark) >= 0, "the release reaches the daemon: " + w.log.subList(mark, w.log.size()));
        check(w.last.promotions.toString().equals("[10100/7, 10101/3]"), "the entry is gone: " + w.last.promotions);
        // A release that the daemon refuses is a lost daemon, like any other failed request.
        w.failures.put("daemon.release", 1);
        w.controller.release(10101, 3, 27000);
        check(w.last.phase == Status.Phase.PAUSED && w.last.promotions.isEmpty(), "a failed release pauses and forgets the promotions: " + w.last);
        // The daemon restarts empty: the promotions are not carried over.
        w.controller.tick(28000);
        check(w.last.phase == Status.Phase.RUNNING && w.last.promotions.isEmpty(), "running again without promotions: " + w.last);
        // Going idle forgets them as well, and a release while the daemon is down only forgets the entry.
        w.nextPromotions.add(new DaemonApi.Promotion(10100, 7, true));
        w.controller.tick(33000);
        check(w.last.promotions.size() == 1, "promoted again");
        w.consent = Consent.OFF;
        w.controller.changed(34000);
        check(w.last.phase == Status.Phase.IDLE && w.last.promotions.isEmpty(), "idle: no promotions");
        mark = w.log.size();
        w.controller.release(10100, 7, 35000);
        check(w.indexOf("daemon.release", mark) < 0, "no daemon, no release call: " + w.log.subList(mark, w.log.size()));
        check(ExitController.PROMOTION_STALE_MS == 10000, "two polls");
    }

    private static void refusedRequests() {
        World w = world();
        w.choices.set("org.a.one", Choices.Choice.TOR);
        w.failures.put("daemon.setPolicy", 1);
        w.controller.start(0);
        w.controller.tick(0);
        check(w.last.phase == Status.Phase.CONNECTING && w.indexOf("net.register", 0) < 0, "a refused table stops the sequence before the network: " + w.log);
        check(w.indexOf("status:RUNNING", 0) < 0, "never running");
        w.controller.tick(1000);
        check(w.last.phase == Status.Phase.RUNNING, "and the retry succeeds");
        // The platform refusing the network is handled the same way.
        World v = world();
        v.choices.set("org.a.one", Choices.Choice.TOR);
        v.failures.put("net.register", 1);
        v.controller.start(0);
        v.controller.tick(0);
        check(v.last.phase == Status.Phase.CONNECTING && v.indexOf("status:RUNNING", 0) < 0, "a refused network: not running");
        // A runtime exception from the platform (a missing permission) is a lost link as well, not a crash.
        World x = new World();
        x.apps.add(new Policy.App("org.a.one", 10100));
        x.choices.set("org.a.one", Choices.Choice.TOR);
        ExitController c = new ExitController(x.daemon, x.route, new PlatformApi() {
            @Override
            public int[] users() {
                return new int[] {0};
            }

            @Override
            public List<Policy.App> installedApps() {
                return x.apps;
            }

            @Override
            public boolean vpnActive() {
                return false;
            }

            @Override
            public boolean privateDnsStrict() {
                return false;
            }

            @Override
            public long registerTorNetwork(String iface) {
                throw new SecurityException("no permission");
            }

            @Override
            public void unregisterTorNetwork() {}

            @Override
            public void setOemPreferences(Map<String, Integer> prefs) {}

            @Override
            public void notifyFailure(long uid, int company) {}

            @Override
            public void notifyPromotion(long uid, int company) {}

            @Override
            public void publish(Status s) {
                x.last = s;
            }
        }, x.store, new Ladder());
        c.start(0);
        c.tick(0);
        check(x.last.phase == Status.Phase.CONNECTING && "SecurityException".equals(x.last.lastError), "a SecurityException is a lost link: " + x.last);
    }

    private static void warnings() {
        World w = world();
        w.vpn = true;
        w.strictDns = true;
        w.controller.start(0);
        w.controller.tick(0);
        check(w.last.warnings.contains(Policy.Warning.VPN_ACTIVE) && w.last.warnings.contains(Policy.Warning.PRIVATE_DNS_LIMITED), "warnings reach the status: " + w.last);
    }

    // ---- the clients ----------------------------------------------------------------------------------------------

    /**
     * A scripted peer. The replies are in segments: segment k is released by the k-th write of the client, so that a reply
     * that belongs to a later request is not yet there when an earlier one is read. {@code reply} adds to the last segment.
     */
    private static final class Script implements Link.Connector {
        final ByteArrayOutputStream sent = new ByteArrayOutputStream();
        final List<ByteArrayOutputStream> segments = new ArrayList<>(Arrays.asList(new ByteArrayOutputStream()));
        int writes;
        int opens;

        Script reply(byte[]... frames) {
            for (byte[] f : frames) {
                segments.get(segments.size() - 1).write(f, 0, f.length);
            }
            return this;
        }

        Script then() {
            segments.add(new ByteArrayOutputStream());
            return this;
        }

        @Override
        public Link.Transport open() {
            opens++;
            writes = 0;
            final int[] consumed = {0};
            final InputStream in = new InputStream() {
                private byte[] released() {
                    ByteArrayOutputStream all = new ByteArrayOutputStream();
                    for (int i = 0; i < Math.min(writes, segments.size()); i++) {
                        byte[] b = segments.get(i).toByteArray();
                        all.write(b, 0, b.length);
                    }
                    return all.toByteArray();
                }

                @Override
                public int available() {
                    return released().length - consumed[0];
                }

                @Override
                public int read() {
                    byte[] b = released();
                    return consumed[0] < b.length ? b[consumed[0]++] & 0xff : -1;
                }

                @Override
                public int read(byte[] dst, int off, int n) {
                    byte[] b = released();
                    int k = Math.min(n, b.length - consumed[0]);
                    if (k <= 0) {
                        return -1;
                    }
                    System.arraycopy(b, consumed[0], dst, off, k);
                    consumed[0] += k;
                    return k;
                }
            };
            final OutputStream out = new OutputStream() {
                @Override
                public void write(int b) {
                    sent.write(b);
                }

                @Override
                public void write(byte[] b, int off, int n) {
                    sent.write(b, off, n);
                    writes++;
                }
            };
            return new Link.Transport() {
                @Override
                public InputStream in() {
                    return in;
                }

                @Override
                public OutputStream out() {
                    return out;
                }

                @Override
                public void close() {}
            };
        }
    }

    private static byte[] frame(int tag, int... payload) {
        byte[] b = new byte[5 + payload.length];
        b[3] = (byte) (1 + payload.length);
        b[4] = (byte) tag;
        for (int i = 0; i < payload.length; i++) {
            b[5 + i] = (byte) payload[i];
        }
        return b;
    }

    private static byte[] cat(byte[]... parts) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        for (byte[] p : parts) {
            o.write(p, 0, p.length);
        }
        return o.toByteArray();
    }

    private static byte[] ready(int version, String iface) {
        int[] p = new int[1 + iface.length()];
        p[0] = version;
        for (int i = 0; i < iface.length(); i++) {
            p[1 + i] = iface.charAt(i);
        }
        return frame(0x81, p);
    }

    private static void expectIo(IoCall c, String what) {
        try {
            c.run();
        } catch (IOException e) {
            return;
        }
        check(false, what + " must fail with an IOException");
    }

    private interface IoCall {
        void run() throws IOException;
    }

    private static void daemonClient() throws IOException {
        List<Policy.Cls> classes = Arrays.asList(new Policy.Cls(10000, 19999, Policy.Kind.MASKED));
        // Hello, a policy, overrides and a poll with reports after the state; each request carries the next sequence number.
        Script s = new Script().reply(ready(3, "exit0"), frame(0x84, 1), frame(0x84, 2),
                frame(0x82, 1), cat(frame(0x83, 0, 0, 0x27, 0x8b, 0, 7, 0, 0, 0, 3)), cat(frame(0x83, 0, 0, 0x27, 0x8c, 0, 8, 0, 0, 0, 4)));
        DaemonClient c = new DaemonClient(s);
        check("exit0".equals(c.connect()), "interface name");
        c.setPolicy(classes);
        c.setOverrides(10123, new int[] {7});
        DaemonApi.Poll p = c.poll();
        check(p.state == 1 && p.failures.size() == 2 && p.failures.get(0).uid == 10123 && p.failures.get(0).company == 7
                && p.failures.get(0).count == 3 && p.failures.get(1).uid == 10124, "poll: " + p.failures.size());
        byte[] want = cat(ControlCodec.hello(), ControlCodec.setPolicy(1, classes), ControlCodec.setOverrides(2, 10123, new int[] {7}),
                ControlCodec.getState(3));
        check(Arrays.equals(s.sent.toByteArray(), want), "the requests, byte for byte");

        // Shutdown is a request like the others.
        s = new Script().reply(ready(3, "exit0"), frame(0x84, 1));
        c = new DaemonClient(s);
        c.connect();
        c.shutdown();
        check(Arrays.equals(s.sent.toByteArray(), cat(ControlCodec.hello(), ControlCodec.shutdown(1))), "shutdown request");

        // A shutdown that is not acknowledged is a failure.
        c = new DaemonClient(new Script().reply(ready(3, "exit0")));
        c.connect();
        DaemonClient noAck = c;
        expectIo(noAck::shutdown, "a shutdown without an Ack");

        // A refusal carries its code and is an IOException.
        s = new Script().reply(ready(3, "exit0"), frame(0x85, 1, 2));
        c = new DaemonClient(s);
        c.connect();
        try {
            c.setPolicy(classes);
            check(false, "a refused table must throw");
        } catch (Link.ProtocolException e) {
            check(e.code == ControlCodec.ERR_TOO_MANY, "the code of the refusal");
        }
        // A wrong sequence number, a reply of the wrong kind, a wrong version, a closed link and garbage.
        for (Script bad : new Script[] {
                new Script().reply(ready(3, "exit0"), frame(0x84, 9)),
                new Script().reply(ready(3, "exit0"), frame(0x82, 1)),
                new Script().reply(ready(3, "exit0")),
                new Script().reply(ready(3, "exit0"), new byte[] {0, 0, 0, 2, 0x7f, 1})}) {
            DaemonClient d = new DaemonClient(bad);
            d.connect();
            expectIo(() -> d.setPolicy(classes), "a bad answer to a request");
        }
        for (Script bad : new Script[] {new Script().reply(ready(2, "exit0")), new Script().reply(frame(0x85, 0, 4)), new Script()}) {
            DaemonClient d = new DaemonClient(bad);
            expectIo(d::connect, "a bad answer to hello");
        }
        // Reports that arrive before an Ack are kept for the next poll.
        s = new Script().reply(ready(3, "exit0"), frame(0x83, 0, 0, 0x27, 0x8b, 0, 7, 0, 0, 0, 3), frame(0x84, 1), frame(0x82, 1));
        c = new DaemonClient(s);
        c.connect();
        c.setPolicy(classes);
        p = c.poll();
        check(p.failures.size() == 1 && p.failures.get(0).company == 7, "a report before an Ack is not lost");
        // After a poll the queue is empty.
        s = new Script().reply(ready(3, "exit0")).then().reply(frame(0x83, 0, 0, 0x27, 0x8b, 0, 7, 0, 0, 0, 3), frame(0x82, 1)).then().reply(frame(0x82, 0));
        c = new DaemonClient(s);
        c.connect();
        check(c.poll().failures.size() == 1, "a report before the state is returned with it");
        check(c.poll().failures.isEmpty(), "and is not returned again");
        // The sequence number wraps after 255 and never becomes 0.
        Script many = new Script().reply(ready(3, "exit0"));
        for (int i = 1; i <= 256; i++) {
            many.reply(frame(0x84, i % 255 == 0 ? 255 : i % 255));
        }
        c = new DaemonClient(many);
        c.connect();
        for (int i = 0; i < 256; i++) {
            c.setOverrides(10000, new int[0]);
        }
        check(many.sent.size() > 0, "256 requests");
        // Reconnecting opens a new connection and starts the numbers again from 1.
        c.connect();
        c.setOverrides(10000, new int[0]);
        check(many.opens == 2, "two connections");
    }

    /** A connector that serves the first connection from {@code first} and every later one from {@code later}. */
    private static Link.Connector twice(Script first, Script later) {
        final int[] n = {0};
        return () -> n[0]++ == 0 ? first.open() : later.open();
    }

    private static void statusEquality() {
        java.util.Set<Policy.Warning> none = java.util.Collections.<Policy.Warning>emptySet();
        Status a = new Status(Status.Phase.RUNNING, 1, none, null, Arrays.asList(new Status.Promoted(10100, 7)));
        Status same = new Status(Status.Phase.RUNNING, 1, none, null, Arrays.asList(new Status.Promoted(10100, 7)));
        Status other = new Status(Status.Phase.RUNNING, 1, none, null, Arrays.asList(new Status.Promoted(10100, 8)));
        Status empty = new Status(Status.Phase.RUNNING, 1, none, null);
        check(a.equals(same) && a.hashCode() == same.hashCode(), "equal statuses");
        check(!a.equals(empty) && !a.equals(other) && !empty.equals(a), "the promotions are part of the status");
        check(a.toString().contains("10100/7") && !empty.toString().contains("promoted"), "the promotions are in the text");
        check(a.promotions.size() == 1 && empty.promotions.isEmpty(), "the list");
    }

    private static void daemonClientPromotions() throws IOException {
        // What the old connection queued is forgotten with it: a new connection starts without it.
        byte[] promotedFrame = frame(0x86, 0, 0, 0x27, 0x8b, 0, 7, 1);
        Script first = new Script().reply(ready(3, "exit0")).then().reply(promotedFrame, frame(0x84, 1));
        Script later = new Script().reply(ready(3, "exit0")).then().reply(frame(0x82, 1));
        DaemonClient fresh = new DaemonClient(twice(first, later));
        fresh.connect();
        fresh.setPolicy(Arrays.asList(new Policy.Cls(10000, 19999, Policy.Kind.MASKED)));
        fresh.connect();
        check(fresh.poll().promotions.isEmpty(), "a promotion queued on the old connection is not carried to the new one");

        // Promotions ride on the reply to GetState, after the state; a frame that has not arrived yet comes with the next reply.
        byte[] promoted = frame(0x86, 0, 0, 0x27, 0x8b, 0, 7, 1);
        byte[] again = frame(0x86, 0, 0, 0x27, 0x8b, 0, 7, 0);
        Script s = new Script().reply(ready(3, "exit0")).then().reply(frame(0x82, 1), promoted).then().reply(frame(0x82, 1), again);
        DaemonClient c = new DaemonClient(s);
        c.connect();
        DaemonApi.Poll p = c.poll();
        check(p.promotions.size() == 1 && p.promotions.get(0).uid == 10123 && p.promotions.get(0).company == 7 && p.promotions.get(0).fresh, "a fresh promotion");
        p = c.poll();
        check(p.promotions.size() == 1 && !p.promotions.get(0).fresh, "a listed promotion is not fresh");
        // A promotion that arrives while a request waits for its Ack is kept for the next poll.
        s = new Script().reply(ready(3, "exit0"), promoted, frame(0x84, 1), frame(0x82, 1));
        c = new DaemonClient(s);
        c.connect();
        c.setPolicy(Arrays.asList(new Policy.Cls(10000, 19999, Policy.Kind.MASKED)));
        p = c.poll();
        check(p.promotions.size() == 1 && p.promotions.get(0).company == 7, "a promotion before an Ack is not lost");
        // The queue is empty after a poll and after a new connection.
        s = new Script().reply(ready(3, "exit0")).then().reply(frame(0x82, 1), promoted).then().reply(frame(0x82, 1));
        c = new DaemonClient(s);
        c.connect();
        c.poll();
        check(c.poll().promotions.isEmpty(), "not returned again");
        // A release is a request like the others and is acknowledged.
        s = new Script().reply(ready(3, "exit0"), frame(0x84, 1));
        c = new DaemonClient(s);
        c.connect();
        c.release(10123, 7);
        check(Arrays.equals(s.sent.toByteArray(), cat(ControlCodec.hello(), ControlCodec.release(1, 10123, 7))), "release request, byte for byte");
        // A refused release carries its code; an unacknowledged one is a failure.
        s = new Script().reply(ready(3, "exit0"), frame(0x85, 1, 1));
        c = new DaemonClient(s);
        c.connect();
        try {
            c.release(5, 7);
            check(false, "a refused release must throw");
        } catch (Link.ProtocolException e) {
            check(e.code == ControlCodec.ERR_BAD_VALUE, "the code of the refusal");
        }
        DaemonClient noAck = new DaemonClient(new Script().reply(ready(3, "exit0")));
        noAck.connect();
        expectIo(() -> noAck.release(10123, 7), "a release without an Ack");
    }

    private static void routeClient() throws IOException {
        List<int[]> steer = Arrays.asList(new int[] {10000, 10099});
        List<int[]> guard = Arrays.asList(new int[] {10100, 10100});
        Script s = new Script().reply(frame(0x81, 1), frame(0x84, 1), frame(0x84, 2));
        RouteClient r = new RouteClient(s);
        r.connect();
        r.apply("exit0", 101, steer, guard);
        r.clear();
        byte[] want = cat(ControlCodec.routeHello(), ControlCodec.routeApply(1, "exit0", 101, steer, guard), ControlCodec.routeClear(2));
        check(Arrays.equals(s.sent.toByteArray(), want), "route requests, byte for byte");
        s = new Script().reply(frame(0x81, 1), frame(0x85, 1, 5));
        r = new RouteClient(s);
        r.connect();
        try {
            r.apply("exit0", 0, steer, guard);
            check(false, "must throw");
        } catch (Link.ProtocolException e) {
            check(e.code == ControlCodec.ERR_FAILED, "the helper's refusal");
        }
        for (Script bad : new Script[] {new Script().reply(frame(0x81, 2)), new Script().reply(frame(0x85, 0, 4)), new Script()}) {
            RouteClient b = new RouteClient(bad);
            expectIo(b::connect, "a bad hello");
        }
        s = new Script().reply(frame(0x81, 1), frame(0x84, 7));
        RouteClient c = new RouteClient(s);
        c.connect();
        expectIo(() -> c.clear(), "a wrong sequence number");
        // Reconnecting starts the numbers again from 1.
        s = new Script().reply(frame(0x81, 1), frame(0x84, 1));
        RouteClient again = new RouteClient(s);
        again.connect();
        again.clear();
        again.connect();
        again.clear();
        // A closed link ends with an EOF.
        s = new Script().reply(frame(0x81, 1));
        RouteClient e = new RouteClient(s);
        e.connect();
        try {
            e.clear();
            check(false, "must throw");
        } catch (EOFException ok) {
            // expected
        }
    }

    public static void run() throws Exception {
        bootOrder();
        daemonRestart();
        backoff();
        idleWithoutConsentOrTorApps();
        maskedOnly();
        torOnlyWithoutConsent();
        changesWhileRunning();
        choiceWhileDaemonIsNotRunning();
        preferenceFailuresAreRetried();
        failuresAndLadder();
        promotions();
        refusedRequests();
        warnings();
        daemonClient();
        statusEquality();
        daemonClientPromotions();
        routeClient();
    }
}
