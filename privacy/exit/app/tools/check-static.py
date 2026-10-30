#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Static checks of the exit app's manifest and permission files (ADR-184, ADR-186).

    check-static.py [APP_DIR]        exit status 0 when every rule holds, 1 with one line per broken rule

Rules: no INTERNET permission (the app talks to its services over local sockets); the app is persistent and direct-boot
aware and allows no backup; every exported component has a permission and none is an IA_SETTINGS tile any more (the
Compartments row is a preference of the Settings app); the privapp allowlist is exactly the manifest's privileged permissions; the
default-permissions file grants POST_NOTIFICATIONS and nothing else; no receiver object is registered for both the current
user and all users.
"""
import os
import re
import sys
import xml.etree.ElementTree as ET

ANDROID = "{http://schemas.android.com/apk/res/android}"
PKG = "org.freeholdos.exit"
# Permissions the platform grants without listing them in the privapp allowlist (normal, runtime or signature-only).
NOT_PRIVILEGED = {
    "android.permission.ACCESS_NETWORK_STATE",
    "android.permission.POST_NOTIFICATIONS",
    "android.permission.QUERY_ALL_PACKAGES",
}
IA_ACTION = "com.android.settings.action.IA_SETTINGS"
# The platform calls of the app and the permission that the system service checks for each (any one of the alternatives).
# The host checks never reach a real Binder call, so a call whose permission the manifest lacks passed every check and failed
# on the device: `UserManager.getUserHandles` needs MANAGE_USERS or CREATE_USERS and the app had QUERY_USERS, so each pass of the
# controller ended in a SecurityException (NET-009, lynx image 2026102303). Sources on the build server, 2026-10-29:
API_PERMISSIONS = [
    (r"\.getUserHandles\(", {"MANAGE_USERS", "CREATE_USERS"}),  # UserManagerService.getUsers: checkCreateUsersPermission
    (r"\.setOemNetworkPreference\(", {"CONTROL_OEM_PAID_NETWORK_PREFERENCE"}),  # ConnectivityService.java:4397
    (r"\.registerNetworkProvider\(|new NetworkAgent\(", {"NETWORK_FACTORY"}),  # ConnectivityService.java:4329
    (r"\.(getActiveNetwork|getNetworkCapabilities|getLinkProperties|registerDefaultNetworkCallback)\(", {"ACCESS_NETWORK_STATE"}),
    (r"\.registerReceiverForAllUsers\(", {"INTERACT_ACROSS_USERS_FULL"}),
    (r"\.createContextAsUser\(", {"INTERACT_ACROSS_USERS_FULL"}),
    (r"\.(getInstalledApplications|queryIntentActivities)\(", {"QUERY_ALL_PACKAGES"}),
    # The Compartments policy service (ADR-208) checks MANAGE_COMPARTMENTS on every call but getProfile. A name with a dot is the full name.
    (r"ServiceManager|ICompartmentsPolicy", {"org.freeholdos.permission.MANAGE_COMPARTMENTS"}),
]

# ADR-206, ADR-208: no string claims what the image does not hold. A string of the app that names a location, a place, the time
# zone or a persona (except on the page of what stays visible) needs the patches that move them in the package of the same
# service: the fixes of the persona (frameworks-base 0005) and the time zone and network strings (frameworks-base 0006).
# "takes the place of" is English for a replacement, not a place.
CLAIM_NAME = re.compile(r"^(class_location|value_location|identity_location|identity_time_zone|identity_defaults_location|persona|place)")
CLAIM_TEXT = re.compile(r"\b(location|time zone|persona)\b|\bplace\b(?! of\b)", re.I)
CLAIM_PATCHES = (("frameworks-base", "0005"), ("frameworks-base", "0006"))
# The patch that enforces what each row of ProfileRows offers: a row whose patch is not in the package would be a control that
# changes a value nothing reads.
ROW_PATCHES = {
    "CLASS_SENSORS": ("frameworks-native", "0003"),
    "CLASS_LOOPBACK": ("frameworks-base", "0002"),
    "CLASS_SETTINGS": ("frameworks-base", "0002"),
    "CLASS_LOCATION": ("frameworks-base", "0005"),
    "ROW_PLACE": ("frameworks-base", "0005"),
}

# The policy package of the same service: the numbers of ProfileRows are the numbers of the enums of its policy core (the
# interface constants), and its patches are what the strings and rows may claim.
PACKAGE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "..", "compartments-profiles")
CORE = os.path.join(PACKAGE, "system-server", "src", "com", "android", "server", "compartments", "Profiles.java")
ROW_CONSTANTS = {"PROFILE": "Profile", "CLASS": "Cls", "VALUE": "Value"}


def attr(el, name):
    return el.get(ANDROID + name)


def check(app, package=PACKAGE):
    problems = []
    manifest = ET.parse(os.path.join(app, "AndroidManifest.xml")).getroot()
    if manifest.get("package") != PKG:
        problems.append(f"package is not {PKG}")
    perms = {attr(p, "name") for p in manifest.findall("uses-permission")}
    if "android.permission.INTERNET" in perms:
        problems.append("the manifest requests INTERNET")
    src = os.path.join(app, "src")
    for root, _, files in os.walk(src):
        for f in sorted(files):
            if not f.endswith(".java"):
                continue
            text = open(os.path.join(root, f)).read()
            for pattern, alternatives in API_PERMISSIONS:
                if re.search(pattern, text) and not any((p if "." in p else "android.permission." + p) in perms for p in alternatives):
                    problems.append(f"{f} calls {pattern}: the manifest lacks {' or '.join(sorted(alternatives))}")
    application = manifest.find("application")
    if application is None:
        return problems + ["no <application>"]
    for flag, want in (("persistent", "true"), ("directBootAware", "true"), ("allowBackup", "false")):
        if attr(application, flag) != want:
            problems.append(f"application {flag} is not {want}")
    if attr(application, "debuggable") == "true":
        problems.append("application is debuggable")
    for comp in application:
        if comp.tag not in ("activity", "service", "receiver", "provider"):
            continue
        name = attr(comp, "name")
        filters = comp.findall("intent-filter")
        exported = attr(comp, "exported")
        if filters and exported is None:
            problems.append(f"{name}: has an intent filter and no explicit exported attribute")
        if exported == "true" and not attr(comp, "permission"):
            problems.append(f"{name}: exported without a permission")
        # Since the Compartments row is a preference of the Settings app (patch packages-apps-Settings/0002), nothing is
        # injected as a tile any more: a leftover IA_SETTINGS filter would list a second row that search cannot find.
        if any(attr(a, "name") == IA_ACTION for f in filters for a in f.findall("action")):
            problems.append(f"{name}: still an IA_SETTINGS tile")
    # The privapp allowlist is what the platform grants of the privileged permissions: it must match the manifest.
    privapp = ET.parse(os.path.join(app, f"privapp-permissions-{PKG}.xml")).getroot()
    listed = {p.get("name") for p in privapp.iter("permission")}
    want = perms - NOT_PRIVILEGED
    if listed != want:
        problems.append(f"privapp allowlist differs from the manifest: only listed {sorted(listed - want)}, "
                        f"only requested {sorted(want - listed)}")
    default = ET.parse(os.path.join(app, f"default-permissions-{PKG}.xml")).getroot()
    granted = [(e.get("package"), p.get("name")) for e in default.iter("exception") for p in e.iter("permission")]
    if granted != [(PKG, "android.permission.POST_NOTIFICATIONS")]:
        problems.append(f"default-permissions grants {granted}")
    problems += receiver_scopes(os.path.join(app, "src"))
    rows_java = os.path.join(app, "src", "org", "freeholdos", "exit", "ProfileRows.java")
    problems += patch_claims(os.path.join(app, "res", "values", "strings.xml"), package)
    problems += row_patches(rows_java, package)
    problems += row_numbers(rows_java)
    return problems


def has_patch(package, repo, number):
    """Whether the package holds the patch numbered `number` for `repo`."""
    folder = os.path.join(package, "patches", repo)
    return os.path.isdir(folder) and any(n.startswith(number + "-") and n.endswith(".patch") for n in os.listdir(folder))


def patch_claims(strings, package):
    """A string that names a location, a place, the time zone or a persona needs the patches of CLAIM_PATCHES in the package,
    except on the page of what stays visible, whose names start with identity_visible_."""
    missing = [f"{repo}/{n}" for repo, n in CLAIM_PATCHES if not has_patch(package, repo, n)]
    problems = []
    for s in ET.parse(strings).getroot().iter("string"):
        name, text = s.get("name"), "".join(s.itertext())
        if name.startswith("identity_visible_") or not missing:
            continue
        if CLAIM_NAME.match(name) or CLAIM_TEXT.search(text):
            problems.append(f"string {name} claims a location, place, time zone or persona feature and the package lacks "
                            f"patch {', '.join(missing)} (not built)")
    return problems


def row_patches(rows_java, package):
    """Every row that ProfileRows can offer needs the patch that enforces it."""
    if not os.path.exists(rows_java):  # a copy of the app without src, as the tests of the other rules make
        return []
    with open(rows_java, encoding="utf-8") as f:
        text = f.read()
    problems = []
    for const, (repo, number) in ROW_PATCHES.items():
        if re.search(r"\b" + const + r"\b", text) and not has_patch(package, repo, number):
            problems.append(f"ProfileRows offers {const} and the package lacks patch {repo}/{number}")
    return problems


def row_numbers(rows_java):
    """Every PROFILE_, CLASS_ and VALUE_ constant of ProfileRows names the member of the policy core's enum at the same number,
    and none is missing: the row of a class that the core does not hold would be set through a number it reads differently."""
    if not os.path.exists(rows_java):  # a copy of the app without src, as the tests of the other rules make
        return []
    try:
        with open(CORE, encoding="utf-8") as f:
            core = f.read()
    except OSError as e:
        return [f"cannot read the policy core {os.path.basename(CORE)}: {e.strerror}"]
    enums = {}
    for kind, names in re.findall(r"enum\s+(Profile|Cls|Value)\s*\{([^}]*)\}", core):
        enums[kind] = [n.strip() for n in names.split(",") if n.strip()]
    with open(rows_java, encoding="utf-8") as f:
        text = f.read()
    problems = []
    seen = {k: 0 for k in ROW_CONSTANTS}
    for prefix, name, number in re.findall(r"static final int (PROFILE|CLASS|VALUE)_([A-Z]+)\s*=\s*(\d+);", text):
        kind = ROW_CONSTANTS[prefix]
        seen[prefix] += 1
        members = enums.get(kind, [])
        if int(number) >= len(members) or members[int(number)] != name:
            problems.append(f"ProfileRows.{prefix}_{name} = {number}: the policy core's {kind} has no {name} at that number")
    for prefix, kind in ROW_CONSTANTS.items():
        if seen[prefix] != len(enums.get(kind, [])):
            problems.append(f"ProfileRows has {seen[prefix]} {prefix}_ constants and the policy core's {kind} has {len(enums.get(kind, []))}")
    return problems


def receiver_scopes(src):
    """One receiver object is registered for one user scope: the activity manager throws "previously registered for user -1"
    when it is registered with `registerReceiver` and `registerReceiverForAllUsers` (IMG-014, which only a device can show)."""
    problems = []
    for root, _, names in os.walk(src):
        for name in names:
            if not name.endswith(".java"):
                continue
            with open(os.path.join(root, name), encoding="utf-8") as f:
                text = f.read()
            plain = set(re.findall(r"\bregisterReceiver\(\s*(\w+)\s*,", text))
            all_users = set(re.findall(r"\bregisterReceiverForAllUsers\(\s*(\w+)\s*,", text))
            for receiver in sorted(plain & all_users):
                problems.append(f"{name}: receiver {receiver} is registered for the current user and for all users")
    return problems


def main():
    app = sys.argv[1] if len(sys.argv) > 1 else os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    problems = check(app)
    for p in problems:
        print(f"FAIL: {p}")
    if not problems:
        print("exit app static checks passed")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
