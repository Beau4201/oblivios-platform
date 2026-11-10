// SPDX-License-Identifier: Apache-2.0
package org.oblivios.datamap.model;

import java.util.List;

/** Server places in the model (ADR-161): which services get one, how they are grouped, what they are not joined to. */
public final class ServerPlacesCheck {
    private static void check(boolean ok, String what) {
        if (!ok) {
            throw new AssertionError(what);
        }
    }

    private static MapModel.Entry entry(String pkg, String key, String owner, int kind, String cc, String city,
            double lat, double lon, String operator) {
        MapModel.Entry e = new MapModel.Entry();
        e.pkg = pkg;
        e.serviceKey = key;
        e.ownerId = owner;
        e.category = MapModel.CAT_ADVERTISING;
        e.firstWall = 1;
        e.lastWall = 2;
        e.count = 1;
        e.viaDns = true;
        e.serverKind = kind;
        e.serverCountry = cc;
        e.serverCity = city;
        e.serverLat = lat;
        e.serverLon = lon;
        e.serverOperator = operator;
        return e;
    }

    private static MapModel.Owner owner(String id, String cc, double lat, double lon) {
        MapModel.Owner o = new MapModel.Owner();
        o.id = id;
        o.name = id;
        o.countryCode = cc;
        o.lat = lat;
        o.lon = lon;
        return o;
    }

