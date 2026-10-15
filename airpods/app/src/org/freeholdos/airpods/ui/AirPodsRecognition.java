// SPDX-License-Identifier: Apache-2.0
package org.freeholdos.airpods.ui;

import java.util.Collection;

/**
 * Decides which bonded device is treated as AirPods: it advertises Apple's accessory-protocol
 * service UUID in its cached SDP records. A bonded peer can claim this UUID, so the result is only
 * the candidate test; the session still publishes nothing until a valid frame arrives.
 */
public final class AirPodsRecognition {
    public static final String AAP_SERVICE_UUID = "74ec2172-0bad-4d01-8f77-997b2be0722a";

    private AirPodsRecognition() {}

    public static boolean matches(Collection<String> uuids) {
        if (uuids == null) return false;
        for (String u : uuids) {
            if (AAP_SERVICE_UUID.equalsIgnoreCase(u)) return true;
        }
        return false;
    }
}
