// SPDX-License-Identifier: Apache-2.0
package org.freeholdos.datamap.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns one snapshot of the Data Map service into what the four tabs draw. Pure Java: the Android
 * layer copies the Binder parcelables into {@link Input} and reads {@link Output}. Nothing here
 * decides what a company is; it only arranges what the classifier file said, and it never invents a
 * place: an app whose publisher is unknown, and a service whose owner is unknown, are listed as
 * "not on the map".
 */
public final class MapModel {
    // The codes of the service's Category class; part of the Binder contract (DataMapEntry#category).
    public static final int CAT_UNKNOWN = 0;
    public static final int CAT_CORE = 1;
    public static final int CAT_CDN = 2;
    public static final int CAT_ANALYTICS = 3;
    public static final int CAT_ADVERTISING = 4;
    public static final int CAT_ATTRIBUTION = 5;
    public static final int CAT_SOCIAL = 6;
    public static final int CAT_IDENTITY = 7;
    // The kinds of a server place (ADR-161); part of the Binder contract (DataMapEntry#serverKind).
    public static final int SRV_NONE = 0;
    public static final int SRV_CITY = 1;
    public static final int SRV_COUNTRY = 2;
    public static final int SRV_UNCERTAIN = 3;
    public static final int SRV_ANYCAST = 4;
    public static final int SRV_STEERED = 5;

    private MapModel() {}

    public static boolean isTracker(int category) {
        return category >= CAT_ANALYTICS && category <= CAT_IDENTITY;
    }

    // ------------------------------------------------------------------------------------ input

    public static final class Entry {
        public String pkg = "";
        public String serviceKey = "";
        public String ownerId = "";
        public int category;
        public long firstWall;
        public long lastWall;
        public int count;
        public int foreground;
        public int background;
        public boolean viaDns;
        /** What the first address of the service said (ADR-161): where the server lies, not where the company is. */
        public int serverKind;
        public String serverCountry = "";
        public String serverCity = "";
        public double serverLat;
        public double serverLon;
        public String serverOperator = "";
    }

    public static final class Owner {
        public String id = "";
        public String name = "";
        public String legalName = "";
        public String countryCode = "";
        public String city = "";
        public double lat;
        public double lon;
        public String sourceId = "";
    }

    public static final class Relation {
        public String ownerA = "";
        public String type = "";
        public String ownerB = "";
        public String note = "";
        public String sourceId = "";
    }

    public static final class Link {
        public String pkgA = "";
        public String pkgB = "";
        public int kind;
        public String[] evidence = new String[0];
        public boolean stronger;
    }

    public static final class Input {
        public final List<Entry> entries = new ArrayList<>();
        public final Map<String, Owner> owners = new HashMap<>();
        public final List<Relation> relations = new ArrayList<>();
        /** package name to the owner id of its publisher */
        public final Map<String, String> publisherOf = new HashMap<>();
        public final List<Link> links = new ArrayList<>();
    }

    // ----------------------------------------------------------------------------------- output

    public static final class Contact {
        public String serviceKey;
        public String ownerId;
        public int category;
        public long firstWall;
        public long lastWall;
        public int count;
        public int foreground;
        public int background;
        public boolean viaDns;
    }

    public static final class AppRow {
        public String pkg;
        /** The owner id of the publisher, or null if the classifier does not know it. */
        public String publisherId;
        public boolean onMap;
        public final List<Contact> contacts = new ArrayList<>();
        public long firstWall = Long.MAX_VALUE;
        public long lastWall;
        public int count;
        public int foreground;
        public int background;
        /** Distinct companies (owners) the app contacted. */
        public int companies;
        public boolean touchesTracker;
    }

