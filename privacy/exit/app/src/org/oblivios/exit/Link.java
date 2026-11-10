// SPDX-License-Identifier: Apache-2.0
package org.oblivios.exit;

import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * One framed control connection (the daemon's or the helper's) over a pair of streams: sends frames, reads replies into a
 * buffer that never grows past one frame plus one read. A reply that does not decode ends the link. The streams come from a
 * {@link Connector}, so the host checks run it without a socket. Not thread safe.
 */
final class Link implements Closeable {
    /** One open connection. */
    interface Transport extends Closeable {
        InputStream in();

        OutputStream out();
    }

    /** Opens connections; on a device a local socket of the init service, in the host checks a scripted fake. */
    interface Connector {
        Transport open() throws IOException;
    }

    /** The peer sent a well-formed reply that this client did not expect, or refused a request. */
    static final class ProtocolException extends IOException {
        private static final long serialVersionUID = 1L;
        /** {@code ControlCodec.ERR_*} when the peer refused a request, else 0. */
        final int code;

        ProtocolException(String what, int code) {
            super(what);
            this.code = code;
        }
    }

    private final Connector connector;
    private final boolean daemon;
    private Transport transport;
    private final byte[] buf = new byte[4 + ControlCodec.MAX_FRAME];
    private int len;

    Link(Connector connector, boolean daemon) {
        this.connector = connector;
        this.daemon = daemon;
    }

    void open() throws IOException {
        close();
        transport = connector.open();
        len = 0;
    }

    void send(byte[] frame) throws IOException {
        if (transport == null) {
            throw new IOException("not connected");
        }
        transport.out().write(frame);
        transport.out().flush();
    }

    /** True when a reply can be read without waiting: bytes are buffered or waiting in the stream. */
    boolean ready() throws IOException {
        return transport != null && (len > 0 || transport.in().available() > 0);
    }

    /** The next reply; blocks until it is whole (the transport's own time-out applies). */
    ControlCodec.Reply next() throws IOException {
        if (transport == null) {
            throw new IOException("not connected");
        }
        while (true) {
            ControlCodec.Reply r;
            try {
                r = daemon ? ControlCodec.decodeDaemon(buf, len) : ControlCodec.decodeRoute(buf, len);
            } catch (ControlCodec.CodecException e) {
                close();
                throw new IOException("bad reply: " + e.getMessage());
            }
            if (r != null) {
                System.arraycopy(buf, r.used, buf, 0, len - r.used);
                len -= r.used;
                return r;
            }
            int n = transport.in().read(buf, len, buf.length - len);
            if (n < 0) {
                close();
                throw new EOFException("the peer closed the connection");
            }
            len += n;
        }
    }

    @Override
    public void close() {
        if (transport != null) {
            try {
                transport.close();
            } catch (IOException e) {
                // closing a broken connection: nothing left to do
            }
            transport = null;
        }
        len = 0;
    }
}
