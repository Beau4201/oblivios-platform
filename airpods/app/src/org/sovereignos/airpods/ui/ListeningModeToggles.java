package org.sovereignos.airpods.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.sovereignos.airpods.ui.AirPodsUiState.Feature;
import org.sovereignos.airpods.validation.ListeningModeState.ListeningMode;

/** The buttons of the listening-mode toggle, in the order of the approved design. */
public final class ListeningModeToggles {
    private ListeningModeToggles() {}

    public static List<ListeningMode> modes(AirPodsUiState s) {
        List<ListeningMode> m = new ArrayList<>();
        m.add(ListeningMode.OFF);
        m.add(ListeningMode.TRANSPARENCY);
        m.add(ListeningMode.ANC);
        if (s.supported.contains(Feature.ADAPTIVE_MODE)) m.add(ListeningMode.ADAPTIVE);
        return Collections.unmodifiableList(m);
    }

    public static int indexOf(List<ListeningMode> modes, ListeningMode mode) {
        return mode == null ? -1 : modes.indexOf(mode);
    }

    public static ListeningMode modeAt(List<ListeningMode> modes, int index) {
        return index >= 0 && index < modes.size() ? modes.get(index) : null;
    }
}
