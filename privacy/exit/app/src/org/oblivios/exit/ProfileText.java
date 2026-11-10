// SPDX-License-Identifier: Apache-2.0
package org.oblivios.exit;

import android.content.Context;

import androidx.preference.ListPreference;

/**
 * The words and the choice rows of the Compartments identity pages (ADR-208), shared by the main page and the page of one app.
 * Which profiles, classes and values exist is decided by {@link ProfileRows}; this class only names them.
 */
final class ProfileText {
    private ProfileText() {}

    static int profileName(int profile) {
        switch (profile) {
            case ProfileRows.PROFILE_TRUSTED:
                return R.string.profile_trusted;
            case ProfileRows.PROFILE_ISOLATED:
                return R.string.profile_isolated;
            default:
                return R.string.profile_protected;
        }
    }

    /** The sentence under a profile on the radio page. */
    static int profileSummary(int profile) {
        switch (profile) {
            case ProfileRows.PROFILE_TRUSTED:
                return R.string.identity_identified_summary;
            case ProfileRows.PROFILE_ISOLATED:
                return R.string.identity_substituted_summary;
            default:
                return R.string.identity_unlinked_summary;
        }
    }

    static int classTitle(int cls) {
        switch (cls) {
            case ProfileRows.CLASS_LOOPBACK:
                return R.string.class_loopback;
            case ProfileRows.CLASS_SETTINGS:
                return R.string.class_settings;
            case ProfileRows.CLASS_LOCATION:
                return R.string.class_location;
            default:
                return R.string.class_sensors;
        }
    }

    static int valueName(int cls, int value) {
        if (value == ProfileRows.VALUE_BLOCKED) {
            switch (cls) {
                case ProfileRows.CLASS_SETTINGS:
                    return R.string.value_settings_blocked;
                case ProfileRows.CLASS_LOOPBACK:
                    return R.string.value_loopback_blocked;
                default:
                    return R.string.value_blocked;
            }
        }
        switch (cls) {
            case ProfileRows.CLASS_LOOPBACK:
                return R.string.value_loopback_real;
            case ProfileRows.CLASS_SETTINGS:
                return R.string.value_settings_real;
            case ProfileRows.CLASS_LOCATION:
                return value == ProfileRows.VALUE_SUBSTITUTE ? R.string.value_location_substitute : R.string.value_location_real;
            default:
                return value == ProfileRows.VALUE_SUBSTITUTE ? R.string.value_sensors_substitute : R.string.value_sensors_real;
        }
    }

    /** What a choice row calls one of its profiles. */
    interface Names {
        int of(int value);
    }

    /**
     * A row that opens a dialog with the choices and shows the current one as its summary. It stores nothing itself: the caller
     * sets the value from what the service reports after each change, so the row never shows a value that was not stored.
     */
    static ListPreference choice(Context ctx, String key, int title, int[] values, Names names, int current) {
        CharSequence[] entries = new CharSequence[values.length];
        CharSequence[] entryValues = new CharSequence[values.length];
        for (int i = 0; i < values.length; i++) {
            entries[i] = ctx.getString(names.of(values[i]));
            entryValues[i] = Integer.toString(values[i]);
        }
        ListPreference p = new ListPreference(ctx);
        p.setKey(key);
        p.setPersistent(false);
        p.setTitle(title);
        p.setEntries(entries);
        p.setEntryValues(entryValues);
        p.setValue(Integer.toString(current));
        p.setSummaryProvider(ListPreference.SimpleSummaryProvider.getInstance());
        return p;
    }
}
