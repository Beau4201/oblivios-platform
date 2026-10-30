#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""check-static.py must pass on the app as it is and fail, for the right reason, on each broken copy."""
import importlib.util
import os
import shutil
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
APP = os.path.dirname(HERE)
spec = importlib.util.spec_from_file_location("cs", os.path.join(APP, "tools", "check-static.py"))
cs = importlib.util.module_from_spec(spec)
spec.loader.exec_module(cs)

assert cs.check(APP) == [], cs.check(APP)

CASES = [
    ("AndroidManifest.xml", '<uses-permission android:name="android.permission.QUERY_ALL_PACKAGES" />',
     '<uses-permission android:name="android.permission.QUERY_ALL_PACKAGES" />\n<uses-permission android:name="android.permission.INTERNET" />',
     "requests INTERNET"),
    ("AndroidManifest.xml", 'android:persistent="true"', 'android:persistent="false"', "persistent is not true"),
    ("AndroidManifest.xml", 'android:directBootAware="true"', '', "directBootAware is not true"),
    ("AndroidManifest.xml", 'android:allowBackup="false"', 'android:allowBackup="true"', "allowBackup is not false"),
    ("AndroidManifest.xml", 'android:name=".ActionReceiver"\n            android:exported="false"',
     'android:name=".ActionReceiver"\n            android:exported="true"', "exported without a permission"),
    ("AndroidManifest.xml", 'android:name=".ExitAppsActivity"\n            android:label="@string/apps_title"\n            android:excludeFromRecents="true"\n            android:exported="false"',
     'android:name=".ExitAppsActivity"\n            android:label="@string/apps_title"\n            android:excludeFromRecents="true"\n            android:exported="true"', "exported without a permission"),
    ("AndroidManifest.xml", '<uses-permission android:name="android.permission.MANAGE_USERS" />', '', "only listed"),
    ("privapp-permissions-org.freeholdos.exit.xml", '<permission name="android.permission.MANAGE_USERS"/>', '', "only requested"),
    ("privapp-permissions-org.freeholdos.exit.xml", '<permission name="android.permission.MANAGE_USERS"/>',
     '<permission name="android.permission.MANAGE_USERS"/><permission name="android.permission.CREATE_USERS"/>', "only listed"),
    ("default-permissions-org.freeholdos.exit.xml", 'android.permission.POST_NOTIFICATIONS', 'android.permission.READ_CONTACTS',
     "default-permissions grants"),
]
for fname, old, new, want in CASES:
    d = tempfile.mkdtemp()
    shutil.copytree(APP, os.path.join(d, "a"), ignore=shutil.ignore_patterns("src", "tests", ".typecheck-*"))
    p = os.path.join(d, "a", fname)
    text = open(p).read()
    assert text.count(old) == 1, (fname, old)
    open(p, "w").write(text.replace(old, new))
    problems = cs.check(os.path.join(d, "a"))
    assert any(want in x for x in problems), (want, problems)
    shutil.rmtree(d)
# The defect of NET-009 (lynx image 2026102303): the manifest asks for QUERY_USERS, which none of the calls that list users accepts.
# The copy keeps src, so the source scan sees the call of getUserHandles.
d = tempfile.mkdtemp()
shutil.copytree(APP, os.path.join(d, "a"), ignore=shutil.ignore_patterns("tests", ".typecheck-*"))
for fname in ("AndroidManifest.xml", "privapp-permissions-org.freeholdos.exit.xml"):
    p = os.path.join(d, "a", fname)
    text = open(p).read()
    assert text.count("android.permission.MANAGE_USERS") >= 1
    open(p, "w").write(text.replace("android.permission.MANAGE_USERS", "android.permission.QUERY_USERS"))
problems = cs.check(os.path.join(d, "a"))
assert any("getUserHandles" in x and "MANAGE_USERS" in x for x in problems), problems
shutil.rmtree(d)
# ADR-208: the allowlist and the manifest agree on MANAGE_COMPARTMENTS in both directions.
MANAGE = "org.freeholdos.permission.MANAGE_COMPARTMENTS"
for fname, old, want in (
        ("privapp-permissions-org.freeholdos.exit.xml", f'<permission name="{MANAGE}"/>', "only requested"),
        ("AndroidManifest.xml", f'<uses-permission android:name="{MANAGE}" />', "only listed")):
    d = tempfile.mkdtemp()
    shutil.copytree(APP, os.path.join(d, "a"), ignore=shutil.ignore_patterns("src", "tests", ".typecheck-*"))
    p = os.path.join(d, "a", fname)
    text = open(p).read()
    assert text.count(old) == 1, (fname, old)
    open(p, "w").write(text.replace(old, ""))
    problems = cs.check(os.path.join(d, "a"))
    assert any(want in x and "MANAGE_COMPARTMENTS" in x for x in problems), (want, problems)
    shutil.rmtree(d)

