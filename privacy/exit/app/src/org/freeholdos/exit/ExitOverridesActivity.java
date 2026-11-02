// SPDX-License-Identifier: Apache-2.0
package org.freeholdos.exit;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MenuItem;

import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity;

import java.util.List;

/** The hosts that the user chose to show the real address to, one row per app and company; a tap hides the address again. */
public final class ExitOverridesActivity extends CollapsingToolbarBaseActivity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.overrides_activity);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public static final class OverridesFragment extends CalmPreferenceFragment implements ExitRuntime.Listener {
        private final Handler main = new Handler(Looper.getMainLooper());
        private ExitRuntime rt;
        private CompanyNames names;
        private AppIndex index = AppIndex.EMPTY;
        private String built = "";

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            Context ctx = requireContext();
            rt = ExitRuntime.get(ctx);
            names = rt.companyNames();
            setPreferenceScreen(getPreferenceManager().createPreferenceScreen(ctx));
            AppIndex.load(ctx, main, i -> {
                index = i;
                render();
            });
        }

        @Override
        public void onStart() {
            super.onStart();
            rt.setListener(this);
            render();
        }

        @Override
        public void onStop() {
            rt.clearListener(this);
            super.onStop();
        }

        @Override
        public void onChanged(ExitRuntime.Snapshot s) {
            main.post(this::render);
        }

        private void render() {
            Context ctx = getContext();
            if (ctx == null || rt == null) {
                return;
            }
            ExitRuntime.Snapshot s = rt.snapshot();
            PreferenceScreen screen = getPreferenceScreen();
            if (!rt.runsHere() || s == null) {
                if (!"owner".equals(built)) {
                    screen.removeAll();
                    screen.addPreference(ExitSettingsActivity.note(ctx, null, getString(R.string.owner_only)));
                    built = "owner";
                }
                return;
            }
            List<Presenter.OverrideRow> rows = Presenter.overrideRows(s.choices, names);
            // The status is polled every few seconds and each poll reaches here: the rows are built only when they change.
            StringBuilder shape = new StringBuilder().append(index.listed.size()).append('#');
            for (Presenter.OverrideRow r : rows) {
                shape.append(r.pkg).append(':').append(r.company).append(',');
            }
            if (shape.toString().equals(built)) {
                return;
            }
            built = shape.toString();
            screen.removeAll();
            if (rows.isEmpty()) {
                screen.addPreference(ExitSettingsActivity.note(ctx, null, getString(R.string.overrides_empty)));
            }
            for (Presenter.OverrideRow r : rows) {
                String company = r.companyName != null ? r.companyName : getString(R.string.notif_unknown_company);
                Preference p = new Preference(ctx);
                p.setPersistent(false);
                p.setTitle(getString(R.string.override_row, index.label(r.pkg), company));
                p.setSummary(R.string.override_remove);
                android.graphics.drawable.Drawable icon = index.icons.get(r.pkg);
                if (icon != null) {
                    p.setIcon(icon);
                }
                p.setOnPreferenceClickListener(x -> {
                    rt.setShowsAddress(r.pkg, r.company, false);
                    return true;
                });
                screen.addPreference(p);
            }
        }
    }
}
