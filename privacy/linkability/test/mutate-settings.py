#!/usr/bin/env python3
"""Mutation check for SettingsHardeningTest: every mutant of SettingsHardening.java must fail it.
Usage: mutate-settings.py <SettingsHardening.java>"""
import os, subprocess, sys, tempfile

src = open(sys.argv[1]).read()
here = os.path.dirname(os.path.abspath(__file__))
mutants = {
    "allow device_name": ('"accessibility_captioning_font_scale");', '"accessibility_captioning_font_scale", "device_name");'),
    "drop font_scale": ('"font_scale", ', ''),
    "drop user_rotation": ('"accelerometer_rotation", "user_rotation", "show_ime_with_hard_keyboard",', '"accelerometer_rotation", "show_ime_with_hard_keyboard",'),
    "drop show_ime": ('"user_rotation", "show_ime_with_hard_keyboard",', '"user_rotation",'),
    "drop device_provisioned": ('"device_provisioned", "user_setup_complete",', '"user_setup_complete",'),
    "drop user_setup_complete": ('"device_provisioned", "user_setup_complete",', '"device_provisioned",'),
    "drop tts_default_synth": ('"tts_default_synth", "tts_default_locale",', '"tts_default_locale",'),
    "drop key_repeat_delay": ('"key_repeat_timeout", "key_repeat_delay",', '"key_repeat_timeout",'),
    "drop caption typeface": ('"accessibility_captioning_typeface", "accessibility_captioning_font_scale");', '"accessibility_captioning_font_scale");'),
    "prefix match": ("ALLOWED_FOR_APPS.contains(name)", "ALLOWED_FOR_APPS.stream().anyMatch(name::startsWith)"),
    "case fold": ("ALLOWED_FOR_APPS.contains(name)", "ALLOWED_FOR_APPS.contains(name.toLowerCase())"),
    "allow null": ("return name != null && ALLOWED_FOR_APPS.contains(name);", "return name == null || ALLOWED_FOR_APPS.contains(name);"),
    "list substring": ("entry.trim().equals(packageName)", "entry.contains(packageName)"),
    "list no trim": ("entry.trim().equals(packageName)", "entry.equals(packageName)"),
    "list null true": ("if (commaSeparated == null || packageName == null) {\n            return false;", "if (commaSeparated == null || packageName == null) {\n            return true;"),
}
survived = []
for name, (old, new) in mutants.items():
    assert src.count(old) == 1, (name, src.count(old))
    with tempfile.TemporaryDirectory() as d:
        os.makedirs(d + "/m")
        open(d + "/m/SettingsHardening.java", "w").write(src.replace(old, new))
        out = d + "/out"
        os.makedirs(out)
        c = subprocess.run(["javac", "-d", out, d + "/m/SettingsHardening.java", here + "/SettingsHardeningTest.java"], capture_output=True)
        assert c.returncode == 0, (name, c.stderr.decode())
        r = subprocess.run(["java", "-cp", out, "com.android.providers.settings.SettingsHardeningTest"], capture_output=True)
        print(("killed  " if r.returncode != 0 else "SURVIVED"), name)
        if r.returncode == 0:
            survived.append(name)
print(f"{len(mutants) - len(survived)} of {len(mutants)} killed")
sys.exit(1 if survived else 0)
