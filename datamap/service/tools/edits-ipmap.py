#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
# Exact edits for patch frameworks-base/0010 (ADR-161, server places), as (repo, path, old, new). OLD must occur exactly
# once (apply-edits.py). New files: ServerPlace.java and IpMap.java are copied, not edited.
FB = "frameworks/base"
D = "services/core/java/com/android/server/datamap/"
A = "core/java/com/android/internal/datamap/"
EDITS = [
    # ---- the collector hands the address on ----
    (FB, D + "DataMapCollector.java",
     "        void record(int uid, String key, boolean viaDns, long wallMs);\n",
     "        /** @param address one address the contact answered with, or null: the server place is looked up from it */\n"
     "        void record(int uid, String key, boolean viaDns, long wallMs, String address);\n"),
    (FB, D + "DataMapCollector.java",
     "            mRecorder.record(uid, host, true, timestamp);\n",
     "            mRecorder.record(uid, host, true, timestamp,\n"
     "                    ServiceKeys.preferredAddress(ipAddresses, MAX_ADDRESSES));\n"),
    (FB, D + "DataMapCollector.java",
     "            mRecorder.record(uid, key, false, timestamp);\n",
     "            mRecorder.record(uid, key, false, timestamp, ipAddr);\n"),
    (FB, D + "ServiceKeys.java",
     "        return \"ip:\" + normalizeIp(ip);\n    }\n}\n",
     "        return \"ip:\" + normalizeIp(ip);\n    }\n\n"
     "    /**\n"
     "     * The address of a DNS answer that the server place is looked up from: the first IPv4 address, otherwise the\n"
     "     * first address, looking at no more than {@code max}. The city data is IPv4 only (ADR-161, D6). Null when the\n"
     "     * answer has none.\n"
     "     */\n"
     "    static String preferredAddress(String[] addresses, int max) {\n"
     "        String first = null;\n"
     "        if (addresses == null) {\n"
     "            return null;\n"
     "        }\n"
     "        for (int i = 0; i < addresses.length && i < max; i++) {\n"
     "            String a = addresses[i];\n"
     "            if (a == null || a.isEmpty()) {\n"
     "                continue;\n"
     "            }\n"
     "            if (a.indexOf(':') < 0) {\n"
     "                return a;\n"
     "            }\n"
     "            if (first == null) {\n"
     "                first = a;\n"
     "            }\n"
     "        }\n"
     "        return first;\n"
     "    }\n}\n"),
    # ---- the store keeps the server place of a row ----
    (FB, D + "DataMapStore.java",
     "        final boolean viaDns;\n\n        Row(int uid, String pkg, String key, long firstWall, long lastWall, int count, int fg,\n"
     "                int bg, boolean viaDns) {\n",
     "        final boolean viaDns;\n"
     "        /** What the first address of this service said (ADR-161), or null. Never the address. */\n"
     "        final ServerPlace place;\n\n"
     "        Row(int uid, String pkg, String key, long firstWall, long lastWall, int count, int fg,\n"
     "                int bg, boolean viaDns) {\n"
     "            this(uid, pkg, key, firstWall, lastWall, count, fg, bg, viaDns, null);\n"
     "        }\n\n"
     "        Row(int uid, String pkg, String key, long firstWall, long lastWall, int count, int fg,\n"
     "                int bg, boolean viaDns, ServerPlace place) {\n"),
    (FB, D + "DataMapStore.java",
     "            this.viaDns = viaDns;\n        }\n    }\n\n    private static final class Entry {",
     "            this.viaDns = viaDns;\n            this.place = place;\n        }\n    }\n\n    private static final class Entry {"),
    (FB, D + "DataMapStore.java",
     "        boolean dns;\n    }\n",
     "        boolean dns;\n        ServerPlace place;\n    }\n"),
    (FB, D + "DataMapStore.java",
     "    synchronized void record(int userId, int uid, String pkg, String key, boolean foreground,\n"
     "            boolean viaDns, long wallMs) {\n"
     "        Entry e = entryFor(userId, uid, pkg, key, wallMs);\n",
     "    synchronized void record(int userId, int uid, String pkg, String key, boolean foreground,\n"
     "            boolean viaDns, long wallMs) {\n"
     "        record(userId, uid, pkg, key, foreground, viaDns, wallMs, null);\n"
     "    }\n\n"
     "    /** @param place what the first address said, or null; the first one that says something stays */\n"
     "    synchronized void record(int userId, int uid, String pkg, String key, boolean foreground,\n"
     "            boolean viaDns, long wallMs, ServerPlace place) {\n"
     "        Entry e = entryFor(userId, uid, pkg, key, wallMs);\n"
     "        if (e.place == null && place != null && place.known()) {\n"
     "            e.place = place;   // the first address that said something stays\n"
     "        }\n"),
    (FB, D + "DataMapStore.java",
     "            e.dns |= r.viaDns;\n        }\n        UserData u = users.get(userId);",
     "            e.dns |= r.viaDns;\n            if (e.place == null) {\n                e.place = r.place;\n            }\n        }\n        UserData u = users.get(userId);"),
    (FB, D + "DataMapStore.java",
     "                    out.add(new Row(e.uid, e.pkg, e.key, e.first, e.last, e.count, e.fg, e.bg, e.dns));\n",
     "                    out.add(new Row(e.uid, e.pkg, e.key, e.first, e.last, e.count, e.fg, e.bg, e.dns, e.place));\n"),
    # ---- the user's choice to keep server places in the history (D4) ----
    (FB, D + "UserSettings.java",
     "    final int days;\n\n    UserSettings(boolean collecting, boolean history, int days) {\n",
     "    final int days;\n"
     "    /** The history also keeps the hosting country, city and operator of a service (never an address). */\n"
     "    final boolean serverHistory;\n\n"
     "    UserSettings(boolean collecting, boolean history, int days) {\n"
     "        this(collecting, history, days, false);\n"
     "    }\n\n"
     "    UserSettings(boolean collecting, boolean history, int days, boolean serverHistory) {\n"),
    (FB, D + "UserSettings.java",
     "        this.days = days;\n    }\n",
     "        this.days = days;\n        this.serverHistory = serverHistory;\n    }\n"),
    (FB, D + "UserSettings.java",
     "        return collecting && !history && days == DEFAULT_DAYS;\n",
     "        return collecting && !history && days == DEFAULT_DAYS && !serverHistory;\n"),
    (FB, D + "UserSettings.java",
     "        return new UserSettings(value, history, days);\n    }\n",
     "        return new UserSettings(value, history, days, serverHistory);\n    }\n"),
    (FB, D + "UserSettings.java",
     "        return new UserSettings(collecting, enabled, newDays);\n    }\n",
     "        return new UserSettings(collecting, enabled, newDays, serverHistory);\n    }\n\n"
     "    UserSettings withServerHistory(boolean value) {\n"
     "        return new UserSettings(collecting, history, days, value);\n    }\n"),
    (FB, D + "UserSettings.java",
     "                + (history ? 1 : 0) + \"\\ndays\\t\" + days + \"\\n\";\n",
     "                + (history ? 1 : 0) + \"\\ndays\\t\" + days + (serverHistory ? \"\\nserverHistory\\t1\" : \"\") + \"\\n\";\n"),
    (FB, D + "UserSettings.java",
     "        int days = DEFAULT_DAYS;\n        boolean header = false;\n",
     "        int days = DEFAULT_DAYS;\n        boolean serverHistory = false;\n        boolean header = false;\n"),
    (FB, D + "UserSettings.java",
     "                    case \"days\":\n                        days = Integer.parseInt(f[1]);\n                        break;\n",
     "                    case \"days\":\n                        days = Integer.parseInt(f[1]);\n                        break;\n"
     "                    case \"serverHistory\":\n                        serverHistory = flag(f[1]);\n                        break;\n"),
    (FB, D + "UserSettings.java",
     "        return new UserSettings(collecting, history, days);\n    }\n\n    private static boolean flag",
     "        return new UserSettings(collecting, history, days, serverHistory);\n    }\n\n    private static boolean flag"),
    # ---- the history file keeps the place of a row when the user chose it ----
    (FB, D + "HistoryCodec.java",
     " * row  uid  package  key  first  last  count  foreground  background  viaDns(0|1)\n * </pre>\n",
     " * row  uid  package  key  first  last  count  foreground  background  viaDns(0|1)\n"
     " * </pre>\n"
     " * A row has seven more fields, kind country city latitude longitude asn operator, when the user chose to keep\n"
     " * server places (ADR-161). The decoder reads both forms.\n"),
    (FB, D + "HistoryCodec.java",
     "    static String encode(TreeMap<Integer, List<DataMapStore.Row>> days) {\n",
     "    static String encode(TreeMap<Integer, List<DataMapStore.Row>> days) {\n"
     "        return encode(days, false);\n"
     "    }\n\n"
     "    /** @param withPlaces the user chose to keep server places (ADR-161): each row then carries its place */\n"
     "    static String encode(TreeMap<Integer, List<DataMapStore.Row>> days, boolean withPlaces) {\n"),
    (FB, D + "HistoryCodec.java",
     "                        .append(r.viaDns ? 1 : 0).append('\\n');\n",
     "                        .append(r.viaDns ? 1 : 0);\n"
     "                ServerPlace p = r.place;\n"
     "                if (withPlaces && p != null && p.known() && clean(p.country) && clean(p.city)\n"
     "                        && clean(p.operator)) {\n"
     "                    sb.append('\\t').append(p.kind).append('\\t').append(p.country).append('\\t').append(p.city)\n"
     "                            .append('\\t').append(p.lat).append('\\t').append(p.lon).append('\\t').append(p.asn)\n"
     "                            .append('\\t').append(p.operator);\n"
     "                }\n"
     "                sb.append('\\n');\n"),
    (FB, D + "HistoryCodec.java",
     "    private static boolean bad(String s) {\n",
     "    /** A text that may be empty but has no tab or line break. */\n"
     "    private static boolean clean(String s) {\n"
     "        return s != null && s.indexOf('\\t') < 0 && s.indexOf('\\n') < 0 && s.indexOf('\\r') < 0;\n"
     "    }\n\n"
     "    private static boolean bad(String s) {\n"),
    (FB, D + "HistoryCodec.java",
     "                } else if (f[0].equals(\"row\") && f.length == 10 && current != null) {\n",
     "                } else if (f[0].equals(\"row\") && (f.length == 10 || f.length == 17) && current != null) {\n"),
    (FB, D + "HistoryCodec.java",
     "                    current.add(new DataMapStore.Row(Integer.parseInt(f[1]), f[2], f[3],\n"
     "                            Long.parseLong(f[4]), Long.parseLong(f[5]), count, fg, bg,\n"
     "                            f[9].equals(\"1\")));\n",
     "                    ServerPlace place = null;\n"
     "                    if (f.length == 17) {\n"
     "                        int kind = Integer.parseInt(f[10]);\n"
     "                        if (kind < ServerPlace.NONE || kind > ServerPlace.STEERED || f[11].length() > 2) {\n"
     "                            throw new FormatException(\"bad server place\");\n"
     "                        }\n"
     "                        float lat = Float.parseFloat(f[13]);\n"
     "                        float lon = Float.parseFloat(f[14]);\n"
     "                        if (Float.isNaN(lat) || Float.isInfinite(lat) || Float.isNaN(lon) || Float.isInfinite(lon)) {\n"
     "                            throw new FormatException(\"bad coordinates\");\n"
     "                        }\n"
     "                        place = new ServerPlace(kind, f[11], f[12], lat, lon, Integer.parseInt(f[15]), f[16]);\n"
     "                    }\n"
     "                    current.add(new DataMapStore.Row(Integer.parseInt(f[1]), f[2], f[3],\n"
     "                            Long.parseLong(f[4]), Long.parseLong(f[5]), count, fg, bg,\n"
     "                            f[9].equals(\"1\"), place));\n"),
    (FB, D + "HistoryCodec.java",
     "            out.add(new DataMapStore.Row(r.uid, r.pkg, r.key, Math.max(r.firstWall, switchedOnWall), r.lastWall,\n"
     "                    count, fg, bg, r.viaDns));\n",
     "            out.add(new DataMapStore.Row(r.uid, r.pkg, r.key, Math.max(r.firstWall, switchedOnWall), r.lastWall,\n"
     "                    count, fg, bg, r.viaDns, r.place));\n"),
    (FB, D + "HistoryCodec.java",
     "                            m.count + r.count, m.fg + r.fg, m.bg + r.bg, m.viaDns || r.viaDns));\n",
     "                            m.count + r.count, m.fg + r.fg, m.bg + r.bg, m.viaDns || r.viaDns,\n"
     "                            m.place != null ? m.place : r.place));\n"),

    # ---- the service: the file, the lookup, the snapshot, the choice ----
    (FB, D + "DataMapService.java",
     "    private static final int MAX_CLASSIFIER_BYTES = 4 * 1024 * 1024;\n",
     "    private static final int MAX_CLASSIFIER_BYTES = 4 * 1024 * 1024;\n"
     "    private static final String IPMAP_PATH = \"/system/etc/datamap/ipmap.bin\";\n"
     "    private static final long MAX_IPMAP_BYTES = 256L * 1024 * 1024;\n"),
    (FB, D + "DataMapService.java",
     "    private volatile Classifier mClassifier = Classifier.empty();\n",
     "    private volatile Classifier mClassifier = Classifier.empty();\n"
     "    /** The mapped address data (ADR-161), or null: then no service has a server place. */\n"
     "    private volatile IpMap mIpMap;\n"),
    (FB, D + "DataMapService.java",
     "            mClassifier = loadClassifier();\n            refreshRegistration();\n",
     "            mClassifier = loadClassifier();\n            mIpMap = loadIpMap();\n            refreshRegistration();\n"),
    (FB, D + "DataMapService.java",
     "    // ------------------------------------------------ the callback in netd's list\n",
     "    private IpMap loadIpMap() {\n"
     "        File f = new File(IPMAP_PATH);\n"
     "        try {\n"
     "            if (!f.isFile()) {\n"
     "                Slog.w(TAG, \"no address data; no service has a server place\");\n"
     "                return null;\n"
     "            }\n"
     "            IpMap m = IpMap.open(f, MAX_IPMAP_BYTES);\n"
     "            Slog.i(TAG, \"address data: \" + f.length() + \" bytes\");\n"
     "            return m;\n"
     "        } catch (IpMap.FormatException | IOException e) {\n"
     "            Slog.e(TAG, \"the address data is refused: \" + e.getMessage());\n"
     "            return null;\n"
     "        }\n"
     "    }\n\n"
     "    // ------------------------------------------------ the callback in netd's list\n"),
    (FB, D + "DataMapService.java",
     "    @Override\n    public void record(int uid, String key, boolean viaDns, long wallMs) {\n",
     "    @Override\n    public void record(int uid, String key, boolean viaDns, long wallMs, String address) {\n"),
    (FB, D + "DataMapService.java",
     "                recordOnServiceThread(uid, key, viaDns, wallMs);\n",
     "                recordOnServiceThread(uid, key, viaDns, wallMs, address);\n"),
    (FB, D + "DataMapService.java",
     "    private void recordOnServiceThread(int uid, String key, boolean viaDns, long wallMs) {\n",
     "    private void recordOnServiceThread(int uid, String key, boolean viaDns, long wallMs, String address) {\n"),
    (FB, D + "DataMapService.java",
     "                ServiceKeys.storageKey(key, viaDns, mClassifier), isForeground(uid), viaDns, wallMs);\n",
     "                ServiceKeys.storageKey(key, viaDns, mClassifier), isForeground(uid), viaDns, wallMs,\n"
     "                placeOf(address));\n"),
    (FB, D + "DataMapService.java",
     "    @Override\n    public void invalid(int uid) {\n",
     "    /** The server place of an address, or null. The address is looked up here and goes no further. */\n"
     "    private ServerPlace placeOf(String address) {\n"
     "        IpMap m = mIpMap;\n"
     "        if (m == null || address == null) {\n"
     "            return null;\n"
     "        }\n"
     "        ServerPlace p = m.lookup(address);\n"
     "        return p != null && p.known() ? p : null;\n"
     "    }\n\n"
     "    @Override\n    public void invalid(int uid) {\n"),
    (FB, D + "DataMapService.java",
     "            e.viaDns = r.viaDns;\n            entries.add(e);\n",
     "            e.viaDns = r.viaDns;\n"
     "            ServerPlace sp = r.place;\n"
     "            e.serverKind = sp == null ? ServerPlace.NONE : sp.kind;\n"
     "            e.serverCountry = sp == null ? \"\" : sp.country;\n"
     "            e.serverCity = sp == null ? \"\" : sp.city;\n"
     "            e.serverLat = sp == null ? 0f : sp.lat;\n"
     "            e.serverLon = sp == null ? 0f : sp.lon;\n"
     "            e.serverAsn = sp == null ? 0 : sp.asn;\n"
     "            e.serverOperator = sp == null ? \"\" : sp.operator;\n"
     "            entries.add(e);\n"),
    (FB, D + "DataMapService.java",
     "        out.historyDays = st.days;\n",
     "        out.historyDays = st.days;\n        out.serverHistory = st.serverHistory;\n"),
    (FB, D + "DataMapService.java",
     "        if (writeAtomically(historyFile(user), HistoryCodec.encode(days))) {\n",
     "        if (writeAtomically(historyFile(user), HistoryCodec.encode(days, st.serverHistory))) {\n"),
    (FB, D + "DataMapService.java",
     "    @Override\n    public void clear() {\n",
     "    @Override\n"
     "    public void setServerHistory(boolean enabled) {\n"
     "        int user = enforceCaller();\n"
     "        UserSettings next = mSettings.compute(user, (k, cur) ->\n"
     "                (cur == null ? UserSettings.defaults() : cur).withServerHistory(enabled));\n"
     "        mHistoryGeneration.incrementAndGet();\n"
     "        mHandler.post(() -> {\n"
     "            saveSettings(user, next);\n"
     "            if (next.history) {\n"
     "                writeHistory(user);   // writes the file again, with the places or without them\n"
     "            }\n"
     "        });\n"
     "    }\n\n"
     "    @Override\n    public void clear() {\n"),
    (FB, D + "DataMapService.java",
     "                + \", classifier services: \" + mClassifier.serviceCount());\n",
     "                + \", classifier services: \" + mClassifier.serviceCount() + \", address data: \" + (mIpMap != null));\n"),
    # ---- the Binder contract, which the app compiles a copy of ----
    (FB, A + "DataMapEntry.aidl",
     "    boolean viaDns;\n",
     "    boolean viaDns;\n"
     "    /** What the first address of this service said (ADR-161): 0 nothing, 1 city, 2 country, 3 uncertain, 4 anycast,\n"
     "        5 steered. This is where the server lies in the data, never where the company is based. */\n"
     "    int serverKind;\n"
     "    /** Two capital letters, or an empty string. */\n"
     "    String serverCountry;\n"
     "    String serverCity;\n"
     "    /** Where to draw kind 1 and 2; zero otherwise. */\n"
     "    float serverLat;\n"
     "    float serverLon;\n"
     "    int serverAsn;\n"
     "    String serverOperator;\n"),
    (FB, A + "DataMapSnapshot.aidl",
     "    long nowWallMs;\n",
     "    long nowWallMs;\n    /** The history also keeps server places. */\n    boolean serverHistory;\n"),
    (FB, A + "IDataMapService.aidl",
     "    /** Deletes everything collected for the calling user, in memory and on disk. */\n    void clear();\n",
     "    /** Deletes everything collected for the calling user, in memory and on disk. */\n    void clear();\n\n"
     "    /** The history keeps the hosting country, city and operator of a service, or not (ADR-161). */\n"
     "    void setServerHistory(boolean enabled);\n"),
]
