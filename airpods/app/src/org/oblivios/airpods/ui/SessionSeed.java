// SPDX-License-Identifier: Apache-2.0
package org.oblivios.airpods.ui;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import org.oblivios.airpods.validation.AirPodsControl;
import org.oblivios.airpods.validation.ListeningModeState.ListeningMode;

/**
 * What a new session starts from: the values the AirPods do not report back or that are out of
 * reach at the start. The ear detection switch, the lowered-volume choice and the press and hold
 * selection are the owner's; the case battery is the last value the case reported (-1 when it never
 * did). {@code pressHoldMask} is the last complete selection written (see {@link PressAndHoldChoices#mask}),
 * 0 when none was.
 */
public record SessionSeed(boolean earDetection, int duckPercent, int casePercent, boolean caseCharging, int pressHoldMask,
        Map<AirPodsControl, Integer> controls) {
    public SessionSeed {
        if (!DuckLevels.isValid(duckPercent)) throw new IllegalArgumentException("bad lowered volume");
        if (casePercent < -1 || casePercent > 100) throw new IllegalArgumentException("bad case percent");
        if (pressHoldMask != 0 && PressAndHoldChoices.fromMask(pressHoldMask).isEmpty()) {
            throw new IllegalArgumentException("bad press and hold mask");
        }
        Map<AirPodsControl, Integer> copy = new EnumMap<>(AirPodsControl.class);
        copy.putAll(controls);
        controls = Collections.unmodifiableMap(copy);
        for (Map.Entry<AirPodsControl, Integer> e : controls.entrySet()) {
            if (e.getValue() == null || !e.getKey().accepts(e.getValue())) {
                throw new IllegalArgumentException("bad control value");
            }
        }
    }

    /** A seed without stored control values. */
    public SessionSeed(boolean earDetection, int duckPercent, int casePercent, boolean caseCharging, int pressHoldMask) {
        this(earDetection, duckPercent, casePercent, caseCharging, pressHoldMask, Map.of());
    }

    /** A seed without a press and hold selection. */
    public SessionSeed(boolean earDetection, int duckPercent, int casePercent, boolean caseCharging) {
        this(earDetection, duckPercent, casePercent, caseCharging, 0);
    }

    public Set<ListeningMode> pressHoldModes() {
        return PressAndHoldChoices.fromMask(pressHoldMask);
    }

    /** No stored values: the AirPods' own default for ear detection, the default lowered volume, no case, nothing chosen. */
    public static SessionSeed fresh(boolean earDetection) {
        return new SessionSeed(earDetection, DuckLevels.DEFAULT, -1, false, 0, Map.of());
    }
}
