// SPDX-License-Identifier: Apache-2.0
package org.sovereignos.datamap;

import android.app.AlertDialog;
import android.view.Gravity;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import org.sovereignos.datamap.model.KeepFor;

/**
 * The choices of the owner: whether anything is collected, whether a history is kept on this phone and for
 * how long, and the deletion of everything collected. Each change goes to the service at once. The page has
 * no title of its own: it opens under the back arrow of the activity.
 */
final class SettingsScreen {
    /** What the screen asks of the service; implemented by the activity, which runs it off the main thread. */
    interface Actions {
        void setCollecting(boolean collecting);

        void setHistory(boolean enabled, int days);

        void clear();
    }

    private final Ui mUi;
    private final Actions mActions;

    SettingsScreen(Ui ui, Actions actions) {
        mUi = ui;
        mActions = actions;
    }

    View build(boolean collecting, boolean historyOn, int historyDays, int invalidCount) {
        final LinearLayout p = new LinearLayout(mUi.ctx);
        p.setOrientation(LinearLayout.VERTICAL);
        p.addView(mUi.space(60));

        final int[] choices = KeepFor.choices(historyDays);
        final int[] days = {choices[KeepFor.selected(historyDays)]};

        Ui.Group g = new Ui.Group(mUi);
        final Switch collect = new Switch(mUi.ctx);
        collect.setChecked(collecting);
        g.add(switchRow(R.string.collect, R.string.collect_summary, collect));

        final Switch history = new Switch(mUi.ctx);
        history.setChecked(historyOn);
        g.add(switchRow(R.string.history, R.string.history_summary, history));

        final LinearLayout keepRow = keepForRow(choices, KeepFor.selected(historyDays), days);
        keepRow.setVisibility(historyOn ? View.VISIBLE : View.GONE);
        g.add(keepRow);

        TextView clear = mUi.text(mUi.ctx.getString(R.string.clear), 16, true, R.color.dm_err);
        clear.setGravity(Gravity.CENTER_VERTICAL);
        clear.setMinimumHeight(mUi.dp(56));
        clear.setPadding(mUi.dp(16), mUi.dp(10), mUi.dp(16), mUi.dp(10));
        clear.setOnClickListener(v -> new AlertDialog.Builder(mUi.ctx)
                .setMessage(R.string.clear_confirm)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.clear, (d, w) -> mActions.clear())
                .show());
        g.add(clear);

        final LinearLayout about = new LinearLayout(mUi.ctx);
        about.setOrientation(LinearLayout.VERTICAL);
        about.setVisibility(View.GONE);
        about.addView(mUi.para(mUi.ctx.getString(R.string.blind_spots)));
        about.addView(mUi.para(mUi.ctx.getString(R.string.not_listed_not_proof)));
        about.addView(mUi.note(mUi.ctx.getString(R.string.data_sources)));
        if (invalidCount > 0) {
            about.addView(mUi.note(mUi.ctx.getResources().getQuantityString(
                    R.plurals.n_invalid, invalidCount, invalidCount)));
        }
        TextView aboutRow = mUi.text(mUi.ctx.getString(R.string.about), 16, true, R.color.dm_on);
        aboutRow.setGravity(Gravity.CENTER_VERTICAL);
        aboutRow.setMinimumHeight(mUi.dp(56));
        aboutRow.setPadding(mUi.dp(16), mUi.dp(10), mUi.dp(16), mUi.dp(10));
        aboutRow.setOnClickListener(v -> about.setVisibility(about.getVisibility() == View.VISIBLE
                ? View.GONE : View.VISIBLE));
        g.add(aboutRow);
        p.addView(g.seal().view);
        p.addView(about);

        collect.setOnCheckedChangeListener((CompoundButton b, boolean on) -> mActions.setCollecting(on));
        history.setOnCheckedChangeListener((CompoundButton b, boolean on) -> {
            keepRow.setVisibility(on ? View.VISIBLE : View.GONE);
            mActions.setHistory(on, days[0]);
        });
        return p;
    }

    /** "Keep for" as one tap on a choice: the presets of {@link KeepFor}, the selected one filled. */
    private LinearLayout keepForRow(final int[] choices, int selected, final int[] days) {
        LinearLayout row = new LinearLayout(mUi.ctx);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(mUi.dp(16), mUi.dp(12), mUi.dp(16), mUi.dp(14));
        row.addView(mUi.text(mUi.ctx.getString(R.string.history_days), 16, true, R.color.dm_on));
        final LinearLayout options = new LinearLayout(mUi.ctx);
        options.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams op = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        op.topMargin = mUi.dp(10);
        row.addView(options, op);
        final TextView[] labels = new TextView[choices.length];
        for (int i = 0; i < choices.length; i++) {
            final int value = choices[i];
            TextView t = mUi.text(mUi.ctx.getResources().getQuantityString(R.plurals.n_days, value, value),
                    14, true, R.color.dm_onv);
            t.setGravity(Gravity.CENTER);
            t.setMinHeight(mUi.dp(40));
            t.setSingleLine(true);
            t.setOnClickListener(v -> {
                days[0] = value;
                paint(labels, choices, value);
                mActions.setHistory(true, value);
            });
            labels[i] = t;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            lp.setMarginEnd(i == choices.length - 1 ? 0 : mUi.dp(6));
            options.addView(t, lp);
        }
        paint(labels, choices, choices[selected]);
        return row;
    }

    private void paint(TextView[] labels, int[] choices, int selectedValue) {
        for (int i = 0; i < labels.length; i++) {
            boolean on = choices[i] == selectedValue;
            labels[i].setBackground(mUi.shape(on ? R.color.dm_sc : R.color.dm_hst, 20, 20));
            labels[i].setTextColor(mUi.color(on ? R.color.dm_osc : R.color.dm_onv));
        }
    }

    private View switchRow(int titleRes, int summaryRes, Switch toggle) {
        LinearLayout row = new LinearLayout(mUi.ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(mUi.dp(64));
        row.setPadding(mUi.dp(16), mUi.dp(10), mUi.dp(16), mUi.dp(10));
        LinearLayout tx = new LinearLayout(mUi.ctx);
        tx.setOrientation(LinearLayout.VERTICAL);
        tx.addView(mUi.text(mUi.ctx.getString(titleRes), 16, true, R.color.dm_on));
        tx.addView(mUi.text(mUi.ctx.getString(summaryRes), 14, false, R.color.dm_onv));
        row.addView(tx, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(toggle);
        row.setOnClickListener(v -> toggle.toggle());
        return row;
    }
}
