// SPDX-License-Identifier: Apache-2.0
package org.sovereignos.airpods.debugharness;

/** Strict hex decoding for the validation harness: even length, ASCII hex digits only, bounded. */
final class HexBytes {
    /** Above the platform's 2048-byte metadata limit so AIR-036's oversize write is reachable. */
    static final int MAX_BYTES = 4096;

    private HexBytes() {}

    /** @return the bytes, or null if {@code hex} is null, odd-length, too long or not ASCII hex. */
    static byte[] parse(String hex) {
        if (hex == null || (hex.length() & 1) != 0 || hex.length() / 2 > MAX_BYTES) return null;
        byte[] out = new byte[hex.length() / 2];
        for (int i = 0; i < out.length; i++) {
            int hi = nibble(hex.charAt(2 * i));
            int lo = nibble(hex.charAt(2 * i + 1));
            if (hi < 0 || lo < 0) return null;
            out[i] = (byte) (hi << 4 | lo);
        }
        return out;
    }

    /** Not Character.digit: that accepts non-ASCII digits. */
    private static int nibble(char c) {
        if (c >= '0' && c <= '9') return c - '0';
        if (c >= 'a' && c <= 'f') return c - 'a' + 10;
        if (c >= 'A' && c <= 'F') return c - 'A' + 10;
        return -1;
    }
}
