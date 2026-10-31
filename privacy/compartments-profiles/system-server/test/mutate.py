#!/usr/bin/env python3
"""Mutation check for the Compartments policy core: every mutant must make a test assertion fail.
Usage: mutate.py   (run from anywhere; reads the sources next to this file)"""
import pathlib, shutil, subprocess, sys, tempfile

ROOT = pathlib.Path(__file__).resolve().parent.parent
MUTANTS = [
    # P4, P20, B1 (review fixes of 2026-10-30, second round)
    ("MirrorPlan.java", "the withheld set leaves out the blocked apps", "Set<String> packages = new TreeSet<>(locationBlocked(s));", "Set<String> packages = new TreeSet<>();"),
    ("MirrorPlan.java", "the withheld set leaves out the unresolved apps", "        packages.addAll(locationUnresolved(s, r, salt, places, country, lat, lon, before, table));\n        Set<Integer> out", "        Set<Integer> out"),
    ("MirrorPlan.java", "the withheld set ignores the kept place", "locationUnresolved(s, r, salt, places, country, lat, lon, before, table));\n        Set<Integer> out", "locationUnresolved(s, r, salt, places, country, lat, lon, null, null));\n        Set<Integer> out"),
    ("MirrorPlan.java", "the withheld set takes a platform uid", "            int appId = ordinaryAppId(r.uidOf(pkg));\n            if (appId >= 0) {\n                out.add(appId);", "            int appId = r.uidOf(pkg) % USER_OFFSET;\n            if (appId >= 0) {\n                out.add(appId);"),
    ("ActivityEstimator.java", "the estimator keeps its state across a gap", "if (lastMs != Long.MIN_VALUE && nowMs - lastMs > STALE_MS) {", "if (false) {"),
    ("ActivityEstimator.java", "the gap resets only the activity", "            candidate = Activity.STILL;\n            seen = 0;\n        }\n        lastMs = nowMs;", "        }\n        lastMs = nowMs;"),
    ("ActivityEstimator.java", "a gap of the stale limit resets", "nowMs - lastMs > STALE_MS) {\n            current", "nowMs - lastMs >= STALE_MS) {\n            current"),
    ("Places.java", "a place with an unknown zone is kept", "if (zones == null || zones.contains(f[5])) {", "if (true) {"),
    ("Places.java", "every place is dropped when zones are given", "if (zones == null || zones.contains(f[5])) {", "if (zones == null) {"),
    ("Places.java", "null zones drop every place", "if (zones == null || zones.contains(f[5])) {", "if (zones != null && zones.contains(f[5])) {"),
    # Places
    ("Places.java", "a repeated id is accepted", "if (id <= lastId) {", "if (id < lastId) {"),
    ("Places.java", "ids may descend", "if (id <= lastId) {", "if (id == lastId) {"),
    ("Places.java", "id 0 is accepted", "int lastId = 0;", "int lastId = -1;"),
    ("Places.java", "one line too many is accepted", "if (out.size() >= MAX_LINES) {", "if (out.size() > MAX_LINES) {"),
    ("Places.java", "a line with more fields is accepted", "if (f.length != 7) {", "if (f.length < 7) {"),
    ("Places.java", "a line with fewer fields is accepted", "if (f.length != 7) {", "if (f.length > 7) {"),
    ("Places.java", "the name is not checked", "if (!validName(f[1]) || ", "if ("),
    ("Places.java", "the country is not checked", "!COUNTRY.matcher(f[2]).matches() || ", ""),
    ("Places.java", "the time zone is not checked", " || !ZONE.matcher(f[5]).matches()) {", ") {"),
    ("Places.java", "the latitude is not checked for its form", "if (!LAT.matcher(f[3]).matches() || ", "if ("),
    ("Places.java", "the longitude is not checked for its form", " || !LON.matcher(f[4]).matches()) {", ") {"),
    ("Places.java", "latitude 80.5 is accepted", "Math.abs(lat) > 80.0", "Math.abs(lat) > 81.0"),
    ("Places.java", "latitude 80 is refused", "Math.abs(lat) > 80.0", "Math.abs(lat) >= 80.0"),
    ("Places.java", "longitude 181 is accepted", "Math.abs(lon) > 180.0", "Math.abs(lon) > 181.0"),
    ("Places.java", "longitude 180 is refused", "Math.abs(lon) > 180.0", "Math.abs(lon) >= 180.0"),
    ("Places.java", "mcc 199 is accepted", "if (mcc < 200 || mcc > 799) {", "if (mcc < 199 || mcc > 799) {"),
    ("Places.java", "mcc 800 is accepted", "if (mcc < 200 || mcc > 799) {", "if (mcc < 200 || mcc > 800) {"),
    ("Places.java", "a number with a leading zero is accepted", 'Pattern.compile("0|[1-9][0-9]{0,8}")', 'Pattern.compile("0|[0-9]{1,9}")'),
    ("Places.java", "a number in another script is accepted", 'Pattern.compile("0|[1-9][0-9]{0,8}")', 'Pattern.compile("0|\\\\p{Nd}{1,9}")'),
    ("Places.java", "an empty name is accepted", "if (s.isEmpty() || s.length() > MAX_NAME) {", "if (s.length() > MAX_NAME) {"),
    ("Places.java", "a name of 101 characters is accepted", "s.length() > MAX_NAME", "s.length() > MAX_NAME + 1"),
    ("Places.java", "delete is accepted in a name", "if (Character.isISOControl(s.charAt(i))) {", "if (s.charAt(i) < 0x20) {"),
    ("Places.java", "a control character is accepted in a name", "if (Character.isISOControl(s.charAt(i))) {", "if (s.charAt(i) == 0x7f) {"),
    ("Places.java", "byId looks in the wrong half", "if (v < id) {", "if (v > id) {"),
    ("Places.java", "search is case-sensitive", "final String q = query.toLowerCase(Locale.ROOT);", "final String q = query;"),
    ("Places.java", "search finds only names that start with the query", "} else if (name.contains(q)) {", "} else if (false) {"),
    ("Places.java", "search does not sort the names that start with the query", "Collections.sort(starts, order);", ""),
    ("Places.java", "search does not sort the other names", "Collections.sort(inside, order);", ""),
    ("Places.java", "search puts the other names first", "starts.addAll(inside);\n        return new ArrayList<>(starts.subList",
     "inside.addAll(starts);\n        starts = inside;\n        return new ArrayList<>(starts.subList"),
    ("Places.java", "search sorts by id first", "int c = a.name.toLowerCase(Locale.ROOT).compareTo(b.name.toLowerCase(Locale.ROOT));\n            return c != 0 ? c : Integer.compare(a.id, b.id);",
     "int c = Integer.compare(a.id, b.id);\n            return c != 0 ? c : a.name.compareTo(b.name);"),
    ("Places.java", "search ignores the limit", "Math.min(limit, starts.size())", "starts.size()"),
    ("Places.java", "search takes a negative limit", "if (query == null || query.isEmpty() || limit <= 0) {", "if (query == null || query.isEmpty()) {"),
    ("Places.java", "search takes a null query", "if (query == null || query.isEmpty() || limit <= 0) {", "if (query.isEmpty() || limit <= 0) {"),
    ("Places.java", "the automatic place is nearer", "MIN_AUTOMATIC_DISTANCE_M = 150000.0;", "MIN_AUTOMATIC_DISTANCE_M = 15000.0;"),
    ("Places.java", "the automatic place is farther", "MIN_AUTOMATIC_DISTANCE_M = 150000.0;", "MIN_AUTOMATIC_DISTANCE_M = 1500000.0;"),
    ("Places.java", "the automatic place is of any country", "if (!p.country.equals(countryIso)) {", "if (false) {"),
    ("Places.java", "the seed does not change the automatic place", "mix(seed ^ countryHash)", "mix(countryHash)"),
    ("Places.java", "the farthest place is the nearest", "if (d > farthestM) {", "if (d < farthestM || farthest == null) {"),
    ("Places.java", "no fallback to the farthest place", "if (far.isEmpty()) {\n            return farthest == null ? 0 : farthest.id;", "if (far.isEmpty()) {\n            return 0;"),
    ("Places.java", "a country without a place gives a place", "return farthest == null ? 0 : farthest.id;", "return farthest == null ? 1 : farthest.id;"),
    ("Places.java", "a position that is not finite is trusted", "boolean known = !Double.isNaN(realLat) && ", "boolean known = "),
    ("Places.java", "mix drops its increment", "z += 0x9E3779B97F4A7C15L;", ""),
    ("Places.java", "mix drops its last step", "return z ^ (z >>> 31);", "return z;"),
    ("Places.java", "mix uses another shift", "z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;", "z = (z ^ (z >>> 28)) * 0x94D049BB133111EBL;"),
    # CellSubset
    ("CellSubset.java", "a cell line with more fields is accepted", "if (f.length != 5) {", "if (f.length < 5) {"),
    ("CellSubset.java", "a cell line with fewer fields is accepted", "if (f.length != 5) {", "if (f.length > 5) {"),
    ("CellSubset.java", "place 0 is accepted for a cell", "place < 1 ||", "place < 0 ||"),
    ("CellSubset.java", "the operator is of the last cell", "Cell first = all.get(0);", "Cell first = all.get(all.size() - 1);"),
    ("CellSubset.java", "the mnc is not padded", '(first.mnc < 10 ? "0" : "")', '""'),
    ("CellSubset.java", "the mnc is padded to three digits", '(first.mnc < 10 ? "0" : "")', '(first.mnc < 100 ? "0" : "")'),
    ("CellSubset.java", "the mcc is dropped", "return first.mcc + (first.mnc", 'return "" + (first.mnc'),
    ("CellSubset.java", "a place without cells has an operator", "if (all == null) {\n            return \"\";", "if (all == null) {\n            return \"0\";"),
    ("CellSubset.java", "mcc 199 is accepted for a cell", "mcc < 200 ||", "mcc < 199 ||"),
    ("CellSubset.java", "mcc 800 is accepted for a cell", "mcc > 799 ||", "mcc > 800 ||"),
    ("CellSubset.java", "mnc 1000 is accepted", "mnc > 999", "mnc > 1000"),
    ("CellSubset.java", "tac 65536 is accepted", "tac > 65535", "tac > 65536"),
    ("CellSubset.java", "cid 2^32 is accepted", "cid > 4294967295L", "cid > 4294967296L"),
    ("CellSubset.java", "65 cells of a place are accepted", "if (list.size() >= MAX_PER_PLACE) {", "if (list.size() > MAX_PER_PLACE) {"),
    ("CellSubset.java", "63 cells of a place are the limit", "if (list.size() >= MAX_PER_PLACE) {", "if (list.size() >= MAX_PER_PLACE - 1) {"),
    ("CellSubset.java", "one line too many is accepted for the cells", "if (++lines > MAX_LINES) {", "if (lines++ > MAX_LINES) {"),
    ("CellSubset.java", "more than 8 cells are returned", "static final int RETURNED = 8;", "static final int RETURNED = 9;"),
    ("CellSubset.java", "fewer than 8 cells are returned", "static final int RETURNED = 8;", "static final int RETURNED = 7;"),
    ("CellSubset.java", "all the cells are returned", "Math.min(RETURNED, all.size())", "all.size()"),
    ("CellSubset.java", "an unknown place gives null", "if (all == null) {\n            return Collections.emptyList();\n        }", ""),
    ("CellSubset.java", "a cell number in another script is accepted", 'Pattern.compile("0|[1-9][0-9]{0,9}")', 'Pattern.compile("0|\\\\p{Nd}{1,10}")'),
    ("CellSubset.java", "a cell number with a leading zero is accepted", 'Pattern.compile("0|[1-9][0-9]{0,9}")', 'Pattern.compile("0|[0-9]{1,10}")'),
    ("CellSubset.java", "a cell file without a final newline is accepted", "|| tsv.charAt(tsv.length() - 1) != '\\n'", "|| tsv.charAt(tsv.length() - 1) == 'x'"),
    # PersonaFile
    ("PersonaFile.java", "the file is not in app id order", "Map<Integer, Integer> sorted = new TreeMap<>();", "Map<Integer, Integer> sorted = new java.util.LinkedHashMap<>();"),
    ("PersonaFile.java", "app id 9999 is written", "if (appId == null || place == null || appId < FIRST_APP_ID || appId > LAST_APP_ID || place < 1) {", "if (appId == null || place == null || appId < FIRST_APP_ID - 1 || appId > LAST_APP_ID || place < 1) {"),
    ("PersonaFile.java", "app id 20000 is written", "if (appId == null || place == null || appId < FIRST_APP_ID || appId > LAST_APP_ID || place < 1) {", "if (appId == null || place == null || appId < FIRST_APP_ID || appId > LAST_APP_ID + 1 || place < 1) {"),
    ("PersonaFile.java", "place 0 is written", "if (appId == null || place == null || appId < FIRST_APP_ID || appId > LAST_APP_ID || place < 1) {", "if (appId == null || place == null || appId < FIRST_APP_ID || appId > LAST_APP_ID || place < 0) {"),
    ("PersonaFile.java", "too many apps are written", "appIdToPlace.size() > MAX_LINES - 1", "appIdToPlace.size() > MAX_LINES"),
    ("PersonaFile.java", "the salt is written short", "for (int i = hex.length(); i < 16; i++) {", "for (int i = hex.length(); i < 15; i++) {"),
    ("PersonaFile.java", "the salt is written long", "for (int i = hex.length(); i < 16; i++) {", "for (int i = hex.length(); i < 17; i++) {"),
    ("PersonaFile.java", "the salt is not padded", "for (int i = hex.length(); i < 16; i++) {", "for (int i = hex.length(); i < 0; i++) {"),
    ("PersonaFile.java", "upper-case hex is read", 'Pattern.compile("s [0-9a-f]{16}")', 'Pattern.compile("s [0-9a-fA-F]{16}")'),
    ("PersonaFile.java", "15 hex digits are read", 'Pattern.compile("s [0-9a-f]{16}")', 'Pattern.compile("s [0-9a-f]{15,16}")'),
    ("PersonaFile.java", "17 hex digits are read", 'Pattern.compile("s [0-9a-f]{16}")', 'Pattern.compile("s [0-9a-f]{16,17}")'),
    ("PersonaFile.java", "the salt need not come first", "if (lines == 1) {", "if (lines == 1 && line.startsWith(\"s \")) {"),
    ("PersonaFile.java", "a repeated app id is read", "if (out.put((int) appId, (int) place) != null) {", "if (out.put((int) appId, (int) place) == Integer.valueOf(-1)) {"),
    ("PersonaFile.java", "app id 9999 is read", "if (appId < FIRST_APP_ID || appId > LAST_APP_ID || place < 1 || place > Integer.MAX_VALUE) {", "if (appId < FIRST_APP_ID - 1 || appId > LAST_APP_ID || place < 1 || place > Integer.MAX_VALUE) {"),
    ("PersonaFile.java", "app id 20000 is read", "if (appId < FIRST_APP_ID || appId > LAST_APP_ID || place < 1 || place > Integer.MAX_VALUE) {", "if (appId < FIRST_APP_ID || appId > LAST_APP_ID + 1 || place < 1 || place > Integer.MAX_VALUE) {"),
    ("PersonaFile.java", "place 0 is read", "if (appId < FIRST_APP_ID || appId > LAST_APP_ID || place < 1 || place > Integer.MAX_VALUE) {", "if (appId < FIRST_APP_ID || appId > LAST_APP_ID || place < 0 || place > Integer.MAX_VALUE) {"),
    ("PersonaFile.java", "a place beyond int is read", "|| place > Integer.MAX_VALUE) {", ") {"),
    ("PersonaFile.java", "one line too many is read", "if (++lines > MAX_LINES) {", "if (lines++ > MAX_LINES) {"),
    ("PersonaFile.java", "a number with a leading zero is read", 'Pattern.compile("p (0|[1-9][0-9]{0,9}) (0|[1-9][0-9]{0,9})")', 'Pattern.compile("p ([0-9]{1,10}) ([0-9]{1,10})")'),
    ("PersonaFile.java", "a line type other than p is read", 'Pattern.compile("p (0|', 'Pattern.compile("[px] (0|'),
    ("PersonaFile.java", "the file need not end with a newline", "|| text.charAt(text.length() - 1) != '\\n'", "|| text.charAt(text.length() - 1) == 'x'"),
    ("PersonaFile.java", "the places of another user are not found", "int appId = uid % USER_OFFSET;", "int appId = uid;"),
    ("PersonaFile.java", "the first sandbox has no place", "appId >= FIRST_APP_ID + SANDBOX_OFFSET &&", "appId > FIRST_APP_ID + SANDBOX_OFFSET &&"),
    ("PersonaFile.java", "the last sandbox has no place", "appId <= LAST_APP_ID + SANDBOX_OFFSET", "appId < LAST_APP_ID + SANDBOX_OFFSET"),
    ("PersonaFile.java", "a sandbox answers for the wrong app", "appId -= SANDBOX_OFFSET;", "appId += SANDBOX_OFFSET;"),
    ("PersonaFile.java", "an app without a place gets one", "return place == null ? 0 : place;", "return place == null ? 1 : place;"),
    ("Profiles.java", "the order of Profile is changed", "enum Profile { TRUSTED, PROTECTED, ISOLATED }", "enum Profile { PROTECTED, TRUSTED, ISOLATED }"),
    ("Profiles.java", "the order of Cls is changed", "enum Cls { SENSORS, LOOPBACK, SETTINGS, LOCATION }", "enum Cls { LOOPBACK, SENSORS, SETTINGS, LOCATION }"),
    ("Profiles.java", "LOCATION is not the last class", "enum Cls { SENSORS, LOOPBACK, SETTINGS, LOCATION }", "enum Cls { SENSORS, LOCATION, LOOPBACK, SETTINGS }"),
    ("Profiles.java", "Isolated location is not substituted", "if (c == Cls.SENSORS || c == Cls.LOCATION) {", "if (c == Cls.SENSORS) {"),
    ("Profiles.java", "Isolated location is Real", "if (c == Cls.SENSORS || c == Cls.LOCATION) {\n            return p == Profile.ISOLATED ? Value.SUBSTITUTE : Value.REAL;", "if (c == Cls.SENSORS || c == Cls.LOCATION) {\n            return Value.REAL;"),
    ("Profiles.java", "location cannot be substituted", "return c == Cls.SENSORS || c == Cls.LOCATION || v != Value.SUBSTITUTE;", "return c == Cls.SENSORS || v != Value.SUBSTITUTE;"),
    ("Profiles.java", "loopback can be substituted", "return c == Cls.SENSORS || c == Cls.LOCATION || v != Value.SUBSTITUTE;", "return c != Cls.SETTINGS || v != Value.SUBSTITUTE;"),
    ("Profiles.java", "the order of Value is changed", "enum Value { REAL, SUBSTITUTE, BLOCKED }", "enum Value { REAL, BLOCKED, SUBSTITUTE }"),
    ("Profiles.java", "Isolated sensors are not substituted", "return p == Profile.ISOLATED ? Value.SUBSTITUTE : Value.REAL;", "return Value.REAL;"),
    ("Profiles.java", "Trusted loopback and settings are blocked", "return p == Profile.TRUSTED ? Value.REAL : Value.BLOCKED;", "return Value.BLOCKED;"),
    ("Profiles.java", "everything is valid", "return c == Cls.SENSORS || c == Cls.LOCATION || v != Value.SUBSTITUTE;", "return true;"),
    ("Profiles.java", "every real sensor is raw", "return p == Profile.TRUSTED ? SensorMode.RAW : SensorMode.FOLDED;", "return SensorMode.RAW;"),
    ("Profiles.java", "a Trusted app is folded", "return p == Profile.TRUSTED ? SensorMode.RAW : SensorMode.FOLDED;", "return SensorMode.FOLDED;"),
    ("Profiles.java", "blocked sensors are real", "case BLOCKED:\n                return SensorMode.BLOCKED;", "case BLOCKED:\n                return SensorMode.FOLDED;"),
    ("PolicyStore.java", "Trusted may be the default", "d.profile != Profile.TRUSTED\n                && ", ""),
    ("PolicyStore.java", "loopback may be opened by default", "&& d.value(Cls.LOOPBACK) == Profiles.defaultValue(d.profile, Cls.LOOPBACK)\n", "\n"),
    ("PolicyStore.java", "a new profile keeps the overrides", "return new AppPolicy(p, new Value[Cls.values().length], profile == Profile.ISOLATED && p == Profile.ISOLATED ? place : 0);", "return new AppPolicy(p, overrides, profile == Profile.ISOLATED && p == Profile.ISOLATED ? place : 0);"),
    ("PolicyStore.java", "a new profile keeps the place", "new Value[Cls.values().length], profile == Profile.ISOLATED && p == Profile.ISOLATED ? place : 0);", "new Value[Cls.values().length], place);"),
    ("PolicyStore.java", "a new profile resets the place of an app that stays Isolated", "profile == Profile.ISOLATED && p == Profile.ISOLATED ? place : 0);", "0);"),
    ("PolicyStore.java", "leaving Isolated keeps the place", "profile == Profile.ISOLATED && p == Profile.ISOLATED ? place : 0);", "profile == Profile.ISOLATED ? place : 0);"),
    ("PolicyStore.java", "entering Isolated keeps the place", "profile == Profile.ISOLATED && p == Profile.ISOLATED ? place : 0);", "p == Profile.ISOLATED ? place : 0);"),
    ("PolicyStore.java", "a class value resets the place", "return new AppPolicy(profile, next, place);", "return new AppPolicy(profile, next, 0);"),
    ("PolicyStore.java", "a negative place is accepted", "if (place < 0) {\n                throw new IllegalArgumentException(\"a place is positive, or 0 for Automatic\");", "if (place < -1000) {\n                throw new IllegalArgumentException(\"a place is positive, or 0 for Automatic\");"),
    ("PolicyStore.java", "withPlace sets another place", "return new AppPolicy(profile, overrides, id);", "return new AppPolicy(profile, overrides, id + 1);"),
    ("PolicyStore.java", "the defaults may have a place", "\n                && d.place == 0", ""),
    ("PolicyStore.java", "an app without an entry has a substituted location", "return c == Cls.LOCATION ? Value.REAL : defaults.value(c);", "return defaults.value(c);"),
    ("PolicyStore.java", "an app without an entry has a real motion sensor", "return c == Cls.LOCATION ? Value.REAL : defaults.value(c);", "return Value.REAL;"),
    ("PolicyStore.java", "value() ignores the entry", "return p != null ? p.value(c) : valueWithoutEntry(c);", "return valueWithoutEntry(c);"),
    ("PolicyStore.java", "value() ignores the defaults", "return p != null ? p.value(c) : valueWithoutEntry(c);", "return p != null ? p.value(c) : Value.REAL;"),
    ("PolicyStore.java", "the file is written as version 1", "private static final int VERSION = 2;", "private static final int VERSION = 1;"),
    ("PolicyStore.java", "version 3 is accepted", "if (version != 1 && version != VERSION) {", "if (version != 1 && version != VERSION && version != 3) {"),
    ("PolicyStore.java", "version 1 is refused", "if (version != 1 && version != VERSION) {", "if (version != VERSION) {"),
    ("PolicyStore.java", "version 0 is accepted", "if (version != 1 && version != VERSION) {", "if (version > VERSION) {"),
    ("PolicyStore.java", "a version 1 file may have four classes", "classes > (version == 1 ? VERSION_1_CLASSES : Cls.values().length)", "classes > Cls.values().length"),
    ("PolicyStore.java", "a version 2 file may have five classes", "classes > (version == 1 ? VERSION_1_CLASSES : Cls.values().length)", "classes > (version == 1 ? VERSION_1_CLASSES : Cls.values().length + 1)"),
    ("PolicyStore.java", "a version 2 file may have only three classes", "classes > (version == 1 ? VERSION_1_CLASSES : Cls.values().length)", "classes > VERSION_1_CLASSES"),
    ("PolicyStore.java", "a version 1 file has a place", "int place = version == 1 ? 0 : in.readInt();", "int place = in.readInt();"),
    ("PolicyStore.java", "a version 2 file has no place", "int place = version == 1 ? 0 : in.readInt();", "int place = 0;"),
    ("PolicyStore.java", "a negative place is accepted when read", "return place < 0 ? null : new AppPolicy(", "return new AppPolicy("),
    ("PolicyStore.java", "a negative place is clamped when read", "return place < 0 ? null : new AppPolicy(Profile.values()[profile], overrides, place);", "return new AppPolicy(Profile.values()[profile], overrides, Math.max(place, 0));"),
    ("PolicyStore.java", "the place is not written", "        out.writeInt(p.place);\n", ""),
    ("PolicyStore.java", "the place is read as 0", "int place = version == 1 ? 0 : in.readInt();", "int place = version == 1 ? 0 : (in.readInt() & 0);"),
    ("PolicyStore.java", "a value equal to the default is stored", "next[c.ordinal()] = v == Profiles.defaultValue(profile, c) ? null : v;", "next[c.ordinal()] = v;"),
    ("PolicyStore.java", "the number of apps is unbounded", "apps.size() >= MAX_APPS", "apps.size() > MAX_APPS + 1000000"),
    ("PolicyStore.java", "the file may hold too many apps", "if (n < 0 || n > MAX_APPS) {", "if (n < 0) {"),
    ("PolicyStore.java", "trailing bytes are accepted", "return in.read() == -1 ? new PolicyStore(d, apps) : EMPTY;", "return new PolicyStore(d, apps);"),
    ("PolicyStore.java", "a duplicate package is accepted", "|| apps.put(pkg, p) != null) {", "|| (apps.put(pkg, p) != null && false)) {"),
    ("PolicyStore.java", "a package name is not checked when read", "!validPackage(pkg) ||", ""),
    ("PolicyStore.java", "a longer class list is accepted", "|| classes > (version == 1 ? VERSION_1_CLASSES : Cls.values().length)", ""),
    ("PolicyStore.java", "an invalid value is accepted when read", "|| !Profiles.isValid(Cls.values()[i], Value.values()[v])", ""),
    ("PolicyStore.java", "the version is not checked", "if (version != 1 && version != VERSION) {", "if (version < 0) {"),
    ("PolicyStore.java", "a Trusted default is accepted when read", "if (d == null || !validDefaults(d)) {", "if (d == null) {"),
    ("Geo.java", "a negative longitude is not wrapped", "if (x < 0) {\n            x += 360.0;\n        }", ""),
    ("Geo.java", "the latitude is not clamped", "return Math.max(-90.0, Math.min(90.0, lat));", "return lat;"),
    ("PersonaTrack.java", "the first fix is not the place", "setAnchor(lat, lon, placeLat, placeLon);\n        } else if", "setAnchor(lat, lon, lat, lon);\n        } else if"),
    ("PersonaTrack.java", "a re-anchor jumps to the place", "setAnchor(lat, lon, here[0], here[1]);", "setAnchor(lat, lon, placeLat, placeLon);"),
    ("PersonaTrack.java", "the north displacement is dropped", "fakeLat + north", "fakeLat"),
    ("PersonaTrack.java", "the east displacement is not scaled", "east * scale", "east"),
    ("PersonaTrack.java", "the east scale is inverted", "Math.max(Math.cos(Math.toRadians(realLat)), MIN_COS) / Math.max(Math.cos(Math.toRadians(fakeLat)), MIN_COS)",
     "Math.max(Math.cos(Math.toRadians(fakeLat)), MIN_COS) / Math.max(Math.cos(Math.toRadians(realLat)), MIN_COS)"),
    ("PersonaTrack.java", "the east displacement is not wrapped", "double east = Geo.wrapLon(lon - realLon);", "double east = lon - realLon;"),
    ("PersonaTrack.java", "the cosine is not guarded", "private static final double MIN_COS = 0.05;", "private static final double MIN_COS = 0.0;"),
    ("PersonaTrack.java", "a NaN or infinite longitude is a valid fix", "&& !Double.isNaN(lon) && !Double.isInfinite(lon);", ";"),
    ("PersonaTrack.java", "an infinite longitude is a valid fix", " && !Double.isInfinite(lon);", ";"),
    ("PersonaTrack.java", "a latitude beyond 90 is a valid fix", "return Math.abs(lat) <= 90.0 &&", "return true &&"),
    ("PersonaTrack.java", "tryApply applies a bad fix", "return validFix(lat, lon) ? apply(lat, lon) : null;", "return apply(lat, lon);"),
    ("PersonaTrack.java", "tryApply never applies", "return validFix(lat, lon) ? apply(lat, lon) : null;", "return null;"),
    ("PersonaTrack.java", "apply accepts a bad fix", "if (!validFix(lat, lon)) {\n            throw", "if (false) {\n            throw"),
    ("PersonaTrack.java", "a place near the pole is accepted", "!(Math.abs(placeLat) <= 80.0)", "!(Math.abs(placeLat) <= 90.0)"),
    ("ActivityEstimator.java", "one fix changes the activity", "static final int CONFIRM = 3;", "static final int CONFIRM = 1;"),
    ("ActivityEstimator.java", "stale one tick early", "return lastMs != Long.MIN_VALUE && nowMs - lastMs > STALE_MS", "return lastMs != Long.MIN_VALUE && nowMs - lastMs >= STALE_MS"),
    ("ActivityEstimator.java", "a fix from the past counts", "if (nowMs < lastMs) {\n            return current;\n        }", ""),
    ("ActivityEstimator.java", "driving has no hysteresis", "speedMps >= 3.5 ? Activity.DRIVING", "speedMps >= 4.6 ? Activity.DRIVING"),
    ("ActivityEstimator.java", "walking has no hysteresis", "speedMps >= 0.4 ? Activity.WALKING", "speedMps >= 0.7 ? Activity.WALKING"),
    ("ActivityEstimator.java", "a NaN speed counts", "if (!(speedMps >= 0.0)) {\n            return from;  // NaN or negative: no information\n        }", ""),
    ("MirrorPlan.java", "the settings list holds every app", "if (s.value(pkg, Cls.SETTINGS) == Value.REAL) {", "if (true) {"),
    ("MirrorPlan.java", "the settings list has no separator", 'out.append(out.length() == 0 ? "" : ",")', 'out.append("")'),
    ("MirrorPlan.java", "every configured app holds loopback", "if (s.value(pkg, Cls.LOOPBACK) == Value.REAL) {", "if (true) {"),
    ("MirrorPlan.java", "a line equal to the default is written", "if (e.getValue() != dflt) {", "if (true) {"),
    ("MirrorPlan.java", "a platform app id gets a line", "appId >= FIRST_APP_ID && appId <= LAST_APP_ID", "appId <= LAST_APP_ID"),
    ("MirrorPlan.java", "kept: an Automatic place is not kept", "return kept > 0 ? kept : places.automaticFor(", "return places.automaticFor("),
    ("MirrorPlan.java", "kept: a kept place beats the chosen one", "if (p.place != 0) {\n            return places.byId(p.place)", "if (kept == 0 && p.place != 0) {\n            return places.byId(p.place)"),
    ("MirrorPlan.java", "kept: a chosen place of before is kept", "if (before.policyOf(pkg).place != 0 || ", "if ("),
    ("MirrorPlan.java", "kept: a place from before a real location is kept", " || before.value(pkg, Cls.LOCATION) != Value.SUBSTITUTE) {\n            return 0;", ") {\n            return 0;"),
    ("MirrorPlan.java", "kept: a place that left the data is kept", "return kept > 0 && places.byId(kept) != null ? kept : 0;", "return kept > 0 ? kept : 0;"),
    ("MirrorPlan.java", "kept: nothing is kept without a table", "if (before == null || table == null || places == null) {", "if (before == null || places == null) {"),
    ("MirrorPlan.java", "restart: a profile change is ignored", "if (a.profile != b.profile || ", "if ("),
    ("MirrorPlan.java", "restart: a sensor change is ignored", "|| before.value(pkg, Cls.SENSORS) != after.value(pkg, Cls.SENSORS)", ""),
    ("MirrorPlan.java", "restart: a location change is ignored", "|| before.value(pkg, Cls.LOCATION) != after.value(pkg, Cls.LOCATION) || a.place != b.place)", "|| a.place != b.place)"),
    ("MirrorPlan.java", "restart: a place change is ignored", " || a.place != b.place) {", ") {"),
    ("MirrorPlan.java", "restart: an added app is ignored", "all.addAll(after.configured());", ""),
    ("MirrorPlan.java", "restart: a cleared app is ignored", "Set<String> all = new TreeSet<>(before.configured());", "Set<String> all = new TreeSet<>();"),
    ("MirrorPlan.java", "retry: the first wait is 0", "return previousMs <= 0 ? RETRY_FIRST_MS :", "return previousMs < 0 ? RETRY_FIRST_MS :"),
    ("MirrorPlan.java", "retry: the wait does not double", "(int) Math.min(2L * previousMs, RETRY_MAX_MS)", "(int) Math.min(previousMs + 10_000L, RETRY_MAX_MS)"),
    ("MirrorPlan.java", "retry: the wait has no end", "(int) Math.min(2L * previousMs, RETRY_MAX_MS)", "(int) (2L * previousMs)"),
    ("MirrorPlan.java", "retry: the wait overflows", "(int) Math.min(2L * previousMs, RETRY_MAX_MS)", "Math.min(2 * previousMs, RETRY_MAX_MS)"),
    ("CellSubset.java", "the operator digits follow the locale", 'first.mcc + (first.mnc < 10 ? "0" : "") + first.mnc;', 'first.mcc + String.format("%02d", first.mnc);'),
    ("MirrorPlan.java", "a sandbox app id gets a line", "appId >= FIRST_APP_ID && appId <= LAST_APP_ID", "appId >= FIRST_APP_ID"),
    ("MirrorPlan.java", "the first app id is left out", "appId >= FIRST_APP_ID && appId <= LAST_APP_ID", "appId > FIRST_APP_ID && appId <= LAST_APP_ID"),
    ("MirrorPlan.java", "the last app id is left out", "appId >= FIRST_APP_ID && appId <= LAST_APP_ID", "appId >= FIRST_APP_ID && appId < LAST_APP_ID"),
    ("MirrorPlan.java", "the line holds the uid of one user", "int appId = uid % USER_OFFSET;", "int appId = uid;"),
    ("MirrorPlan.java", "a later package with the same app id wins", "MirrorPlan::stricter", "(old, next) -> next"),
    ("MirrorPlan.java", "the looser of two modes wins", "return rank(a) >= rank(b) ? a : b;", "return rank(a) >= rank(b) ? b : a;"),
    ("MirrorPlan.java", "blocked is not the strictest", "            default:\n                return 3;", "            default:\n                return 1;"),
    ("MirrorPlan.java", "substitute is not stricter than folded", "            case RAW:\n                return 0;", "            case RAW:\n                return 5;"),
    ("MirrorPlan.java", "the default line ignores the defaults", "mode(s.defaults.profile, s.defaults.value(Cls.SENSORS))", "mode(Profiles.Profile.PROTECTED, Value.REAL)"),
    ("MirrorPlan.java", "the activity line is written for a still phone", "if (activity != ActivityEstimator.Activity.STILL) {", "if (true) {"),
    ("MirrorPlan.java", "the activity line is never written", "if (activity != ActivityEstimator.Activity.STILL) {", "if (false) {"),
    ("MirrorPlan.java", "the activity line names another activity", 'out.append("t ").append(activity).append', 'out.append("t ").append(ActivityEstimator.Activity.WALKING).append'),
    ("MirrorPlan.java", "the old call writes an activity", "return sensorModeFile(s, r, salt, ActivityEstimator.Activity.STILL);", "return sensorModeFile(s, r, salt, ActivityEstimator.Activity.WALKING);"),
    ("MirrorPlan.java", "every configured app has a blocked location", "if (s.value(pkg, Cls.LOCATION) == Value.BLOCKED) {", "if (true) {"),
    ("MirrorPlan.java", "a substituted location counts as blocked", "if (s.value(pkg, Cls.LOCATION) == Value.BLOCKED) {", "if (s.value(pkg, Cls.LOCATION) == Value.SUBSTITUTE) {"),
    ("MirrorPlan.java", "the seed ignores the app", "return salt ^ (appId * 0x9E3779B97F4A7C15L);", "return salt;"),
    ("MirrorPlan.java", "the seed ignores the phone", "return salt ^ (appId * 0x9E3779B97F4A7C15L);", "return appId * 0x9E3779B97F4A7C15L;"),
    ("MirrorPlan.java", "the place of the user is ignored", "if (p.place != 0) {", "if (false) {"),
    ("MirrorPlan.java", "an unknown place of the user is kept", "return places.byId(p.place) != null ? p.place : 0;", "return p.place;"),
    ("MirrorPlan.java", "no place data still gives the chosen place", "if (places == null) {\n            return 0;\n        }\n        if (p.place != 0) {", "if (places == null && p.place == 0) {\n            return 0;\n        }\n        if (p.place != 0) {"),
    ("MirrorPlan.java", "a Protected app with a real location gets a persona", "if (appId < 0 || s.value(pkg, Cls.LOCATION) != Value.SUBSTITUTE) {", "if (appId < 0) {"),
    ("MirrorPlan.java", "only an Isolated profile gets a persona", "if (appId < 0 || s.value(pkg, Cls.LOCATION) != Value.SUBSTITUTE) {", "if (appId < 0 || s.policyOf(pkg).profile != Profiles.Profile.ISOLATED) {"),
    ("MirrorPlan.java", "an app without a place is written", "if (place > 0) {\n                perApp.merge(appId, place, Math::min);", "if (place >= 0) {\n                perApp.merge(appId, place, Math::min);"),
    ("MirrorPlan.java", "a shared app id gets the higher place", "perApp.merge(appId, place, Math::min);", "perApp.merge(appId, place, Math::max);"),
    ("MirrorPlan.java", "a shared app id gets the last place", "perApp.merge(appId, place, Math::min);", "perApp.merge(appId, place, (old, next) -> next);"),
    ("MirrorPlan.java", "the persona file holds uids", "perApp.merge(appId, place, Math::min);", "perApp.merge(appId + 0, place, Math::min);\n                perApp.remove(appId);\n                perApp.merge(r.uidOf(pkg), place, Math::min);"),
    ("MirrorPlan.java", "an unresolved app is not reported", "&& placeOf(s.policyOf(pkg), appId, salt, places, country, lat, lon, keptPlace(before, table, pkg, appId, places)) == 0) {", "&& false) {"),
    ("MirrorPlan.java", "a resolved app is reported", "&& placeOf(s.policyOf(pkg), appId, salt, places, country, lat, lon, keptPlace(before, table, pkg, appId, places)) == 0) {", "&& placeOf(s.policyOf(pkg), appId, salt, places, country, lat, lon, keptPlace(before, table, pkg, appId, places)) >= 0) {"),
    ("MirrorPlan.java", "an unresolved app with a real location is reported", "if (appId >= 0 && s.value(pkg, Cls.LOCATION) == Value.SUBSTITUTE\n", "if (appId >= 0\n"),
    ("MirrorPlan.java", "an unresolved app that is not installed is reported", "if (appId >= 0 && s.value(pkg, Cls.LOCATION) == Value.SUBSTITUTE\n", "if (s.value(pkg, Cls.LOCATION) == Value.SUBSTITUTE\n"),
]
def run_tests(src_dir, test_dir):
    """Compiles the sources with the tests and runs every test class; the worst exit status, or the compiler's."""
    out = test_dir.parent / "out"
    c = subprocess.run(["javac", "-d", str(out)] + [str(p) for p in src_dir.glob("*.java")] + [str(p) for p in test_dir.glob("*.java")], capture_output=True, text=True)
    if c.returncode != 0:
        return c
    runs = [subprocess.run(["java", "-ea", "-cp", str(out), "com.android.server.compartments." + test], capture_output=True, text=True)
            for test in ("ProfilesTest", "PersonaTest", "PlacesTest")]
    return max(runs, key=lambda x: x.returncode)


