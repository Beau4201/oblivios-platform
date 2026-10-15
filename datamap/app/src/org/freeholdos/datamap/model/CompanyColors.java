// SPDX-License-Identifier: Apache-2.0
package org.freeholdos.datamap.model;

/**
 * One colour per company, for its ring point and its lines (ADR-160, A1): the eight colours of the design page,
 * dark and light. The companies of the page's sample are pinned to the colour the page gives them; every other
 * company gets one of the eight by a fixed hash of its id, so the same company always has the same colour and
 * repeats are allowed.
 */
public final class CompanyColors {
    /** {dark, light} as 0xAARRGGBB. */
    private static final int[][] PALETTE = {
        {0xFF22E58B, 0xFF12A85F},   // 0 green
        {0xFF2F7BFF, 0xFF1F5FD1},   // 1 blue
        {0xFFB9C6F5, 0xFF7C8DB5},   // 2 pale blue
        {0xFFFFB224, 0xFFD98A00},   // 3 amber
        {0xFF9B5CFF, 0xFF7A3FE0},   // 4 purple
        {0xFF19D3E8, 0xFF0AA3B8},   // 5 cyan
        {0xFFFF6FB5, 0xFFD6458F},   // 6 pink
        {0xFF7DE2FF, 0xFF2490B8},   // 7 light cyan
    };

    private CompanyColors() {}

    private static int index(String ownerId) {
        switch (ownerId) {
            case "google":
                return 0;
            case "meta":
                return 1;
            case "apple":
                return 2;
            case "amazon":
                return 3;
            case "microsoft":
                return 4;
            case "appsflyer":
                return 6;
            default:
                int h = 0x811C9DC5;   // FNV-1a over the UTF-16 units
                for (int i = 0; i < ownerId.length(); i++) {
                    h = (h ^ ownerId.charAt(i)) * 0x01000193;
                }
                return (h & 0x7FFFFFFF) % PALETTE.length;
        }
    }

    public static int of(String ownerId, boolean light) {
        return PALETTE[index(ownerId == null ? "" : ownerId)][light ? 1 : 0];
    }
}
