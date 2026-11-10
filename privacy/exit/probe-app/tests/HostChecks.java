// SPDX-License-Identifier: Apache-2.0
import java.util.HashMap;
import java.util.Map;
import org.oblivios.exitprobe.PacketSummary;
import org.oblivios.exitprobe.ProbeArgs;

/** Host checks of the pure Java of the probe (no Android class). Exits 1 on the first failure. */
public final class HostChecks {
    private static void check(boolean ok, String what) {
        if (!ok) {
            System.out.println("FAIL: " + what);
            System.exit(1);
        }
    }

    private static Map<String, String> m(String... kv) {
        Map<String, String> r = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            r.put(kv[i], kv[i + 1]);
        }
        return r;
    }

    private static void rejects(String what, Map<String, String> extras) {
        try {
            ProbeArgs.parse(extras);
        } catch (IllegalArgumentException e) {
            return;
        }
        check(false, "parse must reject " + what);
    }

    private static byte[] ipv4(int proto, int dport, int total) {
        byte[] b = new byte[total];
        b[0] = 0x45;
        b[9] = (byte) proto;
        b[12] = 10; b[13] = (byte) 255; b[14] = 0; b[15] = 2;
        b[16] = 10; b[17] = (byte) 255; b[18] = 0; b[19] = 9;
        b[22] = (byte) (dport >> 8);
        b[23] = (byte) dport;
        return b;
    }

    private static byte[] ipv6(int next, int dport, int total) {
        byte[] b = new byte[total];
        b[0] = 0x60;
        b[6] = (byte) next;
        b[42] = (byte) (dport >> 8);
        b[43] = (byte) dport;
        return b;
    }

    public static void main(String[] args) {
        ProbeArgs a = ProbeArgs.parse(m("package", "org.lane.probea", "seconds", "20"));
        check(a.pkg.equals("org.lane.probea"), "pkg");
        check(a.seconds == 20, "seconds");
        check(a.transport == ProbeArgs.Transport.TEST, "default transport is TEST");
        check(!a.bareTun && !a.prefOnly, "bare and pref_only default to false");

        rejects("a missing package", m("seconds", "20"));
        rejects("package with a space", m("package", "a b", "seconds", "20"));
        rejects("package with a path", m("package", "../x", "seconds", "20"));
        rejects("seconds 0", m("package", "a.b", "seconds", "0"));
        rejects("seconds 301", m("package", "a.b", "seconds", "301"));
        rejects("seconds abc", m("package", "a.b", "seconds", "abc"));
        rejects("seconds +5", m("package", "a.b", "seconds", "+5"));
        rejects("transport cellular", m("package", "a.b", "seconds", "5", "transport", "cellular"));
        rejects("bare=maybe", m("package", "a.b", "seconds", "5", "bare", "maybe"));
        rejects("pref_only=2", m("package", "a.b", "seconds", "5", "pref_only", "2"));

        ProbeArgs b = ProbeArgs.parse(m("package", "a.b", "seconds", "300", "transport", "wifi",
                "bare", "true", "pref_only", "true"));
        check(b.seconds == 300 && b.transport == ProbeArgs.Transport.WIFI && b.bareTun && b.prefOnly,
                "wifi, bare and pref_only accepted");

        byte[] syn = ipv4(6, 80, 40);
        check(PacketSummary.describe(syn, 40).equals("ipv4 src=10.255.0.2 dst=10.255.0.9 proto=6 dport=80"),
                "tcp syn: " + PacketSummary.describe(syn, 40));
        check(PacketSummary.describe(ipv4(17, 53, 40), 40)
                .equals("ipv4 src=10.255.0.2 dst=10.255.0.9 proto=17 dport=53"), "udp dns");
        check(PacketSummary.describe(ipv4(1, 0, 28), 28)
                .equals("ipv4 src=10.255.0.2 dst=10.255.0.9 proto=1"), "icmp has no port");
        check(PacketSummary.describe(ipv6(6, 443, 60), 60).equals("ipv6 proto=6 dport=443"), "ipv6 tcp");
        check(PacketSummary.describe(ipv6(58, 0, 48), 48).equals("ipv6 proto=58"), "ipv6 icmp has no port");

        check(PacketSummary.describe(new byte[10], 10).equals("unparsed len=10"), "shorter than the header");
        check(PacketSummary.describe(new byte[0], 0).equals("unparsed len=0"), "empty");
        byte[] v5 = ipv4(6, 80, 40);
        v5[0] = 0x55;
        check(PacketSummary.describe(v5, 40).equals("unparsed len=40"), "version nibble 5");
        check(PacketSummary.describe(syn, 41).equals("unparsed len=41"), "len larger than the array");
        check(PacketSummary.describe(syn, -1).equals("unparsed len=-1"), "negative len");
        check(PacketSummary.describe(null, 3).equals("unparsed len=3"), "null buffer");
        byte[] ihl = ipv4(6, 80, 40);
        ihl[0] = 0x4f; // header length 60 > len 40
        check(PacketSummary.describe(ihl, 40).equals("unparsed len=40"), "header length beyond len");
        check(PacketSummary.describe(syn, 23).equals("ipv4 src=10.255.0.2 dst=10.255.0.9 proto=6"),
                "tcp header cut before the port prints no port");
        for (int n = 0; n <= 60; n++) { // never throws, whatever the length
            PacketSummary.describe(ipv6(6, 80, 60), n);
            PacketSummary.describe(syn, n);
        }
        System.out.println("all probe host checks passed");
    }
}
