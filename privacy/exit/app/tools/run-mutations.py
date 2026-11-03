#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Mutation check of the pure Java of the exit app (model: freeholdos-services/exit-daemon/tools/run-mutations.py).

Copies the app directory to a temporary directory, applies ONE mutation at a time (an exact, unique string replaced by
another) and runs tools/run-host-checks.sh on the copy. A mutation is killed when the host checks fail for it. One that
does not compile proves nothing and is not counted as killed. A mutation whose text is not found exactly once in its file
is an error of this list, not a surviving mutant. Everything runs on the workstation (javac only).

    tools/run-mutations.py [--check-applies] [NAME_FILTER ...]

Left out on purpose: `choices: Tor wins over Open in choiceOf` (the order of the two tests in `Choices.choiceOf`): `set` keeps the Tor
and open lines apart and `parse` refuses an app on both, so no package is in both and the order cannot be seen.

Also left out: `sentCountries.clear()` in the idle path of `ExitController`. Nothing sends a country before `connect()`, which clears
the map again, so removing it cannot be seen (an equivalent mutant; the line stays as a second guard).

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
U, C, K, P, L, D, X, DC, RC, LK, PRE, CN, TN, ST, PR = (J + n + ".java" for n in ("UidRanges", "Choices", "Consent", "Policy", "Ladder", "ControlCodec", "ExitController", "DaemonClient", "RouteClient", "Link", "Presenter", "CompanyNames", "TunNet", "Status", "ProfileRows"))

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
    ("choices: an existing shown address counts against the limit", C, "if (!showsAddress(pkg, company) && shownCount() >= MAX_SHOWN) {", "if (shownCount() >= MAX_SHOWN) {"),
    ("choices: no limit on shown addresses", C, "if (!showsAddress(pkg, company) && shownCount() >= MAX_SHOWN) {", "if (false) {"),
    ("choices: company 0", C, "company < 1 || company > 0xffff", "company < 0 || company > 0xffff"),
    ("choices: company above 16 bits", C, "company < 1 || company > 0xffff", "company < 1 || company > 0x10000"),
    ("choices: package length 256", C, "name.length() <= 255", "name.length() <= 256"),
    ("choices: a package without a dot", C, "(\\\\.[A-Za-z][A-Za-z0-9_]*)+\");", "(\\\\.[A-Za-z][A-Za-z0-9_]*)*\");"),
    ("choices: the header is not checked", C, "if (!HEADER.equals(lines.get(0))) {", "if (false) {"),
    ("choices: the size limit is not checked", C, "if (text.length() > MAX_FILE_CHARS) {", "if (false) {"),
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
    ("policy: the classes are not sorted", P, "classes.sort((x, y) -> Integer.compare(x.start, y.start));", ""),
    ("policy: another preference value", P, "prefs.put(a.pkg, OEM_PRIVATE_ONLY);", "prefs.put(a.pkg, 3);"),
    ("policy: a package of another user is skipped", U, "int appId = uid % PER_USER;", "int appId = uid;"),
    ("policy: a system uid is accepted", U, "return appId >= FIRST_APP && appId <= LAST_APP;", "return appId <= LAST_APP;"),
    ("policy: a uid above the app ids is accepted", U, "return appId >= FIRST_APP && appId <= LAST_APP;", "return appId >= FIRST_APP;"),
    ("presenter: a system uid is listed", PRE, "if (!UidRanges.isAppUid(a.uid)) {", "if (false) {"),
    ("policy: no VPN warning", P, "if (vpnActive) {", "if (!vpnActive) {"),
    ("policy: the DNS warning without consent", P, "if (privateDnsStrict && consent == Consent.ON) {", "if (privateDnsStrict) {"),
    ("policy: no DNS warning", P, "if (privateDnsStrict && consent == Consent.ON) {", "if (false) {"),
    ("policy: Tor class written as masked", P, "classes.add(new Cls(r[0], r[1], Kind.TOR));", "classes.add(new Cls(r[0], r[1], Kind.MASKED));"),
    ("policy: masked class written as Tor", P, "classes.add(new Cls(r[0], r[1], Kind.MASKED));", "classes.add(new Cls(r[0], r[1], Kind.TOR));"),
    ("policy: Tor wire value", P, "TOR(1)", "TOR(2)"),
    ("policy: masked wire value", P, "MASKED(2)", "MASKED(1)"),
    ("policy: exempt wire value", P, "EXEMPT(3)", "EXEMPT(2)"),
    ("policy: a repeated user is two ranges", P, "new TreeSet<Integer>(toList(users))", "toList(users)"),
    # Ladder
    ("ladder: the window ends one millisecond early", L, "nowMs - t > WINDOW_MS", "nowMs - t >= WINDOW_MS"),
    ("ladder: old keys are never forgotten", L, "firstSeen.values().removeIf(t -> nowMs - t > WINDOW_MS);", ""),
    ("ladder: the window is the daemon's ten minutes", L, "6 * 60 * 60 * 1000L", "10 * 60 * 1000L"),
    ("controller: failures are notified while Tor starts", X, "daemonState != ControlCodec.STATE_BOOTSTRAPPING && ", ""),
    ("controller: only an up daemon notifies", X, "daemonState != ControlCodec.STATE_BOOTSTRAPPING &&", "daemonState == ControlCodec.STATE_UP &&"),
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
    ("codec: a reply of the helper accepts State", D, "case TAG_STATE: {\n                if (!daemon) {\n                    throw new CodecException(\"unknown tag\");\n                }", "case TAG_STATE: {"),
    ("codec: a reply of the helper accepts Failures", D, "case TAG_FAILURES:\n                if (!daemon) {\n                    throw new CodecException(\"unknown tag\");\n                }", "case TAG_FAILURES:"),
    ("codec: the helper's Ready carries a name", D, "                exact(n, 1);\n                return new Reply(Type.READY, buf[p] & 0xff, null,", "                return new Reply(Type.READY, buf[p] & 0xff, null,"),
    # ExitController
    ("controller: consent alone does not start the daemon", X, "return consent == Consent.ON || !choices.torApps().isEmpty();", "return !choices.torApps().isEmpty();"),
    ("controller: an app on Tor does not start the daemon", X, "return consent == Consent.ON || !choices.torApps().isEmpty();", "return consent == Consent.ON;"),
    ("controller: no cleanup at the first start", X, "if (!started || wanted || phase != Status.Phase.IDLE || prefsPending) {", "if (wanted || phase != Status.Phase.IDLE || prefsPending) {"),
    ("controller: a refused clearing is not retried after a sync", X, "if (!started || wanted || phase != Status.Phase.IDLE || prefsPending) {", "if (!started || wanted || phase != Status.Phase.IDLE) {"),
    ("controller: no deny-by-default preference", X, "                platform.setOemPreferences(out.oemPrefs);\n                appliedPrefs = out.oemPrefs;\n            }\n            prefsPending = false;\n        } catch", "                appliedPrefs = out.oemPrefs;\n            }\n            prefsPending = false;\n        } catch"),
        ("controller: the poll comes late", X, "if (nowMs >= nextPollMs) {", "if (nowMs > nextPollMs) {"),
    ("controller: the daemon state is not kept", X, "daemonState = p.state;", ""),
    ("controller: every failure notifies", X, "daemonState != ControlCodec.STATE_BOOTSTRAPPING && ladder.shouldNotify((int) f.uid, f.company, nowMs)) {", "true) {"),
    ("controller: the backoff starts at two seconds", X, "BACKOFF_MS[Math.min(attempts, BACKOFF_MS.length) - 1]", "BACKOFF_MS[Math.min(attempts, BACKOFF_MS.length - 1)]"),
    ("controller: the backoff is not reset", X, "            ranBefore = true;\n            attempts = 0;", "            ranBefore = true;"),
    ("controller: a lost link is not paused", X, "phase = ranBefore ? Status.Phase.PAUSED : Status.Phase.CONNECTING;", "phase = Status.Phase.CONNECTING;"),
    ("controller: the first failure is paused", X, "phase = ranBefore ? Status.Phase.PAUSED : Status.Phase.CONNECTING;", "phase = Status.Phase.PAUSED;"),
    ("controller: a run is not remembered", X, "            ranBefore = true;\n", ""),
    ("controller: the rules are not sent", X, "if (rulesChanged) {", "if (false) {"),
    ("controller: the table is not sent", X, "if (tableChanged) {", "if (false) {"),
    ("controller: the overrides are not sent", X, "        sendOverrides(choices);\n", ""),
    # the exit follows the place (ADR-208)
    ("codec: a country of three letters is valid", D, "country.length() == 2 &&", "country.length() >= 2 &&"),
    ("codec: a lower-case country is valid", D, "return c >= 'A' && c <= 'Z';", "return c >= 'A' && c <= 'z';"),
    ("codec: Z is not a capital letter of a country", D, "return c >= 'A' && c <= 'Z';", "return c >= 'A' && c < 'Z';"),
    ("codec: A is not a capital letter of a country", D, "return c >= 'A' && c <= 'Z';", "return c > 'A' && c <= 'Z';"),
    ("codec: tag set country", D, "TAG_SET_COUNTRY = 0x0a;", "TAG_SET_COUNTRY = 0x1a;"),
    ("codec: no country is written with a count of one", D, "o.write(0);\n        } else {\n            o.write(2);", "o.write(1);\n        } else {\n            o.write(2);"),
    ("codec: a country is written with a count of one", D, "o.write(2);\n            o.write(country.charAt(0));", "o.write(1);\n            o.write(country.charAt(0));"),
    ("codec: the letters of a country swap", D, "o.write(country.charAt(0));\n            o.write(country.charAt(1));", "o.write(country.charAt(1));\n            o.write(country.charAt(0));"),
    ("codec: an empty country is not no country", D, "boolean none = country == null || country.isEmpty();", "boolean none = country == null;"),
    ("codec: a country is not validated", D, "if (!none && !validCountry(country)) {", "if (!none && false) {"),
    ("codec: a country loses its uid", D, "        o.write(seq);\n        putU32(o, uid);\n        if (none) {", "        o.write(seq);\n        putU32(o, 0);\n        if (none) {"),
    ("codec: a country uid below zero is accepted", D, "if (uid < 0 || uid > 0xffffffffL) {\n            throw new IllegalArgumentException(\"a uid is an unsigned 32 bit number\");", "if (uid > 0xffffffffL) {\n            throw new IllegalArgumentException(\"a uid is an unsigned 32 bit number\");"),
    ("codec: a country uid above 32 bits is accepted", D, "if (uid < 0 || uid > 0xffffffffL) {\n            throw new IllegalArgumentException(\"a uid is an unsigned 32 bit number\");", "if (uid < 0) {\n            throw new IllegalArgumentException(\"a uid is an unsigned 32 bit number\");"),
    ("controller: an unreadable service removes the countries", X, "            return; // the service could not be read: keep every country already sent", "            places = new HashMap<>();"),
    ("presenter: a code that is not a country is one", PRE, "!ControlCodec.validCountry(personaCountry) || personaCountry.equals(ownCountry)", "personaCountry == null || personaCountry.equals(ownCountry)"),
    ("presenter: the own country is abroad", PRE, "!ControlCodec.validCountry(personaCountry) || personaCountry.equals(ownCountry)", "!ControlCodec.validCountry(personaCountry)"),
    ("presenter: Direct is moved by its place", PRE, "return stand == Choices.Choice.TOR ? Exit.COUNTRY : Exit.MISMATCH;", "return stand != Choices.Choice.OPEN ? Exit.COUNTRY : Exit.MISMATCH;"),
    ("presenter: Tor is not moved by its place", PRE, "return stand == Choices.Choice.TOR ? Exit.COUNTRY : Exit.MISMATCH;", "return Exit.MISMATCH;"),
    ("presenter: an app that is not on Tor has no disagreement", PRE, "return stand == Choices.Choice.TOR ? Exit.COUNTRY : Exit.MISMATCH;", "return stand == Choices.Choice.TOR ? Exit.COUNTRY : Exit.NONE;"),
    ("controller: the countries are not sent", X, "        sendCountries(choices);\n", ""),
    ("controller: the poll does not look at the places", X, "                    sendCountries(store.choices());\n", ""),
    ("controller: a country is sent for an app that is not on Tor", X, "== Presenter.Exit.COUNTRY) {", "!= Presenter.Exit.NONE) {"),
    ("controller: a sent country is sent again", X, "if (!e.getValue().equals(sentCountries.get(e.getKey()))) {", "if (true) {"),
    ("controller: a changed country is not sent", X, "if (!e.getValue().equals(sentCountries.get(e.getKey()))) {", "if (!sentCountries.containsKey(e.getKey())) {"),
    ("controller: a removed country stays", X, "daemon.setCountry(uid, null);\n                sentCountries.remove(uid);", "sentCountries.remove(uid);"),
    ("controller: the countries are kept over a reconnect", X, "            sentCountries.clear();\n            appliedPrefs = null;", "            appliedPrefs = null;"),
    ("controller: the own country is ignored", X, "String own = platform.ownCountry();", "String own = \"\";"),
    ("controller: no places left leaves the country", X, "if (places.isEmpty() && sentCountries.isEmpty()) {", "if (places.isEmpty()) {"),
    ("controller: a second network is registered", X, "if (needNetwork && netId == 0) {", "if (needNetwork) {"),
    ("controller: the network is not registered", X, "boolean needNetwork = !out.guard.isEmpty() || !out.oemPrefs.isEmpty();", "boolean needNetwork = false;"),
    ("controller: the rules do not get the network id", X, "            netId = platform.registerTorNetwork(iface);\n            route.apply(iface, netId, out.steer, out.guard);\n", "            netId = platform.registerTorNetwork(iface);\n"),
    ("controller: a change of the preferences is not sent", X, "// 0. the preferences of the apps on Tor: with no Tor network yet they have no network at all, so a step below that\n        // fails leaves them closed and never on the default network (C2-01)\n        if (appliedPrefs == null || !appliedPrefs.equals(out.oemPrefs)) {", "// 0. the preferences of the apps on Tor: with no Tor network yet they have no network at all, so a step below that\n        // fails leaves them closed and never on the default network (C2-01)\n        if (appliedPrefs == null) {"),
    ("controller: the network stays when the last Tor app leaves", X, "if (!needNetwork && netId != 0) {", "if (false) {"),
    ("controller: a change of the guard alone is not sent", X, "|| !same(applied.steer, out.steer) || !same(applied.guard, out.guard);", "|| !same(applied.steer, out.steer);"),
    ("controller: a change of the steer list alone is not sent", X, "|| !same(applied.steer, out.steer) || !same(applied.guard, out.guard);", "|| !same(applied.guard, out.guard);"),
    ("controller: an unchanged table is sent again", X, "boolean tableChanged = all || applied == null || !sameClasses(applied.classes, out.classes);", "boolean tableChanged = true;"),
    ("controller: an unchanged rule set is sent again", X, "boolean rulesChanged = all || applied == null ||", "boolean rulesChanged = true || all || applied == null ||"),
    ("controller: an unchanged override is sent again", X, "if (!want.get(e.getKey()).equals(sentOverrides.get(e.getKey()))) {", "if (true) {"),
    ("controller: a removed override is not cleared", X, "if (!want.containsKey(uid)) {\n                daemon.setOverrides(uid, new int[0]);", "if (false) {\n                daemon.setOverrides(uid, new int[0]);"),
    ("controller: the Tor network stays when a link is lost", X, "        platform.unregisterTorNetwork();\n        netId = 0;\n        daemon.close();", "        netId = 0;\n        daemon.close();"),
    ("controller: a Tor network that the platform took down is not registered again", X, "if (netId != 0 && nowMs >= nextNetworkMs && !platform.torNetworkLive()) {", "if (false) {"),
    ("controller: a live Tor network is registered again", X, "if (netId != 0 && nowMs >= nextNetworkMs && !platform.torNetworkLive()) {", "if (netId != 0 && nowMs >= nextNetworkMs) {"),
    ("controller: the registration of a lost network ignores the delay", X, "if (netId != 0 && nowMs >= nextNetworkMs && !platform.torNetworkLive()) {", "if (netId != 0 && !platform.torNetworkLive()) {"),
    ("controller: the delay of the network retry is not set", X, "                nextNetworkMs = nowMs + NETWORK_RETRY_MS;\n", ""),
    ("controller: the lost network id is kept", X, "                nextNetworkMs = nowMs + NETWORK_RETRY_MS;\n                netId = 0;\n", "                nextNetworkMs = nowMs + NETWORK_RETRY_MS;\n"),
    ("controller: a failed registration of a lost network is not a lost link", X, "                    lost(nowMs, e);\n                    return;\n                }\n            }\n            if (nowMs >= nextPollMs) {", "                    return;\n                }\n            }\n            if (nowMs >= nextPollMs) {"),
    ("controller: the rules stay when everything is off", X, "            route.clear();\n", ""),
    ("controller: the preference stays when everything is off", X, "            platform.setOemPreferences(Collections.<String, Integer>emptyMap());\n", ""),
    ("controller: the overrides are not sent to a new daemon", X, "            sentOverrides.clear();\n            sentCountries.clear();", "            sentCountries.clear();"),
    ("controller: the preferences are not sent to a new daemon", X, "            appliedPrefs = null;\n            netId = 0;\n            push(true);", "            netId = 0;\n            push(true);"),
    ("controller: the daemon is polled while idle", X, "        if (!wanted) {\n            if (prefsPending && nowMs >= prefsRetryMs) {\n                goIdle(nowMs); // the clearing of the preference was refused: an app set back to Direct has no network until it works\n            }\n            return;\n        }\n        if (phase == Status.Phase.RUNNING) {", "        if (phase == Status.Phase.RUNNING) {"),
    ("controller: a refused clearing is not retried while idle", X, "            if (prefsPending && nowMs >= prefsRetryMs) {\n                goIdle(nowMs);", "            if (false) {\n                goIdle(nowMs);"),
    ("controller: a refused preference is not retried while the daemon is down", X, "            if (prefsPending && nowMs >= prefsRetryMs) {\n                applyPreferences(nowMs);", "            if (false) {\n                applyPreferences(nowMs);"),
    ("controller: the retry ignores the delay", X, "if (prefsPending && nowMs >= prefsRetryMs) {\n                applyPreferences(nowMs);", "if (prefsPending) {\n                applyPreferences(nowMs);"),
    ("controller: the lost link ignores the delay", X, "if (!prefsPending || nowMs >= prefsRetryMs) {\n            applyPreferences(nowMs);\n        }\n        publish();", "applyPreferences(nowMs);\n        publish();"),
    ("controller: the lost link does not set the preference", X, "if (!prefsPending || nowMs >= prefsRetryMs) {\n            applyPreferences(nowMs);\n        }\n        publish();", "publish();"),
    ("controller: the preference comes after the rules", X, "        if (appliedPrefs == null || !appliedPrefs.equals(out.oemPrefs)) {\n            platform.setOemPreferences(out.oemPrefs);\n            appliedPrefs = out.oemPrefs;\n        }\n        prefsPending = false;\n        // 1. the rules", "        // 1. the rules"),
    ("controller: a refusal is not remembered", X, "lastError = e.getClass().getSimpleName();\n            prefsPending = true;\n            prefsRetryMs = nowMs + PREFS_RETRY_MS;\n        }\n    }\n\n    /** Nothing needs", "lastError = e.getClass().getSimpleName();\n        }\n    }\n\n    /** Nothing needs"),
    ("controller: a refused clearing is not remembered", X, "lastError = e.getClass().getSimpleName();\n            prefsPending = true;\n            prefsRetryMs = nowMs + PREFS_RETRY_MS;\n        }\n        appliedPrefs = null;", "lastError = e.getClass().getSimpleName();\n        }\n        appliedPrefs = null;"),
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
    ("names: a row with four columns is read", CN, "if (f.length != 3 ||", "if (f.length < 3 ||"),
    ("names: an id that is not digits is read", CN, "!f[1].matches(\"[0-9]{1,5}\")", "false"),
    ("names: an empty name is read", CN, "|| f[2].isEmpty() ||", "||"),
    ("names: a name of 65 characters is read", CN, "f[2].length() > 64", "f[2].length() > 65"),
    ("names: company 0 is a name", CN, "if (id >= 1 && id <= 0xffff) {", "if (id >= 0 && id <= 0xffff) {"),
    ("names: an id past 16 bits is a name", CN, "if (id >= 1 && id <= 0xffff) {", "if (id >= 1) {"),
    ("names: the header is not checked", CN, "!\"exit-mask-list\\t1\".equals(lines[0])", "false"),
    ("names: the last row of an id wins", CN, "c.names.putIfAbsent(id,", "c.names.put(id,"),
    ("names: control characters are kept", CN, ".replaceAll(\"\\\\p{Cntrl}\", \"\")", ""),
    ("tun: the v4 address", TN, "42, 127};", "42, 126};"),
    ("tun: the v4 prefix", TN, "V4_PREFIX = 24;", "V4_PREFIX = 25;"),
    ("tun: the v6 address", TN, "0, 0, 0, 0, 0, 0, 0, 2};", "0, 0, 0, 0, 0, 0, 0, 3};"),
    ("tun: the dns address", TN, "42, 53};", "42, 54};"),
    # DaemonClient, RouteClient, Link
    ("client: the sequence number does not wrap", DC, "seq = seq % 255 + 1; // 1..255\n        return seq;\n    }\n\n    @Override\n    public String connect()", "seq = seq + 1;\n        return seq;\n    }\n\n    @Override\n    public String connect()"),
    ("client: the sequence number wraps late", DC, "seq = seq % 255 + 1; // 1..255\n        return seq;\n    }\n\n    @Override\n    public String connect()", "seq = seq % 256 + 1;\n        return seq;\n    }\n\n    @Override\n    public String connect()"),
    ("client: the sequence is not reset on connect", DC, "        link.open();\n        seq = 0;", "        link.open();"),
    ("client: another protocol version is accepted", DC, "|| r.version != ControlCodec.DAEMON_PROTOCOL) {", ") {"),
    ("client: a wrong sequence number is accepted", DC, "if (r.type == ControlCodec.Type.ACK && r.seq == s) {", "if (r.type == ControlCodec.Type.ACK) {"),
    ("client: the reports after the state are not read", DC, "while (link.ready()) {", "while (link.ready() && false) {"),
    ("route client: another protocol version is accepted", RC, "|| r.version != ControlCodec.ROUTE_PROTOCOL) {", ") {"),
    ("route client: a wrong sequence number is accepted", RC, "if (r.type == ControlCodec.Type.ACK && r.seq == s) {", "if (r.type == ControlCodec.Type.ACK) {"),
    ("route client: the sequence is not reset on connect", RC, "        link.open();\n        seq = 0;", "        link.open();"),
    ("link: a stream that is ready when it is not", LK, "transport.in().available() > 0);", "transport.in().available() >= 0);"),
    ("link: the buffer is not shifted", LK, "System.arraycopy(buf, r.used, buf, 0, len - r.used);\n                len -= r.used;", "len -= r.used;"),
    ("link: a closed stream is read as empty", LK, "if (n < 0) {\n                close();", "if (n < -1) {\n                close();"),
    ('choices: the Tor limit is one more', C, 'return tor.contains(pkg) || tor.size() < MAX_TOR_APPS;', 'return tor.contains(pkg) || tor.size() <= MAX_TOR_APPS;'),
    ('choices: no Tor limit', C, 'case TOR:\n                return tor.contains(pkg) || tor.size() < MAX_TOR_APPS;', 'case TOR:\n                return true;'),
    ('choices: an app already on Tor counts against the limit', C, 'return tor.contains(pkg) || tor.size() < MAX_TOR_APPS;', 'return tor.size() < MAX_TOR_APPS;'),
    ('choices: the open limit is one more', C, 'return open.contains(pkg) || open.size() < MAX_OPEN_APPS;', 'return open.contains(pkg) || open.size() <= MAX_OPEN_APPS;'),
    ('choices: no open limit', C, 'case OPEN:\n                return open.contains(pkg) || open.size() < MAX_OPEN_APPS;', 'case OPEN:\n                return true;'),
    ('choices: an open app counts against the limit', C, 'return open.contains(pkg) || open.size() < MAX_OPEN_APPS;', 'return open.size() < MAX_OPEN_APPS;'),
    ('choices: the open limit constant', C, 'public static final int MAX_OPEN_APPS = 24;', 'public static final int MAX_OPEN_APPS = 25;'),
    ('choices: Direct does not leave Tor', C, '        tor.remove(pkg);\n        open.remove(pkg);\n        if (choice == Choice.TOR) {', '        open.remove(pkg);\n        if (choice == Choice.TOR) {'),
    ('choices: Tor does not leave Open', C, '        tor.remove(pkg);\n        open.remove(pkg);\n        if (choice == Choice.TOR) {', '        tor.remove(pkg);\n        if (choice == Choice.TOR) {'),
    ('choices: Open is not added', C, '} else if (choice == Choice.OPEN) {\n            open.add(pkg);', '} else if (choice == Choice.OPEN) {\n            tor.remove(pkg);'),
    ('choices: Tor is not added', C, 'if (choice == Choice.TOR) {\n            tor.add(pkg);', 'if (choice == Choice.TOR) {\n            open.add(pkg);'),
    ('choices: a bad name is taken', C, '        if (!validPackage(pkg)) {\n            return false;\n        }\n        switch (choice) {', '        switch (choice) {'),
    ('choices: open lines are not written', C, '        for (String p : open) {\n            b.append("open\\t").append(p).append(\'\\n\');\n        }\n', ''),
    ('choices: a Tor line is not applied', C, 'ok = c.choiceOf(f[1]) != Choice.OPEN && c.set(f[1], Choice.TOR);', 'ok = true;'),
    ('choices: a Tor line after an open line is taken', C, 'ok = c.choiceOf(f[1]) != Choice.OPEN && c.set(f[1], Choice.TOR);', 'ok = c.set(f[1], Choice.TOR);'),
    ('choices: an open line after a Tor line is taken', C, 'ok = c.choiceOf(f[1]) != Choice.TOR && c.set(f[1], Choice.OPEN);', 'ok = c.set(f[1], Choice.OPEN);'),
    ('choices: an open line is not applied', C, 'ok = c.choiceOf(f[1]) != Choice.TOR && c.set(f[1], Choice.OPEN);', 'ok = true;'),
    ('policy: masked includes Tor', P, 'masked = UidRanges.subtract(UidRanges.subtract(UidRanges.subtract(all, tor), exempt), open);', 'masked = UidRanges.subtract(UidRanges.subtract(all, exempt), open);'),
    ('policy: masked includes exempt', P, 'masked = UidRanges.subtract(UidRanges.subtract(UidRanges.subtract(all, tor), exempt), open);', 'masked = UidRanges.subtract(UidRanges.subtract(all, tor), open);'),
    ('policy: masked includes open', P, 'masked = UidRanges.subtract(UidRanges.subtract(UidRanges.subtract(all, tor), exempt), open);', 'masked = UidRanges.subtract(UidRanges.subtract(all, tor), exempt);'),
    ('policy: the steer limit is not applied', P, 'if (masked.size() > MAX_RANGES ||', 'if (false ||'),
    ('policy: the steer limit is one more', P, 'if (masked.size() > MAX_RANGES ||', 'if (masked.size() > MAX_RANGES + 1 ||'),
    ('policy: the table limit is not applied', P, '|| tor.size() + exempt.size() + open.size() + masked.size() > MAX_CLASSES) {', '|| false) {'),
    ('policy: the table limit forgets the open ranges', P, 'tor.size() + exempt.size() + open.size() + masked.size() > MAX_CLASSES', 'tor.size() + exempt.size() + masked.size() > MAX_CLASSES'),
    ('policy: exempt class written as masked', P, 'for (int[] r : exempt) {\n            classes.add(new Cls(r[0], r[1], Kind.EXEMPT));', 'for (int[] r : exempt) {\n            classes.add(new Cls(r[0], r[1], Kind.MASKED));'),
    ('policy: open class written as masked', P, 'for (int[] r : open) {\n            classes.add(new Cls(r[0], r[1], Kind.EXEMPT));', 'for (int[] r : open) {\n            classes.add(new Cls(r[0], r[1], Kind.MASKED));'),
    ('policy: open class is not written', P, '        for (int[] r : open) {\n            classes.add(new Cls(r[0], r[1], Kind.EXEMPT));\n        }', ''),
    ('policy: an open app is not collected', P, '} else if (choices.choiceOf(a.pkg) == Choices.Choice.OPEN) {\n                openUids.add(a.uid);', '} else if (choices.choiceOf(a.pkg) == Choices.Choice.OPEN) {\n                exemptUids.add(a.uid);'),
    ('policy: an open app shares the uid of an exempt app', P, 'UidRanges.subtract(UidRanges.subtract(UidRanges.coalesce(toArray(openUids)), exempt), tor)', 'UidRanges.subtract(UidRanges.coalesce(toArray(openUids)), tor)'),
    ('policy: an open app shares the uid of a Tor app', P, 'UidRanges.subtract(UidRanges.subtract(UidRanges.coalesce(toArray(openUids)), exempt), tor)', 'UidRanges.subtract(UidRanges.coalesce(toArray(openUids)), exempt)'),
    ('policy: the exempt room is 65', P, 'public static final int MAX_EXEMPT_RANGES = 64;', 'public static final int MAX_EXEMPT_RANGES = 65;'),
    ('policy: the room ignores the system packages', P, 'int room = Math.max(0, MAX_EXEMPT_RANGES - exempt.size());', 'int room = MAX_EXEMPT_RANGES;'),
    ('policy: open ranges are not cut', P, 'if (open.size() > room) {', 'if (false) {'),
    ('policy: open ranges are cut one late', P, 'if (open.size() > room) {', 'if (open.size() > room + 1) {'),
    ('policy: no warning for too many open apps', P, 'warnings.add(Warning.TOO_MANY_OPEN_APPS);', 'warnings.add(Warning.TOO_MANY_TOR_APPS);'),
    ('presenter: the options lose Open', PRE, 'Choices.Choice.TOR, Choices.Choice.OPEN};', 'Choices.Choice.TOR};'),
    ('presenter: the options change order', PRE, '{Choices.Choice.DIRECT, Choices.Choice.TOR, Choices.Choice.OPEN}', '{Choices.Choice.TOR, Choices.Choice.DIRECT, Choices.Choice.OPEN}'),
    ('presenter: the row does not show the choice', PRE, 'rows.add(new AppRow(a.pkg, a.label, choices.choiceOf(a.pkg)));', 'rows.add(new AppRow(a.pkg, a.label, Choices.Choice.DIRECT));'),
    ('presenter: the row does not show Tor', PRE, 'this.tor = choice == Choices.Choice.TOR;', 'this.tor = false;'),
    ('codec: the protocol version', D, 'public static final int DAEMON_PROTOCOL = 4;', 'public static final int DAEMON_PROTOCOL = 3;'),
    ('codec: the release tag moves', D, 'private static final int TAG_RELEASE = 0x09;', 'private static final int TAG_RELEASE = 0x0a;'),
    ('codec: a release of company 0', D, 'if (company < 1 || company > 0xffff) {\n            throw new IllegalArgumentException("company id is 1 to 65535");', 'if (company < 0 || company > 0xffff) {\n            throw new IllegalArgumentException("company id is 1 to 65535");'),
    ('codec: a release of company 65536', D, 'if (company < 1 || company > 0xffff) {\n            throw new IllegalArgumentException("company id is 1 to 65535");', 'if (company < 1 || company > 0x10000) {\n            throw new IllegalArgumentException("company id is 1 to 65535");'),
    ('codec: a release loses the company', D, '        putU32(o, uid);\n        putU16(o, company);\n        return frame(TAG_RELEASE', '        putU32(o, uid);\n        putU16(o, 0);\n        return frame(TAG_RELEASE'),
    ('codec: the helper reads a Promoted', D, '                if (daemon) {\n                    // A Promoted', '                if (true) {\n                    // A Promoted'),
    ('codec: a daemon reads an Applied', D, '                if (daemon) {\n                    // A Promoted', '                if (false) {\n                    // A Promoted'),
    ('codec: a Promoted has six bytes', D, '                    exact(n, 7);\n                    int fresh', '                    exact(n, 6);\n                    int fresh'),
    ('codec: the fresh flag of two', D, 'if (fresh > 1) {', 'if (fresh > 2) {'),
    ('codec: a Promoted is always fresh', D, '((buf[p + 4] & 0xff) << 8) | (buf[p + 5] & 0xff), 0, 0, fresh == 1, end);', '((buf[p + 4] & 0xff) << 8) | (buf[p + 5] & 0xff), 0, 0, true, end);'),
    ('codec: a Promoted is never fresh', D, '((buf[p + 4] & 0xff) << 8) | (buf[p + 5] & 0xff), 0, 0, fresh == 1, end);', '((buf[p + 4] & 0xff) << 8) | (buf[p + 5] & 0xff), 0, 0, false, end);'),
    ('codec: a Promoted loses its company', D, 'return new Reply(Type.PROMOTED, 0, null, 0, 0, 0, u32(buf, p),\n                            ((buf[p + 4] & 0xff) << 8) | (buf[p + 5] & 0xff),', 'return new Reply(Type.PROMOTED, 0, null, 0, 0, 0, u32(buf, p),\n                            ((buf[p + 0] & 0xff) << 8) | (buf[p + 1] & 0xff),'),
    ('codec: failures company is read from the uid', D, 'u32(buf, p), ((buf[p + 4] & 0xff) << 8) | (buf[p + 5] & 0xff),\n                        u32(buf, p + 6)', 'u32(buf, p), ((buf[p + 0] & 0xff) << 8) | (buf[p + 1] & 0xff),\n                        u32(buf, p + 6)'),
    ('client: a failure report is dropped', DC, '            pending.add(new Failure(r.uid, r.company, r.count));\n            return true;', '            return true;'),
    ('client: a promotion is dropped', DC, '            pendingPromotions.add(new Promotion(r.uid, r.company, r.fresh));\n            return true;', '            return true;'),
    ('client: a promotion is never fresh', DC, 'new Promotion(r.uid, r.company, r.fresh)', 'new Promotion(r.uid, r.company, false)'),
    ('client: the reports are returned twice', DC, '        pending.clear();\n        List<Promotion> promoted', '        List<Promotion> promoted'),
    ('client: the promotions are returned twice', DC, '        pendingPromotions.clear();\n        return new Poll', '        return new Poll'),
    ('client: a release is not awaited', DC, 'link.send(ControlCodec.release(s, uid, company));\n        awaitAck(s);', 'link.send(ControlCodec.release(s, uid, company));'),
    ('client: a report after the state is refused', DC, '            if (!queue(r)) {\n                throw refused(r);', '            if (false) {\n                throw refused(r);'),
    ('client: a promotion before an Ack is refused', DC, '            if (queue(r)) {\n                continue;\n            }\n            if (r.type == ControlCodec.Type.ACK', '            if (r.type == ControlCodec.Type.FAILURES) {\n                continue;\n            }\n            if (r.type == ControlCodec.Type.ACK'),
    ('client: a reconnect keeps the promotions', DC, '        pending.clear();\n        pendingPromotions.clear();\n        link.send(ControlCodec.hello());', '        pending.clear();\n        link.send(ControlCodec.hello());'),
    ('controller: a promotion is dropped after three polls', X, 'public static final long PROMOTION_STALE_MS = 2 * POLL_MS;', 'public static final long PROMOTION_STALE_MS = 3 * POLL_MS;'),
    ('controller: a promotion is dropped after one poll', X, 'public static final long PROMOTION_STALE_MS = 2 * POLL_MS;', 'public static final long PROMOTION_STALE_MS = POLL_MS;'),
    ('controller: a promotion is dropped one tick late', X, 'nowMs - seen >= PROMOTION_STALE_MS', 'nowMs - seen > PROMOTION_STALE_MS'),
    ('controller: a stale promotion is kept', X, 'promoted.values().removeIf(seen -> nowMs - seen >= PROMOTION_STALE_MS);', 'promoted.values().removeIf(seen -> false);'),
    ('controller: a promotion is not refreshed', X, 'promoted.put(new Status.Promoted(q.uid, q.company), nowMs);', 'promoted.putIfAbsent(new Status.Promoted(q.uid, q.company), nowMs);'),
    ('controller: every promotion notifies', X, 'if (q.fresh) {\n                            platform.notifyPromotion', 'if (true) {\n                            platform.notifyPromotion'),
    ('controller: a choice while the daemon is not running waits for the link', X,
     '        } else {\n            // The daemon is being reached or is down: a choice made now still closes the app at once (F11). Without this\n            // an app set to Tor while the daemon restarts would stay Direct until the connection succeeds, which may be never.\n            applyPreferences(nowMs);\n            publish();\n        }',
     '        }'),
    ('controller: the preference is set again every time', X,
     'if (appliedPrefs == null || !appliedPrefs.equals(out.oemPrefs)) {\n                platform.setOemPreferences(out.oemPrefs);\n                appliedPrefs = out.oemPrefs;\n            }\n            prefsPending = false;\n',
     'if (true) {\n                platform.setOemPreferences(out.oemPrefs);\n                appliedPrefs = out.oemPrefs;\n            }\n            prefsPending = false;\n'),
    ('controller: no promotion notifies', X, 'if (q.fresh) {\n                            platform.notifyPromotion', 'if (false) {\n                            platform.notifyPromotion'),
    ('controller: a release keeps the entry', X, '        promoted.remove(new Status.Promoted(uid, company));\n        if (phase', '        if (phase'),
    ('controller: a release is not sent', X, '                daemon.release(uid, company);', '                // not sent'),
    ('controller: a release is sent to a daemon that is down', X, '        if (phase == Status.Phase.RUNNING) {\n            try {\n                daemon.release', '        if (true) {\n            try {\n                daemon.release'),
    ('controller: a failed release is ignored', X, '            } catch (IOException | RuntimeException e) {\n                lost(nowMs, e);\n                return;\n            }\n        }\n        publish();', '            } catch (IOException | RuntimeException e) {\n                lastError = e.getClass().getSimpleName();\n            }\n        }\n        publish();'),
    ('controller: a lost daemon keeps the promotions', X, '        // A daemon that starts again has forgotten its promotions (they live in its memory only).\n        promoted.clear();\n', '        // A daemon that starts again has forgotten its promotions (they live in its memory only).\n'),
    ('controller: going idle keeps the promotions', X, '        sentCountries.clear();\n        promoted.clear();', '        sentCountries.clear();'),
    ('controller: the status has no promotions', X, '                new java.util.ArrayList<>(promoted.keySet()));', '                new java.util.ArrayList<>());'),
    ('status: equality ignores the promotions', ST, '&& java.util.Objects.equals(lastError, s.lastError) && promotions.equals(s.promotions);', '&& java.util.Objects.equals(lastError, s.lastError);'),
    # ProfileRows (ADR-208)
    ("profiles: the defaults may be Trusted", PR, "? new int[] {PROFILE_PROTECTED, PROFILE_ISOLATED}", "? new int[] {PROFILE_TRUSTED, PROFILE_PROTECTED, PROFILE_ISOLATED}"),
    ("profiles: an app cannot be Trusted", PR, ": new int[] {PROFILE_TRUSTED, PROFILE_PROTECTED, PROFILE_ISOLATED};", ": new int[] {PROFILE_PROTECTED, PROFILE_ISOLATED};"),
    ("profiles: the sensor values are in another order", PR, "CLASS_SENSORS, new int[] {VALUE_REAL, VALUE_SUBSTITUTE, VALUE_BLOCKED}, sensors));\n        rows.add(row(CLASS_LOOPBACK", "CLASS_SENSORS, new int[] {VALUE_REAL, VALUE_BLOCKED, VALUE_SUBSTITUTE}, sensors));\n        rows.add(row(CLASS_LOOPBACK"),
    ("profiles: the loopback is offered as substitute data", PR, "CLASS_LOOPBACK, new int[] {VALUE_REAL, VALUE_BLOCKED}", "CLASS_LOOPBACK, new int[] {VALUE_REAL, VALUE_SUBSTITUTE, VALUE_BLOCKED}"),
    ("profiles: the settings are offered as substitute data", PR, "CLASS_SETTINGS, new int[] {VALUE_REAL, VALUE_BLOCKED}", "CLASS_SETTINGS, new int[] {VALUE_REAL, VALUE_SUBSTITUTE, VALUE_BLOCKED}"),
    ("profiles: the loopback row names the settings class", PR, "row(CLASS_LOOPBACK,", "row(CLASS_SETTINGS,"),
    ("profiles: a current value that is not offered is accepted", PR, "if (v == current) {", "if (true) {"),
    ("profiles: the row forgets the current value", PR, "return new Row(cls, values, current);", "return new Row(cls, values, values[0]);"),
    ("profiles: a profile below Trusted is accepted", PR, "profile < PROFILE_TRUSTED ||", "profile < -1 ||"),
    ("profiles: a profile above Isolated is accepted", PR, "profile > PROFILE_ISOLATED", "profile > PROFILE_ISOLATED + 1"),
    ("profiles: rowsFor does not check the profile", PR, "requireProfile(profile);\n        List<Row> rows = new ArrayList<>();\n        rows.add(row(CLASS_LOCATION", "List<Row> rows = new ArrayList<>();\n        rows.add(row(CLASS_LOCATION"),
    ("location: the location row is not offered", PR, "rows.add(row(CLASS_LOCATION, new int[] {VALUE_REAL, VALUE_SUBSTITUTE, VALUE_BLOCKED}, location));\n        rows.add(row(CLASS_SENSORS", "rows.add(row(CLASS_SENSORS"),
    ("location: the location row names the settings class", PR, "row(CLASS_LOCATION, new int[] {VALUE_REAL, VALUE_SUBSTITUTE, VALUE_BLOCKED}, location));\n        rows.add(row(CLASS_SENSORS",
     "row(CLASS_SETTINGS, new int[] {VALUE_REAL, VALUE_SUBSTITUTE, VALUE_BLOCKED}, location));\n        rows.add(row(CLASS_SENSORS"),
    ("location: blocked is not offered for the location", PR, "row(CLASS_LOCATION, new int[] {VALUE_REAL, VALUE_SUBSTITUTE, VALUE_BLOCKED}, location));\n        rows.add(row(CLASS_SENSORS",
     "row(CLASS_LOCATION, new int[] {VALUE_REAL, VALUE_SUBSTITUTE}, location));\n        rows.add(row(CLASS_SENSORS"),
    ("location: substitute is not offered for the location", PR, "row(CLASS_LOCATION, new int[] {VALUE_REAL, VALUE_SUBSTITUTE, VALUE_BLOCKED}, location));\n        rows.add(row(CLASS_SENSORS",
     "row(CLASS_LOCATION, new int[] {VALUE_REAL, VALUE_BLOCKED}, location));\n        rows.add(row(CLASS_SENSORS"),
    # the page order and the place row (ADR-209)
    ("rows: location and sensors swap places", PR, "rows.add(row(CLASS_LOCATION, new int[] {VALUE_REAL, VALUE_SUBSTITUTE, VALUE_BLOCKED}, location));\n        rows.add(row(CLASS_SENSORS, new int[] {VALUE_REAL, VALUE_SUBSTITUTE, VALUE_BLOCKED}, sensors));",
     "rows.add(row(CLASS_SENSORS, new int[] {VALUE_REAL, VALUE_SUBSTITUTE, VALUE_BLOCKED}, sensors));\n        rows.add(row(CLASS_LOCATION, new int[] {VALUE_REAL, VALUE_SUBSTITUTE, VALUE_BLOCKED}, location));"),
    ("rows: loopback and settings swap places", PR, "rows.add(row(CLASS_LOOPBACK, new int[] {VALUE_REAL, VALUE_BLOCKED}, loopback));\n        rows.add(row(CLASS_SETTINGS, new int[] {VALUE_REAL, VALUE_BLOCKED}, settings));",
     "rows.add(row(CLASS_SETTINGS, new int[] {VALUE_REAL, VALUE_BLOCKED}, settings));\n        rows.add(row(CLASS_LOOPBACK, new int[] {VALUE_REAL, VALUE_BLOCKED}, loopback));"),
    ("rows: Protected gets substitute sensors and location", PR, "return profile == PROFILE_ISOLATED ? VALUE_SUBSTITUTE : VALUE_REAL;", "return profile != PROFILE_TRUSTED ? VALUE_SUBSTITUTE : VALUE_REAL;"),
    ("rows: Isolated blocks the loopback and the settings", PR, "return profile == PROFILE_TRUSTED ? VALUE_REAL : VALUE_BLOCKED;", "return profile != PROFILE_ISOLATED ? VALUE_REAL : VALUE_BLOCKED;"),
    ("rows: profileDefault accepts an unknown class", PR, "throw new IllegalArgumentException(\"class \" + cls);", "return VALUE_REAL;"),
    ("rows: profileDefault accepts an unknown profile", PR, "requireProfile(profile);\n        switch (cls) {", "switch (cls) {"),
    ("rows: identityOf ignores the sensors", PR, "boolean plain = sensors == profileDefault(profile, CLASS_SENSORS) &&", "boolean plain = true &&"),
    ("rows: identityOf ignores the loopback", PR, "loopback == profileDefault(profile, CLASS_LOOPBACK)", "true"),
    ("rows: identityOf ignores the settings", PR, "settings == profileDefault(profile, CLASS_SETTINGS)", "true"),
    ("rows: identityOf ignores the location", PR, "&& location == profileDefault(profile, CLASS_LOCATION);", "&& true;"),
    ("rows: identityOf always returns the profile", PR, "return plain ? profile : IDENTITY_CUSTOM;", "return profile;"),
    ("rows: unknown identity is read as Custom", PR, "IDENTITY_UNKNOWN = -2;", "IDENTITY_UNKNOWN = -1;"),
    ("rows: identityOf is always Custom", PR, "return plain ? profile : IDENTITY_CUSTOM;", "return IDENTITY_CUSTOM;"),
    ("rows: identityOf does not check the values", PR, "rowsFor(profile, sensors, loopback, settings, location);\n        boolean plain", "boolean plain"),
    ("rows: selectedChoice ignores that the app has an entry", PR, "return configured ? profile : CHOICE_FOLLOW_DEFAULT;", "return profile;"),
    ("rows: selectedChoice always follows the default", PR, "return configured ? profile : CHOICE_FOLLOW_DEFAULT;", "return CHOICE_FOLLOW_DEFAULT;"),
    ("rows: selectedChoice is inverted", PR, "return configured ? profile : CHOICE_FOLLOW_DEFAULT;", "return !configured ? profile : CHOICE_FOLLOW_DEFAULT;"),
    ("rows: the place row shows for real location", PR, "if (location != VALUE_SUBSTITUTE) {", "if (location == -1) {"),
    ("rows: the place row shows for blocked location", PR, "if (location != VALUE_SUBSTITUTE) {", "if (location == VALUE_REAL) {"),
    ("rows: the place row is never shown", PR, "if (location != VALUE_SUBSTITUTE) {", "if (true) {"),
    ("rows: a place id of 0 counts as a place", PR, "personaPlaceId <= 0 ?", "personaPlaceId < 0 ?"),
    ("rows: a place id below 0 counts as a place", PR, "personaPlaceId <= 0 ?", "personaPlaceId == 0 ?"),
    ("rows: the place row is never available", PR, "personaPlaceId <= 0 ?", "false ?"),
    ("rows: the place row is never usable", PR, "personaPlaceId <= 0 ?", "true ?"),
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
