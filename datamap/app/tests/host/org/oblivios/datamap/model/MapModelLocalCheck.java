// SPDX-License-Identifier: Apache-2.0
package org.oblivios.datamap.model;

public final class MapModelLocalCheck {
    private static void check(boolean ok, String what) {
        if (!ok) throw new AssertionError(what);
    }

    private static MapModel.Entry entry(String pkg, String key, String owner) {
        MapModel.Entry e = new MapModel.Entry();
        e.pkg = pkg;
        e.serviceKey = key;
        e.ownerId = owner;
        e.firstWall = 1000;
        e.lastWall = 2000;
        e.count = 1;
        e.foreground = 1;
        return e;
    }

    public static void main(String[] args) {
        MapModel.Input in = new MapModel.Input();
        in.entries.add(entry("com.only.local", "localhost", ""));
        in.entries.add(entry("com.only.local", "ip:127.0.0.1", ""));
        in.entries.add(entry("com.mixed", "localhost", ""));
        in.entries.add(entry("com.mixed", "example.org", ""));
        MapModel.Output out = MapModel.build(in);
        check(out.apps.size() == 1, "an app that only talks to its own phone is not listed: " + out.apps.size());
        check(out.apps.get(0).pkg.equals("com.mixed"), "the other app stays");
        check(out.apps.get(0).contacts.size() == 1, "its local contact is dropped, the other stays");
        check(out.unplacedServices.size() == 1 && out.unplacedServices.get(0).serviceKey.equals("example.org"),
                "no local name among the services without an owner");
        System.out.println("PASS MapModelLocalCheck");
    }
}
