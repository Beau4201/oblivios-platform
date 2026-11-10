// SPDX-License-Identifier: Apache-2.0
package org.oblivios.exit;

import android.content.Context;
import android.os.Bundle;
import android.view.MenuItem;

import androidx.preference.PreferenceScreen;

import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity;
import com.android.settingslib.preference.PreferenceFragment;

/**
 * "What stays visible" (ADR-206, ADR-208): what an app can still learn about this phone, as read-only rows. The page reads no
 * state and calls no service; the texts are fixed and a row is removed in the change that closes it.
 */
public final class ExitVisibleActivity extends CollapsingToolbarBaseActivity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.visible_activity);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public static final class VisibleFragment extends PreferenceFragment {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            Context ctx = requireContext();
            PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(ctx);
            setPreferenceScreen(screen);
            int[][] rows = {
                {R.string.identity_visible_address_title, R.string.identity_visible_address_summary},
                {R.string.identity_visible_model_title, R.string.identity_visible_model_summary},
                {R.string.identity_visible_installed_title, R.string.identity_visible_installed_summary},
                {R.string.identity_visible_processes_title, R.string.identity_visible_processes_summary},
                {R.string.identity_visible_uptime_title, R.string.identity_visible_uptime_summary},
                {R.string.identity_visible_downloads_title, R.string.identity_visible_downloads_summary},
                {R.string.identity_visible_input_title, R.string.identity_visible_input_summary},
            };
            for (int[] r : rows) {
                screen.addPreference(ExitSettingsActivity.note(ctx, getString(r[0]), getString(r[1])));
            }
        }
    }
}
