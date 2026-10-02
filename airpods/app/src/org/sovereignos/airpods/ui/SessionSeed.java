package org.sovereignos.airpods.ui;

/**
 * What a new session starts from: the values the AirPods do not report back or that are out of
 * reach at the start. The ear detection switch and the lowered-volume choice are the owner's; the
 * case battery is the last value the case reported (-1 when it never did).
 */
public record SessionSeed(boolean earDetection, int duckPercent, int casePercent, boolean caseCharging) {
    public SessionSeed {
        if (!DuckLevels.isValid(duckPercent)) throw new IllegalArgumentException("bad lowered volume");
        if (casePercent < -1 || casePercent > 100) throw new IllegalArgumentException("bad case percent");
    }

    /** No stored values: the AirPods' own default for ear detection, the default lowered volume, no case. */
    public static SessionSeed fresh(boolean earDetection) {
        return new SessionSeed(earDetection, DuckLevels.DEFAULT, -1, false);
    }
}
