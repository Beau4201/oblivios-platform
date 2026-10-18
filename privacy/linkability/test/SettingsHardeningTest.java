package com.android.providers.settings;

/**
 * Host check of SettingsHardening: the allowlist is exactly the 15 keys of the design and nothing
 * near them passes. Run by run-settings-test.sh (javac and java, no Android).
 */
public final class SettingsHardeningTest {
    private static void check(boolean condition, String what) {
        if (!condition) {
            throw new AssertionError(what);
        }
    }

    public static void main(String[] args) {
        String[] allowed = {"airplane_mode_on", "animator_duration_scale",
                "transition_animation_scale", "window_animation_scale", "android_id",
                "long_press_timeout", "multi_press_timeout", "speak_password", "show_password",
                "font_scale", "time_12_24", "date_format", "accelerometer_rotation",
                "user_rotation", "show_ime_with_hard_keyboard"};
        for (String key : allowed) {
            check(SettingsHardening.isAllowedForApps(key), "allowed: " + key);
        }
        // identifying or personal keys seen on the device: all must be hidden
        String[] hidden = {"device_name", "boot_count", "database_creation_buildid",
                "default_input_method", "next_alarm_formatted", "system_locales",
                "screen_brightness", "ringtone", "adb_enabled", "enabled_notification_listeners",
                "autofill_service", "freeholdos_settings_open",
                "freeholdos_settings_real_packages", "freeholdos_loopback_open"};
        for (String key : hidden) {
            check(!SettingsHardening.isAllowedForApps(key), "hidden: " + key);
        }
        // matching is exact, not by prefix, substring or case
        check(!SettingsHardening.isAllowedForApps("font_scale2"), "no suffix match");
        check(!SettingsHardening.isAllowedForApps("device_font_scale"), "no substring match");
        check(!SettingsHardening.isAllowedForApps("FONT_SCALE"), "no case folding");
        check(!SettingsHardening.isAllowedForApps(""), "empty name");
        check(!SettingsHardening.isAllowedForApps(null), "null name");

        check(SettingsHardening.listContains("a.b,c.d", "c.d"), "second entry");
        check(SettingsHardening.listContains(" a.b , c.d ", "a.b"), "entries are trimmed");
        check(!SettingsHardening.listContains("a.bc,c.d", "a.b"), "no prefix match");
        check(!SettingsHardening.listContains("a.b", "a.b.c"), "no extension match");
        check(!SettingsHardening.listContains("", "a.b"), "empty list");
        check(!SettingsHardening.listContains(null, "a.b"), "null list");
        check(!SettingsHardening.listContains("a.b", null), "null package");
        System.out.println("ok");
    }
}
