// SPDX-License-Identifier: Apache-2.0
package com.android.server.datamap;

public final class HostnameValidatorCheck {
    public static void main(String[] args) {
        Check.eq("example.com", HostnameValidator.normalize("Example.COM"), "lower case");
        Check.eq("example.com", HostnameValidator.normalize("example.com."), "one trailing dot goes");
        Check.eq("a_b-c.d9.net", HostnameValidator.normalize("a_b-c.d9.net"), "underscore, dash, digits");
        Check.eq("localhost", HostnameValidator.normalize("localhost"), "a single label is a name");

        Check.eq(null, HostnameValidator.normalize(null), "null");
        Check.eq(null, HostnameValidator.normalize(""), "empty");
        Check.eq(null, HostnameValidator.normalize("."), "only a dot");
        Check.eq(null, HostnameValidator.normalize("example.com.."), "two trailing dots leave an empty label");
        Check.eq(null, HostnameValidator.normalize(".example.com"), "leading dot");
        Check.eq(null, HostnameValidator.normalize("a..b.com"), "empty label");
        Check.eq(null, HostnameValidator.normalize("exa mple.com"), "space");
        Check.eq(null, HostnameValidator.normalize("a/b.com"), "slash");
        Check.eq(null, HostnameValidator.normalize("a@b.com"), "at sign");
        Check.eq(null, HostnameValidator.normalize("bücher.de"), "non-ASCII (it arrives as xn--)");
        Check.eq(null, HostnameValidator.normalize("a\tb.com"), "tab");
        Check.eq(null, HostnameValidator.normalize("a\nb.com"), "line break");
        Check.eq(null, HostnameValidator.normalize("<script>.com"), "markup");

        String l63 = "a".repeat(63);
        Check.eq(l63 + ".com", HostnameValidator.normalize(l63 + ".com"), "a label of 63 is allowed");
        Check.eq(null, HostnameValidator.normalize("a".repeat(64) + ".com"), "a label of 64 is not");

        StringBuilder sb = new StringBuilder();
        while (sb.length() < 250) {
            sb.append("abcdefghi.");
        }
        String n250 = sb.substring(0, 249);                       // 249 characters, ends without a dot
        Check.eq(n250, HostnameValidator.normalize(n250), "249 characters are allowed");
        String n253 = (n250 + ".abc").substring(0, 253);
        Check.eq(n253, HostnameValidator.normalize(n253), "253 characters are allowed");
        Check.eq(null, HostnameValidator.normalize(n253 + "d"), "254 characters are not");
        Check.passed("HostnameValidatorCheck");
    }
}
