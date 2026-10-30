// SPDX-License-Identifier: Apache-2.0
package org.freeholdos.exit;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;

import com.android.internal.compartments.CompartmentsAppPolicy;
import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity;
import com.android.settingslib.preference.PreferenceFragment;
import com.android.settingslib.widget.FooterPreference;
import com.android.settingslib.widget.SelectorWithWidgetPreference;

import java.util.ArrayList;
import java.util.List;

/**
 * The "Compartments" page of one app: what the app is set to (trackers hidden, Tor, never hidden) and the hosts that see its real
 * address. It is the page that the "Compartments" row of the app's info page in Settings opens, and the page that a tap on an app
 * in the list of Compartments opens, so there is one place to set one app. The app is named by the data of the intent
 * ({@code package:<name>}); the page is exported for the platform's Settings app only (the permission {@code NETWORK_SETTINGS}) and
 * ignores a package that is not installed.
 */
public final class ExitAppActivity extends CollapsingToolbarBaseActivity {
    /** The intent that opens the page of {@code pkg}. */
    static Intent intentFor(Context ctx, String pkg) {
        return new Intent(ctx, ExitAppActivity.class).setData(Uri.fromParts("package", pkg, null));
    }

    /** The package that an intent names, or null when it names none. */
    static String packageOf(Intent intent) {
        Uri d = intent == null ? null : intent.getData();
        return d != null && "package".equals(d.getScheme()) ? d.getSchemeSpecificPart() : null;
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.app_activity);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public static final class AppFragment extends PreferenceFragment implements ExitRuntime.Listener {
        private final Handler main = new Handler(Looper.getMainLooper());
        private ExitRuntime rt;
        private CompanyNames names;
        private String pkg;
        private ApplicationInfo info;
        private String built = "";
        private final List<SelectorWithWidgetPreference> radios = new ArrayList<>();
        private ProfileClient profiles;

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            Context ctx = requireContext();
            rt = ExitRuntime.get(ctx);
            names = rt.companyNames();
            profiles = ProfileClient.find();
            pkg = packageOf(requireActivity().getIntent());
            try {
                info = pkg == null ? null : ctx.getPackageManager().getApplicationInfo(pkg, 0);
            } catch (PackageManager.NameNotFoundException e) {
                info = null;
            }
            setPreferenceScreen(getPreferenceManager().createPreferenceScreen(ctx));
            if (info == null) {
                requireActivity().finish();
            }
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
            if (ctx == null || rt == null || info == null) {
                return;
            }
            ExitRuntime.Snapshot s = rt.snapshot();
            PreferenceScreen screen = getPreferenceScreen();
            if (!rt.runsHere() || s == null) {
                if (!"owner".equals(built)) {
                    screen.removeAll();
                    radios.clear();
                    screen.addPreference(ExitSettingsActivity.note(ctx, null, getString(R.string.owner_only)));
                    built = "owner";
                }
                return;
            }
            boolean fixed = Policy.EXEMPT_PACKAGES.contains(pkg) || !UidRanges.isAppUid(info.uid);
            List<Presenter.OverrideRow> shown = new ArrayList<>();
            for (Presenter.OverrideRow r : Presenter.overrideRows(s.choices, names)) {
                if (r.pkg.equals(pkg)) {
                    shown.add(r);
                }
            }
            List<Status.Promoted> promoted = new ArrayList<>();
            for (Status.Promoted q : s.status.promotions) {
                if (q.uid == info.uid) {
                    promoted.add(q);
                }
            }
            CompartmentsAppPolicy policy = fixed || !profiles.available() ? null : profiles.getApp(pkg);
            StringBuilder shape = new StringBuilder().append(fixed);
            for (Presenter.OverrideRow r : shown) {
                shape.append('|').append(r.company);
            }
            for (Status.Promoted q : promoted) {
                shape.append("|p").append(q.company);
            }
            if (policy != null) {
                // a change of the profile or of a value builds the rows again: which rows exist and what they show follow it
                shape.append("|i").append(policy.profile).append(policy.sensors).append(policy.loopback).append(policy.settings);
            }
            if (!shape.toString().equals(built)) {
                build(ctx, screen, fixed, shown, promoted, policy);
                built = shape.toString();
            }
            Choices.Choice now = s.choices.choiceOf(pkg);
            Choices.Choice[] options = Presenter.options();
            for (int i = 0; i < radios.size(); i++) {
                radios.get(i).setChecked(options[i] == now);
            }
        }

