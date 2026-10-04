package org.sovereignos.airpods.ui;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.sovereignos.airpods.validation.ListeningModeState.ListeningMode;

/**
 * Press and hold: which listening modes the stem cycles through (AIR-163), at least two as on the
 * iPhone. The pair does not report the setting, so the page keeps the last complete selection it
 * wrote; a partial selection (fewer than two) is only a state of the page until it is complete.
 */
public final class PressAndHoldChoices {
    private PressAndHoldChoices() {}

    /** The modes the page offers, in the order of the listening mode buttons (Adaptive only when the pair has it). */
    public static List<ListeningMode> offered(AirPodsUiState s) {
        return ListeningModeToggles.modes(s);
    }

    /**
     * The selection after the owner ticks or unticks one mode, or null when that change is refused:
     * unticking from a complete selection would leave fewer than two.
     */
    public static Set<ListeningMode> next(Set<ListeningMode> current, ListeningMode mode, boolean on) {
        Set<ListeningMode> out = EnumSet.noneOf(ListeningMode.class);
        out.addAll(current);
        if (on) {
            out.add(mode);
        } else {
            out.remove(mode);
        }
        if (!on && complete(current) && !complete(out)) {
            return null;
        }
        return Collections.unmodifiableSet(out);
    }

    public static boolean complete(Set<ListeningMode> modes) {
        return modes.size() >= 2;
    }

    /** Off 1, Noise cancel 2, Transparency 4, Adaptive 8: the bits of the frame (AapCodec.setPressAndHoldModes). */
    public static int mask(Set<ListeningMode> modes) {
        int mask = 0;
        for (ListeningMode m : modes) {
            mask |= switch (m) {
                case OFF -> 1;
                case ANC -> 2;
                case TRANSPARENCY -> 4;
                case ADAPTIVE -> 8;
            };
        }
        return mask;
    }

    /** The modes of a stored mask; an empty set when the value is not a complete selection (reads as unknown). */
    public static Set<ListeningMode> fromMask(int mask) {
        if (mask < 0 || mask > 15) {
            return Collections.emptySet();
        }
        Set<ListeningMode> out = EnumSet.noneOf(ListeningMode.class);
        if ((mask & 1) != 0) out.add(ListeningMode.OFF);
        if ((mask & 2) != 0) out.add(ListeningMode.ANC);
        if ((mask & 4) != 0) out.add(ListeningMode.TRANSPARENCY);
        if ((mask & 8) != 0) out.add(ListeningMode.ADAPTIVE);
        return complete(out) ? Collections.unmodifiableSet(out) : Collections.emptySet();
    }
}
