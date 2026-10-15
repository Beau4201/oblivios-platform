// SPDX-License-Identifier: Apache-2.0
package org.freeholdos.airpods.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.freeholdos.airpods.ui.AirPodsUiState.Feature;
import org.freeholdos.airpods.validation.ListeningModeState.ListeningMode;

/** The buttons of the listening-mode toggle in the iPhone's order: Off, Transparency, Adaptive (when the pair has it), Noise cancel (ADR-126). */
public final class ListeningModeToggles {
    private ListeningModeToggles() {}

    public static List<ListeningMode> modes(AirPodsUiState s) {
        List<ListeningMode> m = new ArrayList<>();
        m.add(ListeningMode.OFF);
        m.add(ListeningMode.TRANSPARENCY);
        if (s.supported.contains(Feature.ADAPTIVE_MODE)) m.add(ListeningMode.ADAPTIVE);
        m.add(ListeningMode.ANC);
        return Collections.unmodifiableList(m);
    }

    public static int indexOf(List<ListeningMode> modes, ListeningMode mode) {
        return mode == null ? -1 : modes.indexOf(mode);
    }

    public static ListeningMode modeAt(List<ListeningMode> modes, int index) {
        return index >= 0 && index < modes.size() ? modes.get(index) : null;
    }
}
