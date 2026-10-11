// SPDX-License-Identifier: Apache-2.0
package com.android.bluetooth.btservice;

public final class AirPodsAliasPolicyCheck {
    private static void check(boolean ok, String what) { if (!ok) throw new AssertionError(what); }
    public static void main(String[] a) {
        check(AirPodsAliasPolicy.isAllowed("AirPods Test"), "ordinary name");
        check(AirPodsAliasPolicy.isAllowed("A"), "one byte");
        check(AirPodsAliasPolicy.isAllowed("x".repeat(32)), "32 bytes");
        check(!AirPodsAliasPolicy.isAllowed("x".repeat(33)), "33 bytes");
        check(AirPodsAliasPolicy.isAllowed("é".repeat(16)), "16 two-byte characters = 32 bytes");
        check(!AirPodsAliasPolicy.isAllowed("é".repeat(17)), "34 bytes");
        check(!AirPodsAliasPolicy.isAllowed(null), "null");
        check(!AirPodsAliasPolicy.isAllowed(""), "empty");
        check(!AirPodsAliasPolicy.isAllowed(" a"), "leading space");
        check(!AirPodsAliasPolicy.isAllowed("a "), "trailing space");
        check(!AirPodsAliasPolicy.isAllowed("a\tb"), "tab");
        check(!AirPodsAliasPolicy.isAllowed("a\nb"), "newline");
        check(!AirPodsAliasPolicy.isAllowed("a​b"), "zero width space (format)");
        check(!AirPodsAliasPolicy.isAllowed("a b"), "line separator");
        check(!AirPodsAliasPolicy.isAllowed("ab"), "private use");
        check(!AirPodsAliasPolicy.isAllowed("a\uD800b"), "lone surrogate");
        check(!AirPodsAliasPolicy.isAllowed("\u00A0"), "only a no-break space");
        check(!AirPodsAliasPolicy.isAllowed("a\u00A0"), "trailing no-break space");
        check(!AirPodsAliasPolicy.isAllowed("\u00A0a"), "leading no-break space");
        check(!AirPodsAliasPolicy.isAllowed("\u3164"), "Hangul filler");
        check(!AirPodsAliasPolicy.isAllowed("\u2800"), "Braille blank");
        check(!AirPodsAliasPolicy.isAllowed("a\u3164b"), "a filler inside");
        check(AirPodsAliasPolicy.isAllowed("a b"), "inner space");
        check(AirPodsAliasPolicy.isAllowed("日本語のイヤホン"), "non-latin visible text");
        System.out.println("AirPodsAliasPolicyCheck passed");
    }
}
