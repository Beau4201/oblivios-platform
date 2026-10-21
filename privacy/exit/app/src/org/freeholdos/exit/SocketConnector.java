// SPDX-License-Identifier: Apache-2.0
package org.freeholdos.exit;

import android.net.LocalSocket;
import android.net.LocalSocketAddress;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Opens the control socket of an {@code init} service: a socket in /dev/socket that the service definition names (exitd and
 * exitd_route). SELinux lets only this app's domain connect and the service checks the peer's context before it reads a
 * byte, so the file mode alone is not what keeps others out.
 */
final class SocketConnector implements Link.Connector {
    /** A peer that does not answer within this time is a lost peer. */
    private static final int TIMEOUT_MS = 10_000;

    private final String name;

    SocketConnector(String name) {
        this.name = name;
    }

    @Override
    public Link.Transport open() throws IOException {
        final LocalSocket socket = new LocalSocket(LocalSocket.SOCKET_STREAM);
        try {
            socket.connect(new LocalSocketAddress(name, LocalSocketAddress.Namespace.RESERVED));
            socket.setSoTimeout(TIMEOUT_MS);
            final InputStream in = socket.getInputStream();
            final OutputStream out = socket.getOutputStream();
            return new Link.Transport() {
                @Override
                public InputStream in() {
                    return in;
                }

                @Override
                public OutputStream out() {
                    return out;
                }

                @Override
                public void close() throws IOException {
                    socket.close();
                }
            };
        } catch (IOException | RuntimeException e) {
            try {
                socket.close();
            } catch (IOException ignored) {
                // the connect failed; the close of a socket that never connected has nothing to report
            }
            throw e;
        }
    }
}
