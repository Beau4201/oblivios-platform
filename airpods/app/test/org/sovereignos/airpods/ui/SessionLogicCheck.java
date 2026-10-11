// SPDX-License-Identifier: Apache-2.0
package org.sovereignos.airpods.ui;

import java.util.List;
import org.sovereignos.airpods.ui.AirPodsUiState.Feature;
import org.sovereignos.airpods.validation.AapCodec;
import org.sovereignos.airpods.validation.ListeningModeState.ListeningMode;

/** Host-runnable check: javac + java -ea with airpods-core.jar on the class path. */
public final class SessionLogicCheck {
    private static final String ADDR = "AA:BB:CC:DD:EE:FF";

    private static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError(what);
    }

    private static byte[] hex(String s) {
        String t = s.replace(" ", "");
        byte[] out = new byte[t.length() / 2];
        for (int i = 0; i < out.length; i++) out[i] = (byte) Integer.parseInt(t.substring(2 * i, 2 * i + 2), 16);
        return out;
    }

    private static AirPodsUiState feed(AirPodsUiState s, String frame) {
        return SessionReducer.apply(s, AapCodec.decode(hex(frame)));
    }

    public static void main(String[] args) {
        AirPodsUiState s0 = SessionReducer.connected(ADDR, true);
        check(s0.isConnected() && s0.supported.equals(java.util.Set.of(Feature.EAR_DETECTION, Feature.ALL_SETTINGS_PAGE, Feature.PRESS_AND_HOLD, Feature.RENAME))
                && s0.earDetection && s0.leftPercent == -1 && s0.listeningMode == null,
                "connected offers only the controls that need no report, with the kept ear detection value");
        check(!SessionReducer.connected(ADDR, false).earDetection, "kept ear detection value is used");

        // battery: left 100 discharging, right 99 charging, case 17 discharging (documented sample)
        AirPodsUiState s1 = feed(s0, "04 00 04 00 04 00 03 04 01 64 02 01 02 01 63 01 01 08 01 11 02 01");
        check(s1.leftPercent == 100 && s1.rightPercent == 99 && s1.casePercent == 17, "percents");
        check(!s1.leftCharging && s1.rightCharging && !s1.caseCharging, "charging");
        // a frame that names only the case leaves the pods alone
        AirPodsUiState s2 = feed(s1, "04 00 04 00 04 00 01 08 01 12 01 01");
        check(s2.leftPercent == 100 && s2.rightPercent == 99 && s2.casePercent == 18 && s2.caseCharging, "partial battery");
        // a disconnected pod has no level and is not charging
        AirPodsUiState s3 = feed(s2, "04 00 04 00 04 00 01 02 01 FF 04 01");
        check(s3.rightPercent == -1 && !s3.rightCharging && s3.leftPercent == 100, "disconnected pod");
        // The case out of reach (level 0, status 4 while the pods are in the ears) keeps its last value; the next report replaces it.
        AirPodsUiState s4 = feed(s2, "04 00 04 00 04 00 01 08 01 00 04 01");
        check(s4.casePercent == 18 && s4.caseCharging && s4.leftPercent == 100, "an unreachable case keeps its last value");
        AirPodsUiState s5 = feed(s4, "04 00 04 00 04 00 01 08 01 11 02 01");
        check(s5.casePercent == 17 && !s5.caseCharging, "the next report of the case replaces it");
        // A new session starts from the stored case value and the owner's lowered-volume choice.
        AirPodsUiState seeded = SessionReducer.connected(ADDR, new SessionSeed(true, 35, 42, true));
        check(seeded.casePercent == 42 && seeded.caseCharging && seeded.duckPercent == 35 && seeded.leftPercent == -1 && seeded.earDetection,
                "seeded session");
        check(feed(seeded, "04 00 04 00 04 00 01 08 01 00 04 01").casePercent == 42, "an unreachable case keeps the seeded value");
        check(SessionReducer.connected(ADDR, true).casePercent == -1 && SessionReducer.connected(ADDR, true).duckPercent == 50, "an unseeded session has no case and the default lowered volume");

        // the listening mode and conversation awareness appear with the first report of each
        check(!s1.supported.contains(Feature.LISTENING_MODE), "no mode before a report");
        check(!s1.supported.contains(Feature.CONVERSATION_AWARENESS), "no conversation switch before a report");
        AirPodsUiState m = feed(s1, "04 00 04 00 09 00 0D 03 00 00 00");
        check(m.listeningMode == ListeningMode.TRANSPARENCY && m.supported.contains(Feature.LISTENING_MODE), "mode report");
        AirPodsUiState off = feed(m, "04 00 04 00 09 00 0D 01 00 00 00");
        check(off.listeningMode == ListeningMode.OFF, "off");
        AirPodsUiState ad = feed(m, "04 00 04 00 09 00 0D 04 00 00 00");
        check(ad.listeningMode == ListeningMode.ADAPTIVE && ad.supported.contains(Feature.LISTENING_MODE)
                && ad.supported.contains(Feature.ADAPTIVE_MODE), "adaptive is shown and the button is offered with it");
        check(ListeningModeToggles.modes(ad).equals(List.of(ListeningMode.OFF, ListeningMode.TRANSPARENCY, ListeningMode.ADAPTIVE, ListeningMode.ANC)),
                "four buttons in the iPhone's order once adaptive is reported");
        AirPodsUiState strength = feed(m, "04 00 04 00 09 00 2E 32 00 00 00");
        check(strength.supported.contains(Feature.ADAPTIVE_MODE) && strength.listeningMode == ListeningMode.TRANSPARENCY,
                "the adaptive strength report offers the button without changing the mode");
        check(feed(strength, "04 00 04 00 09 00 0D 04 00 00 00").listeningMode == ListeningMode.ADAPTIVE, "adaptive after the offer");
        check(feed(feed(ad, "04 00 04 00 09 00 0D 02 00 00 00"), "04 00 04 00 09 00 0D 04 00 00 00").listeningMode == ListeningMode.ADAPTIVE,
                "adaptive after noise cancel and back");
        AirPodsUiState e = feed(m, "04 00 04 00 09 00 0A 01 00 00 00");
        check(e.earDetection && e.supported.contains(Feature.EAR_DETECTION), "ear detection on");
        check(!feed(e, "04 00 04 00 09 00 0A 02 00 00 00").earDetection, "ear detection off");
        AirPodsUiState c = feed(e, "04 00 04 00 09 00 28 01 00 00 00");
        check(c.conversationAwareness && c.supported.contains(Feature.CONVERSATION_AWARENESS), "conversation on");
        check(!feed(c, "04 00 04 00 09 00 28 02 00 00 00").conversationAwareness, "conversation off");

        // three buttons in the design's order once the device has reported a mode
        check(ListeningModeToggles.modes(m).equals(List.of(ListeningMode.OFF, ListeningMode.TRANSPARENCY, ListeningMode.ANC)),
                "off, transparency, noise cancel");

        // nothing else changes the UI, and nothing changes a disconnected state
        check(feed(c, "04 00 04 00 06 00 00 02") == c, "ear state is not shown");
        check(feed(c, "04 00 04 00 1D 00 00 02 D5 00") == c, "unrecognised frame");
        check(feed(AirPodsUiState.DISCONNECTED, "04 00 04 00 09 00 0D 02 00 00 00") == AirPodsUiState.DISCONNECTED, "ignored when disconnected");

        // the page of a complete session offers all three controls, a disconnected one none
        List<NativePageLayout.Item> full = NativePageLayout.items(c);
        check(kinds(full).contains(NativePageLayout.Kind.LISTENING_TOGGLE)
                && kinds(full).contains(NativePageLayout.Kind.EAR_DETECTION_SWITCH)
                && kinds(full).contains(NativePageLayout.Kind.CONVERSATION_SWITCH), "page of a session");
        List<NativePageLayout.Kind> none = kinds(NativePageLayout.items(AirPodsUiState.DISCONNECTED));
        check(!none.contains(NativePageLayout.Kind.LISTENING_TOGGLE)
                && !none.contains(NativePageLayout.Kind.EAR_DETECTION_SWITCH)
                && !none.contains(NativePageLayout.Kind.CONVERSATION_SWITCH), "page of a disconnected pair has no controls");

        // recognition
        check(AirPodsRecognition.matches(List.of("0000110b-0000-1000-8000-00805f9b34fb", "74EC2172-0BAD-4D01-8F77-997B2BE0722A")), "uuid, any case");
        check(!AirPodsRecognition.matches(List.of("0000110b-0000-1000-8000-00805f9b34fb")), "no uuid");
        check(!AirPodsRecognition.matches(List.of()) && !AirPodsRecognition.matches(null), "empty and null");

        // backoff
        long[] want = {2_000, 4_000, 8_000, 16_000, 32_000, 60_000, 60_000};
        for (int i = 0; i < want.length; i++) check(Backoff.delayMs(i) == want[i], "backoff " + i);
        check(Backoff.delayMs(1_000_000) == Backoff.MAX_MS, "backoff never overflows");
        try {
            Backoff.delayMs(-1);
            throw new AssertionError("accepted a negative attempt");
        } catch (IllegalArgumentException expected) {
            // ok
        }
        System.out.println("SessionLogicCheck: all checks passed");
    }

    private static List<NativePageLayout.Kind> kinds(List<NativePageLayout.Item> items) {
        return items.stream().map(i -> i.kind).toList();
    }
}
