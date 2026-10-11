// SPDX-License-Identifier: Apache-2.0
package org.sovereignos.datamap;

import android.app.AlertDialog;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

/**
 * The choices of the owner: whether anything is collected, whether a history is kept on this phone and for
 * how many days, and the deletion of everything collected. Each change goes to the service at once.
 */
final class SettingsScreen {
    /** What the screen asks of the service; implemented by the activity, which runs it off the main thread. */
    interface Actions {
        void setCollecting(boolean collecting);

        void setHistory(boolean enabled, int days);

        void clear();
    }

    private static final int MIN_DAYS = 1;
    private static final int MAX_DAYS = 90;

    private final Ui mUi;
    private final Actions mActions;

    SettingsScreen(Ui ui, Actions actions) {
        mUi = ui;
        mActions = actions;
    }

    View build(boolean collecting, boolean historyOn, int historyDays, int invalidCount) {
        final LinearLayout p = new LinearLayout(mUi.ctx);
        p.setOrientation(LinearLayout.VERTICAL);
        TextView title = mUi.text(mUi.ctx.getString(R.string.settings), 36, false, R.color.dm_on);
        title.setPadding(mUi.dp(24), mUi.dp(64), mUi.dp(24), mUi.dp(8));
        p.addView(title);

        Ui.Group g = new Ui.Group(mUi);
        final Switch collect = new Switch(mUi.ctx);
        collect.setChecked(collecting);
        g.add(switchRow(R.string.collect, R.string.collect_summary, collect));

        final Switch history = new Switch(mUi.ctx);
        history.setChecked(historyOn);
        g.add(switchRow(R.string.history, R.string.history_summary, history));

        final int days = Math.max(MIN_DAYS, Math.min(MAX_DAYS, historyDays <= 0 ? 7 : historyDays));
        final TextView daysText = mUi.text(daysLabel(days), 14, false, R.color.dm_onv);
        final SeekBar seek = new SeekBar(mUi.ctx);
        seek.setMax(MAX_DAYS - MIN_DAYS);
        seek.setProgress(days - MIN_DAYS);
        seek.setEnabled(historyOn);
        LinearLayout daysRow = new LinearLayout(mUi.ctx);
        daysRow.setOrientation(LinearLayout.VERTICAL);
        daysRow.setPadding(mUi.dp(16), mUi.dp(12), mUi.dp(16), mUi.dp(12));
        daysRow.addView(mUi.text(mUi.ctx.getString(R.string.history_days), 16, true, R.color.dm_on));
        daysRow.addView(daysText);
        daysRow.addView(seek);
        g.add(daysRow);
        p.addView(g.seal().view);

        collect.setOnCheckedChangeListener((CompoundButton b, boolean on) -> mActions.setCollecting(on));
        history.setOnCheckedChangeListener((CompoundButton b, boolean on) -> {
            seek.setEnabled(on);
            mActions.setHistory(on, seek.getProgress() + MIN_DAYS);
        });
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                daysText.setText(daysLabel(progress + MIN_DAYS));
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {}

            @Override
            public void onStopTrackingTouch(SeekBar s) {
                if (history.isChecked()) {
                    mActions.setHistory(true, s.getProgress() + MIN_DAYS);
                }
            }
        });

        p.addView(mUi.para(mUi.ctx.getString(R.string.history_explained)));

        Button clear = new Button(mUi.ctx);
        clear.setText(R.string.clear);
        clear.setOnClickListener(v -> new AlertDialog.Builder(mUi.ctx)
                .setMessage(R.string.clear_confirm)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.clear, (d, w) -> mActions.clear())
                .show());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(mUi.dp(24), mUi.dp(16), mUi.dp(24), 0);
        p.addView(clear, lp);

        p.addView(mUi.label(mUi.ctx.getString(R.string.cannot_see)));
        p.addView(mUi.para(mUi.ctx.getString(R.string.blind_spots)));
        p.addView(mUi.para(mUi.ctx.getString(R.string.not_listed_not_proof)));
        if (invalidCount > 0) {
            p.addView(mUi.note(mUi.ctx.getResources().getQuantityString(
                    R.plurals.n_invalid, invalidCount, invalidCount)));
        }
        return p;
    }

    private String daysLabel(int days) {
        return mUi.ctx.getResources().getQuantityString(R.plurals.n_days, days, days);
    }

    private View switchRow(int titleRes, int summaryRes, Switch toggle) {
        LinearLayout row = new LinearLayout(mUi.ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
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
