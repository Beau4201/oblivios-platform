// SPDX-License-Identifier: Apache-2.0
package org.oblivios.exitprobe;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * The validated arguments of one probe run (NET-001b, ADR-184). Pure Java: no Android type, so that the host
 * checks run without the SDK. The extras come from an intent that any caller with the service's permission can
 * send, so every value is checked here and nothing is passed on unchecked.
 */
public final class ProbeArgs {
    public enum Transport { TEST, WIFI }

    private static final Pattern PACKAGE = Pattern.compile("^[A-Za-z0-9_.]+$");
    private static final Pattern NUMBER = Pattern.compile("^[0-9]{1,3}$");
    private static final int MAX_SECONDS = 300;
    private static final int DEFAULT_SECONDS = 20;

    public final String pkg;
    public final int seconds;
    public final Transport transport;
    public final boolean bareTun;
    public final boolean prefOnly;

    private ProbeArgs(String pkg, int seconds, Transport transport, boolean bareTun, boolean prefOnly) {
        this.pkg = pkg;
        this.seconds = seconds;
        this.transport = transport;
        this.bareTun = bareTun;
        this.prefOnly = prefOnly;
    }

    /** @throws IllegalArgumentException for a missing or malformed value */
    public static ProbeArgs parse(Map<String, String> extras) {
        String pkg = extras.get("package");
        if (pkg == null || !PACKAGE.matcher(pkg).matches()) {
            throw new IllegalArgumentException("package missing or malformed");
        }
        int seconds = DEFAULT_SECONDS;
        String s = extras.get("seconds");
        if (s != null) {
            if (!NUMBER.matcher(s).matches()) {
                throw new IllegalArgumentException("seconds must be a number");
            }
            seconds = Integer.parseInt(s);
            if (seconds < 1 || seconds > MAX_SECONDS) {
                throw new IllegalArgumentException("seconds must be 1 to " + MAX_SECONDS);
            }
        }
        Transport transport = Transport.TEST;
        String t = extras.get("transport");
        if (t != null) {
            switch (t) {
                case "test": transport = Transport.TEST; break;
                case "wifi": transport = Transport.WIFI; break;
                default: throw new IllegalArgumentException("transport must be test or wifi");
            }
        }
        return new ProbeArgs(pkg, seconds, transport, flag(extras, "bare"), flag(extras, "pref_only"));
    }

    private static boolean flag(Map<String, String> extras, String key) {
        String v = extras.get(key);
        if (v == null || v.equals("false")) {
            return false;
        }
        if (v.equals("true")) {
            return true;
        }
        throw new IllegalArgumentException(key + " must be true or false");
    }
}
