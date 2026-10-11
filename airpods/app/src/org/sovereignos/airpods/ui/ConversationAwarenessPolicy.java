// SPDX-License-Identifier: Apache-2.0
package org.sovereignos.airpods.ui;

import java.util.List;

/**
 * When to lower and give back the music volume while the owner speaks (ADR-125). The AirPods signal a
 * conversation with a level byte (opcode 0x4B). The captures show 01 and 02 when speech starts, 03
 * to 05 while it goes on, and 0B then 04 at the end, followed by 08 and 09 only when the pods switch
 * the listening mode back (in transparency the sequence ends at 04: capture 2026-10-04). A one-word
 * utterance gives 01 and then 06 three seconds later and nothing else (probe, twice). So: lower on
 * 01 or 02, give back on 04, 06, 08 or 09, and change nothing for any other value, so a value we have
 * not seen cannot make the volume flap. Pure: it returns actions and executes none. One lock.
 */
public final class ConversationAwarenessPolicy {
    public enum Action {
        /** Lower the music volume, if music is playing. */
        DUCK,
        /** Give the volume back, if it is still the value we set. */
        RESTORE
    }

    private boolean enabled;
    private boolean lowered;

    public ConversationAwarenessPolicy(boolean enabled) {
        this.enabled = enabled;
    }

    /** The AirPods reported a conversation level (0 to 255). */
    public synchronized List<Action> onLevel(int level) {
        if (level < 0 || level > 255) throw new IllegalArgumentException("level " + level);
        if (!enabled) return List.of();
        if ((level == 1 || level == 2) && !lowered) {
            lowered = true;
            return List.of(Action.DUCK);
        }
        if ((level == 4 || level == 6 || level == 8 || level == 9) && lowered) {
            lowered = false;
            return List.of(Action.RESTORE);
        }
        return List.of();
    }

    /** The user turned the switch on or off. */
    public synchronized List<Action> setEnabled(boolean on) {
        if (on == enabled) return List.of();
        enabled = on;
        return on ? List.of() : giveBack();
    }

    /** Both pods are out of the ears: the conversation is over for the pods. */
    public synchronized List<Action> onPodsOut() {
        return giveBack();
    }

    /** The session ended. */
    public synchronized List<Action> end() {
        return giveBack();
    }

    /**
     * How long to wait for the next level before {@link #onSilence()}: 1 s after 0B, because 04 follows
     * 0B after 0.21 to 0.22 s in every capture, so a 0B with nothing after it is the end; 8 s after any
     * other level, where the longest gap seen inside a conversation is 5.4 s.
     */
    public static long silenceMillis(int lastLevel) {
        return lastLevel == 0x0B ? 1000L : 8000L;
    }

    /**
     * No level arrived for the silence timeout while lowered. A very short utterance makes the pods
     * send only 01 and one more frame and never an end (capture 2026-10-04), so the volume must not
     * depend on an end level alone.
     */
    public synchronized List<Action> onSilence() {
        return giveBack();
    }

    private List<Action> giveBack() {
        if (!lowered) return List.of();
        lowered = false;
        return List.of(Action.RESTORE);
    }
}
