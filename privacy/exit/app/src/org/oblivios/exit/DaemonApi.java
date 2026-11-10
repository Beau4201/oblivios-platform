// SPDX-License-Identifier: Apache-2.0
package org.oblivios.exit;

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

    /** The country whose exit relays one app uid must use (two capital letters); null removes the choice. */
    void setCountry(long uid, String country) throws IOException;

    /** The daemon state, the failure reports that arrived since the last call and the companies it passes on for now. */
    Poll poll() throws IOException;

    /** "Hide again" (ADR-188): ends the speed guard's promotion of a company for one app uid and keeps it hidden. */
    void release(long uid, int company) throws IOException;

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

    /** The speed guard passes a company's names on to an app for now; counts and ids only, never a host name. */
    final class Promotion {
        public final long uid;
        public final int company;
        /** True the first time the daemon reports it: the user is told once. */
        public final boolean fresh;

        public Promotion(long uid, int company, boolean fresh) {
            this.uid = uid;
            this.company = company;
            this.fresh = fresh;
        }
    }

    final class Poll {
        /** {@code ControlCodec.STATE_*}. */
        public final int state;
        public final List<Failure> failures;
        public final List<Promotion> promotions;

        public Poll(int state, List<Failure> failures) {
            this(state, failures, java.util.Collections.<Promotion>emptyList());
        }

        public Poll(int state, List<Failure> failures, List<Promotion> promotions) {
            this.state = state;
            this.failures = failures;
            this.promotions = promotions;
        }
    }
}
