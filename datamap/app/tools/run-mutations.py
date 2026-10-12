#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Mutation check of the app's host checks: each control is removed or broken in a copy of the sources, and
the checks must fail for each. A mutation that survives is a gap in the checks.

usage: run-mutations.py [APP_DIR]
"""
import pathlib, shutil, subprocess, sys, tempfile

HERE = pathlib.Path(__file__).resolve().parent
PKG = "src/org/sovereignos/datamap/model/"

# (name, file, old, new, count) -- `old` must occur exactly `count` times
MUTATIONS = [
    ("model: a line to the app's own publisher", "MapModel.java",
     "if (!o.id.equals(a.publisherId) && edgeKeys.add(", "if (edgeKeys.add(", 1),
    ("model: several lines for one app and company", "MapModel.java",
     "edgeKeys.add(e.pkg + \"\\t\" + o.id)", "(edgeKeys.add(e.pkg + \"\\t\" + o.id) || true)", 1),
    ("model: a company behind another one is not found", "MapModel.java",
     "if (ca && !cb) {", "if (false) {", 1),
    ("model: a company behind another one is found on one side only", "MapModel.java",
     "} else if (cb && !ca) {", "} else if (false) {", 1),
    ("model: a relation is shown when only one company is drawn", "MapModel.java",
     "if (ca != null && cb != null) {", "if (ca != null || cb != null) {", 1),
    ("model: a weaker category wins", "MapModel.java",
     "if (rank(e.category) > rank(co.category)) {", "if (rank(e.category) < rank(co.category)) {", 1),
    ("model: identity is not a tracker", "MapModel.java",
     "category <= CAT_IDENTITY", "category < CAT_IDENTITY", 1),
    ("model: core is a tracker", "MapModel.java",
     "category >= CAT_ANALYTICS", "category >= CAT_CORE", 1),
    ("model: apps are not sorted", "MapModel.java",
     "return Long.compare(y.lastWall, x.lastWall);", "return 0;", 2),
    ("model: an app with an unknown publisher is on the map", "MapModel.java",
     "a.onMap = a.publisherId != null && in.owners.containsKey(a.publisherId);",
     "a.onMap = a.publisherId != null;", 1),
    ("model: a service of an unknown owner is placed", "MapModel.java",
     "if (o == null) {\n                UnplacedService u", "if (o == null && false) {\n                UnplacedService u", 1),
    ("cluster: the limit is inclusive", "Cluster.java",
     "dx * dx + dy * dy < min2", "dx * dx + dy * dy <= min2", 1),
    ("cluster: a marker joins a marker that is itself grouped", "Cluster.java",
     "if (leader[j] != j) {\n                    continue;\n                }", "", 1),
    ("globe: east is to the left", "GlobeMath.java",
     "double x = cosLat * Math.sin(dLon);", "double x = -cosLat * Math.sin(dLon);", 1),
    ("globe: the tilt is not limited", "GlobeMath.java",
     "Math.max(-MAX_TILT, Math.min(MAX_TILT, lat0))", "lat0", 1),
    ("globe: the zoom is not limited", "GlobeMath.java",
     "Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom))", "zoom", 1),
    ("globe: longitude is not wrapped", "GlobeMath.java",
     "return w - 180.0;", "return lon;", 1),
    ("globe: the arc has no lift", "GlobeMath.java",
     "double lift = maxLift * Math.sin(Math.PI * t) * (omega / Math.PI);", "double lift = 0;", 1),
    ("globe: a point behind the globe stays where it is", "GlobeMath.java",
     "if (p.depth > 0) {\n            return new double[] {p.x, p.y};\n        }", "if (true) {\n            return new double[] {p.x, p.y};\n        }", 1),
    ("initials: only the first letter", "Initials.java", "letters < 2", "letters < 1", 1),
    ("initials: not upper case", "Initials.java", "Character.toUpperCase(cp)", "cp", 1),
    ("land: a file with trailing bytes is accepted", "LandData.java", "if (in.read() != -1) {", "if (false) {", 1),
    ("land: no limit on shapes", "LandData.java", "if (nShapes > MAX_SHAPES) {", "if (false) {", 1),
    ("land: a ring may have two points", "LandData.java", "if (n < 3 || total > MAX_POINTS) {", "if (total > MAX_POINTS) {", 1),
    ("land: any version is accepted", "LandData.java", "if (in.readUnsignedByte() != 1) {", "if (in.readUnsignedByte() < 0) {", 1),
    ("globe: the far side is visible", "GlobeMath.java",
     "return depth > 0;", "return true;", 1),
    ("selection: back always closes the sheet", "MapSelection.java",
     "sheet = app != null ? SHEET_HALF : SHEET_NONE;", "sheet = SHEET_NONE;", 1),
    ("selection: the handle does not open the full sheet", "MapSelection.java",
     "sheet = SHEET_FULL;", "sheet = SHEET_HALF;", 1),
    ("selection: half is not 52 percent", "MapSelection.java",
     "Math.round(height * 0.52f)", "Math.round(height * 0.5f)", 1),
    ("selection: the globe is smaller with the sheet", "MapSelection.java",
     "Math.min(width * 0.30f,", "Math.min(width * 0.31f,", 1),
    ("selection: the layout is in pixels, not dp", "MapSelection.java",
     "float top = 108 * density;", "float top = 108;", 1),
    ("selection: other apps' lines are drawn", "MapSelection.java",
     "if (app != null && !app.equals(pkg)) {", "if (false) {", 1),
    ("selection: a relation does not fade the lines", "MapSelection.java",
     "if (relation >= 0) {\n            return 0.10f;", "if (false) {\n            return 0.10f;", 1),
    ("selection: companies are never faint behind a relation", "MapSelection.java",
     "return relation >= 0 && !(ownerId.equals(relA) || ownerId.equals(relB));", "return false;", 1),
    ("selection: apps do not fade behind a relation", "MapSelection.java",
     "return relation >= 0 ? a * 0.4f : a;", "return a;", 1),
    ("selection: a company behind the first end is not found", "MapSelection.java",
     "if (met.contains(r.ownerA) && !met.contains(r.ownerB)) {", "if (false) {", 1),
    ("selection: a company behind the second end is not found", "MapSelection.java",
     "if (met.contains(r.ownerB) && !met.contains(r.ownerA)) {", "if (false) {", 1),
    ("selection: a relation with one end in view is drawn", "MapSelection.java",
     "if (in.contains(rel.ownerA) && in.contains(rel.ownerB)) {", "if (in.contains(rel.ownerA) || in.contains(rel.ownerB)) {", 1),
    ("globe: the angle ignores the cross product", "GlobeMath.java",
     "return Math.atan2(Math.sqrt(cx * cx + cy * cy + cz * cz), a[0] * b[0] + a[1] * b[1] + a[2] * b[2]);",
     "return Math.acos(Math.max(-1, Math.min(1, a[0] * b[0] + a[1] * b[1] + a[2] * b[2]))) + 0.5;", 1),
    ("globe: relation zoom has no floor", "GlobeMath.java",
     "Math.max(1.5, Math.min(2.6, 1.7 / Math.max(angle, 0.5)))", "Math.min(2.6, 1.7 / Math.max(angle, 0.5))", 1),
    ("globe: relation zoom has no ceiling", "GlobeMath.java",
     "Math.max(1.5, Math.min(2.6, 1.7 / Math.max(angle, 0.5)))", "Math.max(1.5, 1.7 / Math.max(angle, 0.5))", 1),
    ("flags: the letters are not offset", "FlagEmoji.java",
     "REGIONAL_INDICATOR_A + (c - 'A')", "REGIONAL_INDICATOR_A + (c - 'B')", 1),
    ("flags: any length is accepted", "FlagEmoji.java",
     "countryCode.length() != 2", "countryCode.length() < 1", 1),
    ("flags: any character is accepted", "FlagEmoji.java",
     "if (c < 'A' || c > 'Z') {", "if (false) {", 1),
    ("model: a local contact is listed", "MapModel.java",
     "if (HostFilter.isLocal(e.serviceKey)) {", "if (false) {", 1),
    ("hostfilter: loopback is a company", "HostFilter.java",
     "return n[0] == 0 || n[0] == 10 || n[0] == 127", "return n[0] == 0 || n[0] == 10", 1),
    ("hostfilter: the private 172 range starts at 15", "HostFilter.java",
     "(n[0] == 172 && n[1] >= 16 && n[1] <= 31)", "(n[0] == 172 && n[1] >= 15 && n[1] <= 31)", 1),
    ("hostfilter: the private 172 range ends at 32", "HostFilter.java",
     "(n[0] == 172 && n[1] >= 16 && n[1] <= 31)", "(n[0] == 172 && n[1] >= 16 && n[1] <= 32)", 1),
    ("hostfilter: carrier NAT starts at 63", "HostFilter.java",
     "n[1] >= 64 && n[1] <= 127", "n[1] >= 63 && n[1] <= 127", 1),
    ("hostfilter: carrier NAT ends at 128", "HostFilter.java",
     "n[1] >= 64 && n[1] <= 127", "n[1] >= 64 && n[1] <= 128", 1),
    ("hostfilter: 192.169 is private", "HostFilter.java",
     "(n[0] == 192 && n[1] == 168)", "(n[0] == 192 && n[1] >= 168)", 1),
    ("hostfilter: all 169 is link-local", "HostFilter.java",
     "(n[0] == 169 && n[1] == 254)", "(n[0] == 169)", 1),
    ("hostfilter: an octet over 255 is accepted", "HostFilter.java",
     "if (n[i] > 255) {", "if (false) {", 1),
    ("hostfilter: an octet of four digits is accepted", "HostFilter.java",
     "p[i].length() > 3", "p[i].length() > 4", 1),
    ("hostfilter: a name of one label is public", "HostFilter.java",
     "if (serviceKey.indexOf('.') < 0) {", "if (false) {", 1),
    ("hostfilter: .lan is public", "HostFilter.java",
     'serviceKey.endsWith(".lan")', 'serviceKey.endsWith(".lanx")', 1),
    ("hostfilter: .local without the dot", "HostFilter.java",
     'serviceKey.endsWith(".local")', 'serviceKey.endsWith("local")', 1),
    ("hostfilter: .internal without the dot", "HostFilter.java",
     'serviceKey.endsWith(".internal")', 'serviceKey.endsWith("internal")', 1),
    ("hostfilter: .home.arpa is public", "HostFilter.java",
     'serviceKey.endsWith(".home.arpa")', 'serviceKey.endsWith(".home.arpax")', 1),
    ("hostfilter: unique-local IPv6 is public", "HostFilter.java",
     '|| a.startsWith("fd");', '|| a.startsWith("fdx");', 1),
    ("hostfilter: fc00::/8 is public", "HostFilter.java",
     '|| a.startsWith("fc")', '|| a.startsWith("fcx")', 1),
    ("hostfilter: fe90 is public", "HostFilter.java",
     'a.startsWith("fe9")', 'a.startsWith("fe9x")', 1),
    ("hostfilter: fea0 is public", "HostFilter.java",
     'a.startsWith("fea")', 'a.startsWith("feax")', 1),
    ("hostfilter: fe80 is public", "HostFilter.java",
     'a.startsWith("fe8")', 'a.startsWith("fe8x")', 1),
    ("hostfilter: an empty key is local", "HostFilter.java",
     "if (serviceKey == null || serviceKey.isEmpty()) {\n            return false;", "if (serviceKey == null || serviceKey.isEmpty()) {\n            return true;", 1),
    ("role: the roles are swapped", "RelationRole.java",
     "if (viewedId.equals(r.ownerA)) {\n            return OBJECT;", "if (viewedId.equals(r.ownerA)) {\n            return SUBJECT;", 1),
    ("role: the other end is swapped", "RelationRole.java",
     "return role == OBJECT ? r.ownerB : role == SUBJECT ? r.ownerA : null;", "return role == OBJECT ? r.ownerA : role == SUBJECT ? r.ownerB : null;", 1),
    ("role: an unknown type is owns", "RelationRole.java",
     "return TYPES.length;", "return 0;", 1),
    ("role: no company is a company", "RelationRole.java",
     "if (viewedId == null) {\n            return NONE;\n        }", "", 1),
    # (end of the list: the mutations of a later task are added above this line)
]


def main():
    src = pathlib.Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else HERE.parent
    survivors = []
    base = pathlib.Path(tempfile.mkdtemp())
    try:
        for d in ("src", "tests", "tools"):
            shutil.copytree(src / d, base / d)
        r = subprocess.run([str(HERE / "run-host-checks.sh"), str(base)], capture_output=True, text=True)
        if r.returncode != 0:
            print("the unmutated sources do not pass the host checks:\n" + r.stdout + r.stderr)
            sys.exit(2)
    finally:
        shutil.rmtree(base, ignore_errors=True)
    for name, fname, old, new, count in MUTATIONS:
        tmp = pathlib.Path(tempfile.mkdtemp())
        try:
            shutil.copytree(src / "src", tmp / "src")
            shutil.copytree(src / "tests", tmp / "tests")
            shutil.copytree(src / "tools", tmp / "tools")
            f = tmp / PKG / fname
            text = f.read_text(encoding="utf-8")
            if text.count(old) != count:
                print(f"BROKEN MUTATION {name}: found {text.count(old)} of the old text, expected {count}")
                sys.exit(2)
            f.write_text(text.replace(old, new), encoding="utf-8")
            r = subprocess.run([str(HERE / "run-host-checks.sh"), str(tmp)], capture_output=True, text=True)
            if "error:" in r.stderr:   # javac, not a check: the mutation is not valid Java and proves nothing
                print(f"BROKEN MUTATION {name}: it does not compile\n{r.stderr}")
                sys.exit(2)
            killed = r.returncode != 0
            print(("KILLED   " if killed else "SURVIVED ") + name)
            if not killed:
                survivors.append(name)
        finally:
            shutil.rmtree(tmp, ignore_errors=True)
    print(f"{len(MUTATIONS) - len(survivors)} of {len(MUTATIONS)} mutations killed")
    sys.exit(1 if survivors else 0)


main()
