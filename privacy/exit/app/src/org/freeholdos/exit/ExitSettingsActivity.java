// SPDX-License-Identifier: Apache-2.0
package org.freeholdos.exit;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreferenceCompat;

import com.android.internal.compartments.CompartmentsAppPolicy;
import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity;
import com.android.settingslib.preference.PreferenceFragment;
import com.android.settingslib.widget.ButtonPreference;
import com.android.settingslib.widget.FooterPreference;
import com.android.settingslib.widget.MainSwitchPreference;

import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * The "Compartments" entry under Network &amp; internet (a preference of the Settings app that starts this activity): the status, the
 * one-time consent, the hidden-address switch, the hosts that the speed guard shows an address to for now (with "Hide
 * again"), the warnings, and the two sub-pages: the apps with their stand and the hosts the user showed an address to.
 * It is built like the pages of the Settings app (the collapsing toolbar, the preference widgets and the theme of SettingsLib,
 * as the Updater does); the fragment only draws what {@link Presenter} lists and posts each change to {@link ExitRuntime}.
 */
public final class ExitSettingsActivity extends CollapsingToolbarBaseActivity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.settings_activity);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /** A line that shows text and is not a control. */
    static Preference note(Context ctx, CharSequence title, CharSequence summary) {
        Preference p = new Preference(ctx);
        p.setPersistent(false);
        p.setSelectable(false);
        if (title != null) {
            p.setTitle(title);
        }
        if (summary != null) {
            p.setSummary(summary);
        }
        return p;
    }

    public static final class MainFragment extends PreferenceFragment implements ExitRuntime.Listener {
        private final Handler main = new Handler(Looper.getMainLooper());
        private ExitRuntime rt;
        private CompanyNames names;
        private AppIndex index = AppIndex.EMPTY;
        private boolean consentChoice = true;
        /** What the screen was built from: it is built again only when this changes, and refreshed in place otherwise. */
        private String built = "";
        private Preference statusPref;
        private Preference appsPref;
        private Preference overridesPref;
        private MainSwitchPreference mainSwitch;
        private ProfileClient profiles;
        private ListPreference defaultProfilePref;
        private ListPreference defaultSensorsPref;

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            Context ctx = requireContext();
            rt = ExitRuntime.get(ctx);
            names = rt.companyNames();
            profiles = ProfileClient.find();
            setPreferenceScreen(getPreferenceManager().createPreferenceScreen(ctx));
            AppIndex.load(ctx, main, i -> {
                index = i;
                built = "";
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
                    screen.addPreference(note(ctx, null, getString(R.string.owner_only)));
                    built = "owner";
                }
                return;
            }
            String shape = shapeOf(s);
            if (!shape.equals(built)) {
                build(ctx, screen, s);
                built = shape;
            }
            refresh(s);
        }

        private String shapeOf(ExitRuntime.Snapshot s) {
            StringBuilder k = new StringBuilder();
            k.append(s.consent).append('|').append(s.choices.damaged());
            for (Policy.Warning w : Policy.Warning.values()) {
                if (s.status.warnings.contains(w)) {
                    k.append('|').append(w.name());
                }
            }
            for (Status.Promoted q : s.status.promotions) {
                k.append('|').append(q.uid).append(':').append(q.company);
            }
            k.append('|').append(index.listed.size());
            return k.toString();
        }

        private void build(Context ctx, PreferenceScreen screen, ExitRuntime.Snapshot s) {
            screen.removeAll();
            mainSwitch = null;
            if (s.consent == Consent.UNSET) {
                PreferenceCategory ask = new PreferenceCategory(ctx);
                ask.setPersistent(false);
                ask.setTitle(R.string.consent_title);
                screen.addPreference(ask);
                ask.addPreference(note(ctx, null, getString(R.string.consent_text)));
                SwitchPreferenceCompat choice = new SwitchPreferenceCompat(ctx);
                choice.setPersistent(false);
                choice.setTitle(R.string.consent_switch);
                choice.setChecked(consentChoice);
                choice.setOnPreferenceChangeListener((p, v) -> {
                    consentChoice = (Boolean) v;
                    return true;
                });
                ask.addPreference(choice);
                ButtonPreference go = new ButtonPreference(ctx);
                go.setTitle(R.string.consent_confirm);
                go.setOnClickListener(v -> rt.setConsent(consentChoice ? Consent.ON : Consent.OFF));
                screen.addPreference(go);
            } else {
                mainSwitch = new MainSwitchPreference(ctx);
                mainSwitch.setPersistent(false);
                mainSwitch.setTitle(R.string.masked_title);
                mainSwitch.addOnSwitchChangeListener((v, on) -> {
                    ExitRuntime.Snapshot now = rt.snapshot();
                    if (now != null && on != (now.consent == Consent.ON)) {
                        rt.setConsent(on ? Consent.ON : Consent.OFF);
                    }
                });
                screen.addPreference(mainSwitch);
            }

            statusPref = note(ctx, "", null);
            screen.addPreference(statusPref);
            for (Policy.Warning w : Policy.Warning.values()) {
                if (s.status.warnings.contains(w)) {
                    screen.addPreference(note(ctx, null, getString(warningText(w))));
                }
            }
            if (s.choices.damaged()) {
                screen.addPreference(note(ctx, null, getString(R.string.warn_damaged)));
            }

            if (!s.status.promotions.isEmpty()) {
                PreferenceCategory promoted = new PreferenceCategory(ctx);
                promoted.setPersistent(false);
                promoted.setTitle(R.string.promoted_title);
                screen.addPreference(promoted);
                promoted.addPreference(note(ctx, null, getString(R.string.promoted_summary)));
                for (Status.Promoted q : s.status.promotions) {
                    promoted.addPreference(promotedRow(ctx, q));
                }
            }

            appsPref = new Preference(ctx);
            appsPref.setPersistent(false);
            appsPref.setTitle(R.string.apps_title);
            appsPref.setIntent(new Intent(ctx, ExitAppsActivity.class));
            screen.addPreference(appsPref);
            overridesPref = new Preference(ctx);
            overridesPref.setPersistent(false);
            overridesPref.setTitle(R.string.overrides_title);
            overridesPref.setIntent(new Intent(ctx, ExitOverridesActivity.class));
            screen.addPreference(overridesPref);

            boolean identity = addIdentity(ctx, screen);

            if (s.consent != Consent.UNSET) {
                FooterPreference footer = new FooterPreference(ctx);
                footer.setSummary(R.string.masked_summary);
                screen.addPreference(footer);
            }
            if (identity) {
                FooterPreference footer = new FooterPreference(ctx);
                footer.setSummary(R.string.identity_footer);
                screen.addPreference(footer);
            }
        }

        /**
         * The profile that apps follow until the user sets them, and the page of what stays visible (ADR-208). Absent when this
         * image has no Compartments policy service or the service does not answer. Returns whether the category was added.
         */
        private boolean addIdentity(Context ctx, PreferenceScreen screen) {
            defaultProfilePref = null;
            defaultSensorsPref = null;
            CompartmentsAppPolicy d = profiles.available() ? profiles.getDefaults() : null;
            if (d == null) {
                return false;
            }
            List<ProfileRows.Row> rows;
            try {
                rows = ProfileRows.defaultRows(d.profile, d.sensors, d.location);
            } catch (IllegalArgumentException e) {
                return false;
            }
            PreferenceCategory category = new PreferenceCategory(ctx);
            category.setPersistent(false);
            category.setTitle(R.string.identity_title);
            screen.addPreference(category);

            defaultProfilePref = ProfileText.choice(ctx, "default_profile", R.string.identity_defaults_profile,
                    ProfileRows.profileChoices(true), ProfileText::profileName, d.profile);
            defaultProfilePref.setOnPreferenceChangeListener((p, v) -> {
                changeDefault(() -> profiles.setDefaultProfile(Integer.parseInt((String) v)));
                return false;
            });
            category.addPreference(defaultProfilePref);

            defaultSensorsPref = ProfileText.choice(ctx, "default_sensors", R.string.identity_defaults_sensors,
                    rows.get(0).values, v -> ProfileText.valueName(ProfileRows.CLASS_SENSORS, v), rows.get(0).current);
            defaultSensorsPref.setOnPreferenceChangeListener((p, v) -> {
                changeDefault(() -> profiles.setDefaultSensors(Integer.parseInt((String) v)));
                return false;
            });
            category.addPreference(defaultSensorsPref);

            // No service call sets the location of apps that have no profile yet, so this row shows the value and offers none.
            ProfileRows.Row location = rows.get(1);
            category.addPreference(note(ctx, getString(R.string.class_location),
                    getString(ProfileText.valueName(ProfileRows.CLASS_LOCATION, location.current))));
            category.addPreference(note(ctx, null, getString(R.string.identity_defaults_location_note)));

            Preference visible = new Preference(ctx);
            visible.setPersistent(false);
            visible.setTitle(R.string.identity_visible_title);
            visible.setSummary(R.string.identity_visible_summary);
            visible.setIntent(new Intent(ctx, ExitVisibleActivity.class));
            category.addPreference(visible);
            return true;
        }

        /** A refused or failed change says so and changes nothing; the rows are read back from the service either way. */
        private void changeDefault(BooleanSupplier change) {
            if (!change.getAsBoolean()) {
                Toast.makeText(requireContext(), R.string.identity_error, Toast.LENGTH_LONG).show();
            }
            CompartmentsAppPolicy d = profiles.getDefaults();
            if (d != null && defaultProfilePref != null && defaultSensorsPref != null) {
                defaultProfilePref.setValue(Integer.toString(d.profile));
                defaultSensorsPref.setValue(Integer.toString(d.sensors));
            }
        }

        /** The values that change often, set in place so that the list does not flicker when the status is polled. */
        private void refresh(ExitRuntime.Snapshot s) {
            boolean failing = s.status.lastError != null && s.status.phase != Status.Phase.RUNNING;
            statusPref.setTitle(failing ? getString(R.string.status_error_title) : statusText(s.status));
            statusPref.setSummary(failing ? errorText(s.status.lastError) : statusSummary(s.status));
            if (mainSwitch != null) {
                boolean on = s.consent == Consent.ON;
                if (mainSwitch.isChecked() != on) {
                    mainSwitch.setChecked(on);
                }
            }
            int tor = s.choices.torApps().size();
            int open = s.choices.openApps().size();
            appsPref.setSummary(tor == 0 && open == 0 ? getString(R.string.apps_summary_none) : getString(R.string.apps_summary, tor, open));
            int allowed = Presenter.overrideRows(s.choices, names).size();
            overridesPref.setSummary(allowed == 0 ? getString(R.string.overrides_none) : getString(R.string.overrides_summary, allowed));
        }

        /** What went wrong, in plain words; the name of the exception only where the image is a debug build. */
        private String errorText(String lastError) {
            String plain = getString(R.string.status_error);
            // Build.IS_DEBUGGABLE is not in the SDK the app builds against; the build type says the same.
            boolean debug = "userdebug".equals(Build.TYPE) || "eng".equals(Build.TYPE);
            return debug ? plain + "\n" + getString(R.string.status_error_detail, lastError) : plain;
        }

        private String statusSummary(Status st) {
            switch (st.phase) {
                case RUNNING:
                    return getString(R.string.status_running_summary);
                case CONNECTING:
                    return getString(R.string.status_connecting_summary);
                case PAUSED:
                    return getString(R.string.status_paused_summary);
                default:
                    return getString(R.string.status_idle_summary);
            }
        }

        /** One company that the speed guard shows an address to for now; a tap hides it again. */
        private Preference promotedRow(Context ctx, Status.Promoted q) {
            String name = names.nameOf(q.company);
            String company = name != null ? name : getString(R.string.notif_unknown_company);
            Preference p = new Preference(ctx);
            p.setPersistent(false);
            p.setTitle(getString(R.string.override_row, index.labelOfUid(q.uid, getString(R.string.promoted_unknown_app)), company));
            p.setSummary(R.string.override_remove);
            p.setOnPreferenceClickListener(x -> {
                rt.release(q.uid, q.company);
                return true;
            });
            return p;
        }

        private String statusText(Status st) {
            switch (st.phase) {
                case RUNNING:
                    return getString(R.string.status_running);
                case CONNECTING:
                    return getString(R.string.status_connecting);
                case PAUSED:
                    return getString(R.string.status_paused);
                default:
                    return getString(R.string.status_idle);
            }
        }

        private static int warningText(Policy.Warning w) {
            switch (w) {
                case VPN_ACTIVE:
                    return R.string.warn_vpn;
                case PRIVATE_DNS_LIMITED:
                    return R.string.warn_dns;
                case MASK_TIER_OFF_TOO_MANY_APPS:
                    return R.string.warn_masks_off;
                case TOO_MANY_OPEN_APPS:
                    return R.string.warn_too_many_open;
                default:
                    return R.string.warn_too_many;
            }
        }
    }
}
