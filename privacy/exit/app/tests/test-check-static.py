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
    ("privapp-permissions-org.oblivios.exit.xml", '<permission name="android.permission.MANAGE_USERS"/>', '', "only requested"),
    ("privapp-permissions-org.oblivios.exit.xml", '<permission name="android.permission.MANAGE_USERS"/>',
     '<permission name="android.permission.MANAGE_USERS"/><permission name="android.permission.CREATE_USERS"/>', "only listed"),
    ("default-permissions-org.oblivios.exit.xml", 'android.permission.POST_NOTIFICATIONS', 'android.permission.READ_CONTACTS',
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
for fname in ("AndroidManifest.xml", "privapp-permissions-org.oblivios.exit.xml"):
    p = os.path.join(d, "a", fname)
    text = open(p).read()
    assert text.count("android.permission.MANAGE_USERS") >= 1
    open(p, "w").write(text.replace("android.permission.MANAGE_USERS", "android.permission.QUERY_USERS"))
problems = cs.check(os.path.join(d, "a"))
assert any("getUserHandles" in x and "MANAGE_USERS" in x for x in problems), problems
shutil.rmtree(d)
# ADR-208: the allowlist and the manifest agree on MANAGE_COMPARTMENTS in both directions.
MANAGE = "org.oblivios.permission.MANAGE_COMPARTMENTS"
for fname, old, want in (
        ("privapp-permissions-org.oblivios.exit.xml", f'<permission name="{MANAGE}"/>', "only requested"),
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
                   ("privapp-permissions-org.oblivios.exit.xml", f'<permission name="{MANAGE}"/>')):
    p = os.path.join(d, "a", fname)
    text = open(p).read()
    assert text.count(old) == 1, (fname, old)
    open(p, "w").write(text.replace(old, ""))
problems = cs.check(os.path.join(d, "a"))
assert any("ServiceManager" in x and "MANAGE_COMPARTMENTS" in x for x in problems), problems
shutil.rmtree(d)

# ADR-206, ADR-208: no claim for a closure that is not in the image. A package of the patches that holds all of them, and one
# that lacks one, built as a folder of empty patch files.
PATCHES = {"frameworks-base": ("0002", "0005", "0006"), "frameworks-native": ("0003",)}


def package(without=(), empty=()):
    d = tempfile.mkdtemp()
    for repo, numbers in PATCHES.items():
        os.makedirs(os.path.join(d, "patches", repo))
        for n in numbers:
            if (repo, n) not in without:
                with open(os.path.join(d, "patches", repo, f"{n}-x.patch"), "w") as f:
                    if (repo, n) not in empty:
                        path = cs.PATCH_TOUCHES[(repo, n)]
                        f.write(f"diff --git a/{path} b/{path}\n")
    return d


def copy_of_app(*ignore):
    d = tempfile.mkdtemp()
    shutil.copytree(APP, os.path.join(d, "a"), ignore=shutil.ignore_patterns(*ignore, "tests", ".typecheck-*"))
    return d


full = package()
assert cs.check(APP, full) == [], cs.check(APP, full)
# The strings the page ships now name a location, a place and a persona: each missing patch of the claims is named.
for gone in (("frameworks-base", "0005"), ("frameworks-base", "0006")):
    pkg = package((gone,))
    problems = cs.check(APP, pkg)
    assert any("class_location" in x and "not built" in x and "/".join(gone) in x for x in problems), (gone, problems)
    assert any("place_title" in x and "not built" in x for x in problems), (gone, problems)
    shutil.rmtree(pkg)
# A string that names a place, the time zone or a persona, or that mentions one, fails without the patch and passes with it.
for add, want in (
        ('<string name="identity_location_title">x</string>', "identity_location_title"),
        ('<string name="persona_title">x</string>', "persona_title"),
        ('<string name="place_title2">x</string>', "place_title2"),
        ('<string name="shown_text">Your Location is invented.</string>', "shown_text"),
        ('<string name="shown_text">The time zone is another one.</string>', "shown_text"),
        ('<string name="shown_text">Choose a place for this app.</string>', "shown_text"),
        ('<string name="shown_text">The persona of this app.</string>', "shown_text")):
    d = copy_of_app("src")
    p = os.path.join(d, "a", "res", "values", "strings.xml")
    text = open(p).read()
    # the strings of the page are dropped first, so that only the added string can be the cause
    text = "\n".join(l for l in text.split("\n") if not any(k in l for k in (
        "class_location", "value_location", "place_", "persona_", "identity_time_zone", "identity_defaults_location",
        "profile_isolated_footer", "identity_substituted_summary", "app_footer_no_place",
        "compartments_explanation")))
    open(p, "w").write(text.replace("</resources>", "    " + add + "\n</resources>"))
    assert cs.check(os.path.join(d, "a"), full) == [], (add, cs.check(os.path.join(d, "a"), full))
    pkg = package((("frameworks-base", "0005"),))
    problems = cs.check(os.path.join(d, "a"), pkg)
    assert any(want in x and "not built" in x for x in problems), (add, problems)
    shutil.rmtree(pkg)
    shutil.rmtree(d)
# The page of what stays visible (ADR-206) may name them without any patch, and the sentence "takes the place of" of the VPN
# warning is not a place.
d = copy_of_app("src")
p = os.path.join(d, "a", "res", "values", "strings.xml")
text = open(p).read()
text = "\n".join(l for l in text.split("\n") if not any(k in l for k in (
    "class_location", "value_location", "place_", "persona_", "identity_time_zone", "identity_defaults_location",
    "profile_isolated_footer", "identity_substituted_summary", "app_footer_no_place",
        "compartments_explanation")))
open(p, "w").write(text.replace("</resources>", '    <string name="identity_visible_x_summary">Your location and time zone.</string>\n</resources>'))
pkg = package((("frameworks-base", "0005"), ("frameworks-base", "0006")))
assert cs.check(os.path.join(d, "a"), pkg) == [], cs.check(os.path.join(d, "a"), pkg)
shutil.rmtree(pkg)
shutil.rmtree(d)
# Each row that ProfileRows offers needs the patch that enforces it, and names the row.
for gone, row in ((("frameworks-native", "0003"), "CLASS_SENSORS"), (("frameworks-base", "0002"), "CLASS_LOOPBACK"),
                  (("frameworks-base", "0002"), "CLASS_SETTINGS"), (("frameworks-base", "0005"), "CLASS_LOCATION")):
    pkg = package((gone,))
    problems = cs.check(APP, pkg)
    assert any(row in x and "/".join(gone) in x and "offers" in x for x in problems), (gone, row, problems)
    shutil.rmtree(pkg)
# A patch of that number that does not change the file that enforces the claim does not count either (G1): an empty file, a
# patch of another file, a path that only ends like it.
for gone, row in (((("frameworks-native", "0003"), "CLASS_SENSORS"), ((("frameworks-base", "0002"), "CLASS_LOOPBACK")),
                   ((("frameworks-base", "0005"), "CLASS_LOCATION")))):
    pkg = package(empty=(gone,))
    problems = cs.check(APP, pkg)
    assert any(row in x and "/".join(gone) in x and "offers" in x for x in problems), (gone, row, problems)
    shutil.rmtree(pkg)
pkg = package()
other = os.path.join(pkg, "patches", "frameworks-base", "0005-x.patch")
open(other, "w").write("diff --git a/other/LocationProviderManager.java b/other/LocationProviderManager.java\n")
assert any("CLASS_LOCATION" in x for x in cs.check(APP, pkg)), cs.check(APP, pkg)
open(other, "w").write("diff --git a/x/" + cs.PATCH_TOUCHES[("frameworks-base", "0005")] + " b/x/" + cs.PATCH_TOUCHES[("frameworks-base", "0005")] + "\n")
assert any("CLASS_LOCATION" in x for x in cs.check(APP, pkg)), "a longer path that ends like the file"
shutil.rmtree(pkg)
# A file that is not a patch does not count.
pkg = package()
os.rename(os.path.join(pkg, "patches", "frameworks-base", "0005-x.patch"), os.path.join(pkg, "patches", "frameworks-base", "0005-x.txt"))
assert any("CLASS_LOCATION" in x for x in cs.check(APP, pkg)), cs.check(APP, pkg)
shutil.rmtree(pkg)
shutil.rmtree(full)

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
    p = os.path.join(d, "a", "src", "org", "oblivios", "exit", "ProfileRows.java")
    text = open(p).read()
    assert text.count(old) == 1, old
    open(p, "w").write(text.replace(old, new))
    problems = cs.check(os.path.join(d, "a"))
    assert any(want in x and "policy core" in x for x in problems), (want, problems)
    shutil.rmtree(d)
print("check-static tests passed")
sys.exit(0)