    public static final class CompanyRow {
        public Owner owner;
        /** The strongest category among the services of this company that were contacted. */
        public int category;
        /** False for a company that no app contacts but that a relation joins to one that is. */
        public boolean contacted;
        public final Set<String> apps = new LinkedHashSet<>();
        /** Every category of the services of this company that were contacted, once each (the types of the company). */
        public final Set<Integer> categories = new LinkedHashSet<>();
        public final Set<String> services = new LinkedHashSet<>();
        public final Set<String> connectedTo = new LinkedHashSet<>();
        /** A server place (ADR-161), not a company: its owner is made up and has no relations. */
        public boolean server;
        /** Server places only: the apps that contacted each service, and its operator when the data names one. */
        public final Map<String, Set<String>> appsOfService = new LinkedHashMap<>();
        public final Map<String, String> operatorOfService = new LinkedHashMap<>();

        /** Server places only: the services that {@code pkg} reached, or every service when {@code pkg} is null. */
        public List<String> servicesOf(String pkg) {
            List<String> out = new ArrayList<>();
            for (String s : services) {
                Set<String> apps = appsOfService.get(s);
                if (pkg == null || apps != null && apps.contains(pkg)) {
                    out.add(s);
                }
            }
            return out;
        }

        /** Server places only: the operators of {@link #servicesOf}, once each, in order. */
        public List<String> operatorsOf(String pkg) {
            List<String> out = new ArrayList<>();
            for (String s : servicesOf(pkg)) {
                String op = operatorOfService.get(s);
                if (op != null && !out.contains(op)) {
                    out.add(op);
                }
            }
            return out;
        }
    }

    /** A line from an app to a company it contacted. */
    public static final class Edge {
        public final String pkg;
        public final String ownerId;

        Edge(String pkg, String ownerId) {
            this.pkg = pkg;
            this.ownerId = ownerId;
        }
    }

    /** A service whose owner is unknown, with the apps that contacted it. */
    public static final class UnplacedService {
        public String serviceKey;
        public final Set<String> apps = new LinkedHashSet<>();
        public int count;
        /** The strongest category among the contacts of this service. */
        public int category;
        /** Whether any app found it by a name lookup; false for an address that was connected to directly. */
        public boolean viaDns;
        /** The first thing the data said about the server of this service, or SRV_NONE. */
        public int serverKind;
        public String serverCountry = "";
        public String serverCity = "";
        public String serverOperator = "";
    }

    public static final class Output {
        public final List<AppRow> apps = new ArrayList<>();
        public final List<CompanyRow> companies = new ArrayList<>();
        public final List<Edge> edges = new ArrayList<>();
        /** Documented connections between two companies that are both drawn. */
        public final List<Relation> relations = new ArrayList<>();
        public final List<UnplacedService> unplacedServices = new ArrayList<>();
        public final List<String> unplacedApps = new ArrayList<>();
        public final List<Link> links = new ArrayList<>();
        /** Where services with no known company are hosted, one row for each city or, with no city, each country. */
        public final List<CompanyRow> serverRows = new ArrayList<>();

        /** The places of the map: those of the companies, and apart from them those of the servers. */
        public List<Places.Place> places(String onlyApp) {
            List<Places.Place> all = Places.build(companies, onlyApp);
            all.addAll(Places.build(serverRows, onlyApp));
            return all;
        }
    }

    // ------------------------------------------------------------------------------------ build

    /** How strongly a category says "this company builds a picture of a person", for ordering. */
    static int rank(int category) {
        if (isTracker(category)) {
            return 3;
        }
        if (category == CAT_CDN) {
            return 2;
        }
        return category == CAT_CORE ? 1 : 0;
    }

    /** The address of a service key that is one ({@code ip:93.184.216.34}), else the key. */
    public static String address(String serviceKey) {
        return serviceKey.startsWith("ip:") ? serviceKey.substring(3) : serviceKey;
    }

    /** How much an entry says about the server: a place that can be drawn, anything else, nothing. */
    private static int serverRank(int kind) {
        return kind == SRV_CITY || kind == SRV_COUNTRY ? 2 : kind == SRV_NONE ? 0 : 1;
    }

