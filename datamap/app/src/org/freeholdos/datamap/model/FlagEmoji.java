// SPDX-License-Identifier: Apache-2.0
package org.freeholdos.datamap.model;

/**
 * The flag of a country as the emoji of the system font: two regional indicator symbols, one per letter of the
 * ISO 3166-1 code. No flag artwork is shipped, so there is nothing to license and no code can lack its flag.
 */
public final class FlagEmoji {
    private static final int REGIONAL_INDICATOR_A = 0x1F1E6;

    private FlagEmoji() {}

    /** @return the flag, or null when the text is not two letters A to Z. */
    public static String of(String countryCode) {
        if (countryCode == null || countryCode.length() != 2) {
            return null;
        }
        StringBuilder sb = new StringBuilder(4);
        for (int i = 0; i < 2; i++) {
            char c = Character.toUpperCase(countryCode.charAt(i));
            if (c < 'A' || c > 'Z') {
                return null;
            }
            sb.appendCodePoint(REGIONAL_INDICATOR_A + (c - 'A'));
        }
        return sb.toString();
    }
}
