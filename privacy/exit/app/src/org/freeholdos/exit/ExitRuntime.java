// SPDX-License-Identifier: Apache-2.0
package org.freeholdos.exit;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.os.SystemClock;
import android.os.UserManager;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Everything of the exit app that lives for the life of the process: one worker thread that owns the store, the controller
 * and the platform. The settings screens ask it for the state and post their changes to it. It runs only in the system user
 * (the owner): the daemon has one peer, and a second user's copy of this app would take the connection from it.
 */
final class ExitRuntime {
    private static final String TAG = "Exit";
    private static final long TICK_MS = 1000;
    /** The list that the daemon masks by, which also names the companies. */
    private static final String MASK_LIST = "/system/etc/freeholdos/exit-mask-list.tsv";
    private static final int MAX_LIST_BYTES = 4 * 1024 * 1024;

    private static ExitRuntime instance;

    /** What the screens read: one immutable view, replaced after each change. */
    static final class Snapshot {
        final Choices choices;
        final Consent consent;
        final Status status;

        Snapshot(Choices choices, Consent consent, Status status) {
            this.choices = choices;
            this.consent = consent;
            this.status = status;
        }
    }

    interface Listener {
        void onChanged(Snapshot s);
    }

    static synchronized ExitRuntime get(Context ctx) {
        if (instance == null) {
            instance = new ExitRuntime(ctx.getApplicationContext());
        }
        return instance;
    }

    private final Context ctx;
    private final HandlerThread worker = new HandlerThread("exit-worker");
    private Handler handler;
    private AppStore store;
    private ExitController controller;
    private Notifier notifier;
    private volatile CompanyNames names = CompanyNames.parse(null);
    private volatile Status status = new Status(Status.Phase.IDLE, -1, java.util.Collections.<Policy.Warning>emptySet(), null);
    private volatile Snapshot snapshot;
    private volatile Listener listener;
    private boolean started;

    private ExitRuntime(Context ctx) {
        this.ctx = ctx;
    }

    /** Starts the worker; only the system user's process does anything. Idempotent. */
    synchronized void start() {
        if (started || !ctx.getSystemService(UserManager.class).isSystemUser()) {
            return;
        }
        started = true;
        worker.start();
        handler = new Handler(worker.getLooper());
        handler.post(this::init);
    }

    private void init() {
        store = new AppStore(ctx);
        names = CompanyNames.parse(readList());
        notifier = new Notifier(ctx, names);
        AndroidPlatform platform = new AndroidPlatform(ctx, notifier, this::setStatus);
        controller = new ExitController(
                new DaemonClient(new SocketConnector("exitd")), new RouteClient(new SocketConnector("exitd_route")),
                platform, store, new Ladder());
        publishSnapshot();
        controller.start(SystemClock.elapsedRealtime());
        watchTheDevice();
        askForConsent();
        handler.postDelayed(this::tick, TICK_MS);
    }

