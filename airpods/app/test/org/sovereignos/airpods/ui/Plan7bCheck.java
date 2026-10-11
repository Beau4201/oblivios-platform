// SPDX-License-Identifier: Apache-2.0
package org.sovereignos.airpods.ui;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.sovereignos.airpods.ui.AirPodsUiState.Feature;
import org.sovereignos.airpods.validation.AdaptiveLevelSetting;
import org.sovereignos.airpods.validation.DeviceInformation;
import org.sovereignos.airpods.validation.ListeningModeState.ListeningMode;

/** Host-runnable check of the Plan 7B rules: javac + java -ea with airpods-core.jar on the class path. */
public final class Plan7bCheck {
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

    private static Set<ListeningMode> set(ListeningMode... m) {
        Set<ListeningMode> s = EnumSet.noneOf(ListeningMode.class);
        s.addAll(List.of(m));
        return s;
    }

    public static void main(String[] args) {
        // ---- AdaptiveSteps: the pair keeps three positions (AIR-164: 20 -> 0, 40 -> 50, 70 -> 50, 100 -> 100)
        check(AdaptiveSteps.stepOf(0) == 0 && AdaptiveSteps.stepOf(20) == 0 && AdaptiveSteps.stepOf(24) == 0, "low end");
        check(AdaptiveSteps.stepOf(25) == 1 && AdaptiveSteps.stepOf(40) == 1 && AdaptiveSteps.stepOf(50) == 1
                && AdaptiveSteps.stepOf(70) == 1 && AdaptiveSteps.stepOf(74) == 1, "middle");
        check(AdaptiveSteps.stepOf(75) == 2 && AdaptiveSteps.stepOf(100) == 2, "high end");
        check(AdaptiveSteps.stepOf(-1) == -1 && AdaptiveSteps.stepOf(101) == -1, "unknown or out of range");
        check(AdaptiveSteps.percentOf(0) == 0 && AdaptiveSteps.percentOf(1) == 50 && AdaptiveSteps.percentOf(2) == 100, "positions");
        rejects(() -> AdaptiveSteps.percentOf(3), "step 3");
        rejects(() -> AdaptiveSteps.percentOf(-1), "step -1");

        // ---- DuckLevels: a slider from 10 to 90
        check(DuckLevels.MIN == 10 && DuckLevels.MAX == 90, "range");
        check(!DuckLevels.isValid(9) && DuckLevels.isValid(10) && DuckLevels.isValid(55) && DuckLevels.isValid(90) && !DuckLevels.isValid(91), "range edges");

        // ---- PressAndHoldChoices
        check(PressAndHoldChoices.next(set(), ListeningMode.ANC, true).equals(set(ListeningMode.ANC)), "first choice is kept as a partial selection");
        check(PressAndHoldChoices.next(set(ListeningMode.ANC), ListeningMode.TRANSPARENCY, true)
                .equals(set(ListeningMode.ANC, ListeningMode.TRANSPARENCY)), "second choice completes it");
        check(PressAndHoldChoices.next(set(ListeningMode.ANC, ListeningMode.TRANSPARENCY), ListeningMode.TRANSPARENCY, false) == null,
                "unticking below two is refused");
        check(PressAndHoldChoices.next(set(ListeningMode.ANC, ListeningMode.TRANSPARENCY, ListeningMode.OFF), ListeningMode.OFF, false)
                .equals(set(ListeningMode.ANC, ListeningMode.TRANSPARENCY)), "unticking from three to two is allowed");
        check(PressAndHoldChoices.next(set(ListeningMode.ANC), ListeningMode.ANC, false).isEmpty(), "a partial selection can be undone");
        check(PressAndHoldChoices.next(set(ListeningMode.ANC), ListeningMode.ANC, true).equals(set(ListeningMode.ANC)), "ticking again changes nothing");
        check(!PressAndHoldChoices.complete(set()) && !PressAndHoldChoices.complete(set(ListeningMode.OFF))
                && PressAndHoldChoices.complete(set(ListeningMode.OFF, ListeningMode.ANC)), "complete means two or more");
        check(PressAndHoldChoices.mask(set(ListeningMode.ANC, ListeningMode.TRANSPARENCY)) == 6
                && PressAndHoldChoices.mask(EnumSet.allOf(ListeningMode.class)) == 15
                && PressAndHoldChoices.mask(set(ListeningMode.OFF, ListeningMode.ADAPTIVE)) == 9, "mask bits as the codec");
        check(PressAndHoldChoices.fromMask(6).equals(set(ListeningMode.ANC, ListeningMode.TRANSPARENCY)), "mask back to modes");
        check(PressAndHoldChoices.fromMask(0).isEmpty() && PressAndHoldChoices.fromMask(1).isEmpty()
                && PressAndHoldChoices.fromMask(16).isEmpty() && PressAndHoldChoices.fromMask(-1).isEmpty(),
                "a stored value that is not a complete selection reads as unknown");

        // ---- SessionSeed
        check(new SessionSeed(true, 50, -1, false, 6).pressHoldModes().equals(set(ListeningMode.ANC, ListeningMode.TRANSPARENCY)), "seed keeps the mask");
        check(new SessionSeed(true, 50, -1, false).pressHoldModes().isEmpty() && SessionSeed.fresh(true).pressHoldModes().isEmpty(), "old constructor: nothing chosen");
        rejects(() -> new SessionSeed(true, 50, -1, false, 1), "seed with one mode");
        rejects(() -> new SessionSeed(true, 50, -1, false, 16), "seed with mask 16");
        rejects(() -> new SessionSeed(true, 50, -1, false, -1), "seed with mask -1");

        // ---- AirPodsUiState
        AirPodsUiState.Builder b = new AirPodsUiState.Builder().address(ADDR).feature(Feature.EAR_DETECTION);
        AirPodsUiState base = b.build();
        check(base.modelNumber == null && base.firmware == null && base.adaptivePercent == -1 && base.pressHoldModes.isEmpty(), "defaults");
        AirPodsUiState info = base.toBuilder().deviceInformation("A3047", "90.3431000025000000.6807").adaptivePercent(50)
                .pressHoldModes(set(ListeningMode.ANC, ListeningMode.TRANSPARENCY)).build();
        check("A3047".equals(info.modelNumber) && "90.3431000025000000.6807".equals(info.firmware) && info.adaptivePercent == 50
                && info.pressHoldModes.equals(set(ListeningMode.ANC, ListeningMode.TRANSPARENCY)), "fields");
        check(info.toBuilder().build().equals(info) && info.toBuilder().build().hashCode() == info.hashCode(), "toBuilder keeps the new fields");
        check(!info.equals(info.toBuilder().adaptivePercent(0).build()) && !info.equals(info.toBuilder().pressHoldModes(set()).build())
                && !info.equals(info.toBuilder().deviceInformation("A3048", "1").build()), "the new fields are part of equality");
        check(base.toBuilder().pressHoldModes(set(ListeningMode.OFF)).build().pressHoldModes.equals(set(ListeningMode.OFF)), "a partial selection is a valid state");
        rejects(() -> base.toBuilder().deviceInformation("B3047", "1").build(), "bad model number");
        rejects(() -> base.toBuilder().deviceInformation("A3047", "bad firmware!").build(), "bad firmware");
        rejects(() -> base.toBuilder().adaptivePercent(101).build(), "adaptive 101");
        rejects(() -> base.toBuilder().adaptivePercent(-2).build(), "adaptive -2");
        try {
            info.pressHoldModes.add(ListeningMode.OFF);
            throw new AssertionError("the selection is not modifiable");
        } catch (UnsupportedOperationException expected) {
            // unmodifiable on purpose
        }

        // ---- SessionReducer
        AirPodsUiState c = SessionReducer.connected(ADDR, new SessionSeed(true, 50, -1, false, 6));
        check(c.supported.equals(Set.of(Feature.EAR_DETECTION, Feature.ALL_SETTINGS_PAGE, Feature.PRESS_AND_HOLD, Feature.RENAME)),
                "a connected session offers the controls that need no report");
        check(c.pressHoldModes.equals(set(ListeningMode.ANC, ListeningMode.TRANSPARENCY)) && c.adaptivePercent == -1, "seeded press and hold, adaptive unknown");
        AirPodsUiState d = SessionReducer.apply(c, new DeviceInformation("A3047", "90.3431000025000000.6807"));
        check("A3047".equals(d.modelNumber) && "90.3431000025000000.6807".equals(d.firmware), "device information is shown");
        check(SessionReducer.apply(d, new DeviceInformation("A3047", "90.3431000025000000.6807")) == d
                || SessionReducer.apply(d, new DeviceInformation("A3047", "90.3431000025000000.6807")).equals(d), "the same information changes nothing");
        AirPodsUiState a = SessionReducer.apply(c, new AdaptiveLevelSetting(50));
        check(a.adaptivePercent == 50 && a.supported.contains(Feature.ADAPTIVE_MODE), "the reported strength is kept and adaptive is offered");
        check(SessionReducer.apply(AirPodsUiState.DISCONNECTED, new AdaptiveLevelSetting(50)) == AirPodsUiState.DISCONNECTED, "nothing applies to a disconnected state");
        check(NativePageLayout.items(c).stream().anyMatch(i -> i.settingId == NativePageLayout.ID_ALL_SETTINGS), "the link row is on the native page of a connected session");
        check(NativePageLayout.items(AirPodsUiState.DISCONNECTED).stream().noneMatch(i -> i.settingId == NativePageLayout.ID_ALL_SETTINGS),
                "no link without a session");
        System.out.println("Plan7bCheck passed");
    }
}
