// SPDX-License-Identifier: Apache-2.0
package com.android.server.datamap;

public final class UserSettingsCheck {
    public static void main(String[] args) {
        UserSettings d = UserSettings.defaults();
        Check.isTrue(d.collecting, "collecting is on by default");
        Check.isTrue(!d.history, "history is off by default");
        Check.eq(30, d.days, "30 days when it is turned on");
        Check.isTrue(d.isDefault(), "the defaults are the defaults");

        UserSettings s = d.withHistory(true, 7);
        Check.isTrue(!s.isDefault(), "history on is not the default");
        Check.eq(7, UserSettings.decode(s.encode()).days, "days round trip");
        Check.isTrue(UserSettings.decode(s.encode()).history, "history round trip");
        Check.isTrue(UserSettings.decode(s.encode()).collecting, "collecting round trip");
        Check.isTrue(!UserSettings.decode(d.withCollecting(false).encode()).collecting, "collecting off round trip");
        Check.isTrue(!d.withCollecting(false).isDefault(), "collecting off is not the default");
        Check.isTrue(d.withHistory(false, 30).isDefault(), "history off at 30 days is the default again");

        // the range of days: 1 to 90
        Check.eq(1, new UserSettings(true, true, 1).days, "1 day");
        Check.eq(90, new UserSettings(true, true, 90).days, "90 days");
        Check.throwsA(IllegalArgumentException.class, () -> new UserSettings(true, true, 0), "0 days");
        Check.throwsA(IllegalArgumentException.class, () -> new UserSettings(true, true, 91), "91 days");
        Check.throwsA(IllegalArgumentException.class, () -> d.withHistory(true, -5), "negative days");

        // a damaged file never stops the service: defaults
        Check.isTrue(UserSettings.decode("").isDefault(), "empty");
        Check.isTrue(UserSettings.decode("garbage").isDefault(), "garbage");
        Check.isTrue(UserSettings.decode("datamap-settings\t2\n").isDefault(), "another version");
        Check.isTrue(UserSettings.decode("datamap-settings\t1\ndays\tx\n").isDefault(), "days that is no number");
        Check.isTrue(UserSettings.decode("datamap-settings\t1\ndays\t500\n").isDefault(), "days out of range");
        Check.isTrue(UserSettings.decode("datamap-settings\t1\nhistory\t2\n").isDefault(), "a flag of 2");
        // a valid choice next to an unknown key: the whole file is refused, the choice is not kept
        Check.isTrue(UserSettings.decode("datamap-settings\t1\nhistory\t1\nsurprise\t1\n").isDefault(), "an unknown key refuses the file");
        Check.isTrue(UserSettings.decode("datamap-settings\t1\nhistory\t1\t1\n").isDefault(), "too many fields");
        Check.passed("UserSettingsCheck");
    }
}
