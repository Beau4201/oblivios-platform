// SPDX-License-Identifier: Apache-2.0
package org.freeholdos.exit;

import java.util.Collections;
import java.util.EnumSet;
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

    public Status(Phase phase, int daemonState, Set<Policy.Warning> warnings, String lastError) {
        this.phase = phase;
        this.daemonState = daemonState;
        EnumSet<Policy.Warning> copy = EnumSet.noneOf(Policy.Warning.class);
        copy.addAll(warnings);
        this.warnings = Collections.unmodifiableSet(copy);
        this.lastError = lastError;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Status)) {
            return false;
        }
        Status s = (Status) o;
        return phase == s.phase && daemonState == s.daemonState && warnings.equals(s.warnings)
                && java.util.Objects.equals(lastError, s.lastError);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(phase, daemonState, warnings, lastError);
    }

    @Override
    public String toString() {
        return phase + "/" + daemonState + "/" + warnings + (lastError == null ? "" : "/" + lastError);
    }
}
