// SPDX-License-Identifier: Apache-2.0
package org.oblivios.exit;

/**
 * The addresses of the tunnel, as constants of the rule language of the route helper ({@code routes/spec.rs}): the helper
 * puts these addresses on the tun interface, and the network that this app registers must carry the same, or the routes
 * of the Tor network lead nowhere. {@code tests/vectors.txt} holds the values; the Rust tests and the host checks of this
 * app compare them with it. Pure Java.
 */
public final class TunNet {
    private TunNet() {}

    /** 169.254.42.127/24. */
    public static final byte[] V4 = {(byte) 169, (byte) 254, 42, 127};
    public static final int V4_PREFIX = 24;
    /** fd66:7265:6568:1::2/64. */
    public static final byte[] V6 = {(byte) 0xfd, 0x66, 0x72, 0x65, 0x65, 0x68, 0, 1, 0, 0, 0, 0, 0, 0, 0, 2};
    public static final int V6_PREFIX = 64;
    /** The DNS listener of the daemon, 169.254.42.53, in the same /24. */
    public static final byte[] DNS_V4 = {(byte) 169, (byte) 254, 42, 53};
}
