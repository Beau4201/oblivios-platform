package org.sovereignos.airpods.ui;

/**
 * The rules of "Play sound" (ADR-134): a rising tone through the connected AirPods so that a pod that
 * lies somewhere can be heard. It is allowed only while the pair reports that no pod is in an ear, it
 * starts very quiet, rises in steps to a fixed cap and ends by itself. It is not the iPhone's Find My,
 * which runs through Apple's network and works without a connection (AIR-185).
 */
public final class FindSoundPolicy {
    /** Gain of the tone as a fraction of full scale: the first step, the cap and the rise per step. */
    public static final float START_GAIN = 0.25f;
    public static final float MAX_GAIN = 1.0f;
    public static final float STEP_FACTOR = 1.6f;
    public static final long STEP_MS = 2_000L;
    /** The tone ends by itself after this long. */
    public static final long MAX_DURATION_MS = 60_000L;

    private FindSoundPolicy() {}

    /**
     * True when a sound may start: connected, and the last ear state frame said that no pod is in an ear
     * ({@code inEarCount} 0). {@code inEarCount} is -1 before the first ear state frame, which is refused.
     */
    public static boolean allowed(boolean connected, int inEarCount) {
        return connected && inEarCount == 0;
    }

    /** True when a running sound must stop now: a pod went into an ear, the link is gone, or the time is up. */
    public static boolean mustStop(boolean connected, int inEarCount, long elapsedMs) {
        return !allowed(connected, inEarCount) || elapsedMs >= MAX_DURATION_MS;
    }

    /** The gain {@code elapsedMs} after the start: rises by {@link #STEP_FACTOR} every {@link #STEP_MS}, never above {@link #MAX_GAIN}. */
    public static float gainAt(long elapsedMs) {
        if (elapsedMs < 0) return 0f;
        long steps = elapsedMs / STEP_MS;
        float g = START_GAIN;
        for (long i = 0; i < steps && g < MAX_GAIN; i++) {
            g *= STEP_FACTOR;
        }
        return Math.min(g, MAX_GAIN);
    }
}
