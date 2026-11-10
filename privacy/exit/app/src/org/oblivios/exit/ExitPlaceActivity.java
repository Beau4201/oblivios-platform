// SPDX-License-Identifier: Apache-2.0
package org.oblivios.exit;

import android.content.Context;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.SearchView;
import android.widget.Toast;

import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.android.internal.compartments.CompartmentsPlace;
import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity;
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

    public static final class PlaceFragment extends CalmPreferenceFragment {
        private ProfileClient profiles;
        private String pkg;
        /** Read on the worker thread, so it is volatile; the reader takes the latest value when it runs again. */
        private volatile String query = "";
        private final ProfileClient.Reader<Loaded> reader = new ProfileClient.Reader<>(this::read, this::render);

        /** What the service reports, read off the main thread (X1, G8). */
        private static final class Loaded {
            String query;
            int current = -1;
            CompartmentsPlace[] found = new CompartmentsPlace[0];
            /** The place that is set when the search no longer finds it, else null. */
            CompartmentsPlace set;
        }

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            profiles = ProfileClient.find();
            pkg = ExitAppActivity.packageOf(requireActivity().getIntent());
            setPreferenceScreen(getPreferenceManager().createPreferenceScreen(requireContext()));
        }

        @Override
        public void onStart() {
            super.onStart();
            reader.run();
        }

        void setQuery(String q) {
            String next = q == null ? "" : q.trim();
            if (!next.equals(query)) {
                query = next;
                reader.run();
            }
        }

        /** Runs on the worker thread. */
        private Loaded read() {
            Loaded l = new Loaded();
            l.query = query;
            l.current = pkg == null || !profiles.available() ? -1 : profiles.getPlaceId(pkg);
            if (l.current < 0) {
                return l;
            }
            l.found = profiles.searchPlaces(l.query, MAX_RESULTS);
            boolean listed = l.current == 0;
            for (CompartmentsPlace p : l.found) {
                listed |= p.id == l.current;
            }
            // The place that is set is always listed, even when the search no longer finds it.
            l.set = listed ? null : profiles.getPlace(l.current);
            return l;
        }

        private void render(Loaded l) {
            Context ctx = getContext();
            if (ctx == null || profiles == null) {
                return;
            }
            PreferenceScreen screen = getPreferenceScreen();
            screen.removeAll();
            int current = l.current;
            CompartmentsPlace[] found = l.found;
            // No way to read the choice: the page says so and offers nothing it could not store. An empty search is not "no data":
            // the service lists places only for a typed name, and the page of an app opens this page only when the image has them.
            if (current < 0) {
                screen.addPreference(ExitSettingsActivity.note(ctx, null, getString(R.string.place_none)));
                return;
            }
            screen.addPreference(radio(ctx, getString(R.string.place_automatic), getString(R.string.place_automatic_summary),
                    current == 0, 0));
            if (l.set != null) {
                screen.addPreference(radio(ctx, l.set.name, l.set.country, true, l.set.id));
            }
            for (CompartmentsPlace p : found) {
                screen.addPreference(radio(ctx, p.name, p.country, p.id == current, p.id));
            }
            if (l.query.isEmpty()) {
                screen.addPreference(ExitSettingsActivity.note(ctx, null, getString(R.string.place_search_prompt)));
            }
            FooterPreference footer = new FooterPreference(ctx);
            footer.setSummary(R.string.identity_time_zone_note);
            screen.addPreference(footer);
        }

        /** The reason that the service gave for a refused change, else the generic text (X2). */
        private CharSequence refusalOr(int generic) {
            String why = profiles.refusal();
            return why != null ? why : getString(generic);
        }

        private SelectorWithWidgetPreference radio(Context ctx, CharSequence title, CharSequence summary, boolean checked, int id) {
            SelectorWithWidgetPreference radio = new SelectorWithWidgetPreference(ctx);
            radio.setPersistent(false);
            radio.setTitle(title);
            radio.setSummary(summary);
            radio.setChecked(checked);
            radio.setOnClickListener(p -> {
                checkOnly(p);
                ProfileClient.write(() -> profiles.setPlace(pkg, id), ok -> {
                    if (getContext() == null) {
                        return;
                    }
                    if (!ok) {
                        Toast.makeText(requireContext(), refusalOr(R.string.place_error), Toast.LENGTH_LONG).show();
                    }
                    reader.run();
                });
            });
            return radio;
        }

        /** The tapped radio shows at once; the page is built again from the service when the change ends. */
        private void checkOnly(SelectorWithWidgetPreference chosen) {
            PreferenceScreen screen = getPreferenceScreen();
            for (int i = 0; i < screen.getPreferenceCount(); i++) {
                Preference p = screen.getPreference(i);
                if (p instanceof SelectorWithWidgetPreference) {
                    ((SelectorWithWidgetPreference) p).setChecked(p == chosen);
                }
            }
        }
    }
}
