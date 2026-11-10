// SPDX-License-Identifier: Apache-2.0
package org.oblivios.exit;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.IpPrefix;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkAgent;
import android.net.NetworkAgentConfig;
import android.net.NetworkCapabilities;
import android.net.NetworkProvider;
import android.net.NetworkScore;
import android.net.OemNetworkPreferences;
import android.net.RouteInfo;
import android.os.Looper;
import android.os.UserHandle;
import android.os.UserManager;
import android.telephony.TelephonyManager;

import com.android.internal.compartments.CompartmentsAppPolicy;
import com.android.internal.compartments.CompartmentsPlace;

import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * The Android side of {@link PlatformApi}: users and packages, the Tor network over the daemon's tun interface, and the
 * network preference of the apps on Tor. The network is registered the way the probe of NET-001b registered its own
 * (a Wi-Fi transport that carries {@code OEM_PRIVATE} and not {@code NOT_RESTRICTED}), with the addresses, routes and DNS
 * server that the route helper and the daemon fix as constants.
 */
final class AndroidPlatform implements PlatformApi {
    private static final String TAG = "Exit";
    private static final int PREFERENCE_TIMEOUT_SECONDS = 10;

    private final Context ctx;
    private final ConnectivityManager cm;
    private final Notifier notifier;
    private final StatusSink sink;
    private ProfileClient profiles;
    private final Map<Integer, String> placeCountry = new HashMap<>();
    private NetworkProvider provider;
    // The agent is set on the worker and read on the main looper (the callback), the flag the other way round.
    private volatile NetworkAgent agent;
    /**
     * The agent that the platform told its network is unwanted (it tears it down): the controller registers again when this is
     * the current agent. An identity and no flag: the late callback of an agent that was replaced names that old agent and so
     * cannot mark the new one, whatever the order of the reset and the assignment.
     */
    private volatile NetworkAgent goneAgent;

    interface StatusSink {
        void accept(Status s);
    }

    AndroidPlatform(Context ctx, Notifier notifier, StatusSink sink) {
        this.ctx = ctx;
        this.cm = ctx.getSystemService(ConnectivityManager.class);
        this.notifier = notifier;
        this.sink = sink;
    }

    private List<UserHandle> handles() {
        return ctx.getSystemService(UserManager.class).getUserHandles(true);
    }

    @Override
    public int[] users() {
        List<UserHandle> hs = handles();
        int[] ids = new int[hs.size()];
        for (int i = 0; i < ids.length; i++) {
            ids[i] = hs.get(i).getIdentifier();
        }
        return ids;
    }