        private void build(Context ctx, PreferenceScreen screen, boolean fixed, List<Presenter.OverrideRow> shown,
                List<Status.Promoted> promoted, CompartmentsAppPolicy policy) {
            screen.removeAll();
            radios.clear();
            PackageManager pm = ctx.getPackageManager();
            CharSequence label = pm.getApplicationLabel(info);
            Preference header = ExitSettingsActivity.note(ctx, label, null);
            header.setIcon(pm.getApplicationIcon(info));
            screen.addPreference(header);
            if (fixed) {
                screen.addPreference(ExitSettingsActivity.note(ctx, null, getString(R.string.app_exempt)));
                return;
            }

            PreferenceCategory network = new PreferenceCategory(ctx);
            network.setPersistent(false);
            network.setTitle(getString(R.string.app_mode_title, label));
            screen.addPreference(network);
            for (Choices.Choice c : Presenter.options()) {
                SelectorWithWidgetPreference radio = new SelectorWithWidgetPreference(ctx);
                radio.setPersistent(false);
                radio.setTitle(modeText(c));
                radio.setSummary(modeSummary(c));
                radio.setOnClickListener(p -> choose(c));
                radios.add(radio);
                network.addPreference(radio);
            }

            if (policy != null) {
                addIdentity(ctx, screen, policy);
            }

            if (!promoted.isEmpty()) {
                PreferenceCategory now = new PreferenceCategory(ctx);
                now.setPersistent(false);
                now.setTitle(R.string.promoted_title);
                screen.addPreference(now);
                now.addPreference(ExitSettingsActivity.note(ctx, null, getString(R.string.promoted_summary)));
                for (Status.Promoted q : promoted) {
                    Preference p = new Preference(ctx);
                    p.setPersistent(false);
                    p.setTitle(companyOf(q.company));
                    p.setSummary(R.string.override_remove);
                    p.setOnPreferenceClickListener(x -> {
                        rt.release(q.uid, q.company);
                        return true;
                    });
                    now.addPreference(p);
                }
            }

            PreferenceCategory hosts = new PreferenceCategory(ctx);
            hosts.setPersistent(false);
            hosts.setTitle(R.string.overrides_title);
            screen.addPreference(hosts);
            if (shown.isEmpty()) {
                hosts.addPreference(ExitSettingsActivity.note(ctx, null, getString(R.string.overrides_empty)));
            }
            for (Presenter.OverrideRow r : shown) {
                Preference p = new Preference(ctx);
                p.setPersistent(false);
                p.setTitle(r.companyName != null ? r.companyName : getString(R.string.notif_unknown_company));
                p.setSummary(R.string.override_remove);
                p.setOnPreferenceClickListener(x -> {
                    rt.setShowsAddress(r.pkg, r.company, false);
                    return true;
                });
                hosts.addPreference(p);
            }
        }

        /**
         * The profile of the app, one row for each class it can be set for, and what the profile means (ADR-208). Every row
         * stores nothing itself: a change goes to the service and the page is built again from what the service reports.
         */
        private void addIdentity(Context ctx, PreferenceScreen screen, CompartmentsAppPolicy policy) {
            List<ProfileRows.Row> rows;
            try {
                rows = ProfileRows.rowsFor(policy.profile, policy.sensors, policy.loopback, policy.settings);
            } catch (IllegalArgumentException e) {
                return;
            }
            PreferenceCategory category = new PreferenceCategory(ctx);
            category.setPersistent(false);
            category.setTitle(R.string.identity_title);
            screen.addPreference(category);

            ListPreference profile = ProfileText.choice(ctx, "app_profile", R.string.profile_title,
                    ProfileRows.profileChoices(false), ProfileText::profileName, policy.profile);
            profile.setOnPreferenceChangeListener((p, v) -> {
                changed(profiles.setProfile(pkg, Integer.parseInt((String) v)));
                return false;
            });
            category.addPreference(profile);

            for (ProfileRows.Row row : rows) {
                final int cls = row.cls;
                ListPreference pref = ProfileText.choice(ctx, "app_class_" + cls, ProfileText.classTitle(cls), row.values,
                        v -> ProfileText.valueName(cls, v), row.current);
                pref.setOnPreferenceChangeListener((p, v) -> {
                    changed(profiles.setClassValue(pkg, cls, Integer.parseInt((String) v)));
                    return false;
                });
                category.addPreference(pref);
            }

            FooterPreference footer = new FooterPreference(ctx);
            footer.setSummary(ProfileText.profileFooter(policy.profile));
            screen.addPreference(footer);
        }

        /** A refused or failed change says so and changes nothing; the page is built again from the service either way. */
        private void changed(boolean ok) {
            if (!ok) {
                Toast.makeText(requireContext(), R.string.identity_error, Toast.LENGTH_LONG).show();
            }
            main.post(() -> {
                built = "";
                render();
            });
        }

        private String companyOf(int company) {
            String name = names.nameOf(company);
            return name != null ? name : getString(R.string.notif_unknown_company);
        }

        /** A stand that the tables cannot hold says so and changes nothing; the radios are read back from the runtime. */
        private void choose(Choices.Choice c) {
            ExitRuntime.Snapshot now = rt.snapshot();
            if (now != null && !now.choices.accepts(pkg, c)) {
                int limit = c == Choices.Choice.TOR ? Choices.MAX_TOR_APPS : Choices.MAX_OPEN_APPS;
                int text = c == Choices.Choice.TOR ? R.string.apps_limit : R.string.apps_open_limit;
                Toast.makeText(requireContext(), getString(text, limit), Toast.LENGTH_LONG).show();
                render();
                return;
            }
            rt.setChoice(pkg, c);
        }

        private static int modeSummary(Choices.Choice c) {
            switch (c) {
                case TOR:
                    return R.string.app_mode_tor_summary;
                case OPEN:
                    return R.string.app_mode_open_summary;
                default:
                    return R.string.app_mode_direct_summary;
            }
        }

        private static int modeText(Choices.Choice c) {
            switch (c) {
                case TOR:
                    return R.string.app_mode_tor;
                case OPEN:
                    return R.string.app_mode_open;
                default:
                    return R.string.app_mode_direct;
            }
        }
    }
}
