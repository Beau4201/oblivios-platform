package org.sovereignos.airpods.ui;

import java.util.List;

/**
 * When to lower and give back the music volume while the owner speaks (ADR-125). The AirPods signal a
 * conversation with a level byte (opcode 0x4B). Both captures show 01 and 02 when speech starts, 03
 * to 05 while it goes on, 0B and 04 near the end and 08 and 09 when it is over. So: lower on 01 or 02,
 * give back on 08 or 09 only, and change nothing for any other value, so a value we have not seen
 * cannot make the volume flap. Pure: it returns actions and executes none. One lock.
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
        if ((level == 8 || level == 9) && lowered) {
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

    private List<Action> giveBack() {
        if (!lowered) return List.of();
        lowered = false;
        return List.of(Action.RESTORE);
    }
}
