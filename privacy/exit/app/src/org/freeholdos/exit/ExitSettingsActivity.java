// SPDX-License-Identifier: Apache-2.0
package org.freeholdos.exit;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MenuItem;

import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreferenceCompat;

import com.android.internal.compartments.CompartmentsAppPolicy;
import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity;
import com.android.settingslib.preference.PreferenceFragment;
import com.android.settingslib.widget.ButtonPreference;
import com.android.settingslib.widget.FooterPreference;
import com.android.settingslib.widget.TopIntroPreference;

/**
 * The "Compartments" entry under Network &amp; internet (a preference of the Settings app that starts this activity): the one-time
 * consent, the default device identity and the list of apps, the hidden-address switch, the hosts that the speed guard shows an
 * address to for now (with "Hide again"), the warnings, and the hosts the user showed an address to (ADR-209). It is built like
 * the pages of the Settings app (the collapsing toolbar, the preference widgets and the theme of SettingsLib, as the Updater
 * does); the fragment only draws what {@link Presenter} lists and posts each change to {@link ExitRuntime}.
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
        private Preference appsPref;
        private Preference overridesPref;
        /** The hide-my-address switch; null while the consent screen is shown. */
        private SwitchPreferenceCompat switchPref;
        /** The row of the default for new apps; null when the page has no identity group. */
        private Preference defaultPref;
        private ProfileClient profiles;

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
            k.append('|').append(index.listed.size()).append('|').append(profiles.available());
            return k.toString();
        }

        private void build(Context ctx, PreferenceScreen screen, ExitRuntime.Snapshot s) {
            screen.removeAll();
            switchPref = null;
            defaultPref = null;
            appsPref = null;
            overridesPref = null;
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
                buildMain(ctx, screen, s);
            }
        }

        /**
         * The page as the reference shows it (ADR-209): a one-sentence intro, the group of the device identity, the group of the
         * network address with its switch, the page of what stays visible, and one footer. An image without the policy service
         * has no identity words: only the network address, with the list of apps in its group, and the address-only footer.
         */
        private void buildMain(Context ctx, PreferenceScreen screen, ExitRuntime.Snapshot s) {
            CompartmentsAppPolicy defaults = profiles.available() ? profiles.getDefaults() : null;
            boolean identity = defaults != null;
            if (identity) {
                TopIntroPreference intro = new TopIntroPreference(ctx);
                intro.setTitle(R.string.compartments_intro);  // the widget shows its title, as in GrapheneOS's XML pages
                screen.addPreference(intro);
                PreferenceCategory group = category(ctx, R.string.identity_title);
                screen.addPreference(group);
                defaultPref = new Preference(ctx);
                defaultPref.setPersistent(false);
                defaultPref.setTitle(R.string.identity_defaults_profile);
                defaultPref.setIntent(ExitIdentityActivity.intentForDefaults(ctx));
                group.addPreference(defaultPref);
                appsPref = appsRow(ctx);
                appsPref.setSummary(R.string.apps_identity_summary);
                group.addPreference(appsPref);
            }

            PreferenceCategory network = category(ctx, R.string.address_title);
            screen.addPreference(network);
            switchPref = new SwitchPreferenceCompat(ctx);
            switchPref.setPersistent(false);
            switchPref.setTitle(R.string.masked_title);
            switchPref.setOnPreferenceChangeListener((p, v) -> {
                ExitRuntime.Snapshot now = rt.snapshot();
                if (now != null && (Boolean) v != (now.consent == Consent.ON)) {
                    rt.setConsent((Boolean) v ? Consent.ON : Consent.OFF);
                }
                return true;
            });
            network.addPreference(switchPref);
            for (Policy.Warning w : Policy.Warning.values()) {
                if (s.status.warnings.contains(w)) {
                    network.addPreference(note(ctx, null, getString(warningText(w))));
                }
            }
            if (s.choices.damaged()) {
                network.addPreference(note(ctx, null, getString(R.string.warn_damaged)));
            }
            overridesPref = new Preference(ctx);
            overridesPref.setPersistent(false);
            overridesPref.setTitle(R.string.overrides_title);
            overridesPref.setIntent(new Intent(ctx, ExitOverridesActivity.class));
            network.addPreference(overridesPref);
            if (!identity) {
                appsPref = appsRow(ctx);
                network.addPreference(appsPref);
            }

            if (!s.status.promotions.isEmpty()) {
                PreferenceCategory promoted = category(ctx, R.string.promoted_title);
                screen.addPreference(promoted);
                promoted.addPreference(note(ctx, null, getString(R.string.promoted_summary)));
                for (Status.Promoted q : s.status.promotions) {
                    promoted.addPreference(promotedRow(ctx, q));
                }
            }

            if (identity) {
                Preference visible = new Preference(ctx);
                visible.setPersistent(false);
                visible.setTitle(R.string.identity_visible_title);
                visible.setSummary(R.string.identity_visible_summary);
                visible.setIntent(new Intent(ctx, ExitVisibleActivity.class));
                screen.addPreference(visible);
            }

            FooterPreference footer = new FooterPreference(ctx);
            footer.setSummary(identity ? R.string.compartments_explanation : R.string.masked_summary);
            screen.addPreference(footer);
        }

        private PreferenceCategory category(Context ctx, int title) {
            PreferenceCategory c = new PreferenceCategory(ctx);
            c.setPersistent(false);
            c.setTitle(title);
            return c;
        }

        private Preference appsRow(Context ctx) {
            Preference p = new Preference(ctx);
            p.setPersistent(false);
            p.setTitle(R.string.apps_title);
            p.setIntent(new Intent(ctx, ExitAppsActivity.class));
            return p;
        }

        /** The values that change often, set in place so that the list does not flicker when the status is polled. */
        private void refresh(ExitRuntime.Snapshot s) {
            if (switchPref != null) {
                // D4: an error is the summary of the switch, its detail only on a debug build
                boolean failing = s.status.lastError != null && s.status.phase != Status.Phase.RUNNING;
                switchPref.setSummary(failing ? getString(R.string.status_error_title) + "\n" + errorText(s.status.lastError)
                        : statusSummary(s.status));
                boolean on = s.consent == Consent.ON;
                if (switchPref.isChecked() != on) {
                    switchPref.setChecked(on);
                }
            }
            if (defaultPref != null) {
                CompartmentsAppPolicy d = profiles.getDefaults();
                defaultPref.setSummary(d == null ? null : getString(ProfileText.profileName(d.profile)));
            }
            if (defaultPref == null && appsPref != null) {
                int tor = s.choices.torApps().size();
                int open = s.choices.openApps().size();
                appsPref.setSummary(tor == 0 && open == 0 ? getString(R.string.apps_summary_none) : getString(R.string.apps_summary, tor, open));
            }
            if (overridesPref != null) {
                int allowed = Presenter.overrideRows(s.choices, names).size();
                overridesPref.setSummary(allowed == 0 ? getString(R.string.overrides_none) : getString(R.string.overrides_summary, allowed));
            }
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
