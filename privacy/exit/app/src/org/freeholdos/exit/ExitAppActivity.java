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
import com.android.internal.compartments.CompartmentsPlace;
import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseActivity;
import com.android.settingslib.widget.FooterPreference;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

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

    public static final class AppFragment extends CalmPreferenceFragment implements ExitRuntime.Listener {
        private final Handler main = new Handler(Looper.getMainLooper());
        private ExitRuntime rt;
        private CompanyNames names;
        private String pkg;
        private ApplicationInfo info;
        private String built = "";
        /** The row of the network address; null while the page shows no such row. */
        private ListPreference addressPref;
        private ProfileClient profiles;
        /** The place id of the app as the service reported it when the page was last built; -1 when it has no place. */
        private int placeId = -1;
        /** The place the persona has now (also an Automatic one), 0 when none can be made, -1 when the location is not substituted. */
        private int personaPlaceId = -1;
        /** The place of {@link #placeId} and the place of {@link #personaPlaceId}, read with them; null when there is none. */
        private CompartmentsPlace place;
        private CompartmentsPlace personaPlace;
        private final ProfileClient.Reader<Loaded> reader = new ProfileClient.Reader<>(this::read, this::show);

        /** What the service reports about this app, read off the main thread (X1, G8). */
        private static final class Loaded {
            CompartmentsAppPolicy policy;
            int placeId = -1;
            int personaPlaceId = -1;
            CompartmentsPlace place;
            CompartmentsPlace personaPlace;
        }

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
                    addressPref = null;
                    screen.addPreference(ExitSettingsActivity.note(ctx, null, getString(R.string.owner_only)));
                    built = "owner";
                }
                return;
            }
            if (isFixed()) {
                show(new Loaded());
            } else {
                reader.run();
            }
        }

        private boolean isFixed() {
            return Policy.EXEMPT_PACKAGES.contains(pkg) || !UidRanges.isAppUid(info.uid);
        }

        /** Runs on the worker thread: every call to the service that the page needs, in one pass. */
        private Loaded read() {
            Loaded l = new Loaded();
            if (!profiles.available()) {
                return l;
            }
            l.policy = profiles.getApp(pkg);
            if (l.policy != null && l.policy.location == ProfileRows.VALUE_SUBSTITUTE) {
                l.placeId = profiles.getPlaceId(pkg);
                l.personaPlaceId = profiles.getPersonaPlaceId(pkg);
                l.place = l.placeId > 0 ? profiles.getPlace(l.placeId) : null;
                l.personaPlace = l.personaPlaceId > 0 ? profiles.getPlace(l.personaPlaceId) : null;
            }
            return l;
        }

        private void show(Loaded l) {
            Context ctx = getContext();
            if (ctx == null || rt == null || info == null) {
                return;
            }
            ExitRuntime.Snapshot s = rt.snapshot();
            PreferenceScreen screen = getPreferenceScreen();
            if (!rt.runsHere() || s == null) {
                return;  // render() puts up the owner note
            }
            boolean fixed = isFixed();
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
            CompartmentsAppPolicy policy = fixed ? null : l.policy;
            StringBuilder shape = new StringBuilder().append(fixed);
            for (Presenter.OverrideRow r : shown) {
                shape.append('|').append(r.company);
            }
            for (Status.Promoted q : promoted) {
                shape.append("|p").append(q.company);
            }
            if (policy != null) {
                // a change of the profile or of a value builds the rows again: which rows exist and what they show follow it
                placeId = l.placeId;
                personaPlaceId = l.personaPlaceId;
                place = l.place;
                personaPlace = l.personaPlace;
                shape.append("|i").append(policy.profile).append(policy.sensors).append(policy.loopback).append(policy.settings)
                        .append(policy.location).append('p').append(placeId).append('q').append(personaPlaceId)
                        .append(s.choices.choiceOf(pkg));
            }
            if (!shape.toString().equals(built)) {
                build(ctx, screen, fixed, shown, promoted, policy);
                built = shape.toString();
            }
            Choices.Choice now = s.choices.choiceOf(pkg);
            if (addressPref != null) {
                addressPref.setValue(Integer.toString(Arrays.asList(Presenter.options()).indexOf(now)));
            }
        }

        private void build(Context ctx, PreferenceScreen screen, boolean fixed, List<Presenter.OverrideRow> shown,
                List<Status.Promoted> promoted, CompartmentsAppPolicy policy) {
            screen.removeAll();
            addressPref = null;
            PackageManager pm = ctx.getPackageManager();
            CharSequence label = pm.getApplicationLabel(info);
            Preference header = ExitSettingsActivity.note(ctx, label, null);
            header.setIcon(pm.getApplicationIcon(info));
            screen.addPreference(header);
            if (fixed) {
                screen.addPreference(ExitSettingsActivity.note(ctx, null, getString(R.string.app_exempt)));
                return;
            }

            CharSequence footerText = policy == null ? null : addIdentity(ctx, screen, policy, label);

            PreferenceCategory network = category(ctx, R.string.address_title);
            screen.addPreference(network);
            Choices.Choice[] options = Presenter.options();
            int[] indexes = new int[options.length];
            for (int i = 0; i < indexes.length; i++) {
                indexes[i] = i;
            }
            addressPref = ProfileText.choice(ctx, "app_address", R.string.address_title, indexes,
                    i -> modeText(options[i]), 0);
            addressPref.setOnPreferenceChangeListener((p, v) -> {
                addressPref.setValue((String) v);  // shown at once; a refused stand is put back by the render that choose() asks for
                choose(options[Integer.parseInt((String) v)]);
                return false;
            });
            network.addPreference(addressPref);

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

            if (footerText != null) {
                FooterPreference footer = new FooterPreference(ctx);
                footer.setSummary(footerText);
                screen.addPreference(footer);
            }
        }

        private PreferenceCategory category(Context ctx, int title) {
            PreferenceCategory c = new PreferenceCategory(ctx);
            c.setPersistent(false);
            c.setTitle(title);
            return c;
        }

        /**
         * The device identity of the app (ADR-209): one row with the identity, or Custom when a detail differs from it, that opens
         * the radio page; then the four details, and the place of a Substituted app. Every row stores nothing itself: a change
         * goes to the service and the page is built again from what the service reports. Returns the text of the footer, which
         * the caller adds last, or null when the app has no identity rows.
         */
        private CharSequence addIdentity(Context ctx, PreferenceScreen screen, CompartmentsAppPolicy policy, CharSequence label) {
            // Signed with the platform key, the app is the system: the service applies no profile to it, so no row is offered.
            if (ctx.getPackageManager().checkSignatures(pkg, "android") == PackageManager.SIGNATURE_MATCH) {
                screen.addPreference(ExitSettingsActivity.note(ctx, null, getString(R.string.system_app_summary)));
                return null;
            }
            List<ProfileRows.Row> rows;
            int identity;
            try {
                rows = ProfileRows.rowsFor(policy.profile, policy.sensors, policy.loopback, policy.settings, policy.location);
                identity = ProfileRows.identityOf(policy.profile, policy.sensors, policy.loopback, policy.settings, policy.location);
            } catch (IllegalArgumentException e) {
                return null;
            }
            PreferenceCategory group = category(ctx, R.string.identity_title);
            screen.addPreference(group);
            Preference row = new Preference(ctx);
            row.setPersistent(false);
            row.setTitle(R.string.identity_title);
            row.setSummary(identity == ProfileRows.IDENTITY_CUSTOM ? R.string.identity_custom : ProfileText.profileName(identity));
            row.setOnPreferenceClickListener(x -> {
                startActivity(ExitIdentityActivity.intentForApp(ctx, pkg));
                return true;
            });
            group.addPreference(row);

            PreferenceCategory details = category(ctx, R.string.details_title);
            screen.addPreference(details);
            for (ProfileRows.Row r : rows) {
                final int cls = r.cls;
                ListPreference pref = ProfileText.choice(ctx, "app_class_" + cls, ProfileText.classTitle(cls), r.values,
                        v -> ProfileText.valueName(cls, v), r.current);
                pref.setOnPreferenceChangeListener((p, v) -> {
                    int value = Integer.parseInt((String) v);
                    ((ListPreference) p).setValue((String) v);  // shown at once; changed() builds the page again from the service, which puts a refused value back
                    ProfileClient.write(() -> profiles.setClassValue(pkg, cls, value), this::changed);
                    return false;
                });
                details.addPreference(pref);
            }

            ProfileRows.PlaceRow place = ProfileRows.placeRow(policy.location, personaPlaceId);
            if (place != ProfileRows.PlaceRow.HIDDEN) {
                PreferenceCategory placeGroup = category(ctx, R.string.place_title);
                screen.addPreference(placeGroup);
                placeGroup.addPreference(place == ProfileRows.PlaceRow.SHOWN ? placeRow(ctx)
                        : ExitSettingsActivity.note(ctx, getString(R.string.place_title), getString(R.string.place_not_available)));
                if (personaExit(ctx) == Presenter.Exit.MISMATCH) {
                    placeGroup.addPreference(ExitSettingsActivity.note(ctx, null, getString(R.string.persona_disagree)));
                    Preference tor = new Preference(ctx);
                    tor.setPersistent(false);
                    tor.setTitle(R.string.persona_use_tor);
                    tor.setOnPreferenceClickListener(x -> {
                        choose(Choices.Choice.TOR);
                        return true;
                    });
                    placeGroup.addPreference(tor);
                }
            }

            String footer = getString(R.string.app_footer_details);
            // P11: no place can be made, so the service took the location permissions of this app; say so
            return personaPlaceId == 0 && place == ProfileRows.PlaceRow.NOT_AVAILABLE
                    ? getString(R.string.app_footer_no_place, label) + " " + footer : footer;
        }

        /** The row that opens the place page: Automatic with the country it resolves to, the name of the place, or none. */
        private Preference placeRow(Context ctx) {
            Preference row = new Preference(ctx);
            row.setPersistent(false);
            row.setTitle(R.string.place_title);
            if (placeId == 0) {
                row.setSummary(getString(R.string.place_summary_automatic, AndroidPlatform.ownCountry(ctx)));
            } else if (place != null) {
                row.setSummary(place.name);
            } else {
                row.setSummary(R.string.place_none);
            }
            row.setOnPreferenceClickListener(x -> {
                startActivity(new Intent(ctx, ExitPlaceActivity.class).setData(Uri.fromParts("package", pkg, null)));
                return true;
            });
            return row;
        }

        /** What the place of this app asks of its exit; a place in the country the phone is in asks nothing. */
        private Presenter.Exit personaExit(Context ctx) {
            // the place the persona has now, also an Automatic one that was kept from before a trip (X5)
            CompartmentsPlace persona = personaPlace;
            ExitRuntime.Snapshot s = rt.snapshot();
            if (persona == null || persona.country == null || s == null) {
                return Presenter.Exit.NONE;
            }
            return Presenter.exitFor(persona.country.toUpperCase(Locale.ROOT), AndroidPlatform.ownCountry(ctx), s.choices.choiceOf(pkg));
        }

        /** A refused or failed change says so and changes nothing; the page is built again from the service either way. */
        private void changed(boolean ok) {
            if (getContext() == null) {
                return;
            }
            if (!ok) {
                String why = profiles.refusal();  // the reason of the service, when it gave one (X2)
                Toast.makeText(requireContext(), why != null ? why : getString(R.string.identity_error), Toast.LENGTH_LONG).show();
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
