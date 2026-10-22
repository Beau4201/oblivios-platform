// SPDX-License-Identifier: Apache-2.0
package org.freeholdos.exit;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/** What the controller needs from the Android framework; one implementation on a device, a fake on the host. */
public interface PlatformApi {
    /** The ids of every Android user on the device. */
    int[] users();

    /** Every installed package of every user with its uid. */
    List<Policy.App> installedApps();

    /** Whether a VPN is the default network (F04). */
    boolean vpnActive();

    /** Whether the default network has strict Private DNS (the masked tier is limited there, MSK-008). */
    boolean privateDnsStrict();

    /** Registers the Tor network over the tun interface and returns its network id. */
    long registerTorNetwork(String iface) throws IOException;

    /** Removes the Tor network; safe when there is none. */
    void unregisterTorNetwork();

    /** Sets the network preference of the given packages (package to preference); an empty map removes every preference. */
    void setOemPreferences(Map<String, Integer> prefs) throws IOException;

    /** The ladder says an app has trouble with a company's hosts: tell the user once (the caller already deduplicates). */
    void notifyFailure(long uid, int company);

    /** The speed guard passes a company on to an app (ADR-188): tell the user once, with the action "Hide again". */
    void notifyPromotion(long uid, int company);

    /** The state for the screens and the status line. */
    void publish(Status status);
}
