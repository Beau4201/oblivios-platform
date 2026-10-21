// SPDX-License-Identifier: Apache-2.0
package org.freeholdos.exitprobe;

/**
 * One log line for a packet read from the tun of the probe. Pure Java, never throws: a buffer it cannot read as
 * IPv4 or IPv6 is reported as unparsed. The line holds the destination the selected app opened: acceptable for a throwaway
 * userdebug probe, and the reason it must never reach a user build.
 */
public final class PacketSummary {
    private static final int IPV4_MIN = 20;
    private static final int IPV6_HEADER = 40;
    private static final int TCP = 6;
    private static final int UDP = 17;

    private PacketSummary() {}

    public static String describe(byte[] buf, int len) {
        if (buf == null || len < 1 || len > buf.length) {
            return "unparsed len=" + len;
        }
        int version = (buf[0] & 0xff) >> 4;
        if (version == 4 && len >= IPV4_MIN) {
            int ihl = (buf[0] & 0x0f) * 4;
            if (ihl >= IPV4_MIN && ihl <= len) {
                int proto = buf[9] & 0xff;
                return "ipv4 src=" + addr(buf, 12) + " dst=" + addr(buf, 16) + " proto=" + proto
                        + port(buf, len, proto, ihl);
            }
        } else if (version == 6 && len >= IPV6_HEADER) {
            int proto = buf[6] & 0xff;
            return "ipv6 proto=" + proto + port(buf, len, proto, IPV6_HEADER);
        }
        return "unparsed len=" + len;
    }

    /** " dport=<n>" for TCP and UDP when the transport header holds the port, else the empty string. */
    private static String port(byte[] b, int len, int proto, int off) {
        if ((proto != TCP && proto != UDP) || off + 4 > len) {
            return "";
        }
        return " dport=" + (((b[off + 2] & 0xff) << 8) | (b[off + 3] & 0xff));
    }

    private static String addr(byte[] b, int off) {
        return (b[off] & 0xff) + "." + (b[off + 1] & 0xff) + "." + (b[off + 2] & 0xff) + "." + (b[off + 3] & 0xff);
    }
}
