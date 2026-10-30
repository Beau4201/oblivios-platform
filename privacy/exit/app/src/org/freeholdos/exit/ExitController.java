// SPDX-License-Identifier: Apache-2.0
package org.freeholdos.exit;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The brain of the exit app (ADR-186): it owns the policy and drives the daemon, the route helper and the platform in the
 * order that keeps every app closed until its rules are in place. Pure Java: all effects go through {@link DaemonApi},
 * {@link RouteApi} and {@link PlatformApi}, and time is an argument, so the boot order, the restart of the daemon and the
 * retry schedule are host checks. All methods run on one thread (the service's worker).
 *
 * <p>The order after a connection, and the reason for each step (F01, F11):
 * <ol>
 *   <li>the network preferences of the apps on Tor first: an app that is set to Tor has no network at all until the Tor
 *       network exists, so a step that fails later can never leave it on the default network (review of 2026-10-23, C2-01);
 *   <li>the rules, without a Tor network id: its sockets carry the id of another network and are closed;
 *   <li>the class table, then the overrides;
 *   <li>the Tor network, only when some app is set to Tor, and the rules again with its id (now the guard lets it through);
 *   <li>only then {@link Status.Phase#RUNNING} is published.
 * </ol>
 * When a link is lost the Tor network goes away and the preferences stay: the apps on Tor have no network until it is back.
 * A preference that the platform refuses is tried again every {@link #PREFS_RETRY_MS} in every phase, also while the
 * daemon is down or not wanted.
 */
public final class ExitController {
    /** The delay before the n-th retry (n from 1): 1, 2, 4, 8, 16 s, then 30 s. */
    public static final long[] BACKOFF_MS = {1000, 2000, 4000, 8000, 16000, 30000};
    /** How often the daemon is asked for its state and the failure reports. */
    public static final long POLL_MS = 5000;
    /**
     * A promotion that the daemon has not listed for this long is over. The daemon lists every active promotion on each
     * reply, but a frame can arrive with the next reply, so a promotion is dropped only after two polls without it.
     */
    public static final long PROMOTION_STALE_MS = 2 * POLL_MS;
    /** How long to wait before the platform is asked again for a preference that it refused (the call itself may take 10 s). */
    public static final long PREFS_RETRY_MS = 5000;

    private final DaemonApi daemon;
    private final RouteApi route;
    private final PlatformApi platform;
    private final Store store;
    private final Ladder ladder;

    private Status.Phase phase = Status.Phase.IDLE;
    private boolean started;
    private boolean wanted;
    /** Whether the links have been up since the daemon became wanted: tells PAUSED from the first CONNECTING. */
    private boolean ranBefore;
    private int attempts;
    private long nextAttemptMs;
    private long nextPollMs;
    private int daemonState = -1;
    private String lastError;

    // What the daemon, the helper and the platform have been told, so that a change sends only what differs.
    private String iface;
    private long netId; // 0: the Tor network is not registered
    private Policy.Out applied;
    private Map<String, Integer> appliedPrefs;
    /** The platform refused the last preference (or the clearing of it); tick() asks again from {@link #prefsRetryMs}. */
    private boolean prefsPending;
    private long prefsRetryMs;
    private final Map<Long, String> sentOverrides = new HashMap<>();
    /** The exit country the daemon holds for each app uid (the place of an Isolated app on Tor). */
    private final Map<Long, String> sentCountries = new HashMap<>();
    /** The companies the daemon passes on to an app for now: (uid, company) to the time it was last listed. */
    private final java.util.TreeMap<Status.Promoted, Long> promoted = new java.util.TreeMap<>(
            (a, b) -> a.uid != b.uid ? Long.compare(a.uid, b.uid) : Integer.compare(a.company, b.company));

    public ExitController(DaemonApi daemon, RouteApi route, PlatformApi platform, Store store, Ladder ladder) {
        this.daemon = daemon;
        this.route = route;
        this.platform = platform;
        this.store = store;
        this.ladder = ladder;
    }

    public Status status() {
        return new Status(phase, daemonState, applied == null ? Collections.<Policy.Warning>emptySet() : applied.warnings, lastError,
                new java.util.ArrayList<>(promoted.keySet()));
    }

    /**
     * "Hide again": the user wants a company hidden from an app again. The daemon ends the promotion and keeps the company
     * hidden for that app until it restarts. A daemon that cannot be reached has forgotten every promotion already.
     */
    public void release(long uid, int company, long nowMs) {
        promoted.remove(new Status.Promoted(uid, company));
        if (phase == Status.Phase.RUNNING) {
            try {
                daemon.release(uid, company);
            } catch (IOException | RuntimeException e) {
                lost(nowMs, e);
                return;
            }
        }
        publish();
    }

    private static boolean needsDaemon(Choices choices, Consent consent) {
        return consent == Consent.ON || !choices.torApps().isEmpty();
    }

    /** Call at boot and whenever the process starts. */
    public void start(long nowMs) {
        sync(nowMs);
    }

    /** Call when the choices, the consent, the packages or the users changed. */
    public void changed(long nowMs) {
        sync(nowMs);
    }

    /** Call about once a second. */
    public void tick(long nowMs) {
        if (!wanted) {
            if (prefsPending && nowMs >= prefsRetryMs) {
                goIdle(nowMs); // the clearing of the preference was refused: an app set back to Direct has no network until it works
            }
            return;
        }
        if (phase == Status.Phase.RUNNING) {
            if (nowMs >= nextPollMs) {
                nextPollMs = nowMs + POLL_MS;
                try {
                    DaemonApi.Poll p = daemon.poll();
                    daemonState = p.state;
                    sendCountries(store.choices());
                    for (DaemonApi.Failure f : p.failures) {
                        if (ladder.shouldNotify((int) f.uid, f.company, nowMs)) {
                            platform.notifyFailure(f.uid, f.company);
                        }
                    }
                    for (DaemonApi.Promotion q : p.promotions) {
                        promoted.put(new Status.Promoted(q.uid, q.company), nowMs);
                        if (q.fresh) {
                            platform.notifyPromotion(q.uid, q.company);
                        }
                    }
                    promoted.values().removeIf(seen -> nowMs - seen >= PROMOTION_STALE_MS);
                    publish();
                } catch (IOException | RuntimeException e) {
                    lost(nowMs, e);
                }
            }
        } else {
            if (prefsPending && nowMs >= prefsRetryMs) {
                applyPreferences(nowMs); // while the daemon is down, a refused preference would leave an app on Tor on the Wi-Fi
            }
            if (nowMs >= nextAttemptMs) {
                connect(nowMs);
            }
        }
    }

    /** Whether the daemon is needed right now, and what to do about it. */
    private void sync(long nowMs) {
        Choices choices = store.choices();
        boolean want = needsDaemon(choices, store.consent());
        if (!want) {
            // The platform keeps a preference when this process dies, so the first call after a start always cleans up.
            if (!started || wanted || phase != Status.Phase.IDLE || prefsPending) {
                goIdle(nowMs);
            }
            started = true;
            return;
        }
        started = true;
        if (!wanted) {
            wanted = true;
            // Deny by default (F11): the apps on Tor lose the default network now, before the daemon is up, and get the Tor
            // network when it exists. The rules follow with the connection.
            applyPreferences(nowMs);
            phase = Status.Phase.CONNECTING;
            attempts = 0;
            nextAttemptMs = nowMs;
            publish();
            return;
        }
        if (phase == Status.Phase.RUNNING) {
            try {
                push(false);
                publish();
            } catch (IOException | RuntimeException e) {
                lost(nowMs, e);
            }
        } else {
            // The daemon is being reached or is down: a choice made now still closes the app at once (F11). Without this
            // an app set to Tor while the daemon restarts would stay Direct until the connection succeeds, which may be never.
            applyPreferences(nowMs);
            publish();
        }
    }

    /**
     * Sets the platform preference for the current choices when it differs from what is set; the daemon is not needed. A
     * refusal is remembered and tried again by tick() (the preference decides whether an app is on the Wi-Fi or on Tor).
     */
    private void applyPreferences(long nowMs) {
        try {
            Policy.Out out = Policy.compute(store.choices(), platform.installedApps(), platform.users(), store.consent(),
                    platform.vpnActive(), platform.privateDnsStrict());
            if (appliedPrefs == null || !appliedPrefs.equals(out.oemPrefs)) {
                platform.setOemPreferences(out.oemPrefs);
                appliedPrefs = out.oemPrefs;
            }
            prefsPending = false;
        } catch (IOException | RuntimeException e) {
            lastError = e.getClass().getSimpleName();
            prefsPending = true;
            prefsRetryMs = nowMs + PREFS_RETRY_MS;
        }
    }

    /** Nothing needs the daemon any more: no preference, no network, no rule, and the service is stopped. */
    private void goIdle(long nowMs) {
        try {
            platform.setOemPreferences(Collections.<String, Integer>emptyMap());
            prefsPending = false;
        } catch (IOException | RuntimeException e) {
            lastError = e.getClass().getSimpleName();
            prefsPending = true;
            prefsRetryMs = nowMs + PREFS_RETRY_MS;
        }
        appliedPrefs = null;
        platform.unregisterTorNetwork();
        netId = 0;
        // The helper is reached once more, only to remove its rules; a helper that is down has nothing to remove.
        try {
            route.connect();
            route.clear();
        } catch (IOException | RuntimeException e) {
            // nothing to remove, or it cannot be reached: the rules vanish with the helper's next clear
        } finally {
            route.close();
        }
        if (wanted) {
            // The daemon was asked for: stop its Tor client (init starts a fresh daemon that has contacted nobody).
            try {
                daemon.connect();
                daemon.shutdown();
            } catch (IOException | RuntimeException e) {
                lastError = e.getClass().getSimpleName();
            }
        }
        daemon.close();
        wanted = false;
        ranBefore = false;
        phase = Status.Phase.IDLE;
        daemonState = -1;
        applied = null;
        sentOverrides.clear();
        sentCountries.clear();
        promoted.clear();
        attempts = 0;
        publish();
    }

    private void connect(long nowMs) {
        try {
            iface = daemon.connect();
            route.connect();
            // The daemon starts empty: everything is sent again, including overrides.
            sentOverrides.clear();
            sentCountries.clear();
            appliedPrefs = null;
            netId = 0;
            push(true);
            phase = Status.Phase.RUNNING;
            ranBefore = true;
            attempts = 0;
            lastError = null;
            nextPollMs = nowMs + POLL_MS;
            publish();
        } catch (IOException | RuntimeException e) {
            lost(nowMs, e);
        }
    }

    /** Makes the helper, the daemon and the platform match the choices; sends only what changed unless {@code all}. */
    private void push(boolean all) throws IOException {
        Choices choices = store.choices();
        Policy.Out out = Policy.compute(choices, platform.installedApps(), platform.users(), store.consent(),
                platform.vpnActive(), platform.privateDnsStrict());
        boolean needNetwork = !out.guard.isEmpty() || !out.oemPrefs.isEmpty();
        boolean rulesChanged = all || applied == null || !same(applied.steer, out.steer) || !same(applied.guard, out.guard);
        boolean tableChanged = all || applied == null || !sameClasses(applied.classes, out.classes);

        // 0. the preferences of the apps on Tor: with no Tor network yet they have no network at all, so a step below that
        // fails leaves them closed and never on the default network (C2-01)
        if (appliedPrefs == null || !appliedPrefs.equals(out.oemPrefs)) {
            platform.setOemPreferences(out.oemPrefs);
            appliedPrefs = out.oemPrefs;
        }
        prefsPending = false;
        // 1. the rules without a Tor network (a guarded app is closed before the network exists)
        if (rulesChanged) {
            route.apply(iface, netId, out.steer, out.guard);
        }
        // 2. the class table, 3. the overrides
        if (tableChanged) {
            daemon.setPolicy(out.classes);
        }
        sendOverrides(choices);
        sendCountries(choices);
        // 4. the Tor network, once, and the rules with its id
        if (needNetwork && netId == 0) {
            netId = platform.registerTorNetwork(iface);
            route.apply(iface, netId, out.steer, out.guard);
        }
        // The last app left Tor: the preference is gone, so the network and the id in the rules can go too.
        if (!needNetwork && netId != 0) {
            platform.unregisterTorNetwork();
            netId = 0;
            route.apply(iface, 0, out.steer, out.guard);
        }
        applied = out;
    }

    /** Sends the override list of every app uid that has one, and an empty list for a uid that had one and has none now. */
    private void sendOverrides(Choices choices) throws IOException {
        Map<Long, String> want = new HashMap<>();
        Map<Long, int[]> lists = new HashMap<>();
        for (Policy.App a : platform.installedApps()) {
            int[] companies = choices.shownCompanies(a.pkg);
            if (companies.length > 0) {
                want.put((long) a.uid, java.util.Arrays.toString(companies));
                lists.put((long) a.uid, companies);
            }
        }
        for (Map.Entry<Long, int[]> e : lists.entrySet()) {
            if (!want.get(e.getKey()).equals(sentOverrides.get(e.getKey()))) {
                daemon.setOverrides(e.getKey(), e.getValue());
                sentOverrides.put(e.getKey(), want.get(e.getKey()));
            }
        }
        for (Long uid : new java.util.ArrayList<>(sentOverrides.keySet())) {
            if (!want.containsKey(uid)) {
                daemon.setOverrides(uid, new int[0]);
                sentOverrides.remove(uid);
            }
        }
    }

    /**
     * Sends the exit country of every app uid whose persona is abroad and that is on Tor ({@link Presenter#exitFor}), and
     * removes it for a uid that had one and has none now. The place is the service's, so this runs on every poll too.
     */
    private void sendCountries(Choices choices) throws IOException {
        Map<String, String> places = platform.placeCountries();
        if (places == null) {
            return; // the service could not be read: keep every country already sent
        }
        if (places.isEmpty() && sentCountries.isEmpty()) {
            return; // the common case: no persona abroad, and the poll does not list every package for nothing
        }
        String own = platform.ownCountry();
        Map<Long, String> want = new HashMap<>();
        for (Policy.App a : platform.installedApps()) {
            String country = places.get(a.pkg);
            if (country != null && Presenter.exitFor(country, own, choices.choiceOf(a.pkg)) == Presenter.Exit.COUNTRY) {
                want.put((long) a.uid, country);
            }
        }
        for (Map.Entry<Long, String> e : want.entrySet()) {
            if (!e.getValue().equals(sentCountries.get(e.getKey()))) {
                daemon.setCountry(e.getKey(), e.getValue());
                sentCountries.put(e.getKey(), e.getValue());
            }
        }
        for (Long uid : new java.util.ArrayList<>(sentCountries.keySet())) {
            if (!want.containsKey(uid)) {
                daemon.setCountry(uid, null);
                sentCountries.remove(uid);
            }
        }
    }

    /** A link failed: the Tor network goes, the preferences stay, and the connection is tried again later. */
    private void lost(long nowMs, Exception cause) {
        lastError = cause.getClass().getSimpleName();
        platform.unregisterTorNetwork();
        netId = 0;
        daemon.close();
        route.close();
        daemonState = -1;
        // A daemon that starts again has forgotten its promotions (they live in its memory only).
        promoted.clear();
        phase = ranBefore ? Status.Phase.PAUSED : Status.Phase.CONNECTING;
        attempts++;
        nextAttemptMs = nowMs + BACKOFF_MS[Math.min(attempts, BACKOFF_MS.length) - 1];
        // The failed step may have been before the preference of a choice made just now: do it without the daemon (C2-01).
        if (!prefsPending || nowMs >= prefsRetryMs) {
            applyPreferences(nowMs);
        }
        publish();
    }

    private void publish() {
        platform.publish(status());
    }

    private static boolean same(List<int[]> a, List<int[]> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (!java.util.Arrays.equals(a.get(i), b.get(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean sameClasses(List<Policy.Cls> a, List<Policy.Cls> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            Policy.Cls x = a.get(i);
            Policy.Cls y = b.get(i);
            if (x.start != y.start || x.end != y.end || x.kind != y.kind) {
                return false;
            }
        }
        return true;
    }
}
