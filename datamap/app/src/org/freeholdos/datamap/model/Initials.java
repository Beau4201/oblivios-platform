// SPDX-License-Identifier: Apache-2.0
package org.freeholdos.datamap.model;

/** The letters on a tile when an app or a company has no icon: the first letters of its first two words. */
public final class Initials {
    private Initials() {}

    public static String of(String name) {
        if (name == null) {
            return "?";
        }
        StringBuilder out = new StringBuilder(4);
        int letters = 0;   // counted in letters: a letter outside the basic plane is two chars
        boolean startOfWord = true;
        for (int i = 0; i < name.length() && letters < 2; ) {
            int cp = name.codePointAt(i);
            i += Character.charCount(cp);
            if (Character.isLetterOrDigit(cp)) {
                if (startOfWord) {
                    out.appendCodePoint(Character.toUpperCase(cp));
                    letters++;
                    startOfWord = false;
                }
            } else {
                startOfWord = true;
            }
        }
        return out.length() == 0 ? "?" : out.toString();
    }
}
