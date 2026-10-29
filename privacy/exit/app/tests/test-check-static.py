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
    ("AndroidManifest.xml", 'android:exported="false"', 'android:exported="true"', "exported without a permission"),
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
print("check-static tests passed")
sys.exit(0)
