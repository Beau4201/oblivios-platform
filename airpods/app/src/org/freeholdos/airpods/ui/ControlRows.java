// SPDX-License-Identifier: Apache-2.0
package org.freeholdos.airpods.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.freeholdos.airpods.validation.AirPodsControl;

/**
 * The rows of the own page that write a control of the pair (ADR-134), in page order. A control the
 * pair reports (AIR-179) is offered once the pair has reported it, so a model without it never shows
 * the row. A control the pair never reports is offered from the start, with the value last written from
 * this phone, like press and hold: the pair gives no value back for it.
 */
public final class ControlRows {
    public enum Kind {
        /** A switch: {@code onValue} when on, {@code offValue} when off. */
        SWITCH,
        /** A list of values, shown by the label of each value. */
        CHOICE,
        /** A value from {@code min} to {@code max} in steps of {@code step}. */
        SLIDER
    }

    /** The titled groups of the own page that these rows fill. */
    public enum Group {
        SOUND,
        STEMS,
        CALLS,
        COMFORT,
        CASE
    }

    /**
     * {@code lowest} is the smallest value a slider offers and a command may carry. It is at least the
     * control's own minimum: the pair ignores chime volume writes below 15 (AIR-188, ADR-137), so the slider
     * must not offer them and the sink must not send them.
     */
    public record Row(AirPodsControl control, Kind kind, Group group, boolean reportedByPair, int onValue, int offValue,
            List<Integer> choices, int step, int lowest) {
        public Row {
            choices = List.copyOf(choices);
            if (kind == Kind.SWITCH && (!control.accepts(onValue) || !control.accepts(offValue) || onValue == offValue)) {
                throw new IllegalArgumentException("bad switch values for " + control);
            }
            if (kind == Kind.CHOICE) {
                if (choices.size() < 2) throw new IllegalArgumentException("choices for " + control);
                for (int v : choices) {
                    if (!control.accepts(v)) throw new IllegalArgumentException("choice outside the range of " + control);
                }
            }
            if (kind == Kind.SLIDER && (step < 1 || control.max() % step != 0 || control.min() % step != 0)) {
                throw new IllegalArgumentException("bad step for " + control);
            }
            if (kind == Kind.SLIDER && (lowest < control.min() || lowest > control.max() || lowest % step != 0)) {
                throw new IllegalArgumentException("bad lowest value for " + control);
            }
        }
    }

    private static Row sw(AirPodsControl c, Group g, boolean reported, int on, int off) {
        return new Row(c, Kind.SWITCH, g, reported, on, off, List.of(), 1, c.min());
    }

    private static Row choice(AirPodsControl c, Group g, boolean reported, Integer... values) {
        return new Row(c, Kind.CHOICE, g, reported, 0, 0, List.of(values), 1, c.min());
    }

    private static Row slider(AirPodsControl c, Group g, boolean reported, int step) {
        return slider(c, g, reported, step, c.min());
    }

    private static Row slider(AirPodsControl c, Group g, boolean reported, int step, int lowest) {
        return new Row(c, Kind.SLIDER, g, reported, 0, 0, List.of(), step, lowest);
    }

    /** All rows in page order; the groups are contiguous. */
    public static final List<Row> ROWS = List.of(
            sw(AirPodsControl.PERSONALIZED_VOLUME, Group.SOUND, true, 1, 2),
            slider(AirPodsControl.CHIME_VOLUME, Group.SOUND, true, 5, 15),
            choice(AirPodsControl.PRESS_SPEED, Group.STEMS, true, 0, 1, 2),
            choice(AirPodsControl.PRESS_HOLD_DURATION, Group.STEMS, true, 0, 1, 2),
            sw(AirPodsControl.SWIPE_MODE, Group.STEMS, true, 1, 2),
            choice(AirPodsControl.SWIPE_INTERVAL, Group.STEMS, true, 0, 1, 2),
            sw(AirPodsControl.GAIN_SWIPE, Group.STEMS, true, 1, 0),
            choice(AirPodsControl.MICROPHONE, Group.CALLS, false, 0, 1, 2),
            sw(AirPodsControl.SLEEP_DETECTION, Group.COMFORT, true, 1, 2),
            sw(AirPodsControl.CONNECT_AUTOMATICALLY, Group.COMFORT, false, 1, 2),
            sw(AirPodsControl.CASE_TONE, Group.CASE, false, 1, 2),
            slider(AirPodsControl.CASE_TONE_VOLUME, Group.CASE, false, 5));

    private ControlRows() {}

    public static List<Row> rows(Group group) {
        List<Row> out = new ArrayList<>();
        for (Row r : ROWS) {
            if (r.group() == group) out.add(r);
        }
        return Collections.unmodifiableList(out);
    }

    public static Row of(AirPodsControl control) {
        for (Row r : ROWS) {
            if (r.control() == control) return r;
        }
        return null;
    }

    /** True when the session can write this control and the page may show it: connected, and reported if it is a reported kind. */
    public static boolean offered(AirPodsUiState s, AirPodsControl control) {
        Row r = of(control);
        if (r == null || !s.isConnected()) return false;
        return !r.reportedByPair() || s.controls.containsKey(control);
    }

    /** The case tone volume is shown unless the case tone is switched off. */
    public static boolean visible(AirPodsUiState s, AirPodsControl control) {
        if (!offered(s, control)) return false;
        return control != AirPodsControl.CASE_TONE_VOLUME
                || s.controlValue(AirPodsControl.CASE_TONE) != of(AirPodsControl.CASE_TONE).offValue();
    }

    /** True when the switch row is on; false when off or unknown. */
    public static boolean isOn(Row row, int value) {
        return row.kind() == Kind.SWITCH && value == row.onValue();
    }

    /** The value a tap on the switch writes, given the value now shown. */
    public static int toggled(Row row, int current) {
        if (row.kind() != Kind.SWITCH) throw new IllegalArgumentException("not a switch");
        return current == row.onValue() ? row.offValue() : row.onValue();
    }

    /** The slider value closest to the request that the control accepts, a multiple of the step. */
    public static int snapped(Row row, int requested) {
        if (row.kind() != Kind.SLIDER) throw new IllegalArgumentException("not a slider");
        int clamped = Math.max(row.lowest(), Math.min(row.control().max(), requested));
        return Math.round((float) clamped / row.step()) * row.step();
    }

    /**
     * True when the value may be sent: the control accepts it and, for a slider row, it is not below the
     * row's lowest value. A control without a row (a switch or a list value) only has to be accepted.
     */
    public static boolean allowed(AirPodsControl control, int value) {
        if (!control.accepts(value)) return false;
        Row r = of(control);
        return r == null || r.kind() != Kind.SLIDER || value >= r.lowest();
    }
}