    /**
     * Adds a service with no known company to the server place of {@code s}, the entry that says the most about its
     * server. A service has one answer, whichever app saw it (the first entry of the highest rank), so that the map and
     * the lists agree; the apps are those of every entry of the service.
     */
    private static void addServer(Map<String, CompanyRow> servers, Entry e, Entry s) {
        String key = s.serverKind == SRV_CITY
                ? s.serverCountry + "|" + s.serverCity + "|" + s.serverLat + "|" + s.serverLon : s.serverCountry;
        CompanyRow r = servers.get(key);
        if (r == null) {
            Owner o = new Owner();
            o.id = "srv:" + key;
            o.name = s.serverKind == SRV_CITY ? s.serverCity : "";
            o.countryCode = s.serverCountry;
            o.city = s.serverKind == SRV_CITY ? s.serverCity : "";
            o.lat = s.serverLat;
            o.lon = s.serverLon;
            r = new CompanyRow();
            r.owner = o;
            r.contacted = true;
            r.server = true;
            servers.put(key, r);
        }
        if (rank(e.category) > rank(r.category)) {
            r.category = e.category;
        }
        r.apps.add(e.pkg);
        r.categories.add(e.category);
        r.services.add(e.serviceKey);
        Set<String> apps = r.appsOfService.get(e.serviceKey);
        if (apps == null) {
            apps = new LinkedHashSet<>();
            r.appsOfService.put(e.serviceKey, apps);
        }
        apps.add(e.pkg);
        if (!s.serverOperator.isEmpty() && !r.operatorOfService.containsKey(e.serviceKey)) {
            r.operatorOfService.put(e.serviceKey, s.serverOperator);
        }
    }

