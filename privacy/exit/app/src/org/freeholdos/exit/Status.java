// SPDX-License-Identifier: Apache-2.0
package org.freeholdos.exit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** What the settings screen shows about the service. Immutable. */
public final class Status {
    public enum Phase {
        /** Nothing needs the daemon: no consent and no app on Tor. The daemon is not running. */
        IDLE,
        /** The daemon is wanted and the links are being set up (or retried). */
        CONNECTING,
        /** Both links are up and the rules, the table, the network and the preferences are in place. */
        RUNNING,
        /** A link was lost: apps on Tor have no network and masking is paused until the links are back. */
        PAUSED
    }

    public final Phase phase;
    /** {@code ControlCodec.STATE_*} of the daemon, or -1 when it is not connected. */
    public final int daemonState;
    public final Set<Policy.Warning> warnings;
    /** The class name of the last error, for the status line; null when there was none. */
    public final String lastError;
    /** The companies that the speed guard passes on to an app for now (ADR-188), sorted by uid and company. */
    public final List<Promoted> promotions;

    /** An app uid and a company id; never a host name. */
    public static final class Promoted {
        public final long uid;
        public final int company;

        public Promoted(long uid, int company) {
            this.uid = uid;
            this.company = company;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Promoted && ((Promoted) o).uid == uid && ((Promoted) o).company == company;
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(uid, company);
        }

        @Override
        public String toString() {
            return uid + "/" + company;
        }
    }

    public Status(Phase phase, int daemonState, Set<Policy.Warning> warnings, String lastError) {
        this(phase, daemonState, warnings, lastError, Collections.<Promoted>emptyList());
    }

    public Status(Phase phase, int daemonState, Set<Policy.Warning> warnings, String lastError, List<Promoted> promotions) {
        this.phase = phase;
        this.daemonState = daemonState;
        EnumSet<Policy.Warning> copy = EnumSet.noneOf(Policy.Warning.class);
        copy.addAll(warnings);
        this.warnings = Collections.unmodifiableSet(copy);
        this.lastError = lastError;
        this.promotions = Collections.unmodifiableList(new ArrayList<>(promotions));
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Status)) {
            return false;
        }
        Status s = (Status) o;
        return phase == s.phase && daemonState == s.daemonState && warnings.equals(s.warnings)
                && java.util.Objects.equals(lastError, s.lastError) && promotions.equals(s.promotions);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(phase, daemonState, warnings, lastError, promotions);
    }

    @Override
    public String toString() {
        return phase + "/" + daemonState + "/" + warnings + (lastError == null ? "" : "/" + lastError)
                + (promotions.isEmpty() ? "" : "/promoted" + promotions);
    }
}
