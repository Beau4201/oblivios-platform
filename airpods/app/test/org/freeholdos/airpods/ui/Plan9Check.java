// SPDX-License-Identifier: Apache-2.0
package org.freeholdos.airpods.ui;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.freeholdos.airpods.ui.ControlRows.Group;
import org.freeholdos.airpods.ui.ControlRows.Kind;
import org.freeholdos.airpods.ui.ControlRows.Row;
import org.freeholdos.airpods.validation.AirPodsControl;
import org.freeholdos.airpods.validation.CallManagementReport;
import org.freeholdos.airpods.validation.ControlReport;
import org.freeholdos.airpods.validation.EarState;

/** Host-runnable check of the Plan 9 rules: javac + java -ea with airpods-core.jar on the class path. */
public final class Plan9Check {
    private static final String ADDR = "AA:BB:CC:DD:EE:FF";

    private static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError(what);
    }

    private static void rejects(Runnable r, String what) {
        try {
            r.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("accepted: " + what);
    }

    private static AirPodsUiState connected() {
        return SessionReducer.connected(ADDR, SessionSeed.fresh(true));
    }

    public static void main(String[] args) {
        // ---- the table: one row per control, ranges agree with the codec, groups are contiguous
        Set<AirPodsControl> seen = EnumSet.noneOf(AirPodsControl.class);
        for (Row r : ControlRows.ROWS) check(seen.add(r.control()), "one row per control: " + r.control());
        check(!seen.contains(AirPodsControl.PRESS_HOLD_DURATION) || ControlRows.of(AirPodsControl.PRESS_HOLD_DURATION).kind() == Kind.CHOICE, "choice");
        Set<Group> closed = EnumSet.noneOf(Group.class);
        Group current = null;
        for (Row r : ControlRows.ROWS) {
            if (r.group() != current) {
                check(!closed.contains(r.group()), "group is contiguous: " + r.group());
                if (current != null) closed.add(current);
                current = r.group();
            }
        }
        check(ControlRows.rows(Group.STEMS).size() == 5, "five stem rows");
        check(ControlRows.rows(Group.CASE).size() == 2, "two case rows");

        // ---- offered: a reported control only after the pair reported it, an unreported one from the start
        AirPodsUiState s = connected();
        check(!ControlRows.offered(s, AirPodsControl.PERSONALIZED_VOLUME), "reported control hidden before its report");
        check(ControlRows.offered(s, AirPodsControl.MICROPHONE), "unreported microphone offered at once");
        check(ControlRows.offered(s, AirPodsControl.CONNECT_AUTOMATICALLY), "automatic connection offered at once");
        check(ControlRows.offered(s, AirPodsControl.CASE_TONE), "case tone offered at once");
        check(!ControlRows.offered(AirPodsUiState.DISCONNECTED, AirPodsControl.MICROPHONE), "nothing offered while disconnected");
        s = SessionReducer.apply(s, new ControlReport(AirPodsControl.PERSONALIZED_VOLUME, 2));
        check(ControlRows.offered(s, AirPodsControl.PERSONALIZED_VOLUME), "offered after its report");
        check(s.controlValue(AirPodsControl.PERSONALIZED_VOLUME) == 2, "report value kept");
        check(s.controlValue(AirPodsControl.SLEEP_DETECTION) == -1, "unknown is -1");

        // ---- the reducer keeps the last report and ignores nothing it was given
        s = SessionReducer.apply(s, new ControlReport(AirPodsControl.PERSONALIZED_VOLUME, 1));
        check(s.controlValue(AirPodsControl.PERSONALIZED_VOLUME) == 1, "a later report replaces the earlier one");
        AirPodsUiState same = SessionReducer.apply(s, new ControlReport(AirPodsControl.PERSONALIZED_VOLUME, 1));
        check(same.equals(s) && same.hashCode() == s.hashCode(), "equal states are equal");
        check(!SessionReducer.apply(s, new ControlReport(AirPodsControl.CHIME_VOLUME, 50)).equals(s), "another control changes the state");
        AirPodsUiState call = SessionReducer.apply(s, new CallManagementReport(0, 3));
        check(call.callFirst == 0 && call.callSecond == 3, "call management kept");
        check(s.callFirst == -1 && s.callSecond == -1, "call management unknown by default");
        check(SessionReducer.apply(AirPodsUiState.DISCONNECTED, new ControlReport(AirPodsControl.CHIME_VOLUME, 50)) == AirPodsUiState.DISCONNECTED,
                "a disconnected state ignores reports");
        // An ear state frame must not change the state (no extra push to Settings on every ear change).
        check(SessionReducer.apply(s, new EarState(EarState.Placement.IN_EAR, EarState.Placement.OUT_OF_EAR)) == s, "ear state not shown");

        // ---- the state refuses a value outside the range of the control
        final AirPodsUiState base = s;
        rejects(() -> base.toBuilder().control(AirPodsControl.SLEEP_DETECTION, 3).build(), "sleep detection 3");
        rejects(() -> base.toBuilder().control(AirPodsControl.CHIME_VOLUME, 101).build(), "chime 101");
        rejects(() -> base.toBuilder().callManagement(0, -1).build(), "half known call management");
        rejects(() -> base.toBuilder().callManagement(256, 0).build(), "call management 256");
        check(s.toBuilder().build().equals(s), "toBuilder keeps the controls");
        check(s.controls.size() == 1, "one control known");
        boolean immutable = false;
        try {
            s.controls.put(AirPodsControl.CASE_TONE, 1);
        } catch (UnsupportedOperationException e) {
            immutable = true;
        }
        check(immutable, "the control map is read only");

        // ---- the seed carries the values last written, and they show before the first report
        SessionSeed seed = new SessionSeed(true, DuckLevels.DEFAULT, -1, false, 0,
                Map.of(AirPodsControl.MICROPHONE, 2, AirPodsControl.CASE_TONE, 1));
        AirPodsUiState seeded = SessionReducer.connected(ADDR, seed);
        check(seeded.controlValue(AirPodsControl.MICROPHONE) == 2, "seeded microphone");
        check(seeded.controlValue(AirPodsControl.CASE_TONE) == 1, "seeded case tone");
        check(SessionSeed.fresh(true).controls().isEmpty(), "fresh seed has no controls");
        check(new SessionSeed(true, DuckLevels.DEFAULT, -1, false, 0).controls().isEmpty(), "old constructor has no controls");
        rejects(() -> new SessionSeed(true, DuckLevels.DEFAULT, -1, false, 0, Map.of(AirPodsControl.MICROPHONE, 9)), "microphone 9");

        // ---- rows: switch, snapping, case tone volume
        Row sleep = ControlRows.of(AirPodsControl.SLEEP_DETECTION);
        check(ControlRows.isOn(sleep, 1) && !ControlRows.isOn(sleep, 2) && !ControlRows.isOn(sleep, -1), "switch on/off/unknown");
        check(ControlRows.toggled(sleep, 1) == 2 && ControlRows.toggled(sleep, 2) == 1 && ControlRows.toggled(sleep, -1) == 1, "toggle");
        Row gain = ControlRows.of(AirPodsControl.GAIN_SWIPE);
        check(ControlRows.toggled(gain, 1) == 0 && ControlRows.toggled(gain, 0) == 1, "gain swipe is 0 and 1");
        Row chime = ControlRows.of(AirPodsControl.CHIME_VOLUME);
        check(ControlRows.snapped(chime, 52) == 50 && ControlRows.snapped(chime, 53) == 55 && ControlRows.snapped(chime, -4) == 15
                && ControlRows.snapped(chime, 0) == 15 && ControlRows.snapped(chime, 10) == 15
                && ControlRows.snapped(chime, 400) == 100 && ControlRows.snapped(chime, 47) == 45,
                "snap to the step inside the range, never below 15 (AIR-188); a value another phone set is shown on the grid");
        check(chime.lowest() == 15 && !ControlRows.allowed(AirPodsControl.CHIME_VOLUME, 10) && !ControlRows.allowed(AirPodsControl.CHIME_VOLUME, 0)
                && ControlRows.allowed(AirPodsControl.CHIME_VOLUME, 15) && ControlRows.allowed(AirPodsControl.CHIME_VOLUME, 100)
                && !ControlRows.allowed(AirPodsControl.CHIME_VOLUME, 101), "a chime command below 15 is refused");
        check(ControlRows.allowed(AirPodsControl.CASE_TONE_VOLUME, 0) && ControlRows.allowed(AirPodsControl.SLEEP_DETECTION, 1)
                && !ControlRows.allowed(AirPodsControl.SLEEP_DETECTION, 7), "other controls keep their own range");
        check(ControlRows.visible(seeded, AirPodsControl.CASE_TONE_VOLUME), "volume visible while the tone is on");
        check(ControlRows.visible(connected(), AirPodsControl.CASE_TONE_VOLUME), "volume visible while the tone is unknown");
        AirPodsUiState off = seeded.toBuilder().control(AirPodsControl.CASE_TONE, 2).build();
        check(!ControlRows.visible(off, AirPodsControl.CASE_TONE_VOLUME), "volume hidden while the tone is off");
        check(ControlRows.visible(off, AirPodsControl.CASE_TONE), "the tone switch itself stays visible");

        // ---- Play sound
        check(FindSoundPolicy.allowed(true, 0), "allowed with both pods out");
        check(!FindSoundPolicy.allowed(true, 1) && !FindSoundPolicy.allowed(true, 2), "refused with a pod in an ear");
        check(!FindSoundPolicy.allowed(true, -1), "refused while the ear state is unknown");
        check(!FindSoundPolicy.allowed(false, 0), "refused without a connection");
        check(FindSoundPolicy.mustStop(true, 1, 1_000), "stops when a pod goes into an ear");
        check(FindSoundPolicy.mustStop(false, 0, 1_000), "stops when the link is gone");
        check(!FindSoundPolicy.mustStop(true, 0, FindSoundPolicy.MAX_DURATION_MS - 1), "runs until the time is up");
        check(FindSoundPolicy.mustStop(true, 0, FindSoundPolicy.MAX_DURATION_MS), "ends by itself");
        check(FindSoundPolicy.gainAt(0) == FindSoundPolicy.START_GAIN, "starts quiet");
        check(FindSoundPolicy.gainAt(-5) == 0f, "no gain before the start");
        // The values measured on the device (AIR-190: 0.25, 0.40, 0.64, 1.00 every 2 s, 60 s at most). A change of
        // the sound that reaches the ears is a decision of the owner and of the threat review (T24), not a tweak.
        check(FindSoundPolicy.START_GAIN == 0.25f && FindSoundPolicy.MAX_GAIN == 1.0f && FindSoundPolicy.STEP_MS == 2_000L
                && FindSoundPolicy.MAX_DURATION_MS == 60_000L && FindSoundPolicy.STEP_FACTOR == 1.6f, "the measured sound constants");
        check(Math.abs(FindSoundPolicy.gainAt(4_000L) - 0.64f) < 1e-6f && FindSoundPolicy.gainAt(6_000L) == 1.0f, "the measured steps");
        check(FindSoundPolicy.gainAt(FindSoundPolicy.STEP_MS) == FindSoundPolicy.START_GAIN * FindSoundPolicy.STEP_FACTOR, "rises by the factor after one step");
        Set<Float> steps = new HashSet<>();
        float last = 0;
        for (long t = 0; t <= FindSoundPolicy.MAX_DURATION_MS; t += 250) {
            float g = FindSoundPolicy.gainAt(t);
            check(g >= last, "never falls");
            check(g <= FindSoundPolicy.MAX_GAIN, "never above the cap");
            last = g;
            steps.add(g);
        }
        check(last == FindSoundPolicy.MAX_GAIN, "reaches the cap");
        check(steps.size() > 3, "rises in steps");
        // ---- Play sound and the audio route (EarDetectionPolicy)
        EarDetectionPolicy ep = new EarDetectionPolicy(true);
        check(ep.onEars(0).equals(java.util.List.of(EarDetectionPolicy.Action.ROUTE_AWAY)), "both out routes away");
        check(ep.startFind().equals(java.util.List.of(EarDetectionPolicy.Action.ROUTE_BACK)), "find puts the route back");
        check(ep.onEars(0).isEmpty(), "no route away while finding");
        check(ep.endFind().equals(java.util.List.of(EarDetectionPolicy.Action.ROUTE_AWAY)), "after the find the route goes away again");
        check(ep.endFind().isEmpty(), "ending twice does nothing");
        ep = new EarDetectionPolicy(true);
        ep.onEars(0);
        ep.startFind();
        ep.onEars(1);
        check(ep.endFind().isEmpty(), "a pod went into an ear: the route stays");
        ep = new EarDetectionPolicy(false);
        check(ep.startFind().isEmpty() && ep.endFind().isEmpty(), "switch off: nothing to route");
        ep = new EarDetectionPolicy(true);
        ep.onEars(0);
        ep.startFind();
        check(ep.end().isEmpty(), "ending the session while finding adds no route action");
        ep = new EarDetectionPolicy(true);
        ep.startFind();
        check(ep.onEars(0).isEmpty(), "the baseline report while finding routes nothing");
        check(ep.endFind().equals(java.util.List.of(EarDetectionPolicy.Action.ROUTE_AWAY)), "and the end of the find does");
        System.out.println("Plan9Check passed");
    }
}