    public static void main(String[] args) {
        MapModel.Input in = new MapModel.Input();
        in.owners.put("acme", owner("acme", "US", 37.4, -122.1));
        // two services in one city, from two apps; one in a city far away; one with a country only
        in.entries.add(entry("com.a", "t1.example", "", MapModel.SRV_CITY, "DE", "Frankfurt", 50.11, 8.68, "Hetzner Online GmbH"));
        in.entries.add(entry("com.b", "t2.example", "", MapModel.SRV_CITY, "DE", "Frankfurt", 50.11, 8.68, ""));
        in.entries.add(entry("com.a", "t3.example", "", MapModel.SRV_CITY, "SE", "Stockholm", 59.33, 18.07, "Hetzner Online GmbH"));
        in.entries.add(entry("com.a", "t4.example", "", MapModel.SRV_COUNTRY, "BR", "", -10.0, -55.0, ""));
        in.entries.add(entry("com.b", "t5.example", "", MapModel.SRV_COUNTRY, "BR", "", -10.0, -55.0, ""));
        // a country place is one place of the country, whatever point the data gave this service
        in.entries.add(entry("com.b", "t6.example", "", MapModel.SRV_COUNTRY, "BR", "", -12.0, -50.0, ""));
        // no place: anycast, steered, uncertain, nothing
        in.entries.add(entry("com.a", "any.example", "", MapModel.SRV_ANYCAST, "US", "", 0, 0, "Cloudflare, Inc."));
        in.entries.add(entry("com.a", "steer.example", "", MapModel.SRV_STEERED, "US", "", 0, 0, "Amazon.com, Inc."));
        in.entries.add(entry("com.a", "unsure.example", "", MapModel.SRV_UNCERTAIN, "DE", "", 0, 0, "Hetzner Online GmbH"));
        in.entries.add(entry("com.a", "none.example", "", MapModel.SRV_NONE, "", "", 0, 0, ""));
        // a service with a known company keeps the company's place, whatever its server said
        in.entries.add(entry("com.a", "acme.example", "acme", MapModel.SRV_CITY, "DE", "Hamburg", 53.55, 10.0, "Hetzner Online GmbH"));
        // a name of the phone's own network is not a place
        in.entries.add(entry("com.a", "router.local", "", MapModel.SRV_CITY, "DE", "Cologne", 50.94, 6.96, ""));
        MapModel.Output out = MapModel.build(in);

        check(out.serverRows.size() == 3, "three server places: " + out.serverRows.size());
        MapModel.CompanyRow frankfurt = null;
        MapModel.CompanyRow stockholm = null;
        MapModel.CompanyRow brazil = null;
        for (MapModel.CompanyRow r : out.serverRows) {
            check(r.server && r.contacted, "a server row is contacted and marked as a server");
            check(r.owner.id.startsWith("srv:"), "its id cannot be a company's: " + r.owner.id);
            if (r.owner.city.equals("Frankfurt")) {
                frankfurt = r;
            } else if (r.owner.city.equals("Stockholm")) {
                stockholm = r;
            } else if (r.owner.countryCode.equals("BR")) {
                brazil = r;
            }
        }
        check(frankfurt != null && stockholm != null && brazil != null, "Frankfurt, Stockholm and the country of Brazil");
        check(frankfurt.apps.size() == 2 && frankfurt.services.size() == 2, "two apps and two services in Frankfurt");
        check(frankfurt.owner.lat == 50.11 && frankfurt.owner.lon == 8.68, "the place is where the data put it");
        check(frankfurt.owner.name.equals("Frankfurt") && brazil.owner.name.isEmpty() && brazil.owner.city.isEmpty(),
                "a country place has no city name");
        check(brazil.owner.lat == -10.0 && brazil.owner.lon == -55.0, "a country place is at its anchor");
        check(brazil.services.size() == 3 && brazil.apps.size() == 2, "three services of one country are one place");
        check("Hetzner Online GmbH".equals(frankfurt.operatorOfService.get("t1.example")), "the operator of a service");
        check(!frankfurt.operatorOfService.containsKey("t2.example"), "no operator is made up");
        check(frankfurt.appsOfService.get("t1.example").size() == 1 && frankfurt.appsOfService.get("t2.example").contains("com.b"),
                "the apps of each service");
        MapModel.Input ops = new MapModel.Input();
        ops.entries.add(entry("com.a", "o1.example", "", MapModel.SRV_CITY, "DE", "Frankfurt", 50.11, 8.68, "Hetzner Online GmbH"));
        ops.entries.add(entry("com.a", "o2.example", "", MapModel.SRV_CITY, "DE", "Frankfurt", 50.11, 8.68, "Hetzner Online GmbH"));
        ops.entries.add(entry("com.a", "o3.example", "", MapModel.SRV_CITY, "DE", "Frankfurt", 50.11, 8.68, "OVH SAS"));
        check(MapModel.build(ops).serverRows.get(0).operatorsOf(null).equals(java.util.Arrays.asList("Hetzner Online GmbH", "OVH SAS")),
                "an operator is listed once, in the order of the services");
        // what one app reached: the page of an app counts its own services and names its own operators, not those of the others
        check(frankfurt.servicesOf(null).size() == 2 && frankfurt.servicesOf("com.a").equals(java.util.Arrays.asList("t1.example")),
                "com.a reached one of the two services of Frankfurt");
        check(frankfurt.servicesOf("com.b").equals(java.util.Arrays.asList("t2.example")) && frankfurt.servicesOf("com.z").isEmpty(),
                "com.b the other, an app that reached none has none");
        check(frankfurt.operatorsOf(null).equals(java.util.Arrays.asList("Hetzner Online GmbH")), "the operators of the place");
        check(frankfurt.operatorsOf("com.a").equals(java.util.Arrays.asList("Hetzner Online GmbH")) && frankfurt.operatorsOf("com.b").isEmpty(),
                "com.b reached a service with no operator: it is not shown Hetzner");
        check(brazil.servicesOf("com.a").size() == 1 && brazil.servicesOf("com.b").size() == 2, "the country of Brazil, by app");
        for (Places.Place p : out.places(null)) {
            if (p.lead == frankfurt) {
                check(p.services() == 2 && p.services("com.a") == 1 && p.services("com.b") == 1 && p.services(null) == 2,
                        "a place counts the services of the chosen app");
            }
        }
        check(out.serverRows.get(2) == stockholm && out.serverRows.get(0).apps.size() == 2, "most apps first, a tie by id");
        check(out.serverRows.get(0) == brazil && out.serverRows.get(1) == frankfurt, "the tie of two apps goes by id");

        // the companies are unchanged: only the one with a known owner
        check(out.companies.size() == 1 && out.companies.get(0).owner.id.equals("acme"), "one company");
        check(out.companies.get(0).services.size() == 1, "the company's service is not on a server place");

        // the services that have no place of their own keep what the data said
        MapModel.UnplacedService any = null;
        MapModel.UnplacedService steer = null;
        MapModel.UnplacedService unsure = null;
        int unplacedNone = 0;
        for (MapModel.UnplacedService u : out.unplacedServices) {
            if (u.serviceKey.equals("any.example")) {
                any = u;
            } else if (u.serviceKey.equals("steer.example")) {
                steer = u;
            } else if (u.serviceKey.equals("unsure.example")) {
                unsure = u;
            } else if (u.serviceKey.equals("none.example")) {
                unplacedNone++;
            }
        }
        check(any != null && any.serverKind == MapModel.SRV_ANYCAST && any.serverOperator.equals("Cloudflare, Inc."), "anycast kept");
        check(steer != null && steer.serverKind == MapModel.SRV_STEERED, "steered kept");
        check(unsure != null && unsure.serverKind == MapModel.SRV_UNCERTAIN && unsure.serverCountry.equals("DE"), "uncertain kept");
        check(unplacedNone == 1, "a service with nothing is still listed");

        // the lists: a service on a server place is on the map, so it is not "not on the map"
        AtlasModel atlas = AtlasModel.of(out);
        boolean onPlace = false;
        boolean anycastListed = false;
        for (AtlasModel.Unplaced u : atlas.unplaced) {
            onPlace |= u.key.equals("t1.example") || u.key.equals("t4.example");
            if (u.key.equals("any.example")) {
                anycastListed = u.serverKind == MapModel.SRV_ANYCAST && u.serverOperator.equals("Cloudflare, Inc.");
            }
        }
        check(!onPlace, "a service on a server place is not listed as unplaced");
        check(anycastListed, "an anycast service is listed with what the data said");
        check(atlas.unplaced.size() == 4, "any, steer, unsure and none stay in the list: " + atlas.unplaced.size());

        // the places: the company's and, apart from them, the servers'
        List<Places.Place> places = out.places(null);
        check(places.size() == 4, "one company place and three server places: " + places.size());
        int servers = 0;
        for (Places.Place p : places) {
            servers += p.lead.server ? 1 : 0;
        }
        check(servers == 3, "three of them are server places");
        for (Places.Place p : places) {
            if (p.lead.owner.city.equals("Frankfurt")) {
                check(p.services() == 2, "a server place counts its services");
            }
        }
        // a server place at the very spot of a company stays a place of its own, led by the server row
        MapModel.Input near = new MapModel.Input();
        near.owners.put("acme", owner("acme", "US", 37.4, -122.1));
        near.entries.add(entry("com.a", "acme.example", "acme", MapModel.SRV_NONE, "", "", 0, 0, ""));
        near.entries.add(entry("com.a", "x.example", "", MapModel.SRV_CITY, "US", "Mountain View", 37.4, -122.1, ""));
        List<Places.Place> same = MapModel.build(near).places(null);
        check(same.size() == 2 && (same.get(0).lead.server != same.get(1).lead.server), "a server place is not merged into a company's");
        // cities close together stay separate server places: a server place is named after its own city, and a card that
        // said "Hosted in Frankfurt" for servers in Amsterdam and Paris would claim more than the data does
        MapModel.Input close = new MapModel.Input();
        close.entries.add(entry("com.a", "t1.example", "", MapModel.SRV_CITY, "DE", "Frankfurt", 50.11, 8.68, ""));
        close.entries.add(entry("com.a", "t2.example", "", MapModel.SRV_CITY, "NL", "Amsterdam", 52.37, 4.9, ""));
        close.entries.add(entry("com.a", "t3.example", "", MapModel.SRV_CITY, "FR", "Paris", 48.86, 2.35, ""));
        close.entries.add(entry("com.a", "t4.example", "", MapModel.SRV_COUNTRY, "DE", "", 52.52, 13.38, ""));
        List<Places.Place> separate = MapModel.build(close).places(null);
        check(separate.size() == 4, "four places, none merged: " + separate.size());
        for (Places.Place p : separate) {
            check(p.members.size() == 1 && p.countries.size() == 1, "one member and one country in each");
            check(p.lat() == p.lead.owner.lat && p.services() == 1, "at its own point, with its own service");
        }
        // the companies are still joined by distance
        MapModel.Input nearby = new MapModel.Input();
        nearby.owners.put("a", owner("a", "DE", 50.11, 8.68));
        nearby.owners.put("b", owner("b", "NL", 52.37, 4.9));
        nearby.entries.add(entry("com.a", "a.example", "a", MapModel.SRV_NONE, "", "", 0, 0, ""));
        nearby.entries.add(entry("com.a", "b.example", "b", MapModel.SRV_NONE, "", "", 0, 0, ""));
        check(MapModel.build(nearby).places(null).size() == 1, "two companies 5 degrees apart are one place");

        // one service has one answer, whichever app saw it: the one that names a place wins, and the service is on the
        // map or in the list, never both
        MapModel.Input two = new MapModel.Input();
        two.entries.add(entry("com.a", "m1.example", "", MapModel.SRV_ANYCAST, "US", "", 0, 0, "Cloudflare, Inc."));
        two.entries.add(entry("com.b", "m1.example", "", MapModel.SRV_CITY, "DE", "Frankfurt", 50.11, 8.68, "Hetzner Online GmbH"));
        two.entries.add(entry("com.c", "m2.example", "", MapModel.SRV_CITY, "DE", "Frankfurt", 50.11, 8.68, ""));
        two.entries.add(entry("com.d", "m2.example", "", MapModel.SRV_CITY, "NL", "Amsterdam", 52.37, 4.9, ""));
        two.entries.add(entry("com.a", "m3.example", "", MapModel.SRV_STEERED, "US", "", 0, 0, "AMAZON-02"));
        two.entries.add(entry("com.b", "m3.example", "", MapModel.SRV_UNCERTAIN, "DE", "", 0, 0, ""));
        two.entries.add(entry("com.a", "m4.example", "", MapModel.SRV_NONE, "", "", 0, 0, ""));
        two.entries.add(entry("com.b", "m4.example", "", MapModel.SRV_ANYCAST, "US", "", 0, 0, ""));
        MapModel.Output twoOut = MapModel.build(two);
        check(twoOut.serverRows.size() == 1, "m1 and m2 are both in Frankfurt, the first answer at that rank: " + twoOut.serverRows.size());
        MapModel.CompanyRow fra = twoOut.serverRows.get(0);
        check(fra.owner.city.equals("Frankfurt") && fra.services.size() == 2, "Frankfurt holds m1 and m2");
        check(fra.appsOfService.get("m1.example").size() == 2 && fra.appsOfService.get("m2.example").size() == 2,
                "every app that reached the service is listed with it");
        check("Hetzner Online GmbH".equals(fra.operatorOfService.get("m1.example")), "the operator is the one of the answer that is used");
        for (MapModel.UnplacedService u : twoOut.unplacedServices) {
            if (u.serviceKey.equals("m1.example") || u.serviceKey.equals("m2.example")) {
                check(u.serverKind == MapModel.SRV_CITY, "the list says what the map says: " + u.serviceKey);
            }
            if (u.serviceKey.equals("m3.example")) {
                check(u.serverKind == MapModel.SRV_STEERED && u.serverOperator.equals("AMAZON-02"), "the first answer of the same rank is used");
            }
            if (u.serviceKey.equals("m4.example")) {
                check(u.serverKind == MapModel.SRV_ANYCAST, "an answer beats none");
            }
        }
        AtlasModel twoAtlas = AtlasModel.of(twoOut);
        for (AtlasModel.Unplaced u : twoAtlas.unplaced) {
            check(!u.key.equals("m1.example") && !u.key.equals("m2.example"), "a service on the map is not in the list: " + u.key);
        }
        check(twoAtlas.unplaced.size() == 2, "m3 and m4 are in the list");
        // one chosen app: only the places that app reaches
        List<Places.Place> ofB = out.places("com.b");
        check(ofB.size() == 2, "the places of com.b: Frankfurt and Brazil, not Stockholm: " + ofB.size());
        for (Places.Place p : ofB) {
            check(!p.lead.owner.city.equals("Stockholm"), "only the places of the chosen app");
        }

        // a server place is joined to nothing
        in.owners.put("beta", owner("beta", "DE", 50.0, 8.0));
        MapModel.Relation rel = new MapModel.Relation();
        rel.ownerA = "acme";
        rel.ownerB = "beta";
        rel.type = "owns";
        in.relations.add(rel);
        in.entries.add(entry("com.a", "beta.example", "beta", MapModel.SRV_NONE, "", "", 0, 0, ""));
        MapModel.Output linked = MapModel.build(in);
        for (PlaceLinks.Pair pair : PlaceLinks.pairs(linked.places(null), linked.relations, null)) {
            check(!pair.a.lead.server && !pair.b.lead.server, "no line to or from a server place");
        }
        check(PlaceLinks.pairs(linked.places(null), linked.relations, null).size() == 1, "the relation of two companies still joins them");

        // "connected to" never lists companies for a server place, and a company page never counts a server row
        for (Places.Place p : linked.places(null)) {
            if (p.lead.server) {
                check(PlaceLinks.joined(p.members, linked.companies, linked.relations).isEmpty(), "nothing is connected to a server place");
            } else {
                check(!PlaceLinks.joined(p.members, linked.companies, linked.relations).isEmpty()
                        || p.members.size() == 1, "a company place still has its connections");
            }
        }
        check(PlaceLinks.joined(linked.serverRows, linked.companies, linked.relations).isEmpty(), "none for all the server rows");
        List<MapModel.CompanyRow> mixed = new java.util.ArrayList<>(linked.serverRows);
        mixed.add(linked.companies.get(0));
        for (PlaceLinks.Joined j : PlaceLinks.joined(mixed, linked.companies, linked.relations)) {
            check(j.apps.isEmpty() || !j.apps.equals(linked.serverRows.get(0).apps) || j.rels.size() > 0,
                    "a server row in the list adds no app to a connection");
        }

        // the address of a key
        check(MapModel.address("ip:93.184.216.34").equals("93.184.216.34") && MapModel.address("x.com").equals("x.com"), "address of a key");

        // the history choice travels with the data
        check(!AtlasData.empty().serverHistory && !AtlasData.unavailable().serverHistory, "off by default and when the service is not there");
        check(new AtlasData(out, true, true, 7, true, 0).serverHistory, "on when the service says so");
        check(!new AtlasData(out, true, true, 7, 0).serverHistory, "the old constructor is off");
        System.out.println("PASS ServerPlacesCheck");
    }
}