# The unmutated tree must pass first: with a failing baseline every mutant would count as killed and prove nothing.
with tempfile.TemporaryDirectory() as d:
    d = pathlib.Path(d)
    shutil.copytree(ROOT / "src" / "com" / "android" / "server" / "compartments", d / "src")
    shutil.copytree(ROOT / "test", d / "test", ignore=shutil.ignore_patterns("*.py", "*.sh"))
    base = run_tests(d / "src", d / "test")
    if base.returncode != 0:
        print("BASELINE FAILS, no mutant counts:", (base.stdout + base.stderr)[:400])
        sys.exit(2)
survived = 0
for name, what, old, new in MUTANTS:
    with tempfile.TemporaryDirectory() as d:
        d = pathlib.Path(d)
        shutil.copytree(ROOT / "src" / "com" / "android" / "server" / "compartments", d / "src")
        shutil.copytree(ROOT / "test", d / "test", ignore=shutil.ignore_patterns("*.py", "*.sh"))
        f = d / "src" / name
        text = f.read_text()
        assert text.count(old) == 1, f"{name}: {what!r}: pattern found {text.count(old)} times"
        f.write_text(text.replace(old, new))
        c = subprocess.run(["javac", "-d", str(d / "out")] + [str(p) for p in (d / "src").glob("*.java")] + [str(p) for p in (d / "test").glob("*.java")], capture_output=True, text=True)
        if c.returncode != 0:
            print("INVALID MUTANT (does not compile):", name, what, c.stderr[:200])
            survived += 1
            continue
        runs = [subprocess.run(["java", "-ea", "-cp", str(d / "out"), "com.android.server.compartments." + test], capture_output=True, text=True)
                for test in ("ProfilesTest", "PersonaTest", "PlacesTest")]
        r = max(runs, key=lambda x: x.returncode)
        if r.returncode == 0:
            print("SURVIVED:", name, what)
            survived += 1
        else:
            print("killed:  ", name, what)
print(f"{len(MUTANTS) - survived} of {len(MUTANTS)} mutants killed")
sys.exit(1 if survived else 0)
