// SPDX-License-Identifier: Apache-2.0
package org.freeholdos.exit;

/**
 * The user's answer to "hide your address from advertising and analytics hosts" (ADR-181 section 5, ADR-186), one per
 * Android user. Nothing is masked and no Tor client starts for the default tier until it is {@link #ON}. A value that
 * cannot be read is {@link #UNSET}: the safe reading is "ask again", never "on".
 */
public enum Consent {
    UNSET, ON, OFF;

    public static Consent parse(String s) {
        if ("on".equals(s)) {
            return ON;
        }
        if ("off".equals(s)) {
            return OFF;
        }
        return UNSET;
    }

    public String serialize() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
