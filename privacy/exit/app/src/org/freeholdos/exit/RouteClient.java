// SPDX-License-Identifier: Apache-2.0
package org.freeholdos.exit;

import java.io.IOException;
import java.util.List;

/** The control channel to the route helper: Hello, then one request at a time, each answered by an Ack or an Error. */
final class RouteClient implements RouteApi {
    private final Link link;
    private int seq;

    RouteClient(Link.Connector connector) {
        this.link = new Link(connector, false);
    }

    private int nextSeq() {
        seq = seq % 255 + 1; // 1..255
        return seq;
    }

    @Override
    public void connect() throws IOException {
        link.open();
        seq = 0;
        link.send(ControlCodec.routeHello());
        ControlCodec.Reply r = link.next();
        if (r.type != ControlCodec.Type.READY || r.version != ControlCodec.ROUTE_PROTOCOL) {
            throw new Link.ProtocolException("unexpected answer to hello", r.type == ControlCodec.Type.ERROR ? r.code : 0);
        }
    }

    @Override
    public void apply(String iface, long netId, List<int[]> steer, List<int[]> guard) throws IOException {
        int s = nextSeq();
        link.send(ControlCodec.routeApply(s, iface, netId, steer, guard));
        awaitAck(s);
    }

    @Override
    public void clear() throws IOException {
        int s = nextSeq();
        link.send(ControlCodec.routeClear(s));
        awaitAck(s);
    }

    private void awaitAck(int s) throws IOException {
        ControlCodec.Reply r = link.next();
        if (r.type == ControlCodec.Type.ACK && r.seq == s) {
            return;
        }
        throw r.type == ControlCodec.Type.ERROR
                ? new Link.ProtocolException("request refused", r.code)
                : new Link.ProtocolException("unexpected reply " + r.type, 0);
    }

    @Override
    public void close() {
        link.close();
    }
}
