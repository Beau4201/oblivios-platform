// SPDX-License-Identifier: Apache-2.0
package org.freeholdos.exit;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * The control channel to the exit daemon: Hello, then one request at a time, each answered by an Ack or an Error with its
 * sequence number. The daemon never pushes; failure reports ride on the answer to {@code GetState} and are queued when they
 * arrive at any other time. A refusal ({@code Error}) is an {@link IOException} like a lost link, so the controller starts over.
 */
final class DaemonClient implements DaemonApi {
    private final Link link;
    private int seq;
    private final List<Failure> pending = new ArrayList<>();

    DaemonClient(Link.Connector connector) {
        this.link = new Link(connector, true);
    }

    private int nextSeq() {
        seq = seq % 255 + 1; // 1..255
        return seq;
    }

    @Override
    public String connect() throws IOException {
        link.open();
        seq = 0;
        pending.clear();
        link.send(ControlCodec.hello());
        ControlCodec.Reply r = link.next();
        if (r.type == ControlCodec.Type.ERROR) {
            throw new Link.ProtocolException("hello refused", r.code);
        }
        if (r.type != ControlCodec.Type.READY || r.version != ControlCodec.DAEMON_PROTOCOL) {
            throw new Link.ProtocolException("unexpected answer to hello", 0);
        }
        return r.iface;
    }

    @Override
    public void setPolicy(List<Policy.Cls> classes) throws IOException {
        int s = nextSeq();
        link.send(ControlCodec.setPolicy(s, classes));
        awaitAck(s);
    }

    @Override
    public void setOverrides(long uid, int[] companies) throws IOException {
        int s = nextSeq();
        link.send(ControlCodec.setOverrides(s, uid, companies));
        awaitAck(s);
    }

    @Override
    public Poll poll() throws IOException {
        int s = nextSeq();
        link.send(ControlCodec.getState(s));
        ControlCodec.Reply r;
        while ((r = link.next()).type == ControlCodec.Type.FAILURES) {
            pending.add(new Failure(r.uid, r.company, r.count));
        }
        if (r.type != ControlCodec.Type.STATE) {
            throw refused(r);
        }
        int state = r.state;
        // The reports that follow the state were written right after it; take what has arrived, the rest comes with the next call.
        while (link.ready()) {
            r = link.next();
            if (r.type != ControlCodec.Type.FAILURES) {
                throw refused(r);
            }
            pending.add(new Failure(r.uid, r.company, r.count));
        }
        List<Failure> out = new ArrayList<>(pending);
        pending.clear();
        return new Poll(state, out);
    }

    @Override
    public void shutdown() throws IOException {
        int s = nextSeq();
        link.send(ControlCodec.shutdown(s));
        awaitAck(s);
    }

    private void awaitAck(int s) throws IOException {
        while (true) {
            ControlCodec.Reply r = link.next();
            if (r.type == ControlCodec.Type.FAILURES) {
                pending.add(new Failure(r.uid, r.company, r.count));
                continue;
            }
            if (r.type == ControlCodec.Type.ACK && r.seq == s) {
                return;
            }
            throw refused(r);
        }
    }

    private static Link.ProtocolException refused(ControlCodec.Reply r) {
        return r.type == ControlCodec.Type.ERROR
                ? new Link.ProtocolException("request refused", r.code)
                : new Link.ProtocolException("unexpected reply " + r.type, 0);
    }

    @Override
    public void close() {
        link.close();
    }
}
