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
            Check.eq(Integer.parseUnsignedInt(f[6]), p.asn, f[0] + " asn");
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
                "1.2.3.4::", "::1.2.3.4.5", "1.2.3.4:80", " 1.2.3.4", "8.8.8.8%wlan0", "8.8.8.8%", "2001:4860::1%", "%", "\uFF12001:4860::1",
                "\u0661::1", "1.2.3.4\u0661", "2001:4860::1%" + "a".repeat(60), "::ffff:8.8.8.8%" + "a".repeat(60),
                "2001:4860::1%a%b", "8.8.8.8%a:b"}) {
            Check.isTrue(map.lookup(bad) == null, "'" + bad + "' is not an address");
        }
        Check.isTrue(map.lookup(null) == null, "null is not an address");
        Check.isTrue(map.lookup("2001:4860::1%" + "a".repeat(40)) != null, "a zone of 40 letters is still an address");
        // forms of one address
        Check.eq("CITY", kindName(map.lookup("8.8.8.8").kind), "dotted quad");
        Check.eq("CITY", kindName(map.lookup("::ffff:8.8.8.8").kind), "an IPv4-mapped address is an IPv4 address");
        Check.eq("CITY", kindName(map.lookup("::ffff:808:808").kind), "the hex form of a mapped address");
        Check.eq("COUNTRY", kindName(map.lookup("2001:4860::1%wlan0").kind), "the zone of an IPv6 address is ignored");
        Check.eq("CITY", kindName(map.lookup("::ffff:8.8.8.8%wlan0").kind), "so is the zone of a mapped address");
        Check.eq("CITY", kindName(map.lookup("64:ff9b::808:808").kind), "the NAT64 prefix carries an IPv4 address");
        Check.eq("NONE", kindName(map.lookup("64:ff9b:1::808:808").kind), "another 64:ff9b address is IPv6");
        Check.eq("COUNTRY", kindName(map.lookup("a000::1").kind), "an IPv6 key with the top bit set");
        Check.eq("ANYCAST", kindName(map.lookup("a000:1::9").kind), "an anycast /48 with the top bit set");
        Check.eq("STEERED", kindName(map.lookup("11.0.0.1").kind), "a steered network with an AS number of 2**31 or more");
        Check.eq("Example Transit", map.lookup("12.0.0.1").operator, "an operator for an AS number of 2**31 or more");
        Check.eq(-94967296, map.lookup("11.0.0.1").asn, "the AS number is the same 32 bits");
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
        byte[] reserved = bytes.clone();
        reserved[6] = 1;
        Check.throwsA(IpMap.FormatException.class, () -> new IpMap(ByteBuffer.wrap(reserved)), "a reserved header field is set");
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

        // a record that points outside its section gives no place and no exception: a damaged file never takes the service down
        ByteBuffer cb = ByteBuffer.wrap(bytes.clone());
        int cityOff = cb.getInt(8 + 8 * 2);
        int cityLen = cb.getInt(12 + 8 * 2);
        int locOff = cb.getInt(8 + 8 * 3);
        int namesOff = cb.getInt(8 + 8 * 7);
        int namesLen = cb.getInt(12 + 8 * 7);
        int record = -1;
        for (int at = cityOff; at < cityOff + cityLen; at += 8) {
            if (cb.getInt(at) == 0x08080800) {
                record = at;
            }
        }
        Check.isTrue(record >= 0, "the city record of 8.8.8.0 is in the fixture");
        int loc = cb.getInt(record + 4);
        byte[] far = bytes.clone();
        ByteBuffer.wrap(far).putInt(record + 4, 0x00010000);
        Check.eq("NONE", kindName(new IpMap(ByteBuffer.wrap(far)).lookup("8.8.8.8").kind), "a location past the end of its section");
        byte[] edge = bytes.clone();
        ByteBuffer.wrap(edge).putInt(record + 4, cb.getInt(12 + 8 * 3) / 10);   // the first record past the last one
        Check.eq("NONE", kindName(new IpMap(ByteBuffer.wrap(edge)).lookup("8.8.8.8").kind), "the first location past the end");
        byte[] nameFar = bytes.clone();
        ByteBuffer.wrap(nameFar).putInt(locOff + loc * 10 + 6, namesLen);
        Check.eq("", new IpMap(ByteBuffer.wrap(nameFar)).lookup("8.8.8.8").city, "a name offset at the end of the names");
        byte[] nameBeyond = bytes.clone();
        ByteBuffer.wrap(nameBeyond).putInt(locOff + loc * 10 + 6, bytes.length);   // far past the names, and past the file
        Check.eq("", new IpMap(ByteBuffer.wrap(nameBeyond)).lookup("8.8.8.8").city, "a name offset past the end of the file");
        byte[] nameLong = bytes.clone();
        int nameAt = namesOff + cb.getInt(locOff + loc * 10 + 6);
        nameLong[nameAt] = (byte) 0xFF;
        Check.eq("", new IpMap(ByteBuffer.wrap(nameLong)).lookup("8.8.8.8").city, "a name that runs past the names");
        byte[] nameLast = bytes.clone();
        ByteBuffer.wrap(nameLast).putInt(locOff + loc * 10 + 6, namesLen - 1);
        Check.eq("", new IpMap(ByteBuffer.wrap(nameLast)).lookup("8.8.8.8").city, "a name whose length byte is the last byte");
        System.out.println("PASS IpMapCheck");
    }
}