# ADR-208: a call of the service without the permission passed every host check and failed on the device once already (NET-009).
# The copy keeps src, so the source scan sees ServiceManager and ICompartmentsPolicy; the allowlist is kept equal to the manifest.
d = tempfile.mkdtemp()
shutil.copytree(APP, os.path.join(d, "a"), ignore=shutil.ignore_patterns("tests", ".typecheck-*"))
for fname, old in (("AndroidManifest.xml", f'<uses-permission android:name="{MANAGE}" />'),
                   ("privapp-permissions-org.freeholdos.exit.xml", f'<permission name="{MANAGE}"/>')):
    p = os.path.join(d, "a", fname)
    text = open(p).read()
    assert text.count(old) == 1, (fname, old)
    open(p, "w").write(text.replace(old, ""))
problems = cs.check(os.path.join(d, "a"))
assert any("ServiceManager" in x and "MANAGE_COMPARTMENTS" in x for x in problems), problems
shutil.rmtree(d)

# ADR-206: no claim for a closure that is not in the image. A string that names a place, the time zone or a persona, or that
# mentions one, fails until the patch that enforces it is in the package (Task 19 of the plan replaces this by a patch check).
for add, want in (
        ('<string name="identity_location_title">x</string>', "identity_location_title"),
        ('<string name="persona_title">x</string>', "persona_title"),
        ('<string name="place_title">x</string>', "place_title"),
        ('<string name="shown_text">Your Location is invented.</string>', "shown_text"),
        ('<string name="shown_text">The time zone is another one.</string>', "shown_text"),
        ('<string name="shown_text">Choose a place for this app.</string>', "shown_text"),
        ('<string name="shown_text">The persona of this app.</string>', "shown_text")):
    d = tempfile.mkdtemp()
    shutil.copytree(APP, os.path.join(d, "a"), ignore=shutil.ignore_patterns("src", "tests", ".typecheck-*"))
    p = os.path.join(d, "a", "res", "values", "strings.xml")
    text = open(p).read()
    open(p, "w").write(text.replace("</resources>", "    " + add + "\n</resources>"))
    problems = cs.check(os.path.join(d, "a"))
    assert any(want in x and "not built" in x for x in problems), (add, problems)
    shutil.rmtree(d)
# The page of what stays visible (ADR-206) may name them, and the sentence "takes the place of" of the VPN warning is not a place.
d = tempfile.mkdtemp()
shutil.copytree(APP, os.path.join(d, "a"), ignore=shutil.ignore_patterns("src", "tests", ".typecheck-*"))
p = os.path.join(d, "a", "res", "values", "strings.xml")
text = open(p).read()
open(p, "w").write(text.replace("</resources>", '    <string name="identity_visible_x_summary">Your location and time zone.</string>\n</resources>'))
assert cs.check(os.path.join(d, "a")) == [], cs.check(os.path.join(d, "a"))
shutil.rmtree(d)

# ADR-208: the numbers and names of ProfileRows are those of the policy core, which the host test of the core pins.
for old, new, want in (
        ("    public static final int VALUE_BLOCKED = 2;\n", "    public static final int VALUE_BLOCKED = 2;\n    public static final int CLASS_CAMERA = 3;\n", "CLASS_CAMERA"),
        ("CLASS_LOOPBACK = 1;", "CLASS_LOOPBACK = 2;", "CLASS_LOOPBACK"),
        ("VALUE_SUBSTITUTE = 1;", "VALUE_SUBSTITUTE = 3;", "VALUE_SUBSTITUTE"),
        ("PROFILE_ISOLATED = 2;", "PROFILE_ISOLATED = 1;", "PROFILE_ISOLATED"),
        # a class of the core without a constant: the page could not set it, and a later row would be numbered by guess
        ("    public static final int VALUE_BLOCKED = 2;\n", "", "VALUE_ constants")):
    d = tempfile.mkdtemp()
    shutil.copytree(APP, os.path.join(d, "a"), ignore=shutil.ignore_patterns("tests", ".typecheck-*"))
    p = os.path.join(d, "a", "src", "org", "freeholdos", "exit", "ProfileRows.java")
    text = open(p).read()
    assert text.count(old) == 1, old
    open(p, "w").write(text.replace(old, new))
    problems = cs.check(os.path.join(d, "a"))
    assert any(want in x and "policy core" in x for x in problems), (want, problems)
    shutil.rmtree(d)
print("check-static tests passed")
sys.exit(0)
