#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Mutation check of the host checks: every control is removed or broken in a copy of the sources,
and the checks must fail for each. A mutation that survives is a gap in the checks.

usage: run-mutations.py SRC_ROOT    (a tree that holds frameworks/base/...)
"""
import pathlib, shutil, subprocess, sys, tempfile

HERE = pathlib.Path(__file__).resolve().parent.parent
PKG = "frameworks/base/services/core/java/com/android/server/datamap/"

# (name, file, old, new, count) -- `old` must occur exactly `count` times
MUTATIONS = [
    ("store: no per-uid cap", "DataMapStore.java",
     "if (m.size() >= maxPerUid) {", "if (false) {", 1),
    ("store: eviction ignores recency", "DataMapStore.java",
     "new LinkedHashMap<>(16, 0.75f, true)", "new LinkedHashMap<>(16, 0.75f, false)", 1),
    ("store: a full store evicts from the smallest uid", "DataMapStore.java",
     "m.size() > best.size()", "m.size() < best.size()", 1),
    ("store: users share one bucket", "DataMapStore.java",
     "users.put(userId, u);\n        }\n        LinkedHashMap<String, Entry> m = u.byUid.get(uid);",
     "users.put(0, u);\n        }\n        LinkedHashMap<String, Entry> m = u.byUid.get(uid);", 1),
    ("store: restore keeps the later first-seen", "DataMapStore.java",
     "e.first = Math.min(e.first, r.firstWall);", "e.first = Math.max(e.first, r.firstWall);", 1),
    ("store: a change does not raise the version", "DataMapStore.java",
     "e.dns |= viaDns;\n        users.get(userId).version++;", "e.dns |= viaDns;", 1),
    ("links: any category links two apps", "LinkDeriver.java",
     "if (s == null || !Category.isTracker(s.category)) {", "if (s == null) {", 1),
    ("host names: any character is allowed", "HostnameValidator.java",
     "c == '-' || c == '_')", "c == '-' || c == '_' || true)", 1),
    ("host names: no limit on a label", "HostnameValidator.java",
     "if (labelLen > MAX_LABEL) {\n                    return null;\n                }", "", 1),
    ("classifier: unknown sources are accepted", "Classifier.java",
     "if (!sources.containsKey(e.getKey())) {", "if (false) {", 1),
    ("classifier: unknown owners are accepted", "Classifier.java",
     "if (!owners.containsKey(e.getKey())) {", "if (false) {", 1),
    ("classifier: a suffix matches inside a label", "Classifier.java",
     "int dot = host.indexOf('.', i);\n            if (dot < 0) {",
     "int dot = i < host.length() - 1 ? i : -1;\n            if (dot < 0) {", 1),
    ("history: one day too many is kept", "HistoryCodec.java",
     "if (d.getKey() > today - keepDays) {", "if (d.getKey() >= today - keepDays) {", 1),
    ("history: no limit on rows", "HistoryCodec.java",
     "if (++rows > MAX_ROWS) {", "if (false) {", 1),
    ("history: day index rounds toward zero", "HistoryCodec.java",
     "Math.floorDiv(wallMs + tzOffsetMs, DAY_MS)", "(wallMs + tzOffsetMs) / DAY_MS", 1),
    ("answers: not kept per app", "RecentAnswers.java",
     'uid + "|" + ip', "ip", 2),
    ("keys: no two-level suffixes", "ServiceKeys.java",
     "for (String t : TWO_LEVEL) {", "for (String t : new String[0]) {", 1),
    ("settings: 91 days is allowed", "UserSettings.java",
     "days > MAX_DAYS", "days > MAX_DAYS + 1", 2),
    ("settings: an unknown key is accepted", "UserSettings.java",
     "default:\n                        return defaults();", "default:\n                        break;", 1),
    ("settings: an unknown user is collected", "UserSettings.java",
     "return s != null && s.collecting;", "return s == null || s.collecting;", 1),
    ("pending: no limit", "PendingLimit.java",
     "if (n >= limit) {", "if (false) {", 1),
    ("pending: a finished event keeps its place", "PendingLimit.java",
     "held.decrementAndGet();", "held.get();", 1),
]

def main():
    src = pathlib.Path(sys.argv[1]).resolve()
    survivors = []
    for name, fname, old, new, count in MUTATIONS:
        tmp = pathlib.Path(tempfile.mkdtemp())
        try:
            shutil.copytree(src / "frameworks", tmp / "frameworks")
            f = tmp / PKG / fname
            text = f.read_text(encoding="utf-8")
            if text.count(old) != count:
                print(f"BROKEN MUTATION {name}: found {text.count(old)} of the old text, expected {count}")
                sys.exit(2)
            f.write_text(text.replace(old, new), encoding="utf-8")
            r = subprocess.run([str(HERE / "tools/run-host-checks.sh"), str(tmp)],
                               capture_output=True, text=True)
            killed = r.returncode != 0
            print(("KILLED   " if killed else "SURVIVED ") + name)
            if not killed:
                survivors.append(name)
        finally:
            shutil.rmtree(tmp, ignore_errors=True)
    print(f"{len(MUTATIONS) - len(survivors)} of {len(MUTATIONS)} mutations killed")
    sys.exit(1 if survivors else 0)

main()
