// SPDX-License-Identifier: Apache-2.0
package org.oblivios.exit;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * The Java twin of the two control protocols: the daemon's ({@code control.rs}, version 5) and the route helper's
 * ({@code routes/proto.rs}, version 1). A frame is a u32 big-endian length of tag plus payload, the tag byte, the
 * payload. The golden vectors in {@code tests/vectors.txt} are loaded by the Rust tests as well, so that the two codecs
 * cannot drift. A decoder checks the claimed length first and never allocates from it. Pure Java.
 */
public final class ControlCodec {
    public static final int MAX_FRAME = 4096;
    public static final int DAEMON_PROTOCOL = 5;
    public static final int ROUTE_PROTOCOL = 1;
    public static final int IFNAME_MAX = 15;

    private static final int TAG_HELLO = 0x01;
    private static final int TAG_SET_MEMORY_MAX = 0x02;
    private static final int TAG_REFRESH_APP = 0x03;
    private static final int TAG_REFRESH_ALL = 0x04;
    private static final int TAG_SHUTDOWN = 0x05;
    private static final int TAG_GET_STATE = 0x06;
    private static final int TAG_SET_POLICY = 0x07;
    private static final int TAG_SET_OVERRIDES = 0x08;
    private static final int TAG_RELEASE = 0x09;
    private static final int TAG_SET_COUNTRY = 0x0a;
    private static final int TAG_ROUTE_APPLY = 0x02;
    private static final int TAG_ROUTE_CLEAR = 0x03;
    private static final int TAG_ROUTE_GET_APPLIED = 0x04;
    private static final int TAG_READY = 0x81;
    private static final int TAG_STATE = 0x82;
    private static final int TAG_FAILURES = 0x83;
    private static final int TAG_ACK = 0x84;
    private static final int TAG_ERROR = 0x85;
    /** 0x86 is a Promoted from the daemon and an Applied from the route helper: the two protocols are decoded apart. */
    private static final int TAG_APPLIED = 0x86;

    private ControlCodec() {}

    /** A frame that cannot be a valid reply; the stream must be closed. */
    public static final class CodecException extends Exception {
        private static final long serialVersionUID = 1L;

        public CodecException(String what) {
            super(what);
        }
    }

    public enum Type { READY, STATE, FAILURES, PROMOTED, ACK, ERROR, APPLIED }

    /** The daemon states of {@code DaemonState}. */
    public static final int STATE_BOOTSTRAPPING = 0;
    public static final int STATE_UP = 1;
    public static final int STATE_FAILED = 2;

    /** The codes of {@code ErrorCode}. */
    public static final int ERR_BAD_VALUE = 1;
    public static final int ERR_TOO_MANY = 2;
    public static final int ERR_BUSY = 3;
    public static final int ERR_UNSUPPORTED = 4;
    public static final int ERR_FAILED = 5;

    /** One reply of the daemon or of the helper; only the fields of its {@link #type} are set. */
    public static final class Reply {
        public final Type type;
        public final int version;
        public final String iface;
        public final int state;
        public final int seq;
        public final int code;
        public final long uid;
        public final int company;
        public final long count;
        public final int rules;
        /** For {@link Type#PROMOTED}: true the first time the daemon reports the promotion. */
        public final boolean fresh;
        /** Bytes of the buffer this reply used. */
        public final int used;

        Reply(Type type, int version, String iface, int state, int seq, int code, long uid, int company, long count,
                int rules, int used) {
            this(type, version, iface, state, seq, code, uid, company, count, rules, false, used);
        }

        Reply(Type type, int version, String iface, int state, int seq, int code, long uid, int company, long count,
                int rules, boolean fresh, int used) {
            this.fresh = fresh;
            this.type = type;
            this.version = version;
            this.iface = iface;
            this.state = state;
            this.seq = seq;
            this.code = code;
            this.uid = uid;
            this.company = company;
            this.count = count;
            this.rules = rules;
            this.used = used;
        }
    }

    // ---- encoding -------------------------------------------------------------------------------------------------

    private static byte[] frame(int tag, byte[] payload) {
        ByteArrayOutputStream o = new ByteArrayOutputStream(5 + payload.length);
        putU32(o, payload.length + 1L);
        o.write(tag);
        o.write(payload, 0, payload.length);
        return o.toByteArray();
    }

    private static void putU32(ByteArrayOutputStream o, long v) {
        o.write((int) (v >>> 24) & 0xff);
        o.write((int) (v >>> 16) & 0xff);
        o.write((int) (v >>> 8) & 0xff);
        o.write((int) v & 0xff);
    }

    private static byte[] one(int b) {
        return new byte[] {(byte) b};
    }

    private static void seq(int seq) {
        if (seq < 0 || seq > 255) {
            throw new IllegalArgumentException("sequence number is one byte");
        }
    }

    public static byte[] hello() {
        return frame(TAG_HELLO, one(DAEMON_PROTOCOL));
    }

