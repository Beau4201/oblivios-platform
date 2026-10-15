// SPDX-License-Identifier: Apache-2.0
package org.freeholdos.datamap.model;

/** Small text helpers that need no Android class. */
public final class Words {
    private Words() {}

    /** The parts joined by {@code sep}, leaving out null and empty ones, so no line starts or ends with a separator. */
    public static String join(String sep, String... parts) {
        StringBuilder b = new StringBuilder();
        for (String p : parts) {
            if (p == null || p.isEmpty()) {
                continue;
            }
            if (b.length() > 0) {
                b.append(sep);
            }
            b.append(p);
        }
        return b.toString();
    }
}
