package com.android.providers.settings;

/**
 * Host check of SettingsHardening: the allowlist is exactly the keys below (the 15 of the design
 * plus the in-process readers that the whole-branch review found) and nothing near them passes.
 * Run by run-settings-test.sh (javac and java, no Android).
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
                "user_rotation", "show_ime_with_hard_keyboard",
                // read by TextView and PhoneFallbackEventHandler in the app process; 1 after setup
                "device_provisioned", "user_setup_complete",
                // TtsEngines and TextToSpeechService: the engine, locale, rate and pitch of the user
                "tts_default_synth", "tts_default_locale", "tts_default_rate", "tts_default_pitch",
                // TextKeyListener, ShowSecretsSetting, ViewConfiguration
                "auto_caps", "auto_replace", "auto_punctuate", "show_password_touch",
                "show_password_physical", "key_repeat_timeout", "key_repeat_delay",
                "accessibility_text_cursor_blink_interval_ms",
                // ConfirmationPrompt: a running accessibility service refuses the prompt
                "accessibility_enabled",
                // CaptioningManager: how the app draws the captions of the user
                "accessibility_captioning_enabled", "accessibility_captioning_locale",
                "accessibility_captioning_easy_reader_enabled", "accessibility_captioning_preset",
                "accessibility_captioning_background_color",
                "accessibility_captioning_foreground_color", "accessibility_captioning_edge_type",
                "accessibility_captioning_edge_color", "accessibility_captioning_window_color",
                "accessibility_captioning_typeface", "accessibility_captioning_font_scale"};
        for (String key : allowed) {
            check(SettingsHardening.isAllowedForApps(key), "allowed: " + key);
        }
        // identifying or personal keys seen on the device: all must be hidden
        String[] hidden = {"device_name", "boot_count", "database_creation_buildid",
                "default_input_method", "next_alarm_formatted", "system_locales",
                "screen_brightness", "ringtone", "adb_enabled", "enabled_notification_listeners",
                "autofill_service", "oblivios_settings_open",
                "oblivios_settings_real_packages", "oblivios_loopback_open"};
        for (String key : hidden) {
            check(!SettingsHardening.isAllowedForApps(key), "hidden: " + key);
        }
        // matching is exact, not by prefix, substring or case
        check(!SettingsHardening.isAllowedForApps("font_scale2"), "no suffix match");
        check(!SettingsHardening.isAllowedForApps("device_font_scale"), "no substring match");
        check(!SettingsHardening.isAllowedForApps("FONT_SCALE"), "no case folding");
        check(!SettingsHardening.isAllowedForApps(""), "empty name");
        check(!SettingsHardening.isAllowedForApps(null), "null name");

        // the list settings: an app reads its own entries and no others
        String[] ownKeys = {"enabled_notification_listeners", "enabled_accessibility_services",
                "enabled_input_methods", "default_input_method"};
        for (String key : ownKeys) {
            check(SettingsHardening.isOwnEntryKey(key), "own-entry key: " + key);
            check(!SettingsHardening.isAllowedForApps(key), "still hidden as a whole: " + key);
        }
        check(!SettingsHardening.isOwnEntryKey("device_name"), "device_name is not a list");
        check(!SettingsHardening.isOwnEntryKey("enabled_notification_listeners2"), "exact key");
        check(!SettingsHardening.isOwnEntryKey(null), "null key");
        String list = "a.b/.X:c.d/.Y:e.f/.Z";
        check("c.d/.Y".equals(SettingsHardening.ownEntries(list, "c.d")), "one own entry");
        check(SettingsHardening.ownEntries(list, "x.y") == null, "no own entry");
        check("c.d/.Y:c.d/.W".equals(SettingsHardening.ownEntries("c.d/.Y:a.b/.X:c.d/.W", "c.d")),
                "two own entries keep their order and a colon between them");
        check("c.d/.Y;12;34".equals(SettingsHardening.ownEntries("a.b/.X:c.d/.Y;12;34", "c.d")),
                "an input method keeps its subtype ids");
        check(SettingsHardening.ownEntries("c.dx/.Y", "c.d") == null, "no prefix match");
        check(SettingsHardening.ownEntries("xc.d/.Y", "c.d") == null, "no suffix match");
        check(SettingsHardening.ownEntries("c.d/.Y", "c") == null, "no match on a leading part");
        check(SettingsHardening.ownEntries("c.d", "c.d") == null, "an entry without a class");
        check(SettingsHardening.ownEntries("/c.d", "c.d") == null, "an empty package");
        check(SettingsHardening.ownEntries("", "c.d") == null, "empty value");
        check(SettingsHardening.ownEntries(null, "c.d") == null, "null value");
        check(SettingsHardening.ownEntries(list, null) == null, "null package");
        check(SettingsHardening.ownEntries(list, "") == null, "empty package name");
        check(SettingsHardening.ownEntries("/.Y:c.d/.W", "") == null,
                "an entry with an empty package never matches an empty package name");

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
