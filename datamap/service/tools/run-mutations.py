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
     "Math.floorDiv(wallMs + tzOffsetMs, DAY_MS)", "((wallMs + tzOffsetMs) / DAY_MS)", 1),
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
    ("history: the first time is not rounded to the hour", "HistoryCodec.java",
     ".append(toHour(r.firstWall))", ".append(r.firstWall)", 1),
    ("history: the last time is not rounded to the hour", "HistoryCodec.java",
     ".append(toHour(r.lastWall))", ".append(r.lastWall)", 1),
    ("history: the hour rounds toward zero", "HistoryCodec.java",
     "Math.floorDiv(wallMs, HOUR_MS) * HOUR_MS", "(wallMs / HOUR_MS) * HOUR_MS", 1),
    ("history: an unchanged row is kept after the switch-on", "HistoryCodec.java",
     "if (count <= 0) {", "if (count < 0) {", 1),
    ("history: the old count is not taken off", "HistoryCodec.java",
     "int count = r.count - b.count;", "int count = r.count;", 1),
    ("history: the old foreground count is not taken off", "HistoryCodec.java",
     "int fg = Math.max(0, r.fg - b.fg);", "int fg = r.fg;", 1),
    ("history: the old background count is not taken off", "HistoryCodec.java",
     "int bg = Math.max(0, r.bg - b.bg);", "int bg = r.bg;", 1),
    ("history: a row may start before the switch-on", "HistoryCodec.java",
     "Math.max(r.firstWall, switchedOnWall)", "r.firstWall", 1),
    ("history: a new row is dropped after the switch-on", "HistoryCodec.java",
     "if (b == null) {\n                out.add(r);\n                continue;\n            }", "if (b == null) {\n                continue;\n            }", 1),
    ("history: the baseline ignores the app", "HistoryCodec.java",
     'before.get(r.uid + "\\t" + r.key)', 'before.get(r.key)', 1),
    # (end of the list: the mutations of a later task are added above this line)
    ("store: a place that says nothing is kept", "DataMapStore.java",
     "if (e.place == null && place != null && place.known()) {", "if (e.place == null && place != null) {", 1),
    ("store: a later place replaces the first", "DataMapStore.java",
     "if (e.place == null && place != null && place.known()) {", "if (place != null && place.known()) {", 1),
    ("store: a restored row overrides the place", "DataMapStore.java",
     "if (e.place == null) {\n                e.place = r.place;", "if (true) {\n                e.place = r.place;", 1),
    ("store: the snapshot drops the place", "DataMapStore.java",
     "e.bg, e.dns, e.place));", "e.bg, e.dns, null));", 1),
    ("history: places are written when not chosen", "HistoryCodec.java",
     "if (withPlaces && p != null", "if (p != null", 1),
    ("history: a place with a tab is written", "HistoryCodec.java",
     "&& clean(p.city)", "&& true", 1),
    ("history: a bad kind is accepted", "HistoryCodec.java",
     "kind > ServerPlace.STEERED", "kind > 99", 1),
    ("history: a long country is accepted", "HistoryCodec.java",
     "|| f[11].length() > 2", "|| false", 1),
    ("history: a coordinate that is not a number is accepted", "HistoryCodec.java",
     "if (Float.isNaN(lat) ||", "if (false ||", 1),
    ("history: since drops the place", "HistoryCodec.java",
     "count, fg, bg, r.viaDns, r.place));", "count, fg, bg, r.viaDns, null));", 1),
    ("history: aggregate drops the place", "HistoryCodec.java",
     "m.place != null ? m.place : r.place", "null", 1),
    ("settings: the choice is not written", "UserSettings.java",
     "(serverHistory ? \"\\nserverHistory\\t1\" : \"\")", "\"\"", 1),
    ("settings: the choice counts as a default", "UserSettings.java",
     "days == DEFAULT_DAYS && !serverHistory;", "days == DEFAULT_DAYS;", 1),
    ("settings: a change of days loses the choice", "UserSettings.java",
     "return new UserSettings(collecting, enabled, newDays, serverHistory);", "return new UserSettings(collecting, enabled, newDays, false);", 1),
    ("keys: an IPv6 address is preferred", "ServiceKeys.java",
     "if (a.indexOf(':') < 0) {\n                return a;", "if (false) {\n                return a;", 1),
    ("ipmap: the anycast prefix is cut at the wrong place", "IpMap.java",
     "member32(ANY4, n >>> 8)", "member32(ANY4, n >>> 7)", 1),
    ("ipmap: the IPv6 anycast prefix is cut at the wrong place", "IpMap.java",
     "member64(ANY6, hi >>> 16)", "member64(ANY6, hi >>> 15)", 1),
    ("ipmap: a steered network is a place", "IpMap.java",
     "if (asn != 0 && member32(STEER, asn & 0xFFFFFFFFL)) {", "if (false) {", 1),
    ("ipmap: a city is taken against the country", "IpMap.java",
     "if (cc == 0 || lcc == cc) {", "if (true) {", 1),
    ("ipmap: a mapped IPv4 address stays IPv6", "IpMap.java",
     "g[4] == 0 && g[5] == 0xFFFF)", "g[4] == 1 && g[5] == 0xFFFF)", 1),
    ("ipmap: a leading zero is accepted", "IpMap.java",
     "(part.length() > 1 && part.charAt(0) == '0')", "false", 1),
    ("ipmap: an operator is taken from the next lower asn", "IpMap.java",
     "if (i < 0 || (mBuf.getInt(mOff[ORG] + i * 8) & 0xFFFFFFFFL) != key) {", "if (i < 0) {", 1),
    ("ipmap: bytes after the last section are accepted", "IpMap.java",
     "if (end != size) {", "if (false) {", 1),
    ("ipmap: a section out of place is accepted", "IpMap.java",
     "if (off != end || off + len > size || (STRIDE[i] != 0 && len % STRIDE[i] != 0)) {", "if (false) {", 1),
    ("ipmap: a zone is part of the address", "IpMap.java",
     "if (zone >= 0) {", "if (false) {", 1),
]

def main():
    src = pathlib.Path(sys.argv[1]).resolve()
    survivors = []
    base = subprocess.run([str(HERE / "tools/run-host-checks.sh"), str(src)], capture_output=True, text=True)
    if base.returncode != 0:
        print("the unmutated sources do not pass the host checks:\n" + base.stdout + base.stderr)
        sys.exit(2)
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
                if "error:" in r.stderr:   # javac, not a check: the mutation is not valid Java and proves nothing
                    print(f"BROKEN MUTATION {name}: it does not compile\n{r.stderr}")
                    sys.exit(2)
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
