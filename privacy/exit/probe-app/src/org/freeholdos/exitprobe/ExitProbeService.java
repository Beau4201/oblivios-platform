// SPDX-License-Identifier: Apache-2.0
package org.freeholdos.exitprobe;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.net.ConnectivityManager;
import android.net.IpPrefix;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.NetworkAgent;
import android.net.NetworkAgentConfig;
import android.net.NetworkCapabilities;
import android.net.NetworkProvider;
import android.net.NetworkScore;
import android.net.OemNetworkPreferences;
import android.net.RouteInfo;
import android.net.TestNetworkInterface;
import android.net.TestNetworkManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.system.Os;
import android.system.OsConstants;
import android.system.StructPollfd;
import android.util.Log;

import java.io.FileDescriptor;
import java.net.InetAddress;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Throwaway probe for NET-001b (ADR-184): registers a network with NET_CAPABILITY_OEM_PRIVATE and selects it for
 * one package with the production preference OEM_NETWORK_PREFERENCE_OEM_PRIVATE_ONLY, so that a phone answers what
 * the network side of the exit service must do before anything permanent is built. Userdebug images only.
 *
 * Every result line starts with "RESULT " under the log tag "ExitProbe". Every path ends in {@link #cleanup}: the
 * platform keeps the preference when this process dies, so a timer and an alarm both call the cleanup.
 */
public final class ExitProbeService extends Service {
    static final String TAG = "ExitProbe";
    static final String ACTION_RUN = "org.freeholdos.exitprobe.RUN";
    static final String ACTION_CLEAR = "org.freeholdos.exitprobe.CLEAR";

    private static final String CHANNEL = "exitprobe";
    private static final int NOTIFICATION_ID = 1;
    private static final String TEST_NETWORK_SERVICE = "test_network";
    private static final int ALARM_GRACE_SECONDS = 10;
    private static final int PREFERENCE_TIMEOUT_SECONDS = 10;
    private static final byte[] TUN_ADDRESS = {10, (byte) 255, 0, 2};
    private static final int TUN_PREFIX = 24;

    private HandlerThread worker;
    private Handler handler;
    private ConnectivityManager cm;
    // Everything below is touched on the worker thread only, except stopReading.
    private TestNetworkInterface tun;
    private NetworkProvider provider;
    private NetworkAgent agent;
    private Thread reader;
    private volatile boolean stopReading;
    private boolean busy;
    private final AtomicInteger packets = new AtomicInteger();

    @Override
    public void onCreate() {
        super.onCreate();
        cm = getSystemService(ConnectivityManager.class);
        worker = new HandlerThread("exit-probe");
        worker.start();
        handler = new Handler(worker.getLooper());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // A start-foreground-service call must reach startForeground within seconds, whatever the action is.
        startForeground();
        String action = intent == null ? null : intent.getAction();
        if (ACTION_RUN.equals(action)) {
            Map<String, String> extras = extrasOf(intent);
            handler.post(() -> run(extras));
        } else if (ACTION_CLEAR.equals(action)) {
            handler.post(() -> cleanup(true));
        } else {
            handler.post(() -> {
                result("ignored action=" + action);
                stopWhenIdle();
            });
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        handler.post(() -> cleanup(false));
        worker.quitSafely();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void startForeground() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Exit probe",
                NotificationManager.IMPORTANCE_LOW));
        Notification n = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Exit probe running")
                .build();
        startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
    }

    @SuppressWarnings("deprecation")
    private static Map<String, String> extrasOf(Intent intent) {
        Map<String, String> m = new HashMap<>();
        Bundle b = intent.getExtras();
        if (b != null) {
            for (String k : b.keySet()) {
                Object v = b.get(k);
                if (v != null) {
                    m.put(k, String.valueOf(v));
                }
            }
        }
        return m;
    }

    private static void result(String line) {
        Log.i(TAG, "RESULT " + line);
    }

    private void run(Map<String, String> extras) {
        ProbeArgs a;
        try {
            a = ProbeArgs.parse(extras);
        } catch (IllegalArgumentException e) {
            result("bad-args " + e.getMessage());
            stopWhenIdle();
            return;
        }
        if (busy) {
            result("busy: a run is active; send CLEAR first");
            return;
        }
        busy = true;
        // The platform keeps the preference when this process dies: arm the backstop before touching anything.
        armAlarm(a.seconds + ALARM_GRACE_SECONDS);
        try {
            if (!a.prefOnly) {
                registerNetwork(a);
            }
            setPreference(a.pkg);
            result("pref=set package=" + a.pkg);
            handler.postDelayed(() -> cleanup(true), a.seconds * 1000L);
        } catch (Throwable t) {
            result("failed " + t);
            cleanup(true);
        }
    }

    /** A rejected start must not leave the foreground notification up; a run in progress keeps the service. */
    private void stopWhenIdle() {
        if (!busy) {
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
        }
    }

    private void registerNetwork(ProbeArgs a) throws Exception {
        TestNetworkManager tnm = (TestNetworkManager) getSystemService(TEST_NETWORK_SERVICE);
        LinkAddress addr = new LinkAddress(InetAddress.getByAddress(TUN_ADDRESS), TUN_PREFIX);
        // bare=true: the tun carries no address, so the registered LinkProperties are the only source of it.
        tun = tnm.createTunInterface(a.bareTun ? Collections.<LinkAddress>emptyList()
                : Collections.singletonList(addr));
        String iface = tun.getInterfaceName();
        result("tun=" + iface + " bare=" + a.bareTun);

        // The agent and the provider run on the main looper: the worker blocks while it waits for the platform.
        provider = new NetworkProvider(this, Looper.getMainLooper(), TAG);
        result("provider id=" + cm.registerNetworkProvider(provider));

        // NetworkCapabilities.Builder starts from the default set (NOT_RESTRICTED, TRUSTED, NOT_VPN). The
        // OEM_PRIVATE request of the platform has no NOT_RESTRICTED, so the network must not claim it (F02).
        NetworkCapabilities.Builder ncb = new NetworkCapabilities.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_OEM_PRIVATE)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
                .addTransportType(a.transport == ProbeArgs.Transport.WIFI
                        ? NetworkCapabilities.TRANSPORT_WIFI : NetworkCapabilities.TRANSPORT_TEST);
        LinkProperties lp = new LinkProperties();
        lp.setInterfaceName(iface);
        lp.addLinkAddress(addr);
        lp.addRoute(new RouteInfo(new IpPrefix(InetAddress.getByAddress(new byte[4]), 0), null, iface,
                RouteInfo.RTN_UNICAST));

        agent = new NetworkAgent(this, Looper.getMainLooper(), TAG, ncb.build(), lp,
                new NetworkScore.Builder().build(), new NetworkAgentConfig.Builder().build(), provider) {
            @Override
            public void onNetworkUnwanted() {
                result("agent unwanted");
            }
        };
        agent.register();
        agent.markConnected();
        result("agent registered network=" + agent.getNetwork() + " transport=" + a.transport);

        final FileDescriptor fd = tun.getFileDescriptor().getFileDescriptor();
        packets.set(0);
        stopReading = false;
        reader = new Thread(() -> readPackets(fd), "exit-probe-reader");
        reader.setDaemon(true);
        reader.start();
    }

    // The tun fd of a test interface is non-blocking, so a plain read fails at once: poll, then read.
    private void readPackets(FileDescriptor fd) {
        byte[] buf = new byte[2048];
        StructPollfd[] fds = {new StructPollfd()};
        fds[0].fd = fd;
        fds[0].events = (short) OsConstants.POLLIN;
        try {
            while (!stopReading) {
                if (Os.poll(fds, 200) <= 0 || (fds[0].revents & OsConstants.POLLIN) == 0) {
                    continue;
                }
                int n = Os.read(fd, buf, 0, buf.length);
                if (n <= 0) {
                    continue;
                }
                packets.incrementAndGet();
                result("packet " + PacketSummary.describe(buf, n));
            }
        } catch (Throwable t) {
            if (!stopReading) {
                result("reader-error " + t);
            }
        }
    }

    private void setPreference(String pkg) throws Exception {
        apply(new OemNetworkPreferences.Builder()
                .addNetworkPreference(pkg, OemNetworkPreferences.OEM_NETWORK_PREFERENCE_OEM_PRIVATE_ONLY)
                .build());
    }

    private void apply(OemNetworkPreferences p) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        cm.setOemNetworkPreference(p, Runnable::run, done::countDown);
        if (!done.await(PREFERENCE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            throw new IllegalStateException("setOemNetworkPreference did not complete in "
                    + PREFERENCE_TIMEOUT_SECONDS + " s");
        }
    }

    private PendingIntent clearIntent() {
        return PendingIntent.getForegroundService(this, 0,
                new Intent(this, ExitProbeService.class).setAction(ACTION_CLEAR),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private void armAlarm(int seconds) {
        getSystemService(AlarmManager.class).setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + seconds * 1000L, clearIntent());
    }

    /**
     * Idempotent. The preference goes first, so that no app is left selected for a network that is going away;
     * each later step runs even when an earlier one failed.
     */
    private void cleanup(boolean stopService) {
        handler.removeCallbacksAndMessages(null);
        getSystemService(AlarmManager.class).cancel(clearIntent());
        try {
            apply(new OemNetworkPreferences.Builder().build());
            result("pref=empty");
        } catch (Throwable t) {
            result("pref-clear-failed " + t);
        }
        stopReading = true;
        try {
            if (reader != null) {
                reader.join(2000);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        reader = null;
        if (agent != null) {
            try {
                agent.unregister();
            } catch (Throwable t) {
                result("agent-unregister-failed " + t);
            }
            agent = null;
        }
        if (provider != null) {
            try {
                cm.unregisterNetworkProvider(provider);
            } catch (Throwable t) {
                result("provider-unregister-failed " + t);
            }
            provider = null;
        }
        if (tun != null) {
            try {
                tun.getFileDescriptor().close();
            } catch (Throwable t) {
                result("tun-close-failed " + t);
            }
            tun = null;
        }
        result("packets=" + packets.get());
        result("cleaned");
        busy = false;
        if (stopService) {
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();
        }
    }
}
