#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Mutation check of the pure Java of the exit app (model: freeholdos-services/exit-daemon/tools/run-mutations.py).

Copies the app directory to a temporary directory, applies ONE mutation at a time (an exact, unique string replaced by
another) and runs tools/run-host-checks.sh on the copy. A mutation is killed when the host checks fail for it. One that
does not compile proves nothing and is not counted as killed. A mutation whose text is not found exactly once in its file
is an error of this list, not a surviving mutant. Everything runs on the workstation (javac only).

    tools/run-mutations.py [--check-applies] [NAME_FILTER ...]

The result line is "N of N mutations killed"; the exit status is 0 only when all were killed.
"""
import os
import re
import shutil
import subprocess
import sys
import tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
J = "src/org/freeholdos/exit/"
U, C, K, P, L, D, X, DC, RC, LK, PRE, CN = (J + n + ".java" for n in ("UidRanges", "Choices", "Consent", "Policy", "Ladder", "ControlCodec", "ExitController", "DaemonClient", "RouteClient", "Link", "Presenter", "CompanyNames"))

# (name, file, old, new)
MUTATIONS = [
    # UidRanges
    ("uid: a negative user id", U, "if (userId < 0 || userId > MAX_USER) {", "if (userId > MAX_USER) {"),
    ("uid: the largest user id off by one", U, "userId > MAX_USER", "userId >= MAX_USER"),
    ("uid: user range end", U, "return new int[] {base + FIRST_APP, base + LAST_APP};", "return new int[] {base + FIRST_APP, base + LAST_APP + 1};"),
    ("uid: a duplicate uid starts a range", U, "if (last != null && uid <= last[1]) {", "if (last != null && uid < last[1]) {"),
    ("uid: adjacent uids do not join", U, "if (last != null && uid == last[1] + 1) {", "if (false) {"),
    ("uid: the input array is sorted in place", U, "int[] sorted = uids.clone();", "int[] sorted = uids;"),
    ("uid: a cut at the start of a range is skipped", U, "if (cut[1] < start || cut[0] > r[1]) {", "if (cut[1] <= start || cut[0] > r[1]) {"),
    ("uid: an empty piece before a cut", U, "if (cut[0] > start) {", "if (cut[0] >= start) {"),
    ("uid: a single uid left at the end is dropped", U, "if (start <= r[1]) {", "if (start < r[1]) {"),
    # Choices
    ("choices: the Tor limit is one more", C, "tor.size() >= MAX_TOR_APPS", "tor.size() > MAX_TOR_APPS"),
    ("choices: no Tor limit", C, "if (!tor.contains(pkg) && tor.size() >= MAX_TOR_APPS) {", "if (false) {"),
    ("choices: an app already on Tor counts against the limit", C, "if (!tor.contains(pkg) && tor.size() >= MAX_TOR_APPS) {", "if (tor.size() >= MAX_TOR_APPS) {"),
    ("choices: Direct does not remove the app", C, "tor.remove(pkg);\n            return true;", "return true;"),
    ("choices: an existing shown address counts against the limit", C, "if (!showsAddress(pkg, company) && shownCount() >= MAX_SHOWN) {", "if (shownCount() >= MAX_SHOWN) {"),
    ("choices: no limit on shown addresses", C, "if (!showsAddress(pkg, company) && shownCount() >= MAX_SHOWN) {", "if (false) {"),
    ("choices: company 0", C, "company < 1 || company > 0xffff", "company < 0 || company > 0xffff"),
    ("choices: company above 16 bits", C, "company < 1 || company > 0xffff", "company < 1 || company > 0x10000"),
    ("choices: package length 256", C, "name.length() <= 255", "name.length() <= 256"),
    ("choices: a package without a dot", C, "(\\\\.[A-Za-z][A-Za-z0-9_]*)+\");", "(\\\\.[A-Za-z][A-Za-z0-9_]*)*\");"),
    ("choices: the header is not checked", C, "if (!HEADER.equals(lines.get(0))) {", "if (false) {"),
    ("choices: the size limit is not checked", C, "if (text.length() > MAX_FILE_CHARS) {", "if (false) {"),
    ("choices: a Tor line is not applied", C, "ok = c.set(f[1], Choice.TOR);", "ok = true;"),
    ("choices: a show line is not applied", C, "ok = c.setShowsAddress(f[1], Integer.parseInt(f[2]), true);", "ok = true;"),
    ("choices: a company that is not digits is parsed", C, "f[2].matches(\"[0-9]{1,5}\")", "true"),
    ("choices: a bad line is not damage", C, "if (!ok) {\n                c.damaged = true;", "if (false) {\n                c.damaged = true;"),
    ("choices: an empty file is damage", C, "if (text == null || text.isEmpty()) {", "if (text == null) {"),
    ("choices: a Tor line is written in another case", C, "b.append(\"tor\\t\")", "b.append(\"Tor\\t\")"),
    ("choices: a show line is written without the company", C, ".append('\\t').append(c).append('\\n');", ".append('\\t').append(0).append('\\n');"),
    ("choices: a removed show leaves the package listed", C, "if (s.isEmpty()) {\n                    shown.remove(pkg);", "if (false) {\n                    shown.remove(pkg);"),
    # Consent
    ("consent: any case of on", K, "if (\"on\".equals(s)) {", "if (\"on\".equalsIgnoreCase(s)) {"),
    ("consent: any case of off", K, "if (\"off\".equals(s)) {", "if (\"off\".equalsIgnoreCase(s)) {"),
    ("consent: an unreadable value is on", K, "return UNSET;\n    }\n\n    public String", "return ON;\n    }\n\n    public String"),
    # Policy
    ("policy: the exempt packages are not exempt", P, "if (EXEMPT_PACKAGES.contains(a.pkg)) {", "if (false) {"),
    ("policy: an exempt app can be Tor", P, "tor = UidRanges.subtract(tor, exempt);", ""),
    ("policy: the Tor cut is not made", P, "if (tor.size() > MAX_RANGES) {", "if (false) {"),
    ("policy: the Tor cut keeps one more", P, "tor.subList(0, MAX_RANGES)", "tor.subList(0, MAX_RANGES + 1)"),
    ("policy: masked without consent", P, "if (consent == Consent.ON) {", "if (consent != Consent.OFF) {"),
    ("policy: masked always", P, "if (consent == Consent.ON) {", "if (true) {"),
    ("policy: masked includes Tor", P, "masked = UidRanges.subtract(UidRanges.subtract(all, tor), exempt);", "masked = UidRanges.subtract(all, exempt);"),
    ("policy: masked includes exempt", P, "masked = UidRanges.subtract(UidRanges.subtract(all, tor), exempt);", "masked = UidRanges.subtract(all, tor);"),
    ("policy: the steer limit is not applied", P, "if (masked.size() > MAX_RANGES) {", "if (false) {"),
    ("policy: the steer limit is one more", P, "if (masked.size() > MAX_RANGES) {", "if (masked.size() > MAX_RANGES + 1) {"),
    ("policy: the classes are not sorted", P, "classes.sort((x, y) -> Integer.compare(x.start, y.start));", ""),
    ("policy: another preference value", P, "prefs.put(a.pkg, OEM_PRIVATE_ONLY);", "prefs.put(a.pkg, 3);"),
    ("policy: a package of another user is skipped", P, "int appId = a.uid % UidRanges.PER_USER;", "int appId = a.uid;"),
    ("policy: a system uid is accepted", P, "appId < UidRanges.FIRST_APP ||", "false ||"),
    ("policy: a uid above the app ids is accepted", P, "|| appId > UidRanges.LAST_APP)", "|| false)"),
    ("policy: no VPN warning", P, "if (vpnActive) {", "if (!vpnActive) {"),
    ("policy: the DNS warning without consent", P, "if (privateDnsStrict && consent == Consent.ON) {", "if (privateDnsStrict) {"),
    ("policy: no DNS warning", P, "if (privateDnsStrict && consent == Consent.ON) {", "if (false) {"),
    ("policy: Tor class written as masked", P, "classes.add(new Cls(r[0], r[1], Kind.TOR));", "classes.add(new Cls(r[0], r[1], Kind.MASKED));"),
    ("policy: masked class written as Tor", P, "classes.add(new Cls(r[0], r[1], Kind.MASKED));", "classes.add(new Cls(r[0], r[1], Kind.TOR));"),
    ("policy: exempt class written as masked", P, "classes.add(new Cls(r[0], r[1], Kind.EXEMPT));", "classes.add(new Cls(r[0], r[1], Kind.MASKED));"),
    ("policy: Tor wire value", P, "TOR(1)", "TOR(2)"),
    ("policy: masked wire value", P, "MASKED(2)", "MASKED(1)"),
    ("policy: exempt wire value", P, "EXEMPT(3)", "EXEMPT(2)"),
    ("policy: a repeated user is two ranges", P, "new TreeSet<Integer>(toList(users))", "toList(users)"),
    # Ladder
    ("ladder: the window ends one millisecond early", L, "nowMs - t > WINDOW_MS", "nowMs - t >= WINDOW_MS"),
    ("ladder: old keys are never forgotten", L, "firstSeen.values().removeIf(t -> nowMs - t > WINDOW_MS);", ""),
    ("ladder: a second report notifies", L, "if (firstSeen.containsKey(k)) {", "if (false) {"),
    ("ladder: the table grows past its limit", L, "if (firstSeen.size() >= MAX_KEYS) {", "if (firstSeen.size() > MAX_KEYS) {"),
    ("ladder: the newest key is evicted", L, "if (e.getValue() < oldest) {", "if (e.getValue() > oldest) {"),
    ("ladder: the company is not part of the key", L, "return ((long) uid << 16) | (company & 0xffffL);", "return ((long) uid << 16);"),
    ("ladder: the uid is not part of the key", L, "return ((long) uid << 16) | (company & 0xffffL);", "return (company & 0xffffL);"),
    # ControlCodec
    ("codec: a frame of the maximum length is too long", D, "if (claimed > MAX_FRAME) {", "if (claimed >= MAX_FRAME) {"),
    ("codec: a frame past the maximum is read", D, "if (claimed > MAX_FRAME) {", "if (claimed > MAX_FRAME + 1) {"),
    ("codec: a complete frame is incomplete", D, "if (len < end) {", "if (len <= end) {"),
    ("codec: an empty frame is read", D, "if (claimed == 0) {", "if (false) {"),
    ("codec: an interface name of 16 bytes", D, "if (n < 1 || n > IFNAME_MAX) {", "if (n < 1 || n > IFNAME_MAX + 1) {"),
    ("codec: an empty interface name", D, "if (n < 1 || n > IFNAME_MAX) {", "if (n < 0 || n > IFNAME_MAX) {"),
    ("codec: the name . is accepted", D, "if (n == 1 && b[from] == '.' ||", "if (false ||"),
    ("codec: the name .. is accepted", D, "|| n == 2 && b[from] == '.' && b[from + 1] == '.') {", "|| false) {"),
    ("codec: a name with a dash is refused", D, "|| c == '.' || c == '-';", "|| c == '.';"),
    ("codec: a name with a dot is refused", D, "|| c == '_' || c == '.' ||", "|| c == '_' ||"),
    ("codec: a name with an underscore is refused", D, "|| c == '_' ||", "||"),
    ("codec: a name with upper case is refused", D, "c >= 'A' && c <= 'Z' ||", "false ||"),
    ("codec: a name with a space is accepted", D, "c >= '0' && c <= '9' ||", "c >= ' ' && c <= '9' ||"),
    ("codec: state 3 is accepted", D, "if (s > STATE_FAILED) {", "if (s > STATE_FAILED + 1) {"),
    ("codec: state 2 is refused", D, "if (s > STATE_FAILED) {", "if (s >= STATE_FAILED) {"),
    ("codec: error code 0 is accepted", D, "if (code < ERR_BAD_VALUE || code > ERR_FAILED) {", "if (code > ERR_FAILED) {"),
    ("codec: error code 6 is accepted", D, "if (code < ERR_BAD_VALUE || code > ERR_FAILED) {", "if (code < ERR_BAD_VALUE) {"),
    ("codec: error code 1 is refused", D, "code < ERR_BAD_VALUE ||", "code <= ERR_BAD_VALUE ||"),
    ("codec: error code 5 is refused", D, "code > ERR_FAILED) {", "code >= ERR_FAILED) {"),
    ("codec: a trailing byte is read", D, "if (have != want) {", "if (have < want) {"),
    ("codec: a short payload is read", D, "if (have != want) {", "if (have > want) {"),
    ("codec: a sequence number of 256", D, "if (seq < 0 || seq > 255) {", "if (seq < 0 || seq > 256) {"),
    ("codec: a negative sequence number", D, "if (seq < 0 || seq > 255) {", "if (seq > 255) {"),
    ("codec: 257 classes", D, "if (classes.size() > Policy.MAX_CLASSES) {", "if (classes.size() > Policy.MAX_CLASSES + 1) {"),
    ("codec: 256 classes are refused", D, "if (classes.size() > Policy.MAX_CLASSES) {", "if (classes.size() >= Policy.MAX_CLASSES) {"),
    ("codec: 33 companies", D, "if (companies.length > 32) {", "if (companies.length > 33) {"),
    ("codec: 32 companies are refused", D, "if (companies.length > 32) {", "if (companies.length >= 32) {"),
    ("codec: an empty interface name in a specification", D, "if (name.length == 0 || name.length > IFNAME_MAX", "if (name.length > IFNAME_MAX"),
    ("codec: a 16-byte interface name in a specification", D, "name.length > IFNAME_MAX || steer.size()", "name.length > IFNAME_MAX + 1 || steer.size()"),
    ("codec: 129 steer ranges", D, "steer.size() > Policy.MAX_RANGES", "steer.size() > Policy.MAX_RANGES + 1"),
    ("codec: 129 guard ranges", D, "guard.size() > Policy.MAX_RANGES)", "guard.size() > Policy.MAX_RANGES + 1)"),
    ("codec: 128 steer ranges are refused", D, "steer.size() > Policy.MAX_RANGES", "steer.size() >= Policy.MAX_RANGES"),
    ("codec: the class kind is not written", D, "o.write(c.kind.wire);", "o.write(0);"),
    ("codec: the network id is written as 16 bits", D, "putU32(o, netId);", "putU16(o, (int) netId);"),
    ("codec: the memory quota is written as its low half twice", D, "putU32(o, bytes >>> 32);", "putU32(o, bytes & 0xffffffffL);"),
    ("codec: a reply of the daemon accepts Applied", D, "                if (daemon) {\n                    throw new CodecException(\"unknown tag\");\n                }\n                exact(n, 3);", "                exact(n, 3);"),
    ("codec: a reply of the helper accepts State", D, "case TAG_STATE: {\n                if (!daemon) {\n                    throw new CodecException(\"unknown tag\");\n                }", "case TAG_STATE: {"),
    ("codec: a reply of the helper accepts Failures", D, "case TAG_FAILURES:\n                if (!daemon) {\n                    throw new CodecException(\"unknown tag\");\n                }", "case TAG_FAILURES:"),
    ("codec: the helper's Ready carries a name", D, "                exact(n, 1);\n                return new Reply(Type.READY, buf[p] & 0xff, null,", "                return new Reply(Type.READY, buf[p] & 0xff, null,"),
    ("codec: failures company is read from the uid", D, "((buf[p + 4] & 0xff) << 8) | (buf[p + 5] & 0xff),", "((buf[p + 0] & 0xff) << 8) | (buf[p + 1] & 0xff),"),
    # ExitController
    ("controller: consent alone does not start the daemon", X, "return consent == Consent.ON || !choices.torApps().isEmpty();", "return !choices.torApps().isEmpty();"),
    ("controller: an app on Tor does not start the daemon", X, "return consent == Consent.ON || !choices.torApps().isEmpty();", "return consent == Consent.ON;"),
    ("controller: no cleanup at the first start", X, "if (!started || wanted || phase != Status.Phase.IDLE) {", "if (wanted || phase != Status.Phase.IDLE) {"),
    ("controller: no deny-by-default preference", X, "                platform.setOemPreferences(out.oemPrefs);\n                appliedPrefs = out.oemPrefs;\n            } catch", "                appliedPrefs = out.oemPrefs;\n            } catch"),
        ("controller: the poll comes late", X, "if (nowMs >= nextPollMs) {", "if (nowMs > nextPollMs) {"),
    ("controller: the daemon state is not kept", X, "daemonState = p.state;", ""),
    ("controller: every failure notifies", X, "if (ladder.shouldNotify((int) f.uid, f.company, nowMs)) {", "if (true) {"),
    ("controller: the backoff starts at two seconds", X, "BACKOFF_MS[Math.min(attempts, BACKOFF_MS.length) - 1]", "BACKOFF_MS[Math.min(attempts, BACKOFF_MS.length - 1)]"),
    ("controller: the backoff is not reset", X, "            ranBefore = true;\n            attempts = 0;", "            ranBefore = true;"),
    ("controller: a lost link is not paused", X, "phase = ranBefore ? Status.Phase.PAUSED : Status.Phase.CONNECTING;", "phase = Status.Phase.CONNECTING;"),
    ("controller: the first failure is paused", X, "phase = ranBefore ? Status.Phase.PAUSED : Status.Phase.CONNECTING;", "phase = Status.Phase.PAUSED;"),
    ("controller: a run is not remembered", X, "            ranBefore = true;\n", ""),
    ("controller: the rules are not sent", X, "if (rulesChanged) {", "if (false) {"),
    ("controller: the table is not sent", X, "if (tableChanged) {", "if (false) {"),
    ("controller: the overrides are not sent", X, "        sendOverrides(choices);\n", ""),
    ("controller: a second network is registered", X, "if (needNetwork && netId == 0) {", "if (needNetwork) {"),
    ("controller: the network is not registered", X, "boolean needNetwork = !out.guard.isEmpty() || !out.oemPrefs.isEmpty();", "boolean needNetwork = false;"),
    ("controller: the rules do not get the network id", X, "            netId = platform.registerTorNetwork(iface);\n            route.apply(iface, netId, out.steer, out.guard);\n", "            netId = platform.registerTorNetwork(iface);\n"),
    ("controller: a change of the preferences is not sent", X, "if (appliedPrefs == null || !appliedPrefs.equals(out.oemPrefs)) {", "if (appliedPrefs == null) {"),
    ("controller: the network stays when the last Tor app leaves", X, "if (!needNetwork && netId != 0) {", "if (false) {"),
    ("controller: a change of the guard alone is not sent", X, "|| !same(applied.steer, out.steer) || !same(applied.guard, out.guard);", "|| !same(applied.steer, out.steer);"),
    ("controller: a change of the steer list alone is not sent", X, "|| !same(applied.steer, out.steer) || !same(applied.guard, out.guard);", "|| !same(applied.guard, out.guard);"),
    ("controller: an unchanged table is sent again", X, "boolean tableChanged = all || applied == null || !sameClasses(applied.classes, out.classes);", "boolean tableChanged = true;"),
    ("controller: an unchanged rule set is sent again", X, "boolean rulesChanged = all || applied == null ||", "boolean rulesChanged = true || all || applied == null ||"),
    ("controller: an unchanged override is sent again", X, "if (!want.get(e.getKey()).equals(sentOverrides.get(e.getKey()))) {", "if (true) {"),
    ("controller: a removed override is not cleared", X, "if (!want.containsKey(uid)) {", "if (false) {"),
    ("controller: the Tor network stays when a link is lost", X, "        platform.unregisterTorNetwork();\n        netId = 0;\n        daemon.close();", "        netId = 0;\n        daemon.close();"),
    ("controller: the rules stay when everything is off", X, "            route.clear();\n", ""),
    ("controller: the preference stays when everything is off", X, "            platform.setOemPreferences(Collections.<String, Integer>emptyMap());\n", ""),
    ("controller: the overrides are not sent to a new daemon", X, "            sentOverrides.clear();\n            appliedPrefs = null;", "            appliedPrefs = null;"),
    ("controller: the preferences are not sent to a new daemon", X, "            appliedPrefs = null;\n            netId = 0;\n            push(true);", "            netId = 0;\n            push(true);"),
    ("controller: the daemon is polled while idle", X, "        if (!wanted) {\n            return;\n        }\n        if (phase == Status.Phase.RUNNING) {", "        if (phase == Status.Phase.RUNNING) {"),
    ("controller: the daemon is not asked to exit", X, "                daemon.shutdown();\n", ""),
    ("controller: a daemon nobody asked for is shut down", X, "        if (wanted) {\n            // The daemon was asked for", "        if (true) {\n            // The daemon was asked for"),
    ("client: shutdown is not acknowledged", DC, "        link.send(ControlCodec.shutdown(s));\n        awaitAck(s);", "        link.send(ControlCodec.shutdown(s));"),
    # Presenter and CompanyNames
    ("presenter: the exit app is listed", PRE, "if (Policy.EXEMPT_PACKAGES.contains(a.pkg)) {\n                continue;", "if (false) {\n                continue;"),
    ("presenter: the search text is not lower-cased", PRE, "query.trim().toLowerCase(Locale.ROOT);", "query.trim();"),
    ("presenter: the search text is not trimmed", PRE, "query.trim().toLowerCase(Locale.ROOT);", "query.toLowerCase(Locale.ROOT);"),
    ("presenter: the package is not searched", PRE, " && !a.pkg.toLowerCase(Locale.ROOT).contains(q)", ""),
    ("presenter: the label is not searched", PRE, "!a.label.toLowerCase(Locale.ROOT).contains(q) && ", ""),
    ("presenter: the label is not lower-cased for the search", PRE, "!a.label.toLowerCase(Locale.ROOT).contains(q)", "!a.label.contains(q)"),
    ("presenter: apps with one label are not ordered by package", PRE, "return c != 0 ? c : x.pkg.compareTo(y.pkg);", "return c;"),
    ("presenter: the sort is case sensitive", PRE, "x.label.compareToIgnoreCase(y.label)", "x.label.compareTo(y.label)"),
    ("presenter: a tap does not toggle", PRE, "== Choices.Choice.TOR ? Choices.Choice.DIRECT : Choices.Choice.TOR;", "== Choices.Choice.TOR ? Choices.Choice.TOR : Choices.Choice.DIRECT;"),
    ("presenter: the row does not show Tor", PRE, "choices.choiceOf(a.pkg) == Choices.Choice.TOR));", "false));"),
    ("names: a row with four columns is read", CN, "if (f.length != 3 ||", "if (f.length < 3 ||"),
    ("names: an id that is not digits is read", CN, "!f[1].matches(\"[0-9]{1,5}\")", "false"),
    ("names: an empty name is read", CN, "|| f[2].isEmpty() ||", "||"),
    ("names: a name of 65 characters is read", CN, "f[2].length() > 64", "f[2].length() > 65"),
    ("names: company 0 is a name", CN, "if (id >= 1 && id <= 0xffff) {", "if (id >= 0 && id <= 0xffff) {"),
    ("names: an id past 16 bits is a name", CN, "if (id >= 1 && id <= 0xffff) {", "if (id >= 1) {"),
    ("names: the header is not checked", CN, "!\"exit-mask-list\\t1\".equals(lines[0])", "false"),
    ("names: the last row of an id wins", CN, "c.names.putIfAbsent(id,", "c.names.put(id,"),
    ("names: control characters are kept", CN, ".replaceAll(\"\\\\p{Cntrl}\", \"\")", ""),
    # DaemonClient, RouteClient, Link
    ("client: the sequence number does not wrap", DC, "seq = seq % 255 + 1; // 1..255\n        return seq;\n    }\n\n    @Override\n    public String connect()", "seq = seq + 1;\n        return seq;\n    }\n\n    @Override\n    public String connect()"),
    ("client: the sequence number wraps late", DC, "seq = seq % 255 + 1; // 1..255\n        return seq;\n    }\n\n    @Override\n    public String connect()", "seq = seq % 256 + 1;\n        return seq;\n    }\n\n    @Override\n    public String connect()"),
    ("client: the sequence is not reset on connect", DC, "        link.open();\n        seq = 0;", "        link.open();"),
    ("client: another protocol version is accepted", DC, "|| r.version != ControlCodec.DAEMON_PROTOCOL) {", ") {"),
    ("client: a wrong sequence number is accepted", DC, "if (r.type == ControlCodec.Type.ACK && r.seq == s) {", "if (r.type == ControlCodec.Type.ACK) {"),
    ("client: the reports after the state are not read", DC, "while (link.ready()) {", "while (link.ready() && false) {"),
    ("client: a report before the state is dropped", DC, "while ((r = link.next()).type == ControlCodec.Type.FAILURES) {\n            pending.add(new Failure(r.uid, r.company, r.count));\n        }", "while ((r = link.next()).type == ControlCodec.Type.FAILURES) {\n        }"),
    ("client: a report before an Ack is dropped", DC, "            if (r.type == ControlCodec.Type.FAILURES) {\n                pending.add(new Failure(r.uid, r.company, r.count));\n                continue;\n            }", "            if (r.type == ControlCodec.Type.FAILURES) {\n                continue;\n            }"),
    ("client: the reports are returned twice", DC, "        pending.clear();\n        return new Poll(state, out);", "        return new Poll(state, out);"),
    ("route client: another protocol version is accepted", RC, "|| r.version != ControlCodec.ROUTE_PROTOCOL) {", ") {"),
    ("route client: a wrong sequence number is accepted", RC, "if (r.type == ControlCodec.Type.ACK && r.seq == s) {", "if (r.type == ControlCodec.Type.ACK) {"),
    ("route client: the sequence is not reset on connect", RC, "        link.open();\n        seq = 0;", "        link.open();"),
    ("link: a stream that is ready when it is not", LK, "transport.in().available() > 0);", "transport.in().available() >= 0);"),
    ("link: the buffer is not shifted", LK, "System.arraycopy(buf, r.used, buf, 0, len - r.used);\n                len -= r.used;", "len -= r.used;"),
    ("link: a closed stream is read as empty", LK, "if (n < 0) {\n                close();", "if (n < -1) {\n                close();"),
]

_CONST = re.compile(r"^(?P<head>\s*(?:public |private )?static final (?:int|long) (?P<name>[A-Z0-9_]+) = )(?P<val>0x[0-9a-fA-F]+|[0-9]+)(?P<tail>L?;.*)$", re.M)


def step(name):
    """A tag constant moves by 16, so that it never lands on another tag (a duplicate case label is no mutation)."""
    return 16 if name.startswith("TAG_") else 1


def constant_mutations():
    """One mutation per plain numeric constant: its value plus one (a tag, plus 16). A constant that no test pins survives."""
    out = []
    for f in (U, C, P, L, D, X):
        text = open(os.path.join(ROOT, f)).read()
        for m in _CONST.finditer(text):
            old = m.group(0)
            if text.count(old) != 1:
                continue
            val = int(m.group("val"), 0)
            out.append((f"const: {os.path.basename(f)} {m.group('name')} + {step(m.group('name'))}", f, old, m.group("head") + str(val + step(m.group("name"))) + m.group("tail")))
    return out


MUTATIONS += constant_mutations()


def main() -> int:
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    only_check = "--check-applies" in sys.argv
    muts = [m for m in MUTATIONS if not args or any(f in m[0] for f in args)]
    work = tempfile.mkdtemp(prefix="exit-app-mutations-")
    copy = os.path.join(work, "app")
    shutil.copytree(ROOT, copy)
    bad = [n for n, f, old, new in muts if open(os.path.join(copy, f)).read().count(old) != 1]
    for n in bad:
        print(f"DOES NOT APPLY exactly once: {n}")
    if bad:
        return 3
    if only_check:
        print(f"all {len(muts)} mutations apply")
        shutil.rmtree(work)
        return 0
    script = os.path.join(copy, "tools", "run-host-checks.sh")
    base = subprocess.run(["bash", script, copy], capture_output=True, text=True)
    if base.returncode != 0:
        print("the unmutated host checks do not pass; nothing to measure")
        print(base.stdout[-2000:], base.stderr[-2000:])
        return 4
    killed, survivors, uncompiled = 0, [], []
    for name, f, old, new in muts:
        path = os.path.join(copy, f)
        orig = open(path).read()
        open(path, "w").write(orig.replace(old, new))
        try:
            r = subprocess.run(["bash", script, copy], capture_output=True, text=True, timeout=300)
            out = r.stdout + r.stderr
            if r.returncode == 0:
                verdict = "survived"
            elif "error:" in out and "FAIL:" not in out and "Exception" not in out:
                verdict = "does-not-compile"
            else:
                verdict = "killed"
        except subprocess.TimeoutExpired:
            verdict = "killed"  # an endless loop is a failure of the check as much as an assertion
        finally:
            open(path, "w").write(orig)
        print(f"{verdict:<17} {name}", flush=True)
        if verdict == "killed":
            killed += 1
        elif verdict == "survived":
            survivors.append(name)
        else:
            uncompiled.append(name)
    shutil.rmtree(work)
    print(f"{killed} of {len(muts)} mutations killed")
    for n in survivors:
        print(f"SURVIVED: {n}")
    for n in uncompiled:
        print(f"DID NOT COMPILE (proves nothing): {n}")
    return 0 if killed == len(muts) else 1


if __name__ == "__main__":
    sys.exit(main())
