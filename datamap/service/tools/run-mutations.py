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
     "LinkedHashMap<String, String> m = byUid.get(uid);\n        return m == null", "LinkedHashMap<String, String> m = byUid.get(10001);\n        return m == null", 1),
    ("keys: no two-level suffixes", "ServiceKeys.java",
     "for (String t : TWO_LEVEL) {", "for (String t : new String[0]) {", 1),
    ("settings: 91 days is allowed", "UserSettings.java",
     "days > MAX_DAYS", "days > MAX_DAYS + 1", 2),
    ("settings: an unknown key is accepted", "UserSettings.java",
     "default:\n                        return defaults();", "default:\n                        break;", 1),
    ("settings: an unknown user is collected", "UserSettings.java",
     "return s != null && s.collecting;", "return s == null || s.collecting;", 1),
    ("store: restoring no rows into a new user fails", "DataMapStore.java",
     "if (u == null) {   // no rows", "if (false) {   // no rows", 1),
    ("store: the reply keeps the oldest rows", "DataMapStore.java",
     "return Long.compare(b.lastWall, a.lastWall);", "return Long.compare(a.lastWall, b.lastWall);", 1),
    ("links: no cap on the reply", "LinkDeriver.java",
     "all.size() > maxLinks ?", "false ?", 1),
    ("keys: a mapped IPv4 address is a different address", "ServiceKeys.java",
     'if (s.startsWith("::ffff:")) {', "if (false) {", 1),
    ("keys: a name is stored in full", "ServiceKeys.java",
     "return viaDns ? keyFor(key, classifier) : key;", "return key;", 1),
    ("answers: one app can fill the room of all", "RecentAnswers.java",
     "if (m.size() > maxPerUid) {", "if (false) {", 1),
    ("answers: a user's names are kept after clear", "RecentAnswers.java",
     "it.next().getKey() / 100000 == userId", "it.next().getKey() / 100000 != userId", 1),
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
            try:
                r = subprocess.run([str(HERE / "tools/run-host-checks.sh"), str(tmp)],
                                   capture_output=True, text=True, timeout=300)
                killed = r.returncode != 0
            except subprocess.TimeoutExpired:
                killed = True   # a mutation that hangs a check is caught too
            print(("KILLED   " if killed else "SURVIVED ") + name)
            if not killed:
                survivors.append(name)
        finally:
            shutil.rmtree(tmp, ignore_errors=True)
    print(f"{len(MUTATIONS) - len(survivors)} of {len(MUTATIONS)} mutations killed")
    sys.exit(1 if survivors else 0)

main()
