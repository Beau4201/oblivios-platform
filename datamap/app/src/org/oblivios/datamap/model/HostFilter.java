// SPDX-License-Identifier: Apache-2.0
package org.oblivios.datamap.model;

/**
 * Names and addresses that only exist on the phone or on its own network. A contact with one of them is not
 * a contact with a company, so the map never lists it. Pure Java, checked on the host.
 */
public final class HostFilter {
    private HostFilter() {}

    /**
     * @param serviceKey a service key of the collector: a lower-case name, or {@code ip:} and an address
     */
    public static boolean isLocal(String serviceKey) {
        if (serviceKey == null || serviceKey.isEmpty()) {
            return false;
        }
        if (serviceKey.startsWith("ip:")) {
            return isLocalAddress(serviceKey.substring(3));
        }
        if (serviceKey.indexOf('.') < 0) {
            return true;   // one label: "localhost", "router", a name that no public domain can have
        }
        return serviceKey.endsWith(".localhost") || serviceKey.endsWith(".local")
                || serviceKey.endsWith(".lan") || serviceKey.endsWith(".home.arpa")
                || serviceKey.endsWith(".internal");
    }

    private static boolean isLocalAddress(String a) {
        if (a.isEmpty()) {
            return false;
        }
        if (a.indexOf(':') >= 0) {
            return a.equals("::1") || a.equals("::") || a.startsWith("fe8") || a.startsWith("fe9")
                    || a.startsWith("fea") || a.startsWith("feb") || a.startsWith("fc")
                    || a.startsWith("fd");
        }
        String[] p = a.split("\\.", -1);
        if (p.length != 4) {
            return false;
        }
        int[] n = new int[4];
        for (int i = 0; i < 4; i++) {
            if (p[i].isEmpty() || p[i].length() > 3) {
                return false;
            }
            for (int j = 0; j < p[i].length(); j++) {
                if (p[i].charAt(j) < '0' || p[i].charAt(j) > '9') {
                    return false;
                }
            }
            n[i] = Integer.parseInt(p[i]);
            if (n[i] > 255) {
                return false;
            }
        }
        return n[0] == 0 || n[0] == 10 || n[0] == 127
                || (n[0] == 100 && n[1] >= 64 && n[1] <= 127)    // carrier-grade NAT, RFC 6598
                || (n[0] == 169 && n[1] == 254)
                || (n[0] == 172 && n[1] >= 16 && n[1] <= 31)
                || (n[0] == 192 && n[1] == 168);
    }
}
