// SPDX-License-Identifier: Apache-2.0
package org.freeholdos.exit;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MenuItem;

import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreferenceCompat;

import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity;
import com.android.settingslib.preference.PreferenceFragment;
import com.android.settingslib.widget.ButtonPreference;
import com.android.settingslib.widget.FooterPreference;
import com.android.settingslib.widget.MainSwitchPreference;

/**
 * The "Compartments" entry under Network &amp; internet (the {@code IA_SETTINGS} tile of the manifest): the status, the
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

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            Context ctx = requireContext();
            rt = ExitRuntime.get(ctx);
            names = rt.companyNames();
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

            if (s.consent != Consent.UNSET) {
                FooterPreference footer = new FooterPreference(ctx);
                footer.setSummary(R.string.masked_summary);
                screen.addPreference(footer);
            }
        }

        /** The values that change often, set in place so that the list does not flicker when the status is polled. */
        private void refresh(ExitRuntime.Snapshot s) {
            statusPref.setTitle(statusText(s.status));
            statusPref.setSummary(s.status.lastError != null && s.status.phase != Status.Phase.RUNNING
                    ? getString(R.string.status_error, s.status.lastError) : (CharSequence) null);
            if (mainSwitch != null) {
                boolean on = s.consent == Consent.ON;
                if (mainSwitch.isChecked() != on) {
                    mainSwitch.setChecked(on);
                }
            }
            appsPref.setSummary(getString(R.string.apps_summary, s.choices.torApps().size(), s.choices.openApps().size()));
            overridesPref.setSummary(getString(R.string.overrides_summary, Presenter.overrideRows(s.choices, names).size()));
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