    public static byte[] setMemoryMax(int seq, long bytes) {
        seq(seq);
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        o.write(seq);
        putU32(o, bytes >>> 32);
        putU32(o, bytes & 0xffffffffL);
        return frame(TAG_SET_MEMORY_MAX, o.toByteArray());
    }

    public static byte[] refreshApp(int seq, long uid) {
        seq(seq);
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        o.write(seq);
        putU32(o, uid);
        return frame(TAG_REFRESH_APP, o.toByteArray());
    }

    public static byte[] refreshAll(int seq) {
        seq(seq);
        return frame(TAG_REFRESH_ALL, one(seq));
    }

    public static byte[] shutdown(int seq) {
        seq(seq);
        return frame(TAG_SHUTDOWN, one(seq));
    }

    public static byte[] getState(int seq) {
        seq(seq);
        return frame(TAG_GET_STATE, one(seq));
    }

    public static byte[] setPolicy(int seq, List<Policy.Cls> classes) {
        seq(seq);
        if (classes.size() > Policy.MAX_CLASSES) {
            throw new IllegalArgumentException("more classes than the daemon accepts");
        }
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        o.write(seq);
        o.write(classes.size());
        for (Policy.Cls c : classes) {
            putU32(o, c.start);
            putU32(o, c.end);
            o.write(c.kind.wire);
        }
        return frame(TAG_SET_POLICY, o.toByteArray());
    }

    public static byte[] setOverrides(int seq, long uid, int[] companies) {
        seq(seq);
        if (companies.length > 32) {
            throw new IllegalArgumentException("more companies than the daemon accepts");
        }
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        o.write(seq);
        putU32(o, uid);
        o.write(companies.length);
        for (int c : companies) {
            putU32(o, c);
        }
        return frame(TAG_SET_OVERRIDES, o.toByteArray());
    }

    /** "Hide again": ends the speed guard's promotion of {@code company} for {@code uid} (ADR-188). */
    public static byte[] release(int seq, long uid, int company) {
        seq(seq);
        if (company < 1) {
            throw new IllegalArgumentException("company id is 1 to 2147483647");
        }
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        o.write(seq);
        putU32(o, uid);
        putU32(o, company);
        return frame(TAG_RELEASE, o.toByteArray());
    }

    /** Two capital ASCII letters: the only country the daemon accepts (it refuses anything else, as does {@link #setCountry}). */
    public static boolean validCountry(String country) {
        return country != null && country.length() == 2 && isUpper(country.charAt(0)) && isUpper(country.charAt(1));
    }

    private static boolean isUpper(char c) {
        return c >= 'A' && c <= 'Z';
    }

    /**
     * The country whose exit relays the connections of {@code uid} must use (the place of an Isolated app, ADR-208);
     * null or empty removes the choice.
     */
    public static byte[] setCountry(int seq, long uid, String country) {
        seq(seq);
        if (uid < 0 || uid > 0xffffffffL) {
            throw new IllegalArgumentException("a uid is an unsigned 32 bit number");
        }
        boolean none = country == null || country.isEmpty();
        if (!none && !validCountry(country)) {
            throw new IllegalArgumentException("a country is two capital letters");
        }
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        o.write(seq);
        putU32(o, uid);
        if (none) {
            o.write(0);
        } else {
            o.write(2);
            o.write(country.charAt(0));
            o.write(country.charAt(1));
        }
        return frame(TAG_SET_COUNTRY, o.toByteArray());
    }

    public static byte[] routeHello() {
        return frame(TAG_HELLO, one(ROUTE_PROTOCOL));
    }

    /** {@code netId} 0 means "no Tor network yet". Ranges are {start, end} pairs. */
    public static byte[] routeApply(int seq, String iface, long netId, List<int[]> steer, List<int[]> guard) {
        seq(seq);
        byte[] name = iface.getBytes(StandardCharsets.UTF_8);
        if (name.length == 0 || name.length > IFNAME_MAX || steer.size() > Policy.MAX_RANGES
                || guard.size() > Policy.MAX_RANGES) {
            throw new IllegalArgumentException("specification outside the limits of the helper");
        }
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        o.write(seq);
        o.write(name.length);
        o.write(name, 0, name.length);
        putU32(o, netId);
        for (List<int[]> list : java.util.Arrays.asList(steer, guard)) {
            o.write(list.size());
            for (int[] r : list) {
                putU32(o, r[0]);
                putU32(o, r[1]);
            }
        }
        return frame(TAG_ROUTE_APPLY, o.toByteArray());
    }

    public static byte[] routeClear(int seq) {
        seq(seq);
        return frame(TAG_ROUTE_CLEAR, one(seq));
    }

    public static byte[] routeGetApplied(int seq) {
        seq(seq);
        return frame(TAG_ROUTE_GET_APPLIED, one(seq));
    }

    // ---- decoding -------------------------------------------------------------------------------------------------

