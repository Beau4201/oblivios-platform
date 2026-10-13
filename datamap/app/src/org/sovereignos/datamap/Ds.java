// SPDX-License-Identifier: Apache-2.0
package org.sovereignos.datamap;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;
import android.view.animation.PathInterpolator;
import android.widget.TextView;

/**
 * The parts every screen is made of, in the numbers of the design page (ADR-160): dp and colours, the type scale
 * with its line heights and weights, and the boxes with their border and radius. The colours are the resources
 * that tools/extract-tokens.py writes from the page.
 */
final class Ds {
    static final int REGULAR = 400;
    static final int MEDIUM = 500;
    static final int SEMIBOLD = 600;
    static final int BOLD = 700;

    /** The curve of the page: cubic-bezier(.2, 0, 0, 1). */
    static final PathInterpolator STANDARD = new PathInterpolator(0.2f, 0f, 0f, 1f);

    final Context ctx;
    private final float mDensity;

    Ds(Context context) {
        ctx = context;
        mDensity = context.getResources().getDisplayMetrics().density;
    }

    int dp(float v) {
        return Math.round(v * mDensity);
    }

    float px(float dp) {
        return dp * mDensity;
    }

    int color(int res) {
        return ctx.getColor(res);
    }

    boolean light() {
        return (ctx.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                != Configuration.UI_MODE_NIGHT_YES;
    }

    /** Whether the user asked for no animation (the system setting): then nothing moves, it jumps. */
    boolean calm() {
        return !android.animation.ValueAnimator.areAnimatorsEnabled();
    }

    static Typeface face(int weight) {
        return Typeface.create(Typeface.DEFAULT, weight, false);
    }

    /** {@code size} and {@code line} in sp, as the page's {@code font: weight size/line}. */
    TextView text(CharSequence s, float size, int weight, int colorRes, float line) {
        TextView t = new TextView(ctx);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, size);
        t.setTypeface(face(weight));
        t.setTextColor(color(colorRes));
        t.setLineHeight(TypedValue.COMPLEX_UNIT_SP, line);
        t.setIncludeFontPadding(false);
        return t;
    }

    /** A rounded box with a 1 dp border; {@code strokeRes} 0 for none. */
    GradientDrawable box(int fillRes, float radiusDp, int strokeRes) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fillRes == 0 ? Color.TRANSPARENT : color(fillRes));
        g.setCornerRadius(px(radiusDp));
        if (strokeRes != 0) {
            g.setStroke(Math.max(1, dp(1)), color(strokeRes));
        }
        return g;
    }

    /** A box with only its top corners rounded: the sheets. */
    GradientDrawable sheetBox(float radiusDp) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {color(R.color.at_sheet_top), color(R.color.at_sheet_bottom)});
        float r = px(radiusDp);
        g.setCornerRadii(new float[] {r, r, r, r, 0, 0, 0, 0});
        g.setStroke(Math.max(1, dp(1)), color(R.color.at_bd));
        return g;
    }

    /** The fill of a row that is pressed: the pill colour, as the page's {@code :active}. */
    StateListDrawable pressable() {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[] {android.R.attr.state_pressed}, box(R.color.at_pill, 0, 0));
        s.addState(new int[0], new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        return s;
    }

    static int withAlpha(int color, float alpha) {
        return (Math.round(Color.alpha(color) * alpha) << 24) | (color & 0x00FFFFFF);
    }
}