    private String readList() {
        File f = new File(MASK_LIST);
        try {
            if (f.length() > MAX_LIST_BYTES) {
                return null;
            }
            return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    private void tick() {
        controller.tick(SystemClock.elapsedRealtime());
        handler.postDelayed(this::tick, TICK_MS);
    }

    /**
     * The first start after an update asks once, with a notification at the first unlock, because no Tor client runs and
     * nothing is hidden until the user confirms (ADR-181 section 5, ADR-186). A notification posted while the user is
     * still locked is posted again at the unlock.
     */
    private void askForConsent() {
        ctx.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (store.consent() == Consent.UNSET) {
                    notifier.postConsent();
                }
            }
        }, new IntentFilter(Intent.ACTION_USER_UNLOCKED), null, handler, Context.RECEIVER_NOT_EXPORTED);
        if (store.consent() == Consent.UNSET && ctx.getSystemService(UserManager.class).isUserUnlocked()) {
            notifier.postConsent();
        }
    }

    CompanyNames companyNames() {
        return names;
    }

    /** Packages, users and the default network change what the policy is: recompute on each. */
    private void watchTheDevice() {
        BroadcastReceiver changed = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                deviceChanged();
            }
        };
        IntentFilter pkgs = new IntentFilter();
        pkgs.addAction(Intent.ACTION_PACKAGE_ADDED);
        pkgs.addAction(Intent.ACTION_PACKAGE_REMOVED);
        pkgs.addAction(Intent.ACTION_PACKAGE_REPLACED);
        pkgs.addDataScheme("package");
        // For every user: a plain registration sees the packages of user 0 only, and an app set to Tor that is installed in a
        // second or a work profile would stay masked, not Tor, until some other event came (review of 2026-10-22, I2).
        ctx.registerReceiverForAllUsers(changed, pkgs, null, handler, Context.RECEIVER_NOT_EXPORTED);
        IntentFilter users = new IntentFilter();
        users.addAction(Intent.ACTION_USER_ADDED);
        users.addAction(Intent.ACTION_USER_REMOVED);
        // The same receiver as above: the activity manager keeps one user scope for a receiver, so a plain registration here
        // (user 0) after the all-users one (user -1) throws "previously registered for user -1" and the app crashed at every
        // start (IMG-014). These two actions reach every user anyway.
        ctx.registerReceiverForAllUsers(changed, users, null, handler, Context.RECEIVER_NOT_EXPORTED);
        ctx.getSystemService(ConnectivityManager.class).registerDefaultNetworkCallback(
                new ConnectivityManager.NetworkCallback() {
                    @Override
                    public void onCapabilitiesChanged(Network n, NetworkCapabilities nc) {
                        deviceChanged();
                    }

                    @Override
                    public void onLinkPropertiesChanged(Network n, LinkProperties lp) {
                        deviceChanged();
                    }
                }, handler);
    }

    /** Delay that folds a burst of device events (a network reports its properties many times) into one recompute. */
    private static final long DEVICE_EVENT_DELAY_MS = 500;
    private final Runnable recompute = () -> {
        controller.changed(SystemClock.elapsedRealtime());
        publishSnapshot();
    };

    /** A package, a user or the default network changed: recompute once the burst is over. Called on any thread. */
    private void deviceChanged() {
        Handler h = handler;
        if (h != null) {
            h.removeCallbacks(recompute);
            h.postDelayed(recompute, DEVICE_EVENT_DELAY_MS);
        }
    }

    /** Runs {@code change} on the worker, then lets the controller see the new state. */
    void post(Runnable change) {
        if (handler == null) {
            return;
        }
        handler.post(() -> {
            change.run();
            controller.changed(SystemClock.elapsedRealtime());
            publishSnapshot();
        });
    }

    void setChoice(String pkg, Choices.Choice choice) {
        post(() -> {
            if (store.choices().set(pkg, choice)) {
                save();
            }
        });
    }

    void setShowsAddress(String pkg, int company, boolean show) {
        post(() -> {
            if (store.choices().setShowsAddress(pkg, company, show)) {
                save();
            }
        });
    }

    /** "Hide again": the user wants a company hidden from an app again (the speed guard had passed it on). */
    void release(long uid, int company) {
        Handler h = handler;
        if (h == null) {
            return;
        }
        h.post(() -> {
            controller.release(uid, company, SystemClock.elapsedRealtime());
            publishSnapshot();
        });
    }

    void setConsent(Consent c) {
        post(() -> {
            try {
                store.setConsent(c);
            } catch (IOException e) {
                Log.w(TAG, "consent not saved: " + e.getClass().getSimpleName());
            }
        });
    }

    private void save() {
        try {
            store.saveChoices();
        } catch (IOException e) {
            Log.w(TAG, "choices not saved: " + e.getClass().getSimpleName());
        }
    }

    private void setStatus(Status s) {
        status = s;
        publishSnapshot();
    }

    private void publishSnapshot() {
        // A copy: the screens read it on their own thread while the worker goes on changing the live object.
        Snapshot s = new Snapshot(Choices.parse(store.choices().serialize()), store.consent(), status);
        snapshot = s;
        Listener l = listener;
        if (l != null) {
            l.onChanged(s);
        }
    }

    /** The latest state, or null before the worker has started (or in a user that does not run the service). */
    Snapshot snapshot() {
        return snapshot;
    }

    void setListener(Listener l) {
        listener = l;
    }

    boolean runsHere() {
        return started && Process.myUserHandle().isSystem();
    }
}
