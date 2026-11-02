// SPDX-License-Identifier: Apache-2.0
package org.freeholdos.exit;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.android.internal.compartments.CompartmentsAppPolicy;
import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity;
import com.android.settingslib.widget.FooterPreference;
import com.android.settingslib.widget.SelectorWithWidgetPreference;

import java.util.Arrays;

/**
 * The radio page of the device identity (ADR-209), as GrapheneOS builds its own pickers: a title, one sentence under each value,
 * one footer. With a package as data it sets the identity of that app and starts with {@code Follow default}; without data it
 * sets the default for new apps. A tap goes to the service and the rows are built again from what the service reports, so the
 * page never shows an identity that was not stored. Not exported: the pages of Compartments open it.
 */
public final class ExitIdentityActivity extends CollapsingToolbarBaseActivity {
    /** The page of one app: its identity, or Follow default. */
    static Intent intentForApp(Context ctx, String pkg) {
        return new Intent(ctx, ExitIdentityActivity.class).setData(Uri.fromParts("package", pkg, null));
    }

    /** The page of the default for new apps. */
    static Intent intentForDefaults(Context ctx) {
        return new Intent(ctx, ExitIdentityActivity.class);
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.identity_activity);
        if (ExitAppActivity.packageOf(getIntent()) == null) {
            setTitle(R.string.identity_defaults_profile);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public static final class IdentityFragment extends CalmPreferenceFragment {
        private ProfileClient profiles;
        /** Null for the default for new apps. */
        private String pkg;
        private ApplicationInfo info;
        private final ProfileClient.Reader<Loaded> reader = new ProfileClient.Reader<>(this::read, this::render);

        /** What the service reports, read off the main thread (X1, G8). */
        private static final class Loaded {
            boolean available;
            CompartmentsAppPolicy defaults;
            CompartmentsAppPolicy app;
            boolean configured;
        }

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            Context ctx = requireContext();
            profiles = ProfileClient.find();
            pkg = ExitAppActivity.packageOf(requireActivity().getIntent());
            if (pkg != null) {
                try {
                    info = ctx.getPackageManager().getApplicationInfo(pkg, 0);
                } catch (PackageManager.NameNotFoundException e) {
                    info = null;
                }
            }
            setPreferenceScreen(getPreferenceManager().createPreferenceScreen(ctx));
            // Nothing to set: a package that is not installed, an exempt package or a system uid (no service: see render).
            if (pkg != null && (info == null || Policy.EXEMPT_PACKAGES.contains(pkg) || !UidRanges.isAppUid(info.uid))) {
                requireActivity().finish();
            }
        }

        @Override
        public void onStart() {
            super.onStart();
            reader.run();
        }

        /** Runs on the worker thread. */
        private Loaded read() {
            Loaded l = new Loaded();
            l.available = profiles.available();
            if (!l.available) {
                return l;
            }
            l.defaults = profiles.getDefaults();
            l.app = pkg == null ? null : profiles.getApp(pkg);
            String[] configured = pkg == null ? null : profiles.getConfigured();
            l.configured = configured != null && Arrays.asList(configured).contains(pkg);
            return l;
        }

        private void render(Loaded l) {
            Context ctx = getContext();
            if (ctx == null) {
                return;
            }
            if (!l.available) {
                requireActivity().finish();  // no service: nothing to set
                return;
            }
            PreferenceScreen screen = getPreferenceScreen();
            screen.removeAll();
            CompartmentsAppPolicy defaults = l.defaults;
            CompartmentsAppPolicy app = l.app;
            if (defaults == null || (pkg != null && app == null)) {
                screen.addPreference(ExitSettingsActivity.note(ctx, null, getString(R.string.identity_error)));
                return;
            }
            int selected;
            int[] choices;
            if (pkg == null) {
                screen.addPreference(ExitSettingsActivity.note(ctx, null, getString(R.string.identity_default_description)));
                selected = defaults.profile;
                choices = ProfileRows.profileChoices(true);
            } else {
                PackageManager pm = ctx.getPackageManager();
                Preference header = ExitSettingsActivity.note(ctx, pm.getApplicationLabel(info), null);
                header.setIcon(pm.getApplicationIcon(info));
                screen.addPreference(header);
                selected = ProfileRows.selectedChoice(l.configured, app.profile);
                choices = ProfileRows.profileChoices(false);
                screen.addPreference(radio(ctx, ProfileRows.CHOICE_FOLLOW_DEFAULT, selected, R.string.identity_follow_default,
                        getString(R.string.identity_follow_default_summary, getString(ProfileText.profileName(defaults.profile)))));
            }
            for (int profile : choices) {
                screen.addPreference(radio(ctx, profile, selected, ProfileText.profileName(profile),
                        getString(ProfileText.profileSummary(profile))));
            }
            FooterPreference footer = new FooterPreference(ctx);
            footer.setSummary(pkg == null ? R.string.identity_footer_default : R.string.identity_footer_app);
            screen.addPreference(footer);
        }

        private SelectorWithWidgetPreference radio(Context ctx, int value, int selected, int title, String summary) {
            SelectorWithWidgetPreference radio = new SelectorWithWidgetPreference(ctx);
            radio.setPersistent(false);
            radio.setTitle(title);
            radio.setSummary(summary);
            radio.setChecked(value == selected);
            radio.setOnClickListener(p -> {
                checkOnly(p);
                choose(value);
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

        /** A refused or failed change says so and changes nothing; the page is built again from the service either way. */
        private void choose(int value) {
            ProfileClient.write(() -> {
                if (pkg == null) {
                    return profiles.setDefaultProfile(value);
                }
                return value == ProfileRows.CHOICE_FOLLOW_DEFAULT ? profiles.clearApp(pkg) : profiles.setProfile(pkg, value);
            }, ok -> {
                if (getContext() == null) {
                    return;
                }
                if (!ok) {
                    String why = profiles.refusal();
                    Toast.makeText(requireContext(), why != null ? why : getString(R.string.identity_error), Toast.LENGTH_LONG).show();
                }
                reader.run();
            });
        }
    }
}
