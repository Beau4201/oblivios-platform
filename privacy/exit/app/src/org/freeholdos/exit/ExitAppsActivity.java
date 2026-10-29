// SPDX-License-Identifier: Apache-2.0
package org.freeholdos.exit;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.SearchView;

import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity;
import com.android.settingslib.preference.PreferenceFragment;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The apps of this user with what each is set to (trackers hidden, Tor, never hidden), with the search of the Settings pages in the
 * toolbar. A tap on an app opens the page of that app ({@link ExitAppActivity}), the same page that the app's info page in
 * Settings opens, so an app is set in one place.
 */
public final class ExitAppsActivity extends CollapsingToolbarBaseActivity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.apps_activity);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.apps_menu, menu);
        SearchView search = (SearchView) menu.findItem(R.id.search).getActionView();
        search.setQueryHint(getString(R.string.apps_search_hint));
        search.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String q) {
                return true;
            }

            @Override
            public boolean onQueryTextChange(String q) {
                AppsFragment f = (AppsFragment) getSupportFragmentManager().findFragmentById(R.id.fragment_container_view);
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

    public static final class AppsFragment extends PreferenceFragment implements ExitRuntime.Listener {
        private final Handler main = new Handler(Looper.getMainLooper());
        private ExitRuntime rt;
        private AppIndex index = AppIndex.EMPTY;
        private String query = "";
        /** The packages of the rows that are shown, with the query: the rows are built again only when this changes. */
        private String built = "";
        private final Map<String, Preference> rows = new HashMap<>();

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            Context ctx = requireContext();
            rt = ExitRuntime.get(ctx);
            setPreferenceScreen(getPreferenceManager().createPreferenceScreen(ctx));
            AppIndex.load(ctx, main, i -> {
                index = i;
                built = "";
                render();
            });
        }

        void setQuery(String q) {
            query = q == null ? "" : q;
            render();
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
                    rows.clear();
                    screen.addPreference(ExitSettingsActivity.note(ctx, null, getString(R.string.owner_only)));
                    built = "owner";
                }
                return;
            }
            List<Presenter.AppRow> list = Presenter.appRows(s.choices, index.listed, query);
            StringBuilder shape = new StringBuilder(query).append('#');
            for (Presenter.AppRow r : list) {
                shape.append(r.pkg).append(',');
            }
            if (!shape.toString().equals(built)) {
                screen.removeAll();
                rows.clear();
                for (Presenter.AppRow r : list) {
                    Preference p = new Preference(ctx);
                    p.setKey(r.pkg);
                    p.setPersistent(false);
                    p.setTitle(r.label);
                    p.setSingleLineTitle(true);
                    Drawable icon = index.icons.get(r.pkg);
                    if (icon != null) {
                        p.setIcon(icon);
                    }
                    p.setIntent(ExitAppActivity.intentFor(ctx, r.pkg));
                    rows.put(r.pkg, p);
                    screen.addPreference(p);
                }
                built = shape.toString();
            }
            for (Presenter.AppRow r : list) {
                Preference p = rows.get(r.pkg);
                if (p != null) {
                    p.setSummary(hintOf(r.choice));
                }
            }
        }

        private static int hintOf(Choices.Choice c) {
            switch (c) {
                case TOR:
                    return R.string.app_tor_hint;
                case OPEN:
                    return R.string.app_open_hint;
                default:
                    return R.string.app_direct_hint;
            }
        }
    }
}
