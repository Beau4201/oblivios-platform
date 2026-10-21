// SPDX-License-Identifier: Apache-2.0
package org.freeholdos.exit;

import java.io.IOException;
import java.util.List;

/**
 * The exit daemon as the controller sees it. Every method may throw {@link IOException}: the link is gone, a reply did not
 * come in time, or the daemon refused the request. The controller treats each of them as a lost daemon.
 */
public interface DaemonApi {
    /** Opens the control socket and says Hello. Returns the name of the tun interface from Ready. */
    String connect() throws IOException;

    /** Replaces the whole class table of the daemon. */
    void setPolicy(List<Policy.Cls> classes) throws IOException;

    /** The companies whose hosts may see the real address of one app uid; an empty list removes the override. */
    void setOverrides(long uid, int[] companies) throws IOException;

    /** The daemon state and the failure reports that arrived since the last call. */
    Poll poll() throws IOException;

    /**
     * Asks the daemon to exit. {@code init} starts it again, with a Tor client that has contacted nobody; this is how the
     * Tor client is stopped when the user no longer needs it.
     */
    void shutdown() throws IOException;

    /** Closes the link; safe to call at any time and more than once. */
    void close();

    /** A report from the failure ladder: counts only, a company id and never a host name. */
    final class Failure {
        public final long uid;
        public final int company;
        public final long count;

        public Failure(long uid, int company, long count) {
            this.uid = uid;
            this.company = company;
            this.count = count;
        }
    }

    final class Poll {
        /** {@code ControlCodec.STATE_*}. */
        public final int state;
        public final List<Failure> failures;

        public Poll(int state, List<Failure> failures) {
            this.state = state;
            this.failures = failures;
        }
    }
}
