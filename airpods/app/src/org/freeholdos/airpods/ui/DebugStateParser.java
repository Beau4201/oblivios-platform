// SPDX-License-Identifier: Apache-2.0
package org.freeholdos.airpods.ui;

import org.freeholdos.airpods.ui.AirPodsUiState.Feature;
import org.freeholdos.airpods.validation.ListeningModeState.ListeningMode;

/**
 * Parses the debug state string of a debuggable build, e.g.
 * {@code addr=AA:BB:CC:DD:EE:FF;l=80;r=82;c=100;lc=0;rc=0;cc=1;mode=ANC;ear=1;conv=0;feat=LISTENING_MODE,EAR_DETECTION}.
 * Null, empty or "off" means disconnected. Strict: unknown keys, bad numbers, unknown modes and
 * features are rejected. Error messages never contain the input, so no address is ever logged.
 */
public final class DebugStateParser {
    private static final int MAX_CHARS = 512;

    private DebugStateParser() {}

    public static AirPodsUiState parse(String raw) {
        if (raw == null || raw.isEmpty() || raw.equals("off")) return AirPodsUiState.DISCONNECTED;
        if (raw.length() > MAX_CHARS) throw new IllegalArgumentException("input too long");
        AirPodsUiState.Builder b = new AirPodsUiState.Builder();
        int l = -1;
        int r = -1;
        int c = -1;
        boolean lc = false;
        boolean rc = false;
        boolean cc = false;
        for (String pair : raw.split(";")) {
            int eq = pair.indexOf('=');
            if (eq <= 0 || eq == pair.length() - 1) throw new IllegalArgumentException("malformed pair");
            String k = pair.substring(0, eq);
            String v = pair.substring(eq + 1);
            switch (k) {
                case "addr": b.address(v); break;
                case "l": l = percent(v); break;
                case "r": r = percent(v); break;
                case "c": c = percent(v); break;
                case "lc": lc = flag(v); break;
                case "rc": rc = flag(v); break;
                case "cc": cc = flag(v); break;
                case "mode": b.listeningMode(mode(v)); break;
                case "ear": b.earDetection(flag(v)); break;
                case "conv": b.conversationAwareness(flag(v)); break;
                case "feat":
                    for (String f : v.split(",")) b.feature(feature(f));
                    break;
                default: throw new IllegalArgumentException("unknown key");
            }
        }
        return b.percents(l, r, c).charging(lc, rc, cc).build();
    }

    private static int percent(String v) {
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("bad number");
        }
    }

    private static boolean flag(String v) {
        if (v.equals("1")) return true;
        if (v.equals("0")) return false;
        throw new IllegalArgumentException("bad flag");
    }

    private static ListeningMode mode(String v) {
        if (v.equals("-")) return null;
        try {
            return ListeningMode.valueOf(v);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("unknown mode");
        }
    }

    private static Feature feature(String v) {
        try {
            return Feature.valueOf(v);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("unknown feature");
        }
    }
}
