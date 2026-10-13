// SPDX-License-Identifier: Apache-2.0
package org.sovereignos.datamap;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.sovereignos.datamap.model.AtlasData;
import org.sovereignos.datamap.model.KeepFor;

/**
 * The Settings tab of the design page (ADR-160): the same cards, rows and headings as the other tabs. Recording (Collect,
 * History and, under it, Keep for), Your data (delete), About Atlas (two rows that open in place) and a note that
 * everything stays on the phone.
 */
final class SettingsPage {
    interface Actions {
        void setCollecting(boolean collecting);

        void setHistory(boolean enabled, int days);

        void askDelete();
    }

    /** Which of the two About rows are open; kept by the activity so that a refresh does not close them. */
    static final class Open {
        boolean how;
        boolean cannot;
    }

    private final Widgets w;
    private final Ds ds;
    private final Context ctx;
    private final Actions actions;
    private final Open open;

    SettingsPage(Widgets widgets, Actions actions, Open open) {
        this.w = widgets;
        this.ds = widgets.ds;
        this.ctx = widgets.ds.ctx;
        this.actions = actions;
        this.open = open;
    }

    /** {@code .itile}: a 38 dp tile with an icon in it. */
    private View tile(int iconRes, boolean red) {
        FrameLayout f = new FrameLayout(ctx);
        f.setBackground(ds.box(R.color.at_pill, 11, 0));
        f.addView(w.icon(iconRes, 22, red ? R.color.at_err : R.color.at_on), new FrameLayout.LayoutParams(ds.dp(22),
                ds.dp(22), Gravity.CENTER));
        f.setLayoutParams(new LinearLayout.LayoutParams(ds.dp(38), ds.dp(38)));
        return f;
    }

    View build(AtlasData data) {
        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(w.title(ctx.getString(R.string.tab_settings), null));

        // Recording
        Widgets.Card rec = w.card();
        final Controls.Switch collect = new Controls.Switch(ds, data.collecting);
        collect.setOnCheckedChangeListener((b, on) -> actions.setCollecting(on));
        rec.add(w.row(tile(R.drawable.ic_eye, false), ctx.getString(R.string.settings_collect),
                new Object[] {ctx.getString(R.string.settings_collect_summary)}, collect, true, null));
        final Controls.Switch history = new Controls.Switch(ds, data.historyOn);
        rec.add(w.row(tile(R.drawable.ic_hist, false), ctx.getString(R.string.settings_history),
                new Object[] {ctx.getString(R.string.settings_history_summary)}, history, true, null));
        final int stored = data.historyDays;
        int[] choices = KeepFor.choices(stored);
        String[] labels = new String[choices.length];
        for (int i = 0; i < choices.length; i++) {
            labels[i] = ctx.getResources().getQuantityString(R.plurals.n_days, choices[i], choices[i]);
        }
        final int[] days = choices;
        Controls.Segmented seg = new Controls.Segmented(ds, labels, KeepFor.selected(stored),
                i -> actions.setHistory(true, days[i]));
        LinearLayout keep = new LinearLayout(ctx);
        keep.setOrientation(LinearLayout.VERTICAL);
        keep.setGravity(Gravity.CENTER_HORIZONTAL);
        keep.setPadding(0, 0, 0, ds.dp(16));
        View rule = new View(ctx);
        rule.setBackgroundColor(ds.color(R.color.at_div));
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                Math.max(1, ds.dp(1)));
        rp.setMargins(ds.dp(12), 0, ds.dp(12), 0);
        keep.addView(rule, rp);
        TextView label = ds.text(ctx.getString(R.string.settings_keep_for), 13, Ds.MEDIUM, R.color.at_onv, 18);
        label.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = ds.dp(12);
        lp.bottomMargin = ds.dp(10);
        keep.addView(label, lp);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        sp.setMargins(ds.dp(12), 0, ds.dp(12), 0);
        keep.addView(seg, sp);
        final Controls.Collapse keepFor = new Controls.Collapse(ds, keep, data.historyOn);
        rec.add(keepFor);
        history.setOnCheckedChangeListener((b, on) -> {
            keepFor.setOpen(on, true);
            actions.setHistory(on, stored);
        });
        col.addView(rec);

        // Your data
        col.addView(w.h3(ctx.getString(R.string.settings_your_data)));
        Widgets.Card mine = w.card();
        Widgets.Row delete = w.row(tile(R.drawable.ic_trash, true), ctx.getString(R.string.settings_delete),
                new Object[] {ctx.getString(R.string.settings_delete_summary)}, w.chevron(), true,
                v -> actions.askDelete());
        delete.titleView.setTextColor(ds.color(R.color.at_err));
        mine.add(delete);
        col.addView(mine);

        // About Atlas
        col.addView(w.h3(ctx.getString(R.string.settings_about)));
        Widgets.Card about = w.card();
        about.add(opening(R.drawable.ic_lock, R.string.settings_how, open.how, on -> open.how = on,
                new CharSequence[] {ctx.getString(R.string.settings_how_text), ctx.getString(R.string.data_sources)}));
        about.add(opening(R.drawable.ic_info, R.string.settings_cannot_see, open.cannot, on -> open.cannot = on,
                new CharSequence[] {bullet(R.string.cannot_1), bullet(R.string.cannot_2), bullet(R.string.cannot_3),
                    bullet(R.string.cannot_4), bullet(R.string.cannot_5)}));
        col.addView(about);
        col.addView(w.info(ctx.getString(R.string.settings_private), R.drawable.ic_shield, 0, null));
        return col;
    }

    private CharSequence bullet(int res) {
        return "•  " + ctx.getString(res);
    }

    private interface Remember {
        void on(boolean open);
    }

    /** {@code .x}: a row that opens a text under it in place; its chevron turns a quarter. */
    private View opening(int iconRes, int titleRes, boolean isOpen, final Remember remember, CharSequence[] paragraphs) {
        LinearLayout x = new LinearLayout(ctx);
        x.setOrientation(LinearLayout.VERTICAL);
        LinearLayout body = new LinearLayout(ctx);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(ds.dp(64), ds.dp(2), ds.dp(14), ds.dp(14));
        for (int i = 0; i < paragraphs.length; i++) {
            TextView t = ds.text(paragraphs[i], 14, Ds.REGULAR, R.color.at_onv, 20);
            t.setPadding(0, ds.dp(paragraphs.length > 2 ? 3 : i == 0 ? 0 : 8), 0, ds.dp(paragraphs.length > 2 ? 3 : 0));
            body.addView(t, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        final Controls.Collapse collapse = new Controls.Collapse(ds, body, isOpen);
        final View chev = w.chevron();
        chev.setRotation(isOpen ? 90 : 0);
        Widgets.Row row = w.row(tile(iconRes, false), ctx.getString(titleRes), null, chev, true, null);
        row.setClickable(true);
        row.setFocusable(true);
        row.setBackground(ds.pressable());
        row.setOnClickListener(v -> {
            boolean now = !collapse.isOpen();
            collapse.setOpen(now, true);
            remember.on(now);
            if (ds.calm()) {
                chev.setRotation(now ? 90 : 0);
            } else {
                chev.animate().rotation(now ? 90 : 0).setDuration(300).setInterpolator(Ds.STANDARD).start();
            }
        });
        x.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        x.addView(collapse, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return x;
    }
}
