#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
# Exact edits of the app for server places (ADR-161), as (repo, path, old, new) for tools/apply-edits.py of the service
# package, run with the root of sovereignos-platform as the worktree. OLD must occur exactly once.
APP = "datamap/app"
S = "src/org/sovereignos/datamap/"
M = S + "model/"
EDITS = [
    # ---------------------------------------------------------------------------------------------------- the model
    (APP, M + "MapModel.java",
     "    public static final int CAT_IDENTITY = 7;\n",
     "    public static final int CAT_IDENTITY = 7;\n"
     "    // The kinds of a server place (ADR-161); part of the Binder contract (DataMapEntry#serverKind).\n"
     "    public static final int SRV_NONE = 0;\n"
     "    public static final int SRV_CITY = 1;\n"
     "    public static final int SRV_COUNTRY = 2;\n"
     "    public static final int SRV_UNCERTAIN = 3;\n"
     "    public static final int SRV_ANYCAST = 4;\n"
     "    public static final int SRV_STEERED = 5;\n"),
    (APP, M + "MapModel.java",
     "        public int background;\n        public boolean viaDns;\n    }\n\n    public static final class Owner {",
     "        public int background;\n        public boolean viaDns;\n"
     "        /** What the first address of the service said (ADR-161): where the server lies, not where the company is. */\n"
     "        public int serverKind;\n"
     "        public String serverCountry = \"\";\n"
     "        public String serverCity = \"\";\n"
     "        public double serverLat;\n"
     "        public double serverLon;\n"
     "        public String serverOperator = \"\";\n"
     "    }\n\n    public static final class Owner {"),
    (APP, M + "MapModel.java",
     "        public final Set<String> connectedTo = new LinkedHashSet<>();\n    }\n",
     "        public final Set<String> connectedTo = new LinkedHashSet<>();\n"
     "        /** A server place (ADR-161), not a company: its owner is made up and has no relations. */\n"
     "        public boolean server;\n"
     "        /** Server places only: the apps that contacted each service, and its operator when the data names one. */\n"
     "        public final Map<String, Set<String>> appsOfService = new LinkedHashMap<>();\n"
     "        public final Map<String, String> operatorOfService = new LinkedHashMap<>();\n"
     "    }\n"),
    (APP, M + "MapModel.java",
     "        public boolean viaDns;\n    }\n\n    public static final class Output {",
     "        public boolean viaDns;\n"
     "        /** The first thing the data said about the server of this service, or SRV_NONE. */\n"
     "        public int serverKind;\n"
     "        public String serverCountry = \"\";\n"
     "        public String serverCity = \"\";\n"
     "        public String serverOperator = \"\";\n"
     "    }\n\n    public static final class Output {"),
    (APP, M + "MapModel.java",
     "        public final List<String> unplacedApps = new ArrayList<>();\n        public final List<Link> links = new ArrayList<>();\n    }\n",
     "        public final List<String> unplacedApps = new ArrayList<>();\n        public final List<Link> links = new ArrayList<>();\n"
     "        /** Where services with no known company are hosted, one row for each city or, with no city, each country. */\n"
     "        public final List<CompanyRow> serverRows = new ArrayList<>();\n\n"
     "        /** The places of the map: those of the companies, and apart from them those of the servers. */\n"
     "        public List<Places.Place> places(String onlyApp) {\n"
     "            List<Places.Place> all = Places.build(companies, onlyApp);\n"
     "            all.addAll(Places.build(serverRows, onlyApp));\n"
     "            return all;\n"
     "        }\n"
     "    }\n"),
    (APP, M + "MapModel.java",
     "    public static Output build(Input in) {\n",
     "    /** The address of a service key that is one ({@code ip:93.184.216.34}), else the key. */\n"
     "    public static String address(String serviceKey) {\n"
     "        return serviceKey.startsWith(\"ip:\") ? serviceKey.substring(3) : serviceKey;\n"
     "    }\n\n"
     "    /** Adds a service with no known company to the server place that its address was found in. */\n"
     "    private static void addServer(Map<String, CompanyRow> servers, Entry e) {\n"
     "        String key = e.serverKind == SRV_CITY\n"
     "                ? e.serverCountry + \"|\" + e.serverCity + \"|\" + e.serverLat + \"|\" + e.serverLon : e.serverCountry;\n"
     "        CompanyRow r = servers.get(key);\n"
     "        if (r == null) {\n"
     "            Owner o = new Owner();\n"
     "            o.id = \"srv:\" + key;\n"
     "            o.name = e.serverKind == SRV_CITY ? e.serverCity : \"\";\n"
     "            o.countryCode = e.serverCountry;\n"
     "            o.city = e.serverKind == SRV_CITY ? e.serverCity : \"\";\n"
     "            o.lat = e.serverLat;\n"
     "            o.lon = e.serverLon;\n"
     "            r = new CompanyRow();\n"
     "            r.owner = o;\n"
     "            r.contacted = true;\n"
     "            r.server = true;\n"
     "            servers.put(key, r);\n"
     "        }\n"
     "        if (rank(e.category) > rank(r.category)) {\n"
     "            r.category = e.category;\n"
     "        }\n"
     "        r.apps.add(e.pkg);\n"
     "        r.categories.add(e.category);\n"
     "        r.services.add(e.serviceKey);\n"
     "        Set<String> apps = r.appsOfService.get(e.serviceKey);\n"
     "        if (apps == null) {\n"
     "            apps = new LinkedHashSet<>();\n"
     "            r.appsOfService.put(e.serviceKey, apps);\n"
     "        }\n"
     "        apps.add(e.pkg);\n"
     "        if (!e.serverOperator.isEmpty() && !r.operatorOfService.containsKey(e.serviceKey)) {\n"
     "            r.operatorOfService.put(e.serviceKey, e.serverOperator);\n"
     "        }\n"
     "    }\n\n"
     "    public static Output build(Input in) {\n"),
    (APP, M + "MapModel.java",
     "        Set<String> edgeKeys = new HashSet<>();\n",
     "        Set<String> edgeKeys = new HashSet<>();\n        Map<String, CompanyRow> servers = new LinkedHashMap<>();\n"),
    (APP, M + "MapModel.java",
     "                u.viaDns |= e.viaDns;\n                continue;\n",
     "                u.viaDns |= e.viaDns;\n"
     "                if (u.serverKind == SRV_NONE && e.serverKind != SRV_NONE) {\n"
     "                    u.serverKind = e.serverKind;\n"
     "                    u.serverCountry = e.serverCountry;\n"
     "                    u.serverCity = e.serverCity;\n"
     "                    u.serverOperator = e.serverOperator;\n"
     "                }\n"
     "                if (e.serverKind == SRV_CITY || e.serverKind == SRV_COUNTRY) {\n"
     "                    addServer(servers, e);\n"
     "                }\n"
     "                continue;\n"),
    (APP, M + "MapModel.java",
     "        out.unplacedServices.addAll(unplaced.values());\n",
     "        out.unplacedServices.addAll(unplaced.values());\n"
     "        out.serverRows.addAll(servers.values());\n"
     "        Collections.sort(out.serverRows, new Comparator<CompanyRow>() {\n"
     "            @Override\n"
     "            public int compare(CompanyRow x, CompanyRow y) {\n"
     "                int byApps = y.apps.size() - x.apps.size();\n"
     "                return byApps != 0 ? byApps : x.owner.id.compareTo(y.owner.id);\n"
     "            }\n"
     "        });\n"),
    # ---- the lists that read the model
    (APP, M + "AtlasModel.java",
     "        public boolean viaDns;\n        public final Set<String> apps = new LinkedHashSet<>();\n\n        Unplaced(String key) {",
     "        public boolean viaDns;\n"
     "        /** What the data said about the server, for a service with no place of its own (ADR-161). */\n"
     "        public int serverKind;\n"
     "        public String serverCountry = \"\";\n"
     "        public String serverCity = \"\";\n"
     "        public String serverOperator = \"\";\n"
     "        public final Set<String> apps = new LinkedHashSet<>();\n\n        Unplaced(String key) {"),
    (APP, M + "AtlasModel.java",
     "            Unplaced u = new Unplaced(s.serviceKey);\n            u.category = s.category;\n            u.viaDns = s.viaDns;\n            u.apps.addAll(s.apps);\n            m.unplaced.add(u);\n",
     "            if (s.serverKind == MapModel.SRV_CITY || s.serverKind == MapModel.SRV_COUNTRY) {\n"
     "                continue;   // it is on the map, as a server place\n"
     "            }\n"
     "            Unplaced u = new Unplaced(s.serviceKey);\n            u.category = s.category;\n            u.viaDns = s.viaDns;\n"
     "            u.serverKind = s.serverKind;\n            u.serverCountry = s.serverCountry;\n"
     "            u.serverCity = s.serverCity;\n            u.serverOperator = s.serverOperator;\n"
     "            u.apps.addAll(s.apps);\n            m.unplaced.add(u);\n"),
    (APP, M + "AtlasModel.java",
     "            return key.startsWith(\"ip:\") ? key.substring(3) : key;\n",
     "            return MapModel.address(key);\n"),
    (APP, M + "AtlasData.java",
     "    public final int historyDays;\n    public final long nowWallMs;\n",
     "    public final int historyDays;\n    /** The history also keeps the places of servers (ADR-161). */\n    public final boolean serverHistory;\n    public final long nowWallMs;\n"),
    (APP, M + "AtlasData.java",
     "    public AtlasData(MapModel.Output out, boolean collecting, boolean historyOn, int historyDays, long nowWallMs) {\n        this(out, collecting, historyOn, historyDays, nowWallMs, true);\n    }\n\n    private AtlasData(MapModel.Output out, boolean collecting, boolean historyOn, int historyDays, long nowWallMs,\n            boolean available) {\n",
     "    public AtlasData(MapModel.Output out, boolean collecting, boolean historyOn, int historyDays, long nowWallMs) {\n        this(out, collecting, historyOn, historyDays, false, nowWallMs, true);\n    }\n\n"
     "    public AtlasData(MapModel.Output out, boolean collecting, boolean historyOn, int historyDays,\n            boolean serverHistory, long nowWallMs) {\n        this(out, collecting, historyOn, historyDays, serverHistory, nowWallMs, true);\n    }\n\n"
     "    private AtlasData(MapModel.Output out, boolean collecting, boolean historyOn, int historyDays,\n            boolean serverHistory, long nowWallMs, boolean available) {\n"),
    (APP, M + "AtlasData.java",
     "        this.historyDays = historyDays;\n        this.nowWallMs = nowWallMs;\n",
     "        this.historyDays = historyDays;\n        this.serverHistory = serverHistory;\n        this.nowWallMs = nowWallMs;\n"),
    (APP, M + "AtlasData.java",
     "        return new AtlasData(new MapModel.Output(), true, false, KeepFor.DEFAULT, 0, false);\n",
     "        return new AtlasData(new MapModel.Output(), true, false, KeepFor.DEFAULT, false, 0, false);\n"),
    (APP, M + "PlaceLinks.java",
     "            for (MapModel.CompanyRow m : p.members) {\n                cos.add(m);\n                of.add(p);\n            }\n",
     "            for (MapModel.CompanyRow m : p.members) {\n"
     "                if (m.server) {\n"
     "                    continue;   // a server place is joined to nothing: it is where a server lies, not a company\n"
     "                }\n"
     "                cos.add(m);\n                of.add(p);\n            }\n"),
    # ------------------------------------------------------------------------------------------- the Android layer
    (APP, S + "SnapshotMapper.java",
     "                m.viaDns = e.viaDns;\n                in.entries.add(m);\n",
     "                m.viaDns = e.viaDns;\n"
     "                m.serverKind = e.serverKind;\n"
     "                m.serverCountry = nz(e.serverCountry);\n"
     "                m.serverCity = nz(e.serverCity);\n"
     "                m.serverLat = e.serverLat;\n"
     "                m.serverLon = e.serverLon;\n"
     "                m.serverOperator = nz(e.serverOperator);\n"
     "                in.entries.add(m);\n"),
    (APP, S + "MainActivity.java",
     "new AtlasData(out, snap.collecting, snap.historyOn, snap.historyDays,\n                        snap.nowWallMs);",
     "new AtlasData(out, snap.collecting, snap.historyOn, snap.historyDays,\n                        snap.serverHistory, snap.nowWallMs);"),
    (APP, S + "MainActivity.java",
     "    @Override\n    public void askDelete() {\n",
     "    @Override\n    public void setServerHistory(boolean enabled) {\n        call(s -> s.setServerHistory(enabled));\n    }\n\n    @Override\n    public void askDelete() {\n"),
    (APP, S + "SettingsPage.java",
     "        void setHistory(boolean enabled, int days);\n",
     "        void setHistory(boolean enabled, int days);\n\n        void setServerHistory(boolean enabled);\n"),
    (APP, S + "SettingsPage.java",
     "        keep.addView(seg, sp);\n",
     "        keep.addView(seg, sp);\n"
     "        final Controls.Switch serverHistory = new Controls.Switch(ds, data.serverHistory);\n"
     "        serverHistory.setContentDescription(ctx.getString(R.string.settings_server_history));\n"
     "        serverHistory.setOnCheckedChangeListener((b, on) -> actions.setServerHistory(on));\n"
     "        keep.addView(w.row(tile(R.drawable.ic_pin, false), ctx.getString(R.string.settings_server_history),\n"
     "                new Object[] {ctx.getString(R.string.settings_server_history_summary)}, serverHistory, true, null),\n"
     "                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));\n"),
    # ---- the words
    (APP, S + "Texts.java",
     "    static String apps(Context c, int n) {\n",
     "    /** \"Frankfurt\", or the name of the country when the place has no city. */\n"
     "    static String serverName(MapModel.CompanyRow row) {\n"
     "        return row.owner.city.isEmpty() ? country(row.owner.countryCode) : row.owner.city;\n"
     "    }\n\n"
     "    /** The line of a server place in a list: the services, and the operators the data names. */\n"
     "    static String serverSub(Context c, MapModel.CompanyRow row) {\n"
     "        List<String> operators = new ArrayList<>();\n"
     "        for (String op : row.operatorOfService.values()) {\n"
     "            if (!operators.contains(op)) {\n"
     "                operators.add(op);\n"
     "            }\n"
     "        }\n"
     "        String services = c.getResources().getQuantityString(R.plurals.n_services, row.services.size(), row.services.size());\n"
     "        return operators.isEmpty() ? services : services + \" · \" + (operators.size() > 2\n"
     "                ? operators.get(0) + \", \" + operators.get(1) + \" +\" + (operators.size() - 2)\n"
     "                : join(c, operators));\n"
     "    }\n\n"
     "    /** What the data says about the server of a service that is on no place; null when it says nothing. */\n"
     "    static String serverLine(Context c, org.sovereignos.datamap.model.AtlasModel.Unplaced u) {\n"
     "        switch (u.serverKind) {\n"
     "            case MapModel.SRV_UNCERTAIN:\n"
     "                return u.serverOperator.isEmpty() ? c.getString(R.string.server_uncertain)\n"
     "                        : c.getString(R.string.server_uncertain) + \" · \" + u.serverOperator;\n"
     "            case MapModel.SRV_ANYCAST:\n"
     "                return u.serverOperator.isEmpty() ? c.getString(R.string.server_anycast)\n"
     "                        : c.getString(R.string.server_anycast) + \" · \" + u.serverOperator;\n"
     "            case MapModel.SRV_STEERED:\n"
     "                return c.getString(R.string.server_behind,\n"
     "                        u.serverOperator.isEmpty() ? c.getString(R.string.server_a_network) : u.serverOperator);\n"
     "            default:\n"
     "                return null;\n"
     "        }\n"
     "    }\n\n"
     "    static String apps(Context c, int n) {\n"),
    (APP, S + "Pages.java",
     "        col.addView(w.h3(ctx.getString(R.string.not_on_the_map)));\n        Widgets.Card off2 = w.card();\n        for (AtlasModel.Unplaced u : data.atlas.unplaced) {\n            String sub;\n            if (u.key.startsWith(\"ip:\") || !u.viaDns) {",
     "        if (!data.out.serverRows.isEmpty()) {\n"
     "            col.addView(w.h3(ctx.getString(R.string.hosted_in_title)));\n"
     "            Widgets.Card hosted = w.card();\n"
     "            for (MapModel.CompanyRow r : data.out.serverRows) {\n"
     "                hosted.add(w.row(null, ctx.getString(R.string.hosted_in, Texts.serverName(r) + (r.owner.city.isEmpty() ? \"\"\n"
     "                        : \", \" + Texts.country(r.owner.countryCode))), new Object[] {Texts.serverSub(ctx, r)},\n"
     "                        w.stack(new ArrayList<>(r.apps)), false, null));\n"
     "            }\n"
     "            col.addView(hosted);\n"
     "        }\n"
     "        col.addView(w.h3(ctx.getString(R.string.not_on_the_map)));\n        Widgets.Card off2 = w.card();\n        for (AtlasModel.Unplaced u : data.atlas.unplaced) {\n            String sub;\n            String server = Texts.serverLine(ctx, u);\n            if (server != null) {\n                sub = server;\n            } else if (u.key.startsWith(\"ip:\") || !u.viaDns) {"),
    # ---- the map and the sheet
    (APP, S + "GlobeView.java",
     "double[] v = StartView.of(placeLatLons(Places.build(data.out.companies, null)));",
     "double[] v = StartView.of(placeLatLons(data.out.places(null)));"),
    (APP, S + "GlobeView.java",
     "        for (Places.Place p : Places.build(mData.out.companies, null)) {\n",
     "        for (Places.Place p : mData.out.places(null)) {\n"),
    (APP, S + "GlobeView.java",
     "Places.build(mData.out.companies, mApp) : Collections.<Places.Place>emptyList();",
     "mData.out.places(mApp) : Collections.<Places.Place>emptyList();"),
    (APP, S + "GlobeView.java",
     "        for (Places.Place p : Places.build(mData.out.companies, pkg)) {\n",
     "        for (Places.Place p : mData.out.places(pkg)) {\n"),
    (APP, S + "GlobeView.java",
     "double[] v = StartView.of(placeLatLons(Places.build(mData.out.companies, null)));",
     "double[] v = StartView.of(placeLatLons(mData.out.places(null)));"),
    (APP, S + "GlobeView.java",
     "            drawRingPoint(c, px[i], py[i], mPlaceColor[i], mPlaces.get(i).key.equals(mSelected), alpha[i]);\n",
     "            drawRingPoint(c, px[i], py[i], mPlaceColor[i], mPlaces.get(i).key.equals(mSelected), alpha[i],\n"
     "                    mPlaces.get(i).lead.server);\n"),
    (APP, S + "GlobeView.java",
     "    private void drawRingPoint(Canvas c, float x, float y, int col, boolean selected, float alpha) {\n",
     "    /** @param hollow a server place (ADR-161): the ring alone, with nothing in it, where a company's point is filled */\n"
     "    private void drawRingPoint(Canvas c, float x, float y, int col, boolean selected, float alpha, boolean hollow) {\n"),
    (APP, S + "GlobeView.java",
     "        mP.setColor(Ds.withAlpha(cMarkerCore, alpha));\n        c.drawCircle(x, y, rr, mP);\n        mStroke.setColor(Ds.withAlpha(col, alpha));\n        mStroke.setStrokeWidth(mDs.px(2.2f));\n        c.drawCircle(x, y, rr, mStroke);\n        mP.setColor(Ds.withAlpha(mLight ? col : 0xFFFFFFFF, alpha));\n        c.drawCircle(x, y, rr * 0.38f, mP);\n",
     "        if (!hollow) {\n            mP.setColor(Ds.withAlpha(cMarkerCore, alpha));\n            c.drawCircle(x, y, rr, mP);\n        }\n"
     "        mStroke.setColor(Ds.withAlpha(col, alpha));\n        mStroke.setStrokeWidth(mDs.px(2.2f));\n        c.drawCircle(x, y, rr, mStroke);\n"
     "        if (!hollow) {\n            mP.setColor(Ds.withAlpha(mLight ? col : 0xFFFFFFFF, alpha));\n            c.drawCircle(x, y, rr * 0.38f, mP);\n        }\n"),
    (APP, S + "GlobeView.java",
     "    private float cardWidth(int i, Places.Place p) {\n",
     "    /** The name on a card: a company's name, or \"Hosted in\" and the city or country of a server place (ADR-161). */\n"
     "    private String placeName(Places.Place p) {\n"
     "        return p.lead.server ? getContext().getString(R.string.hosted_in, Texts.serverName(p.lead)) : p.lead.owner.name;\n"
     "    }\n\n"
     "    private float cardWidth(int i, Places.Place p) {\n"),
    (APP, S + "GlobeView.java",
     "float name = mText.measureText(p.lead.owner.name) / unit;", "float name = mText.measureText(placeName(p)) / unit;"),
    (APP, S + "GlobeView.java",
     "        mNameShown[i] = p.lead.owner.name;\n", "        mNameShown[i] = placeName(p);\n"),
    (APP, S + "GlobeView.java",
     "mNameShown[i] = cut(p.lead.owner.name, (width - 26 - 16 - 7 - plus) * unit);", "mNameShown[i] = cut(placeName(p), (width - 26 - 16 - 7 - plus) * unit);"),
    (APP, S + "GlobeView.java",
     "R.string.place_description, p.lead.owner.name,", "R.string.place_description, placeName(p),"),
    (APP, S + "MapTab.java",
     "        parts.add(header(flags, Texts.join(c, names), c.getString(R.string.n_companies_n_apps,\n                Texts.companies(c, place.members.size()), Texts.apps(c, place.apps.size())), close, true));\n        Widgets.Card card = mW.card();\n        for (final MapModel.CompanyRow co : place.members) {\n            card.add(mW.companyRow(co, Texts.apps(c, co.apps.size()), v -> mNav.openCompany(co.owner.id)));\n        }\n",
     "        if (place.lead.server) {\n"
     "            parts.add(header(flags, c.getString(R.string.hosted_in, Texts.serverName(place.lead)\n"
     "                    + (place.lead.owner.city.isEmpty() ? \"\" : \", \" + Texts.country(place.lead.owner.countryCode))),\n"
     "                    c.getString(R.string.n_companies_n_apps, c.getResources().getQuantityString(R.plurals.n_services,\n"
     "                            place.services(), place.services()), Texts.apps(c, place.apps.size())), close, true));\n"
     "        } else {\n"
     "            parts.add(header(flags, Texts.join(c, names), c.getString(R.string.n_companies_n_apps,\n"
     "                    Texts.companies(c, place.members.size()), Texts.apps(c, place.apps.size())), close, true));\n"
     "        }\n"
     "        Widgets.Card card = mW.card();\n"
     "        for (final MapModel.CompanyRow co : place.members) {\n"
     "            if (co.server) {\n"
     "                for (String service : co.services) {\n"
     "                    String operator = co.operatorOfService.get(service);\n"
     "                    card.add(mW.row(null, MapModel.address(service), operator == null ? null : new Object[] {operator},\n"
     "                            mW.stack(new ArrayList<>(co.appsOfService.get(service))), false, null));\n"
     "                }\n"
     "            } else {\n"
     "                card.add(mW.companyRow(co, Texts.apps(c, co.apps.size()), v -> mNav.openCompany(co.owner.id)));\n"
     "            }\n"
     "        }\n"),
    (APP, M + "Places.java",
     "        void add(MapModel.CompanyRow row) {\n",
     "        /** The services of every member: for a server place, what is hosted there. */\n"
     "        public int services() {\n"
     "            Set<String> all = new LinkedHashSet<>();\n"
     "            for (MapModel.CompanyRow m : members) {\n"
     "                all.addAll(m.services);\n"
     "            }\n"
     "            return all.size();\n"
     "        }\n\n"
     "        void add(MapModel.CompanyRow row) {\n"),
    # ---- the strings
    (APP, "res/values/strings.xml",
     "    <string name=\"not_on_the_map\">Not on the map</string>\n",
     "    <string name=\"not_on_the_map\">Not on the map</string>\n"
     "    <string name=\"hosted_in\">Hosted in %1$s</string>\n"
     "    <string name=\"hosted_in_title\">Hosted in</string>\n"
     "    <string name=\"server_uncertain\">Location uncertain</string>\n"
     "    <string name=\"server_anycast\">Anycast network</string>\n"
     "    <string name=\"server_behind\">Behind %1$s</string>\n"
     "    <string name=\"server_a_network\">a content network</string>\n"),
    (APP, "res/values/strings.xml",
     "    <string name=\"settings_history_summary\">Keep a daily summary</string>\n",
     "    <string name=\"settings_history_summary\">Keep a daily summary</string>\n"
     "    <string name=\"settings_server_history\">Keep server places</string>\n"
     "    <string name=\"settings_server_history_summary\">Where services were hosted, in the summary</string>\n"),
    (APP, "res/values/strings.xml",
     "and Wikidata (CC0), with facts from company filings. See NOTICE in the classifier.</string>",
     "and Wikidata (CC0), with facts from company filings. See NOTICE in the classifier. Server places: country and operator from ip-location-db (PDDL), anycast prefixes from the University of Twente census (MPL-2.0), city from IP Geolocation by DB-IP (CC BY 4.0, db-ip.com).</string>"),
    (APP, "res/values/strings.xml",
     "    <plurals name=\"n_countries\">",
     "    <plurals name=\"n_services\">\n        <item quantity=\"one\">%d service</item>\n        <item quantity=\"other\">%d services</item>\n    </plurals>\n    <plurals name=\"n_countries\">"),
]
