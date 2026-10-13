// SPDX-License-Identifier: Apache-2.0
package org.sovereignos.datamap.model;

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
        // cities close together are one server place, as companies are (Places.JOIN_DEG)
        MapModel.Input close = new MapModel.Input();
        close.entries.add(entry("com.a", "t1.example", "", MapModel.SRV_CITY, "DE", "Frankfurt", 50.11, 8.68, ""));
        close.entries.add(entry("com.a", "t2.example", "", MapModel.SRV_CITY, "DE", "Berlin", 52.52, 13.4, ""));
        List<Places.Place> joined = MapModel.build(close).places(null);
        check(joined.size() == 1 && joined.get(0).members.size() == 2, "Frankfurt and Berlin are one place");
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

        // the address of a key
        check(MapModel.address("ip:93.184.216.34").equals("93.184.216.34") && MapModel.address("x.com").equals("x.com"), "address of a key");

        // the history choice travels with the data
        check(!AtlasData.empty().serverHistory && !AtlasData.unavailable().serverHistory, "off by default and when the service is not there");
        check(new AtlasData(out, true, true, 7, true, 0).serverHistory, "on when the service says so");
        check(!new AtlasData(out, true, true, 7, 0).serverHistory, "the old constructor is off");
        System.out.println("PASS ServerPlacesCheck");
    }
}
