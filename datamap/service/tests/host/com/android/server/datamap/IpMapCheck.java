// SPDX-License-Identifier: Apache-2.0
package com.android.server.datamap;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

/**
 * The reader against the file the Python tool made from the fixtures (datamap/ipmap/tests/fixtures): every line of
 * ipmap-expected.tsv comes from the Python reader, so this also checks that the two readers agree.
 * usage: IpMapCheck IPMAP.BIN EXPECTED.TSV [REAL.BIN REAL-EXPECTED.TSV]
 */
public final class IpMapCheck {
    private static String kindName(int kind) {
        switch (kind) {
            case ServerPlace.CITY:
                return "CITY";
            case ServerPlace.COUNTRY:
                return "COUNTRY";
            case ServerPlace.UNCERTAIN:
                return "UNCERTAIN";
            case ServerPlace.ANYCAST:
                return "ANYCAST";
            case ServerPlace.STEERED:
                return "STEERED";
            default:
                return "NONE";
        }
    }

    /** Every line of the file is one lookup the Python reader made; the Java reader must give the same. @return the lines */
    private static int compare(IpMap map, String file) throws Exception {
        List<String> lines = Files.readAllLines(Paths.get(file));
        for (String line : lines) {
            String[] f = line.split("\t", -1);
            ServerPlace p = map.lookup(f[0]);
            Check.isTrue(p != null, f[0] + " parses");
            Check.eq(f[1], kindName(p.kind), f[0] + " kind");
            Check.eq(f[2], p.country, f[0] + " country");
            Check.eq(f[3], p.city, f[0] + " city");
            Check.isTrue(Math.abs(Float.parseFloat(f[4]) - p.lat) < 0.006f, f[0] + " latitude " + p.lat);
            Check.isTrue(Math.abs(Float.parseFloat(f[5]) - p.lon) < 0.006f, f[0] + " longitude " + p.lon);
            Check.eq(Integer.parseInt(f[6]), p.asn, f[0] + " asn");
            Check.eq(f[7], p.operator, f[0] + " operator");
        }
        return lines.size();
    }

    public static void main(String[] args) throws Exception {
        byte[] bytes = Files.readAllBytes(Paths.get(args[0]));
        IpMap map = new IpMap(ByteBuffer.wrap(bytes));
        Check.isTrue(compare(map, args[1]) >= 18, "the expectation file has its rows");
        Check.eq("fixture", map.sources(), "the sources line");
        if (args.length >= 4) {   // the real file and a sample made by tools/sample-expected.py
            IpMap real = IpMap.open(new java.io.File(args[2]), 256L << 20);
            System.out.println("compared " + compare(real, args[3]) + " addresses of the real file");
        }

        // an address that is not a literal is not looked up and never resolved
        for (String bad : new String[] {"", "1.2.3", "1.2.3.4.5", "256.1.1.1", "01.2.3.4", "1.2.3.-4", "example.com",
                "localhost", "::g", "1::2::3", "1:2:3:4:5:6:7:8:9", "1:2:3:4:5:6:7", ":1:2:3:4:5:6:7", "12345::",
                "1.2.3.4::", "::1.2.3.4.5", "1.2.3.4:80", " 1.2.3.4"}) {
            Check.isTrue(map.lookup(bad) == null, "'" + bad + "' is not an address");
        }
        Check.isTrue(map.lookup(null) == null, "null is not an address");
        // forms of one address
        Check.eq("CITY", kindName(map.lookup("8.8.8.8").kind), "dotted quad");
        Check.eq("CITY", kindName(map.lookup("::ffff:8.8.8.8").kind), "an IPv4-mapped address is an IPv4 address");
        Check.eq("CITY", kindName(map.lookup("::ffff:808:808").kind), "the hex form of a mapped address");
        Check.eq("CITY", kindName(map.lookup("8.8.8.8%wlan0").kind), "a zone is ignored");
        Check.eq("COUNTRY", kindName(map.lookup("2001:4860:0:0:0:0:0:1").kind), "an IPv6 address written in full");
        Check.eq("COUNTRY", kindName(map.lookup("2001:4860::1.2.3.4").kind), "an IPv6 address with an IPv4 tail");
        Check.eq("COUNTRY", kindName(map.lookup("2001:4860:0:5::").kind), "a trailing ::");
        Check.eq("NONE", kindName(map.lookup("::").kind), "the unspecified address has no place");

        // a file that is not ours, cut short or padded is refused
        Check.throwsA(IpMap.FormatException.class, () -> new IpMap(ByteBuffer.wrap(new byte[10])), "too short");
        byte[] wrong = bytes.clone();
        wrong[0] = 'X';
        Check.throwsA(IpMap.FormatException.class, () -> new IpMap(ByteBuffer.wrap(wrong)), "wrong magic");
        byte[] version = bytes.clone();
        version[5] = 2;
        Check.throwsA(IpMap.FormatException.class, () -> new IpMap(ByteBuffer.wrap(version)), "unknown version");
        byte[] cut = java.util.Arrays.copyOf(bytes, bytes.length - 1);
        Check.throwsA(IpMap.FormatException.class, () -> new IpMap(ByteBuffer.wrap(cut)), "a section runs past the end");
        byte[] padded = java.util.Arrays.copyOf(bytes, bytes.length + 1);
        Check.throwsA(IpMap.FormatException.class, () -> new IpMap(ByteBuffer.wrap(padded)), "bytes after the last section");
        byte[] stride = bytes.clone();
        stride[8 + 8 * 0 + 7] = (byte) (stride[8 + 8 * 0 + 7] + 1);   // the length of CC4 is no multiple of 6
        Check.throwsA(IpMap.FormatException.class, () -> new IpMap(ByteBuffer.wrap(stride)), "a length that is no multiple");
        // the file is mapped, and refused when it is larger than the limit
        IpMap mapped = IpMap.open(new java.io.File(args[0]), 1 << 20);
        Check.eq("CITY", kindName(mapped.lookup("8.8.8.8").kind), "a mapped file answers");
        Check.throwsA(IpMap.FormatException.class, () -> IpMap.open(new java.io.File(args[0]), 100), "a file over the limit");
        Check.throwsA(java.io.IOException.class, () -> IpMap.open(new java.io.File(args[0] + ".missing"), 1 << 20), "a missing file");
        // an ASN that the operator table does not list has no operator: the next lower ASN must not be taken instead
        byte[] orphan = bytes.clone();
        ByteBuffer ob = ByteBuffer.wrap(orphan);
        int asnOff = ob.getInt(8 + 8 * 1);
        int asnLen = ob.getInt(12 + 8 * 1);
        boolean found = false;
        for (int at = asnOff; at < asnOff + asnLen; at += 8) {
            if (ob.getInt(at) == 0x08080800) {
                ob.putInt(at + 4, 15170);
                found = true;
            }
        }
        Check.isTrue(found, "the record of 8.8.8.0 is in the fixture");
        ServerPlace orphaned = new IpMap(ByteBuffer.wrap(orphan)).lookup("8.8.8.8");
        Check.eq(15170, orphaned.asn, "the asn is read as it is");
        Check.eq("", orphaned.operator, "no operator for an asn the table does not list");
        System.out.println("PASS IpMapCheck");
    }
}
