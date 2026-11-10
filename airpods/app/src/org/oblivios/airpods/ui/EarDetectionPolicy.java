// SPDX-License-Identifier: Apache-2.0
package org.oblivios.airpods.ui;

import java.util.ArrayList;
import java.util.List;

/**
 * What the phone does when the AirPods report where the pods are (ADR-125). The rules are Apple's
 * published ones for Automatic Ear Detection: one pod out pauses and putting it back resumes (only
 * what we paused); both out stops playback, does not resume by itself and moves the audio to the
 * phone; with the switch off none of it happens. A pod in the case counts as out; the caller passes
 * the number of pods in the ears (0 to 2). The first report of a session is only a baseline: it
 * pauses and resumes nothing, and routes to the phone if no pod is in the ear.
 *
 * <p>Pure: it returns actions and executes none. The executor reports back with {@link
 * #notePaused()} when a {@link Action#PAUSE_RESUMABLE} really paused something. One lock; every
 * returned list is immutable.
 */
public final class EarDetectionPolicy {
    public enum Action {
        /** Pause if something is playing; remember that we did, so a later resume is ours. */
        PAUSE_RESUMABLE,
        /** Pause if something is playing; never resumed by us. */
        PAUSE_FINAL,
        /** Resume, if nothing else is playing. Only returned for a pause of ours. */
        RESUME,
        /** Take the AirPods out of the active audio slots. */
        ROUTE_AWAY,
        /** Put the AirPods back into the slots we took them out of. */
        ROUTE_BACK,
        /**
         * Put them back at the end of a session, but only if the link is still up after a short wait.
         * A session also ends because the link is dropping (the pods went into the case): putting
         * the AirPods back then moves the audio to a dying link and the player pauses on
         * ACTION_AUDIO_BECOMING_NOISY (measured 2026-10-11). The Bluetooth stack forgets the route
         * itself when the audio profiles disconnect.
         */
        ROUTE_BACK_IF_LINKED
    }

    private static final int UNKNOWN = -1;

    private boolean enabled;
    private int inEar = UNKNOWN;
    private boolean pausedByUs;
    private boolean routedAway;
    /** "Play sound" is running: the AirPods stay in the audio slots, whatever the pods report. */
    private boolean finding;

    public EarDetectionPolicy(boolean enabled) {
        this.enabled = enabled;
    }

    /** The user turned the switch on or off. */
    public synchronized List<Action> setEnabled(boolean on) {
        if (on == enabled) return List.of();
        enabled = on;
        if (!on) {
            pausedByUs = false;
            return routeBack();
        }
        return inEar == 0 ? routeAway() : List.of();
    }

    /** The AirPods reported how many pods are in the ears (0 to 2). */
    public synchronized List<Action> onEars(int count) {
        if (count < 0 || count > 2) throw new IllegalArgumentException("pods in ear: " + count);
        int before = inEar;
        inEar = count;
        if (!enabled) return List.of();
        if (before == UNKNOWN) return count == 0 ? routeAway() : List.of();
        if (before >= 1 && count == 0) {
            pausedByUs = false;
            List<Action> actions = new ArrayList<>();
            actions.add(Action.PAUSE_FINAL);
            actions.addAll(routeAway());
            return List.copyOf(actions);
        }
        if (before == 2 && count == 1) return List.of(Action.PAUSE_RESUMABLE);
        if (before == 0 && count >= 1) return routeBack();
        if (before == 1 && count == 2 && pausedByUs) {
            pausedByUs = false;
            return List.of(Action.RESUME);
        }
        return List.of();
    }

    /**
     * The AirPods became the active A2DP or hands-free device. The session opens before the audio profiles
     * connect, so a {@link Action#ROUTE_AWAY} of the first report can find nothing active to remove and the
     * stack then makes the pods active anyway (measured 2026-10-16: pods taken out of the case and left out
     * of the ears played the music). With no pod in an ear the route goes away again, whatever {@code
     * routedAway} says: the stack removes only what is active, so a repeat is harmless.
     */
    public synchronized List<Action> onAudioActive() {
        if (!enabled || finding || inEar != 0) return List.of();
        routedAway = true;
        return List.of(Action.ROUTE_AWAY);
    }

    /** A {@link Action#PAUSE_RESUMABLE} really paused something. */
    public synchronized void notePaused() {
        pausedByUs = true;
    }

    /** The session ended: restore a route we removed, if the link is still up. */
    public synchronized List<Action> end() {
        pausedByUs = false;
        finding = false;
        if (!routedAway) return List.of();
        routedAway = false;
        return List.of(Action.ROUTE_BACK_IF_LINKED);
    }

    /**
     * "Play sound" starts (ADR-134): the AirPods must be in the audio slots for the tone to reach them, so a
     * route we removed is put back, and no route is removed until {@link #endFind()}.
     */
    public synchronized List<Action> startFind() {
        finding = true;
        return routeBack();
    }

    /** "Play sound" ended: the route goes away again if the switch is on and no pod is in an ear. */
    public synchronized List<Action> endFind() {
        finding = false;
        return enabled && inEar == 0 ? routeAway() : List.of();
    }

    private List<Action> routeAway() {
        if (routedAway || finding) return List.of();
        routedAway = true;
        return List.of(Action.ROUTE_AWAY);
    }

    private List<Action> routeBack() {
        if (!routedAway) return List.of();
        routedAway = false;
        return List.of(Action.ROUTE_BACK);
    }
}