    @Override
    public List<Policy.App> installedApps() {
        List<Policy.App> out = new ArrayList<>();
        for (UserHandle h : handles()) {
            PackageManager pm = ctx.createContextAsUser(h, 0).getPackageManager();
            for (ApplicationInfo ai : pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))) {
                out.add(new Policy.App(ai.packageName, ai.uid));
            }
        }
        return out;
    }

    @Override
    public Map<String, String> placeCountries() {
        Map<String, String> out = new HashMap<>();
        if (profiles == null || !profiles.available()) {
            profiles = ProfileClient.find();
        }
        String[] configured = profiles.getConfigured();
        if (configured == null) {
            return null; // unreadable now: the controller keeps what it sent, a failed read must not remove every country
        }
        for (String pkg : configured) {
            CompartmentsAppPolicy app = profiles.getApp(pkg);
            if (app == null) {
                return null;
            }
            if (app.location != ProfileRows.VALUE_SUBSTITUTE) {
                continue;
            }
            // the place the persona has now, also for an Automatic app (X5): the exit must follow the position it is given
            int id = profiles.getPersonaPlaceId(pkg);
            if (id < 0) {
                return null;
            }
            String country = id == 0 ? "" : countryOfPlace(id);
            if (ControlCodec.validCountry(country)) {
                out.put(pkg, country);
            }
        }
        return out;
    }

    /** The country of a place in capitals, "" when unknown. A place never changes, so it is asked once (X3). */
    private String countryOfPlace(int id) {
        String known = placeCountry.get(id);
        if (known != null) {
            return known;
        }
        CompartmentsPlace place = profiles.getPlace(id);
        if (place == null || place.country == null) {
            return "";
        }
        String country = place.country.toUpperCase(Locale.ROOT);
        placeCountry.put(id, country);
        return country;
    }

    @Override
    public String ownCountry() {
        return ownCountry(ctx);
    }

    /** The network country of the phone, else the SIM country, in capitals; empty when neither is known. */
    static String ownCountry(Context ctx) {
        TelephonyManager tm = ctx.getSystemService(TelephonyManager.class);
        String c = tm == null ? "" : tm.getNetworkCountryIso();
        if (c == null || c.isEmpty()) {
            c = tm == null ? "" : tm.getSimCountryIso();
        }
        return c == null ? "" : c.toUpperCase(Locale.ROOT);
    }

    @Override
    public boolean vpnActive() {
        Network n = cm.getActiveNetwork();
        NetworkCapabilities nc = n == null ? null : cm.getNetworkCapabilities(n);
        return nc != null && nc.hasTransport(NetworkCapabilities.TRANSPORT_VPN);
    }

    @Override
    public boolean privateDnsStrict() {
        Network n = cm.getActiveNetwork();
        LinkProperties lp = n == null ? null : cm.getLinkProperties(n);
        return lp != null && lp.isPrivateDnsActive() && lp.getPrivateDnsServerName() != null;
    }

    @Override
    public long registerTorNetwork(String iface) throws IOException {
        unregisterTorNetwork();
        // The agent and the provider run on the main looper: the worker blocks while the platform answers.
        provider = new NetworkProvider(ctx, Looper.getMainLooper(), TAG);
        cm.registerNetworkProvider(provider);

        // NetworkCapabilities.Builder starts from the default set (NOT_RESTRICTED, TRUSTED, NOT_VPN). The OEM_PRIVATE
        // request of the platform has no NOT_RESTRICTED, so the network must not claim it (F02, NET-001b).
        NetworkCapabilities nc = new NetworkCapabilities.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_OEM_PRIVATE)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build();
        LinkProperties lp = new LinkProperties();
        try {
            lp.setInterfaceName(iface);
            lp.addLinkAddress(new LinkAddress(InetAddress.getByAddress(TunNet.V4), TunNet.V4_PREFIX));
            lp.addLinkAddress(new LinkAddress(InetAddress.getByAddress(TunNet.V6), TunNet.V6_PREFIX));
            lp.addRoute(new RouteInfo(new IpPrefix(InetAddress.getByAddress(new byte[4]), 0), null, iface, RouteInfo.RTN_UNICAST));
            lp.addRoute(new RouteInfo(new IpPrefix(InetAddress.getByAddress(new byte[16]), 0), null, iface, RouteInfo.RTN_UNICAST));
            lp.addDnsServer(InetAddress.getByAddress(TunNet.DNS_V4));
        } catch (java.net.UnknownHostException e) {
            throw new IOException("bad constant address", e);
        }
        agent = new NetworkAgent(ctx, Looper.getMainLooper(), TAG, nc, lp, new NetworkScore.Builder().build(),
                new NetworkAgentConfig.Builder().build(), provider) {
            @Override
            public void onNetworkUnwanted() {
                goneAgent = this;
            }
        };
        agent.register();
        agent.markConnected();
        return agent.getNetwork().getNetId();
    }

    @Override
    public boolean torNetworkLive() {
        NetworkAgent current = agent;
        return current != null && current != goneAgent;
    }

    @Override
    public void unregisterTorNetwork() {
        if (agent != null) {
            try {
                agent.unregister();
            } catch (RuntimeException e) {
                // an agent that is already gone
            }
            agent = null;
        }
        if (provider != null) {
            try {
                cm.unregisterNetworkProvider(provider);
            } catch (RuntimeException e) {
                // a provider that is already gone
            }
            provider = null;
        }
    }

    @Override
    public void setOemPreferences(Map<String, Integer> prefs) throws IOException {
        OemNetworkPreferences.Builder b = new OemNetworkPreferences.Builder();
        for (Map.Entry<String, Integer> e : prefs.entrySet()) {
            b.addNetworkPreference(e.getKey(), e.getValue());
        }
        CountDownLatch done = new CountDownLatch(1);
        cm.setOemNetworkPreference(b.build(), Runnable::run, done::countDown);
        try {
            if (!done.await(PREFERENCE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new IOException("the preference was not applied in " + PREFERENCE_TIMEOUT_SECONDS + " s");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted while setting the preference");
        }
    }

    // A notification that cannot be shown must never be taken for a lost daemon: the controller treats an exception from
    // here like a broken link and drops the Tor network, so the error stops here (the class name only, no host).
    @Override
    public void notifyFailure(long uid, int company) {
        try {
            for (Policy.App a : installedApps()) {
                if (a.uid == uid) {
                    notifier.post(a.pkg, company);
                    return;
                }
            }
        } catch (RuntimeException e) {
            android.util.Log.w("ExitApp", "notification failed: " + e.getClass().getSimpleName());
        }
    }

    @Override
    public void notifyPromotion(long uid, int company) {
        try {
            for (Policy.App a : installedApps()) {
                if (a.uid == uid) {
                    notifier.postPromotion(a.pkg, uid, company);
                    return;
                }
            }
        } catch (RuntimeException e) {
            android.util.Log.w("ExitApp", "notification failed: " + e.getClass().getSimpleName());
        }
    }

    @Override
    public void publish(Status status) {
        sink.accept(status);
    }
}
