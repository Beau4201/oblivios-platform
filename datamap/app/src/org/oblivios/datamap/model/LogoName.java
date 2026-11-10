// SPDX-License-Identifier: Apache-2.0
package org.oblivios.datamap.model;

/**
 * The resource name of a company's logo: {@code logo_} and the owner identifier in lower case, with every
 * character that is not a letter or digit turned into an underscore. tools/import-logos.py makes the same name; both
 * read tests/fixtures/logo-names.tsv.
 */
public final class LogoName {
    private LogoName() {}

    public static String of(String ownerId) {
        StringBuilder sb = new StringBuilder("logo_");
        for (int i = 0; i < ownerId.length(); i++) {
            char c = ownerId.charAt(i);
            if (c >= 'A' && c <= 'Z') {
                c = (char) (c + 32);
            }
            sb.append((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') ? c : '_');
        }
        return sb.toString();
    }
}
