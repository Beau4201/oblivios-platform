// SPDX-License-Identifier: Apache-2.0
package org.freeholdos.exit;

import android.content.Context;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.SearchView;
import android.widget.Toast;

import androidx.preference.PreferenceScreen;

import com.android.internal.compartments.CompartmentsPlace;
import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity;
import com.android.settingslib.preference.PreferenceFragment;
import com.android.settingslib.widget.FooterPreference;
import com.android.settingslib.widget.SelectorWithWidgetPreference;

/**
 * The place of the persona of one app (ADR-208): Automatic, which is a place in the user's own country, or a place found by
 * name in the offline data of the image. A tap goes to the service, and the rows are built again from what the service
 * reports, so the page never shows a place that was not stored. Not exported: the page of the app opens it.
 */
public final class ExitPlaceActivity extends CollapsingToolbarBaseActivity {
    /** The most results the page lists; the service caps it at 50. */
    static final int MAX_RESULTS = 30;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.place_activity);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.place_menu, menu);
        SearchView search = (SearchView) menu.findItem(R.id.search).getActionView();
        search.setQueryHint(getString(R.string.place_search_hint));
        search.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String q) {
                return true;
            }

            @Override
            public boolean onQueryTextChange(String q) {
                PlaceFragment f = (PlaceFragment) getSupportFragmentManager().findFragmentById(R.id.fragment_container_view);
                if (f != null) {
                    f.setQuery(q);
                }
                return true;
            }
        });
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public static final class PlaceFragment extends PreferenceFragment {
        private ProfileClient profiles;
        private String pkg;
        private String query = "";

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            profiles = ProfileClient.find();
            pkg = ExitAppActivity.packageOf(requireActivity().getIntent());
            setPreferenceScreen(getPreferenceManager().createPreferenceScreen(requireContext()));
        }

        @Override
        public void onStart() {
            super.onStart();
            render();
        }

        void setQuery(String q) {
            String next = q == null ? "" : q.trim();
            if (!next.equals(query)) {
                query = next;
                render();
            }
        }

        private void render() {
            Context ctx = getContext();
            if (ctx == null || profiles == null) {
                return;
            }
            PreferenceScreen screen = getPreferenceScreen();
            screen.removeAll();
            int current = pkg == null || !profiles.available() ? -1 : profiles.getPlaceId(pkg);
            CompartmentsPlace[] found = current < 0 ? new CompartmentsPlace[0] : profiles.searchPlaces(query, MAX_RESULTS);
            // No data and no way to read the choice: the page says so and offers nothing it could not store.
            if (current < 0 || (found.length == 0 && query.isEmpty())) {
                screen.addPreference(ExitSettingsActivity.note(ctx, null, getString(R.string.place_none)));
                return;
            }
            screen.addPreference(radio(ctx, getString(R.string.place_automatic), getString(R.string.place_automatic_summary),
                    current == 0, 0));
            // The place that is set is always listed, even when the search no longer finds it.
            boolean listed = current == 0;
            for (CompartmentsPlace p : found) {
                listed |= p.id == current;
            }
            if (!listed) {
                CompartmentsPlace set = profiles.getPlace(current);
                if (set != null) {
                    screen.addPreference(radio(ctx, set.name, set.country, true, set.id));
                }
            }
            for (CompartmentsPlace p : found) {
                screen.addPreference(radio(ctx, p.name, p.country, p.id == current, p.id));
            }
            FooterPreference footer = new FooterPreference(ctx);
            footer.setSummary(R.string.identity_time_zone_note);
            screen.addPreference(footer);
        }

        private SelectorWithWidgetPreference radio(Context ctx, CharSequence title, CharSequence summary, boolean checked, int id) {
            SelectorWithWidgetPreference radio = new SelectorWithWidgetPreference(ctx);
            radio.setPersistent(false);
            radio.setTitle(title);
            radio.setSummary(summary);
            radio.setChecked(checked);
            radio.setOnClickListener(p -> {
                if (!profiles.setPlace(pkg, id)) {
                    Toast.makeText(requireContext(), R.string.place_error, Toast.LENGTH_LONG).show();
                }
                render();
            });
            return radio;
        }
    }
}
