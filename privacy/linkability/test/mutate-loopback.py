#!/usr/bin/env python3
"""Mutation check for LoopbackPermsTest: every mutant of the loopback functions in netd.c must fail it.
Usage: mutate-loopback.py <netd.c> <netd.h>"""
import os, subprocess, sys, tempfile

src = open(sys.argv[1]).read()
hdr = sys.argv[2]
here = os.path.dirname(os.path.abspath(__file__))
mutants = {
    "system sender no longer passes": ("if (is_system_uid(sender_uid) || is_system_uid(receiver_uid)) return true;\n\n    uint32_t sender_perms",
                                       "if (is_system_uid(receiver_uid)) return true;\n\n    uint32_t sender_perms"),
    "system receiver no longer passes": ("if (is_system_uid(sender_uid) || is_system_uid(receiver_uid)) return true;\n\n    uint32_t sender_perms",
                                         "if (is_system_uid(sender_uid)) return true;\n\n    uint32_t sender_perms"),
    "force needs only one bit": ("&& (permissions & PERMISSION_BIT_INTERACT_ACROSS_USERS_FULL);", "|| (permissions & PERMISSION_BIT_INTERACT_ACROSS_USERS_FULL);"),
    "receiver force ignored": ("|| can_force_loopback(receiver_perms)) return true;", "|| false) return true;"),
    "sender force ignored": ("if (can_force_loopback(sender_perms)\n        ||", "if (false\n        ||"),
    "same user passes": ("    if (same_profile) {\n", "    if (same_profile) {\n        return true;\n"),
    "same user never passes": ("        return (sender_perms & PERMISSION_BIT_USE_LOOPBACK_INTERFACE) != 0\n            || (receiver_perms & PERMISSION_BIT_USE_LOOPBACK_INTERFACE) != 0;",
                               "        return false;"),
    "sender permission only": ("        return (sender_perms & PERMISSION_BIT_USE_LOOPBACK_INTERFACE) != 0\n            || (receiver_perms & PERMISSION_BIT_USE_LOOPBACK_INTERFACE) != 0;",
                               "        return (sender_perms & PERMISSION_BIT_USE_LOOPBACK_INTERFACE) != 0;"),
    "receiver permission only": ("        return (sender_perms & PERMISSION_BIT_USE_LOOPBACK_INTERFACE) != 0\n            || (receiver_perms & PERMISSION_BIT_USE_LOOPBACK_INTERFACE) != 0;",
                                 "        return (receiver_perms & PERMISSION_BIT_USE_LOOPBACK_INTERFACE) != 0;"),
    "both permissions needed": ("        return (sender_perms & PERMISSION_BIT_USE_LOOPBACK_INTERFACE) != 0\n            || (receiver_perms & PERMISSION_BIT_USE_LOOPBACK_INTERFACE) != 0;",
                                "        return (sender_perms & PERMISSION_BIT_USE_LOOPBACK_INTERFACE) != 0\n            && (receiver_perms & PERMISSION_BIT_USE_LOOPBACK_INTERFACE) != 0;"),
    "user test on the app id": ("sender_uid / AID_USER_OFFSET == receiver_uid / AID_USER_OFFSET;\n    if (same_profile) {",
                                "sender_uid % AID_USER_OFFSET == receiver_uid % AID_USER_OFFSET;\n    if (same_profile) {"),
    "other user: USE_LOOPBACK admits": ("        return sender_perms & PERMISSION_BIT_INTERACT_ACROSS_USERS_FULL;\n    }",
                                        "        return sender_perms & (PERMISSION_BIT_INTERACT_ACROSS_USERS_FULL | PERMISSION_BIT_USE_LOOPBACK_INTERFACE);\n    }"),
    "same app, other user: no permission": ("            (sender_perms & PERMISSION_BIT_INTERACT_ACROSS_USERS_OR_PROFILES) ||\n            (sender_perms & PERMISSION_BIT_INTERACT_ACROSS_USERS_FULL);",
                                            "            (sender_perms & PERMISSION_BIT_INTERACT_ACROSS_USERS_FULL);"),
    "same app, other user: any": ("            (sender_perms & PERMISSION_BIT_INTERACT_ACROSS_USERS_OR_PROFILES) ||\n            (sender_perms & PERMISSION_BIT_INTERACT_ACROSS_USERS_FULL);",
                                  "            true;"),
    "uid_may_use: system uid fails": ("function bool uid_may_use_loopback(const uint32_t uid) {\n    if (is_system_uid(uid)) return true;",
                                      "function bool uid_may_use_loopback(const uint32_t uid) {\n    if (false) return true;"),
    "uid_may_use: USE ignored": ("return can_force_loopback(perms) || (perms & PERMISSION_BIT_USE_LOOPBACK_INTERFACE) != 0;",
                                 "return can_force_loopback(perms);"),
    "uid_may_use: force ignored": ("return can_force_loopback(perms) || (perms & PERMISSION_BIT_USE_LOOPBACK_INTERFACE) != 0;",
                                   "return (perms & PERMISSION_BIT_USE_LOOPBACK_INTERFACE) != 0;"),
}
survived = []
for name, (old, new) in mutants.items():
    assert src.count(old) == 1, (name, src.count(old))
    with tempfile.TemporaryDirectory() as d:
        open(d + "/netd.c", "w").write(src.replace(old, new))
        r = subprocess.run(["sh", here + "/run-loopback-test.sh", d + "/netd.c", hdr], capture_output=True)
        # A mutant that does not compile proves nothing: it is killed only when the check ran and printed a FAIL line.
        killed = r.returncode != 0 and b"FAIL" in r.stdout
        print("killed  " if killed else ("BROKEN  " if r.returncode != 0 else "SURVIVED"), name)
        if not killed:
            survived.append(name)
print(f"{len(mutants) - len(survived)} of {len(mutants)} killed")
sys.exit(1 if survived else 0)
