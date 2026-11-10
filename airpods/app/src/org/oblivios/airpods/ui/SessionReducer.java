// SPDX-License-Identifier: Apache-2.0
package org.oblivios.airpods.ui;

import org.oblivios.airpods.ui.AirPodsUiState.Feature;
import org.oblivios.airpods.validation.AdaptiveLevelSetting;
import org.oblivios.airpods.validation.AirPodsMessage;
import org.oblivios.airpods.validation.BatteryStatus;
import org.oblivios.airpods.validation.CallManagementReport;
import org.oblivios.airpods.validation.ControlReport;
import org.oblivios.airpods.validation.ConversationAwarenessSetting;
import org.oblivios.airpods.validation.DeviceInformation;
import org.oblivios.airpods.validation.EarDetectionSetting;
import org.oblivios.airpods.validation.ListeningModeState;
import org.oblivios.airpods.validation.ListeningModeState.ListeningMode;

/**
 * Turns decoded frames into the UI state. A control is offered only once the device has reported
 * it (the feature set is evidence, not a model table), so a model without conversation awareness
 * never shows the switch. Four controls need no report and are offered from the start: the ear
 * detection switch (this pair never reports it, AIR-123), press and hold (never reported, AIR-163),
 * rename (a write the pair always answers by its name field, AIR-162) and the link to our own page.
 * Adaptive mode is offered once the pair reports it or reports its strength (ADR-126). Frames that
 * do not change the UI return the same instance.
 */
public final class SessionReducer {
    private SessionReducer() {}

    /**
     * A validated session before its first frame: connected, nothing known, only the controls that need
     * no report offered, with the value we last wrote for ear detection ({@code earDetection}; the AirPods
     * never report it).
     */
    public static AirPodsUiState connected(String address, boolean earDetection) {
        return connected(address, SessionSeed.fresh(earDetection));
    }

    /** A validated session before its first frame, from the stored values of the owner and the case. */
    public static AirPodsUiState connected(String address, SessionSeed seed) {
        return new AirPodsUiState.Builder().address(address).earDetection(seed.earDetection())
                .duckPercent(seed.duckPercent())
                .pressHoldModes(seed.pressHoldModes())
                .controls(seed.controls())
                .percents(-1, -1, seed.casePercent()).charging(false, false, seed.caseCharging())
                .features(Feature.EAR_DETECTION, Feature.ALL_SETTINGS_PAGE, Feature.PRESS_AND_HOLD, Feature.RENAME)
                .build();
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
        if (m instanceof AdaptiveLevelSetting a) {
            // Only pairs that have adaptive mode report its strength: offer the button and keep the value.
            return s.toBuilder().adaptivePercent(a.percent()).feature(Feature.ADAPTIVE_MODE).build();
        }
        if (m instanceof DeviceInformation d) {
            return s.toBuilder().deviceInformation(d.modelNumber(), d.firmware()).build();
        }
        if (m instanceof EarDetectionSetting e) {
            return s.toBuilder().earDetection(e.enabled()).feature(Feature.EAR_DETECTION).build();
        }
        if (m instanceof ControlReport r) {
            return s.toBuilder().control(r.control(), r.value()).build();
        }
        if (m instanceof CallManagementReport c) {
            return s.toBuilder().callManagement(c.first(), c.second()).build();
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
                    // The case is out of reach while the pods are in the ears. The owner wants its last
                    // value shown at all times (ADR-125, decision 13, ADR-128); the next report replaces it.
                    if (e.charge() != BatteryStatus.Charge.DISCONNECTED) {
                        box = e.level();
                        caseCharging = charging;
                    }
                }
            }
        }
        return s.toBuilder().percents(left, right, box)
                .charging(leftCharging, rightCharging, caseCharging).build();
    }
}
