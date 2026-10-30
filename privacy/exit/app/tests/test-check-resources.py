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
NOTIFIER = "src/org/freeholdos/exit/Notifier.java"
CASES = [
    (STRINGS, '<string name="owner_only">', '<string name="unused_one">x</string>\n    <string name="owner_only">', "unused_one is defined and not used"),
    (ACT, "R.string.owner_only", "R.string.not_there", "not_there is used and not defined"),
    (STRINGS, '<string name="notif_consent_text">Choose', '<string name="notif_consent_text">' + "x" * 150 + ' Choose', "notif_consent_text has"),
    (STRINGS, "%1$s is set to Extra private", "%s is set to Extra private", "placeholder without a number"),
    (ACT, "setContentView(R.layout.settings_activity);", 'setContentView(R.layout.settings_activity); getWindow().getDecorView().setBackgroundColor(android.graphics.Color.parseColor("#ffffff"));', "a colour is written into the code"),
    ("res/drawable/ic_compartments.xml", "@android:color/white", "#ffffff", "a colour is written into the resource"),
    (ACT, "R.string.status_idle_summary", "R.string.status_running_summary", "status_idle_summary is defined and not used"),
    # The rule of the review of 2026-10-22 (B1) had no case of its own: a call with too few arguments, and a qualified call.
    (NOTIFIER, "R.string.notif_promo_text, label(pkg), host", "R.string.notif_promo_text, host", "passes 1 arguments and the string has 2 placeholders"),
    (NOTIFIER, "ctx.getString(R.string.notif_promo_text, label(pkg), host)", "ctx.getString(R.string.notif_promo_text, label(pkg), host, host)", "passes 3 arguments"),
    # C2-02: a notification that is not secret shows an app and a company on the lock screen.
    (NOTIFIER, "VISIBILITY_SECRET) // names", "VISIBILITY_PRIVATE) // names", "VISIBILITY_PRIVATE or VISIBILITY_PUBLIC is used"),
    (NOTIFIER, "VISIBILITY_SECRET) // names", "VISIBILITY_PUBLIC) // names", "VISIBILITY_PRIVATE or VISIBILITY_PUBLIC is used"),
    (NOTIFIER, ".setVisibility(Notification.VISIBILITY_SECRET)\n                .setContentTitle(ctx.getString(R.string.consent_title))",
     ".setContentTitle(ctx.getString(R.string.consent_title))", "3 notification builders and 2 VISIBILITY_SECRET"),
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
