// SPDX-License-Identifier: Apache-2.0
package com.android.server.compartments;

/**
 * The activity of the phone from the speed of its real location fixes, for the invented sensor streams (spec section 4.4):
 * STILL below 0.6 m/s, WALKING up to 2.5 m/s, DRIVING above 4.5 m/s, with a hysteresis so that one noisy fix does not change it,
 * and STILL again when no fix has come for {@link #STALE_MS}.
 */
final class ActivityEstimator {
    enum Activity { STILL, WALKING, DRIVING }

    static final long STALE_MS = 60_000;
    static final int CONFIRM = 3;

    private Activity current = Activity.STILL;
    private Activity candidate = Activity.STILL;
    private int seen;
    private long lastMs = Long.MIN_VALUE;

    static Activity classify(double speedMps, Activity from) {
        if (!(speedMps >= 0.0)) {
            return from;  // NaN or negative: no information
        }
        switch (from) {
            case DRIVING:
                return speedMps >= 3.5 ? Activity.DRIVING : (speedMps >= 0.6 ? Activity.WALKING : Activity.STILL);
            case WALKING:
                return speedMps > 4.5 ? Activity.DRIVING : (speedMps >= 0.4 ? Activity.WALKING : Activity.STILL);
            default:
                return speedMps > 4.5 ? Activity.DRIVING : (speedMps > 0.6 ? Activity.WALKING : Activity.STILL);
        }
    }

    /** Feeds one fix; returns the activity after it. Time goes backwards only by a clock change and is then ignored. */
    synchronized Activity onFix(double speedMps, long nowMs) {
        if (nowMs < lastMs) {
            return current;
        }
        lastMs = nowMs;
        Activity next = classify(speedMps, current);
        if (next == current) {
            candidate = current;
            seen = 0;
        } else if (next == candidate) {
            if (++seen >= CONFIRM) {
                current = next;
                seen = 0;
            }
        } else {
            candidate = next;
            seen = 1;
        }
        return current;
    }

    synchronized Activity at(long nowMs) {
        return lastMs != Long.MIN_VALUE && nowMs - lastMs > STALE_MS ? Activity.STILL : current;
    }
}
