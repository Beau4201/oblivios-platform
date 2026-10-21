#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""check-resources.py must pass on the app as it is and fail, for the right reason, on each broken copy."""
import importlib.util
import os
import shutil
import sys
import tempfile

APP = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
spec = importlib.util.spec_from_file_location("cr", os.path.join(APP, "tools", "check-resources.py"))
cr = importlib.util.module_from_spec(spec)
spec.loader.exec_module(cr)
assert cr.check(APP) == [], cr.check(APP)

STRINGS = "res/values/strings.xml"
ACT = "src/org/freeholdos/exit/ExitSettingsActivity.java"
CASES = [
    (STRINGS, '<string name="owner_only">', '<string name="unused_one">x</string>\n    <string name="owner_only">', "unused_one is defined and not used"),
    (ACT, "R.string.owner_only", "R.string.not_there", "not_there is used and not defined"),
    (STRINGS, '<string name="notif_consent_text">Choose', '<string name="notif_consent_text">' + "x" * 150 + ' Choose', "notif_consent_text has"),
    (STRINGS, "%1$s uses Tor", "%s uses Tor", "placeholder without a number"),
    (ACT, "setContentView(scroll);", 'setContentView(scroll); scroll.setBackgroundColor(android.graphics.Color.parseColor("#ffffff"));', "a colour is written into the code"),
    ("res/drawable/ic_compartments.xml", "@android:color/white", "#ffffff", "a colour is written into the resource"),
    (ACT, "R.string.status_idle", "R.string.status_running", "status_idle is defined and not used"),
]
for fname, old, new, want in CASES:
    d = tempfile.mkdtemp()
    shutil.copytree(APP, os.path.join(d, "a"), ignore=shutil.ignore_patterns("tests", ".typecheck-*"))
    p = os.path.join(d, "a", fname)
    text = open(p).read()
    assert text.count(old) >= 1, (fname, old)
    open(p, "w").write(text.replace(old, new, 1))
    problems = cr.check(os.path.join(d, "a"))
    assert any(want in x for x in problems), (want, problems)
    shutil.rmtree(d)
print("check-resources tests passed")
