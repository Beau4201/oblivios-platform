// SPDX-License-Identifier: Apache-2.0
package org.sovereignos.datamap;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.sovereignos.datamap.model.Initials;

/** Builds the small parts of the screens (the roles and sizes come from the design reference page). */
final class Ui {
    final Context ctx;
    private final float mDensity;

    Ui(Context context) {
        ctx = context;
        mDensity = context.getResources().getDisplayMetrics().density;
    }

    int dp(float v) {
        return Math.round(v * mDensity);
    }

    int color(int res) {
        return ctx.getColor(res);
    }

    TextView text(CharSequence s, float sp, boolean medium, int colorRes) {
        TextView t = new TextView(ctx);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color(colorRes));
        t.setTypeface(medium ? Typeface.create("sans-serif-medium", Typeface.NORMAL) : Typeface.DEFAULT);
        return t;
    }

    GradientDrawable shape(int fillRes, float topDp, float bottomDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color(fillRes));
        float t = dp(topDp);
        float b = dp(bottomDp);
        g.setCornerRadii(new float[] {t, t, t, t, b, b, b, b});
        return g;
    }

    View space(int dpHeight) {
        View v = new View(ctx);
        v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(dpHeight)));
        return v;
    }

    /** A section label in the primary colour. */
    TextView label(CharSequence s) {
        TextView t = text(s, 14, true, R.color.dm_pri);
        t.setPadding(dp(24), dp(22), dp(24), dp(8));
        return t;
    }

    /** A paragraph of explanation. */
    TextView para(CharSequence s) {
        TextView t = text(s, 14, false, R.color.dm_onv);
        t.setLineSpacing(0, 1.15f);
        t.setPadding(dp(24), dp(4), dp(24), dp(4));
        return t;
    }

    TextView note(CharSequence s) {
        TextView t = text(s, 12, false, R.color.dm_ol);
        t.setPadding(dp(24), dp(14), dp(24), dp(4));
        return t;
    }

    /** A round tile with the initials of a company or an app that has no icon. */
    View initialsTile(String name, boolean round, int sizeDp) {
        TextView t = text(Initials.of(name), round ? (sizeDp >= 48 ? 22 : 16) : (sizeDp >= 48 ? 20 : 15), true,
                round ? R.color.dm_onpri : R.color.dm_icfg);
        t.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable();
        g.setColor(color(round ? R.color.dm_pri : R.color.dm_icbg));
        g.setCornerRadius(round ? dp(sizeDp) : dp(sizeDp * 0.3f));
        t.setBackground(g);
        t.setLayoutParams(new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)));
        return t;
    }

    /** The icon of an app, or its initials. */
    View appTile(Apps apps, String pkg, int sizeDp) {
        Drawable icon = apps.icon(pkg);
        if (icon == null) {
            return initialsTile(apps.label(pkg), false, sizeDp);
        }
        ImageView v = new ImageView(ctx);
        v.setImageDrawable(icon);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)));
        return v;
    }

    /** A company: its logo on a round light backing, or its initials when no logo is shipped for it (ADR-159). */
    View companyTile(String ownerId, String name, int sizeDp) {
        Drawable logo = Logos.of(ctx, ownerId);
        if (logo == null) {
            return initialsTile(name, true, sizeDp);
        }
        ImageView v = new ImageView(ctx);
        v.setImageDrawable(logo);
        v.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int pad = dp(sizeDp * 0.22f);
        v.setPadding(pad, pad, pad, pad);
        GradientDrawable backing = new GradientDrawable();
        backing.setShape(GradientDrawable.OVAL);
        backing.setColor(color(R.color.dm_logo_bg));
        v.setBackground(backing);
        v.setContentDescription(name);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)));
        return v;
    }

    ImageView flag(String countryCode) {
        Drawable d = Flags.of(ctx, countryCode);
        if (d == null) {
            return null;
        }
        ImageView v = new ImageView(ctx);
        v.setImageDrawable(d);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(20), dp(15)));
        return v;
    }

    /** A vertical group of rows whose first row has rounded top corners and whose last has rounded bottom corners. */
    static final class Group {
        private final Ui mUi;
        final LinearLayout view;

        Group(Ui ui) {
            mUi = ui;
            view = new LinearLayout(ui.ctx);
            view.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(ui.dp(16), 0, ui.dp(16), 0);
            view.setLayoutParams(lp);
        }

        void add(View row) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (view.getChildCount() > 0) {
                lp.topMargin = mUi.dp(2);
            }
            row.setLayoutParams(lp);
            view.addView(row);
        }

        /** Gives the rows their corner radii; call once, after the last add. */
        Group seal() {
            int n = view.getChildCount();
            for (int i = 0; i < n; i++) {
                float top = i == 0 ? 20 : 4;
                float bottom = i == n - 1 ? 20 : 4;
                view.getChildAt(i).setBackground(mUi.shape(R.color.dm_hi, top, bottom));
            }
            return this;
        }
    }

    /**
     * One row: a leading view, a title, a summary line and an optional end text. {@code onClick} may be null.
     */
    View row(View leading, CharSequence title, CharSequence summary, CharSequence end, View.OnClickListener onClick) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(64));
        row.setPadding(dp(16), dp(10), dp(16), dp(10));
        if (leading != null) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(leading.getLayoutParams());
            lp.setMarginEnd(dp(16));
            leading.setLayoutParams(lp);
            row.addView(leading);
        }
        LinearLayout tx = new LinearLayout(ctx);
        tx.setOrientation(LinearLayout.VERTICAL);
        tx.addView(text(title, 16, true, R.color.dm_on));
        if (summary != null && summary.length() > 0) {
            TextView s = text(summary, 14, false, R.color.dm_onv);
            s.setMaxLines(2);
            s.setEllipsize(android.text.TextUtils.TruncateAt.END);
            tx.addView(s);
        }
        row.addView(tx, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (end != null && end.length() > 0) {
            TextView e = text(end, 14, false, R.color.dm_onv);
            e.setSingleLine(true);
            LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            ep.setMarginStart(dp(12));
            row.addView(e, ep);
        }
        if (onClick != null) {
            row.setOnClickListener(onClick);
            row.setClickable(true);
            row.setFocusable(true);
        }
        return row;
    }
}
