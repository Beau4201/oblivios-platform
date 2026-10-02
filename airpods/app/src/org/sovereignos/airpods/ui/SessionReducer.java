package org.sovereignos.airpods.ui;

import org.sovereignos.airpods.ui.AirPodsUiState.Feature;
import org.sovereignos.airpods.validation.AdaptiveLevelSetting;
import org.sovereignos.airpods.validation.AirPodsMessage;
import org.sovereignos.airpods.validation.BatteryStatus;
import org.sovereignos.airpods.validation.ConversationAwarenessSetting;
import org.sovereignos.airpods.validation.EarDetectionSetting;
import org.sovereignos.airpods.validation.ListeningModeState;
import org.sovereignos.airpods.validation.ListeningModeState.ListeningMode;

/**
 * Turns decoded frames into the UI state. A control is offered only once the device has reported
 * it (the feature set is evidence, not a model table), so a model without conversation awareness
 * never shows the switch. The ear detection switch is the exception: this pair never reports that
 * setting (AIR-123), so it is offered from the start with a value we keep ourselves. Adaptive mode
 * is offered once the pair reports it or reports its strength (ADR-126). Frames that do not change
 * the UI return the same instance.
 */
public final class SessionReducer {
    private SessionReducer() {}

    /**
     * A validated session before its first frame: connected, nothing known, only the ear detection
     * switch offered, with the value we last wrote ({@code earDetection}; the AirPods never report it).
     */
    public static AirPodsUiState connected(String address, boolean earDetection) {
        return new AirPodsUiState.Builder().address(address).earDetection(earDetection)
                .feature(Feature.EAR_DETECTION).build();
    }

    public static AirPodsUiState apply(AirPodsUiState s, AirPodsMessage m) {
        if (!s.isConnected()) return s;
        if (m instanceof BatteryStatus b) return battery(s, b);
        if (m instanceof ListeningModeState l) {
            // A report of adaptive mode proves the pair has it, so the button is offered with it.
            AirPodsUiState.Builder b = s.toBuilder().listeningMode(l.mode()).feature(Feature.LISTENING_MODE);
            if (l.mode() == ListeningMode.ADAPTIVE) b.feature(Feature.ADAPTIVE_MODE);
            return b.build();
        }
        if (m instanceof AdaptiveLevelSetting) {
            // Only pairs that have adaptive mode report its strength: offer the button, change nothing else.
            return s.toBuilder().feature(Feature.ADAPTIVE_MODE).build();
        }
        if (m instanceof EarDetectionSetting e) {
            return s.toBuilder().earDetection(e.enabled()).feature(Feature.EAR_DETECTION).build();
        }
        if (m instanceof ConversationAwarenessSetting c) {
            return s.toBuilder().conversationAwareness(c.enabled())
                    .feature(Feature.CONVERSATION_AWARENESS).build();
        }
        return s;
    }

    private static AirPodsUiState battery(AirPodsUiState s, BatteryStatus b) {
        int left = s.leftPercent;
        int right = s.rightPercent;
        int box = s.casePercent;
        boolean leftCharging = s.leftCharging;
        boolean rightCharging = s.rightCharging;
        boolean caseCharging = s.caseCharging;
        for (BatteryStatus.Entry e : b.entries()) {
            boolean charging = e.charge() == BatteryStatus.Charge.CHARGING;
            switch (e.component()) {
                case LEFT_EARBUD -> {
                    left = e.level();
                    leftCharging = charging;
                }
                case RIGHT_EARBUD -> {
                    right = e.level();
                    rightCharging = charging;
                }
                case CASE -> {
                    box = e.level();
                    caseCharging = charging;
                }
            }
        }
        return s.toBuilder().percents(left, right, box)
                .charging(leftCharging, rightCharging, caseCharging).build();
    }
}
