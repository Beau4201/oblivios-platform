// SPDX-License-Identifier: Apache-2.0
package org.sovereignos.airpods.debugharness;

/** Host-runnable check: javac + java -ea, no framework. Exits non-zero on failure. */
public final class HexBytesCheck {
    private static void eq(byte[] actual, int... expected) {
        if (actual == null) throw new AssertionError("got null, expected " + expected.length + " bytes");
        if (actual.length != expected.length) {
            throw new AssertionError("length " + actual.length + " != " + expected.length);
        }
        for (int i = 0; i < expected.length; i++) {
            if ((actual[i] & 0xff) != expected[i]) {
                throw new AssertionError("byte " + i + ": " + (actual[i] & 0xff) + " != " + expected[i]);
            }
        }
    }

    private static void rejected(String hex) {
        if (HexBytes.parse(hex) != null) {
            throw new AssertionError("accepted: "
                    + (hex.length() > 40 ? hex.substring(0, 40) + "... (" + hex.length() + " chars)" : hex));
        }
    }

    public static void main(String[] args) {
        eq(HexBytes.parse(""));
        eq(HexBytes.parse("00ff7F"), 0x00, 0xff, 0x7f);
        eq(HexBytes.parse("AbCd"), 0xab, 0xcd);

        rejected(null);
        rejected("abc");          // odd length
        rejected("zz");           // not hex
        rejected("0x");           // not hex
        rejected("1 ");           // space
        rejected("１１"); // fullwidth digits: Character.digit would accept these
        rejected("٣٣"); // Arabic-Indic digits: same

        StringBuilder atLimit = new StringBuilder();
        for (int i = 0; i < HexBytes.MAX_BYTES; i++) atLimit.append("00");
        if (HexBytes.parse(atLimit.toString()) == null) throw new AssertionError("limit rejected");
        rejected(atLimit + "00"); // one byte over

        System.out.println("HexBytesCheck OK");
    }
}
