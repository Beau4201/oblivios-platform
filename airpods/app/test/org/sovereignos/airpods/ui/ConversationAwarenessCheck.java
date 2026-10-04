package org.sovereignos.airpods.ui;

import java.util.List;
import org.sovereignos.airpods.ui.ConversationAwarenessPolicy.Action;

/** Host-runnable check: javac + java -ea with airpods-core.jar on the class path. */
public final class ConversationAwarenessCheck {
    private static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError(what);
    }

    private static void is(List<Action> got, String what, Action... want) {
        check(got.equals(List.of(want)), what + ": got " + got + ", wanted " + List.of(want));
    }

    public static void main(String[] args) {
        // The captured conversations: 2026-10-02 idle 09, then 01 02 03 05 03 0B 04 08 09; 2026-10-04
        // (transparency) 01 02 03 0B 04 and nothing after it.
        ConversationAwarenessPolicy p = new ConversationAwarenessPolicy(true);
        is(p.onLevel(0x09), "idle at the start of a session");
        is(p.onLevel(0x01), "speech starts", Action.DUCK);
        is(p.onLevel(0x02), "still speech");
        is(p.onLevel(0x03), "speech goes on");
        is(p.onLevel(0x05), "speech goes on");
        is(p.onLevel(0x03), "speech goes on");
        is(p.onLevel(0x0B), "near the end the volume stays lowered");
        is(p.onLevel(0x04), "over at 04, where the transparency capture ends", Action.RESTORE);
        is(p.onLevel(0x08), "08 after 04 changes nothing");
        is(p.onLevel(0x09), "idle again");
        is(p.onLevel(0x01), "a second conversation", Action.DUCK);
        is(p.onLevel(0x01), "a repeated start does not lower twice");
        is(p.onLevel(0x09), "over by 09 as well", Action.RESTORE);
        is(p.onLevel(0x01), "a third conversation", Action.DUCK);
        is(p.onLevel(0x08), "over by 08 as well", Action.RESTORE);
        p = new ConversationAwarenessPolicy(true);
        p.onLevel(0x01);
        p.onLevel(0x03);
        is(p.onLevel(0x0B), "the transparency capture: 0B keeps it lowered");
        is(p.onLevel(0x04), "the transparency capture: 04 gives it back", Action.RESTORE);
        is(p.onLevel(0x04), "a repeated 04 changes nothing");

        // Only 01/02 lower and only 04/06/08/09 give back; every other value (including 03, 05 and 0B,
        // which occur inside a conversation) changes nothing, lowered or not.
        for (int level : new int[] {0x00, 0x03, 0x05, 0x07, 0x0A, 0x0B, 0x0C, 0x20, 0xFF}) {
            ConversationAwarenessPolicy q = new ConversationAwarenessPolicy(true);
            is(q.onLevel(level), "idle, unseen level " + level);
            q.onLevel(0x01);
            is(q.onLevel(level), "lowered, unseen level " + level);
        }

        // Nothing to give back when nothing was lowered.
        p = new ConversationAwarenessPolicy(true);
        is(p.onLevel(0x08), "restore without lowering");

        // Switch off: no lowering; turning it off while lowered gives the volume back.
        p = new ConversationAwarenessPolicy(false);
        is(p.onLevel(0x01), "off, speech");
        is(p.setEnabled(true), "turning on while idle");
        is(p.onLevel(0x01), "on, speech", Action.DUCK);
        is(p.setEnabled(false), "turning off while lowered", Action.RESTORE);
        is(p.onLevel(0x08), "nothing to give back after that");
        is(p.setEnabled(false), "turning off again");

        // Pods out of the ears and the end of the session give the volume back, once.
        p = new ConversationAwarenessPolicy(true);
        p.onLevel(0x01);
        is(p.onPodsOut(), "both pods out while lowered", Action.RESTORE);
        is(p.onPodsOut(), "again");
        is(p.onLevel(0x08), "no second restore");
        p = new ConversationAwarenessPolicy(true);
        p.onLevel(0x02);
        is(p.end(), "session ends while lowered", Action.RESTORE);
        is(p.end(), "second end");
        is(new ConversationAwarenessPolicy(true).end(), "end when nothing was lowered");
        is(new ConversationAwarenessPolicy(true).onPodsOut(), "pods out when nothing was lowered");

        check(ConversationAwarenessPolicy.silenceMillis(0x0B) == 1000L, "after 0B the wait is 1 s");
        for (int level : new int[] {0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x08, 0x09, 0x0A, 0xFF}) {
            check(ConversationAwarenessPolicy.silenceMillis(level) == 8000L, "after " + level + " the wait is 8 s");
        }
        // A one-word utterance (probe 2026-10-04, twice): 01 and then 06 three seconds later.
        p = new ConversationAwarenessPolicy(true);
        is(p.onLevel(0x01), "a one-word utterance starts", Action.DUCK);
        is(p.onLevel(0x06), "06 ends a one-word utterance", Action.RESTORE);
        is(p.onLevel(0x06), "a repeated 06 changes nothing");
        is(new ConversationAwarenessPolicy(true).onLevel(0x06), "06 when nothing was lowered changes nothing");
        // A very short utterance: 01 and one more frame, no end level. The silence timeout gives it back.
        p = new ConversationAwarenessPolicy(true);
        p.onLevel(0x01);
        p.onLevel(0x02);
        is(p.onSilence(), "silence while lowered gives the volume back", Action.RESTORE);
        is(p.onSilence(), "a second silence changes nothing");
        is(new ConversationAwarenessPolicy(true).onSilence(), "silence when nothing was lowered");
        p = new ConversationAwarenessPolicy(true);
        p.onLevel(0x01);
        p.onLevel(0x04);
        is(p.onSilence(), "silence after the end changes nothing");
        is(p.onLevel(0x01), "a new conversation after a silence restore lowers again", Action.DUCK);

        for (int bad : new int[] {-1, 256}) {
            boolean threw = false;
            try {
                new ConversationAwarenessPolicy(true).onLevel(bad);
            } catch (IllegalArgumentException e) {
                threw = true;
            }
            check(threw, "level " + bad + " is refused");
        }
        System.out.println("ConversationAwarenessCheck OK");
    }
}
