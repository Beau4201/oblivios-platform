package android.os;

/**
 * Host check of VibratorCoarse, the rounding of VibratorInfo#withCoarseCalibration. Run by
 * run-vibrator-test.sh (javac and java, no Android).
 */
public final class VibratorCoarseTest {
    private static void check(boolean condition, String what) {
        if (!condition) {
            throw new AssertionError(what);
        }
    }

    private static void same(float expected, float actual, String what) {
        check(Float.floatToIntBits(expected) == Float.floatToIntBits(actual),
                what + ": expected " + expected + " got " + actual);
    }

    public static void main(String[] args) {
        // the values measured on lynx: 163.886 Hz and Q 3.865 become 160 Hz and 4.0
        same(160f, VibratorCoarse.coarse(163.88568f, 10f), "resonant frequency");
        same(4.0f, VibratorCoarse.coarse(3.8646393f, 0.5f), "Q factor");
        same(170f, VibratorCoarse.coarse(165f, 10f), "half rounds up");
        same(160f, VibratorCoarse.coarse(164.9f, 10f), "below the half rounds down");
        same(0f, VibratorCoarse.coarse(0f, 10f), "zero");
        same(-10f, VibratorCoarse.coarse(-14f, 10f), "negative value");
        same(0.5f, VibratorCoarse.coarse(0.74f, 0.5f), "the step is the argument");
        // NaN and the infinities pass through; Math.round(Infinity) would give 2^31 - 1
        same(Float.NaN, VibratorCoarse.coarse(Float.NaN, 10f), "NaN");
        same(Float.POSITIVE_INFINITY, VibratorCoarse.coarse(Float.POSITIVE_INFINITY, 10f), "+inf");
        same(Float.NEGATIVE_INFINITY, VibratorCoarse.coarse(Float.NEGATIVE_INFINITY, 10f), "-inf");
        // a resonant frequency that rounds to zero or less is not valid in a FrequencyProfile
        same(160f, VibratorCoarse.resonant(163.9f), "resonant keeps a positive value");
        same(Float.NaN, VibratorCoarse.resonant(4f), "rounds to 0");
        same(Float.NaN, VibratorCoarse.resonant(0f), "zero");
        same(Float.NaN, VibratorCoarse.resonant(-50f), "negative");
        same(Float.NaN, VibratorCoarse.resonant(Float.NaN), "NaN");
        same(10f, VibratorCoarse.resonant(5f), "5 Hz rounds to 10");
        System.out.println("ok");
    }
}