    public static Output build(Input in) {
        Output out = new Output();
        Map<String, AppRow> apps = new LinkedHashMap<>();
        Map<String, CompanyRow> companies = new LinkedHashMap<>();
        Map<String, UnplacedService> unplaced = new LinkedHashMap<>();
        Set<String> edgeKeys = new HashSet<>();
        Map<String, CompanyRow> servers = new LinkedHashMap<>();
        // the entry of each service that says the most about its server; the first one of the highest rank
        Map<String, Entry> serverOf = new HashMap<>();
        for (Entry e : in.entries) {
            Entry best = serverOf.get(e.serviceKey);
            if (best == null || serverRank(e.serverKind) > serverRank(best.serverKind)) {
                serverOf.put(e.serviceKey, e);
            }
        }

        for (Entry e : in.entries) {
            if (HostFilter.isLocal(e.serviceKey)) {
                continue;   // a name or address of the phone's own network: no company stands behind it
            }
            AppRow a = apps.get(e.pkg);
            if (a == null) {
                a = new AppRow();
                a.pkg = e.pkg;
                a.publisherId = in.publisherOf.get(e.pkg);
                a.onMap = a.publisherId != null && in.owners.containsKey(a.publisherId);
                apps.put(e.pkg, a);
            }
            Contact c = new Contact();
            c.serviceKey = e.serviceKey;
            c.ownerId = e.ownerId;
            c.category = e.category;
            c.firstWall = e.firstWall;
            c.lastWall = e.lastWall;
            c.count = e.count;
            c.foreground = e.foreground;
            c.background = e.background;
            c.viaDns = e.viaDns;
            a.contacts.add(c);
            a.firstWall = Math.min(a.firstWall, e.firstWall);
            a.lastWall = Math.max(a.lastWall, e.lastWall);
            a.count += e.count;
            a.foreground += e.foreground;
            a.background += e.background;
            a.touchesTracker |= isTracker(e.category);

            Owner o = e.ownerId.isEmpty() ? null : in.owners.get(e.ownerId);
            if (o == null) {
                UnplacedService u = unplaced.get(e.serviceKey);
                if (u == null) {
                    u = new UnplacedService();
                    u.serviceKey = e.serviceKey;
                    unplaced.put(e.serviceKey, u);
                }
                u.apps.add(e.pkg);
                u.count += e.count;
                if (rank(e.category) > rank(u.category)) {
                    u.category = e.category;
                }
                u.viaDns |= e.viaDns;
                Entry s = serverOf.get(e.serviceKey);
                u.serverKind = s.serverKind;
                u.serverCountry = s.serverCountry;
                u.serverCity = s.serverCity;
                u.serverOperator = s.serverOperator;
                if (s.serverKind == SRV_CITY || s.serverKind == SRV_COUNTRY) {
                    addServer(servers, e, s);
                }
                continue;
            }
            CompanyRow co = companies.get(o.id);
            if (co == null) {
                co = new CompanyRow();
                co.owner = o;
                co.contacted = true;
                companies.put(o.id, co);
            }
            if (rank(e.category) > rank(co.category)) {
                co.category = e.category;
            }
            co.apps.add(e.pkg);
            co.categories.add(e.category);
            co.services.add(e.serviceKey);
            // A company that is also the app's publisher is not drawn as a line from the app to
            // itself; the contact stays in the app's list.
            if (!o.id.equals(a.publisherId) && edgeKeys.add(e.pkg + "\t" + o.id)) {
                out.edges.add(new Edge(e.pkg, o.id));
            }
        }

        for (AppRow a : apps.values()) {
            Set<String> owners = new HashSet<>();
            for (Contact c : a.contacts) {
                if (!c.ownerId.isEmpty() && in.owners.containsKey(c.ownerId)) {
                    owners.add(c.ownerId);
                }
            }
            a.companies = owners.size();
            if (!a.onMap) {
                out.unplacedApps.add(a.pkg);
            }
            Collections.sort(a.contacts, new Comparator<Contact>() {
                @Override
                public int compare(Contact x, Contact y) {
                    return Long.compare(y.lastWall, x.lastWall);
                }
            });
        }

        // Companies behind a contacted one: documented, but no app contacts them directly.
        for (Relation r : in.relations) {
            Owner oa = in.owners.get(r.ownerA);
            Owner ob = in.owners.get(r.ownerB);
            if (oa == null || ob == null) {
                continue;
            }
            boolean ca = companies.containsKey(oa.id);
            boolean cb = companies.containsKey(ob.id);
            if (ca && !cb) {
                CompanyRow behind = new CompanyRow();
                behind.owner = ob;
                companies.put(ob.id, behind);
            } else if (cb && !ca) {
                CompanyRow behind = new CompanyRow();
                behind.owner = oa;
                companies.put(oa.id, behind);
            }
        }
        for (Relation r : in.relations) {
            CompanyRow ca = companies.get(r.ownerA);
            CompanyRow cb = companies.get(r.ownerB);
            if (ca != null && cb != null) {
                out.relations.add(r);
                ca.connectedTo.add(r.ownerB);
                cb.connectedTo.add(r.ownerA);
            }
        }

        out.apps.addAll(apps.values());
        Collections.sort(out.apps, new Comparator<AppRow>() {
            @Override
            public int compare(AppRow x, AppRow y) {
                return Long.compare(y.lastWall, x.lastWall);
            }
        });
        out.companies.addAll(companies.values());
        Collections.sort(out.companies, new Comparator<CompanyRow>() {
            @Override
            public int compare(CompanyRow x, CompanyRow y) {
                int byApps = y.apps.size() - x.apps.size();
                return byApps != 0 ? byApps : x.owner.name.compareToIgnoreCase(y.owner.name);
            }
        });
        out.unplacedServices.addAll(unplaced.values());
        out.serverRows.addAll(servers.values());
        Collections.sort(out.serverRows, new Comparator<CompanyRow>() {
            @Override
            public int compare(CompanyRow x, CompanyRow y) {
                int byApps = y.apps.size() - x.apps.size();
                return byApps != 0 ? byApps : x.owner.id.compareTo(y.owner.id);
            }
        });
        out.links.addAll(in.links);
        return out;
    }
}