    private static long u32(byte[] b, int at) {
        return ((b[at] & 0xffL) << 24) | ((b[at + 1] & 0xffL) << 16) | ((b[at + 2] & 0xffL) << 8) | (b[at + 3] & 0xffL);
    }

    /**
     * A company id of the daemon (32 bits on the wire, ADR-223). The app holds it in an int, so an id past 31 bits is refused as
     * a malformed frame; the ids of the generator stop at 262144.
     */
    private static int company(byte[] b, int at) throws CodecException {
        long id = u32(b, at);
        if (id > Integer.MAX_VALUE) {
            throw new CodecException("company id past 31 bits");
        }
        return (int) id;
    }

    private static boolean validIface(byte[] b, int from, int to) {
        int n = to - from;
        if (n < 1 || n > IFNAME_MAX) {
            return false;
        }
        if (n == 1 && b[from] == '.' || n == 2 && b[from] == '.' && b[from + 1] == '.') {
            return false;
        }
        for (int i = from; i < to; i++) {
            char c = (char) (b[i] & 0xff);
            boolean ok = c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '_' || c == '.' || c == '-';
            if (!ok) {
                return false;
            }
        }
        return true;
    }

    /**
     * Reads one reply of the daemon from the front of {@code buf[0..len)}; null when more bytes are needed.
     *
     * @throws CodecException on a frame that is too long, unknown or malformed
     */
    public static Reply decodeDaemon(byte[] buf, int len) throws CodecException {
        return decode(buf, len, true);
    }

    /** Like {@link #decodeDaemon} for the replies of the route helper. */
    public static Reply decodeRoute(byte[] buf, int len) throws CodecException {
        return decode(buf, len, false);
    }

    private static Reply decode(byte[] buf, int len, boolean daemon) throws CodecException {
        if (len < 4) {
            return null;
        }
        long claimed = u32(buf, 0);
        if (claimed > MAX_FRAME) {
            throw new CodecException("frame too long");
        }
        int end = 4 + (int) claimed;
        if (len < end) {
            return null;
        }
        if (claimed == 0) {
            throw new CodecException("empty frame");
        }
        int tag = buf[4] & 0xff;
        int p = 5; // payload start
        int n = end - p; // payload length
        switch (tag) {
            case TAG_READY:
                if (daemon) {
                    if (n < 1 || !validIface(buf, p + 1, end)) {
                        throw new CodecException("bad ready");
                    }
                    return new Reply(Type.READY, buf[p] & 0xff, new String(buf, p + 1, n - 1, StandardCharsets.US_ASCII), 0, 0,
                            0, 0, 0, 0, 0, end);
                }
                exact(n, 1);
                return new Reply(Type.READY, buf[p] & 0xff, null, 0, 0, 0, 0, 0, 0, 0, end);
            case TAG_STATE: {
                if (!daemon) {
                    throw new CodecException("unknown tag");
                }
                exact(n, 1);
                int s = buf[p] & 0xff;
                if (s > STATE_FAILED) {
                    throw new CodecException("bad state");
                }
                return new Reply(Type.STATE, 0, null, s, 0, 0, 0, 0, 0, 0, end);
            }
            case TAG_FAILURES:
                if (!daemon) {
                    throw new CodecException("unknown tag");
                }
                exact(n, 12);
                return new Reply(Type.FAILURES, 0, null, 0, 0, 0, u32(buf, p), company(buf, p + 4), u32(buf, p + 8), 0, end);
            case TAG_ACK:
                exact(n, 1);
                return new Reply(Type.ACK, 0, null, 0, buf[p] & 0xff, 0, 0, 0, 0, 0, end);
            case TAG_ERROR: {
                exact(n, 2);
                int code = buf[p + 1] & 0xff;
                if (code < ERR_BAD_VALUE || code > ERR_FAILED) {
                    throw new CodecException("bad error code");
                }
                return new Reply(Type.ERROR, 0, null, 0, buf[p] & 0xff, code, 0, 0, 0, 0, end);
            }
            case TAG_APPLIED:
                if (daemon) {
                    // A Promoted: the speed guard passes a company on to an app (ADR-188).
                    exact(n, 9);
                    int fresh = buf[p + 8] & 0xff;
                    if (fresh > 1) {
                        throw new CodecException("bad fresh flag");
                    }
                    return new Reply(Type.PROMOTED, 0, null, 0, 0, 0, u32(buf, p), company(buf, p + 4), 0, 0, fresh == 1, end);
                }
                exact(n, 3);
                return new Reply(Type.APPLIED, 0, null, 0, buf[p] & 0xff, 0, 0, 0, 0,
                        ((buf[p + 1] & 0xff) << 8) | (buf[p + 2] & 0xff), end);
            default:
                throw new CodecException("unknown tag");
        }
    }

    private static void exact(int have, int want) throws CodecException {
        if (have != want) {
            throw new CodecException("bad length");
        }
    }
}
