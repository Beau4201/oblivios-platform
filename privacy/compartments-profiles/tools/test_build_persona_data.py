#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Test of build-persona-data.py: the rules of its header, and that the two parsers of the policy core accept the output."""
import importlib.util
import os
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(os.path.dirname(HERE), "system-server", "src", "com", "android", "server", "compartments")


def load(path=None):
    spec = importlib.util.spec_from_file_location("bpd", path or os.path.join(HERE, "build-persona-data.py"))
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


def city(gid, name, cc, lat, lon, pop, zone):
    f = [str(gid), name, name, "", str(lat), str(lon), "P", "PPL", cc, "", "", "", "", "", str(pop), "", "", zone, "2024-01-01"]
    return "\t".join(f)


def cell(radio, mcc, net, area, cid, lat, lon):
    return ",".join([radio, str(mcc), str(net), str(area), str(cid), "0", str(lon), str(lat), "1000", "5", "1", "0", "0", "0"])


# 12 GeoNames lines
CITIES = "\n".join([
    city(1, "Madrid", "ES", 40.4168, -3.7038, 3223334, "Europe/Madrid"),
    city(2, "Barcelona", "ES", 41.3851, 2.1734, 1620343, "Europe/Madrid"),
    city(3, "Zaragoza", "ES", 41.6488, -0.8891, 666880, "Europe/Madrid"),          # no cell near: kept without cells
    city(4, "Tiny", "ES", 40.0, -4.0, 14999, "Europe/Madrid"),                      # below 15000: dropped
    city(5, "Exactly", "ES", 39.0, -3.0, 15000, "Europe/Madrid"),                   # 15000: kept
    city(6, "Amsterdam", "NL", 52.3676, 4.9041, 821752, "Europe/Amsterdam"),
    city(7, "Rotterdam", "NL", 51.9244, 4.4777, 598199, "Europe/Amsterdam"),
    city(8, "Polar", "NO", 80.5, 20.0, 50000, "Arctic/Longyearbyen"),                # latitude 80.5: dropped
    city(9, "Edge", "NO", 80.0, 20.0, 50000, "Arctic/Longyearbyen"),                # latitude 80: kept (cells near it)
    city(10, "Bad\x01Name", "NL", 52.0, 5.0, 50000, "Europe/Amsterdam"),           # control character: dropped
    city(11, "Nowhere", "XX", 10.0, 10.0, 50000, "Africa/Lagos"),                   # no cell in the country: dropped
    city(12, "Odd Zone", "NL", 52.1, 5.1, 50000, "Europe/Am sterdam"),              # a space in the time zone: dropped
]) + "\n"


def cells_fixture():
    rows = []
    # 25 LTE cells around Madrid at growing distance; two at the same distance (ties by cid)
    for i in range(25):
        rows.append(cell("LTE", 214, 1 + i % 3, 1000 + i, 5000 + i, 40.4168 + 0.0004 * (i + 1), -3.7038))
    rows.append(cell("LTE", 214, 7, 2000, 9001, 40.4168, -3.7038 + 0.0004))    # equal distance to ...
    rows.append(cell("LTE", 214, 7, 2000, 9000, 40.4168, -3.7038 - 0.0004))    # ... this one: the lower cid first
    rows.append(cell("LTE", 214, 1, 3000, 777, 41.3851, 2.1734))               # Barcelona
    rows.append(cell("LTE", 214, 1, 3001, 778, 41.3855, 2.1734))
    rows.append(cell("LTE", 204, 4, 200, 11, 52.3676, 4.9041))                  # Amsterdam
    rows.append(cell("LTE", 204, 8, 200, 12, 52.3680, 4.9041))
    rows.append(cell("LTE", 204, 8, 200, 13, 52.3690, 4.9041))
    rows.append(cell("LTE", 202, 5, 200, 14, 52.3676, 4.9045))                  # another mcc near Amsterdam: not the country's
    rows.append(cell("LTE", 100, 5, 200, 15, 52.3676, 4.9041))                  # mcc 100: dropped
    rows.append(cell("GSM", 204, 4, 200, 16, 52.3676, 4.9041))                  # not LTE: dropped
    rows.append(cell("LTE", 204, 4, 0, 17, 52.3676, 4.9041))                    # tac 0: dropped
    rows.append(cell("LTE", 204, 4, 70000, 18, 52.3676, 4.9041))                # tac above 65535: dropped
    rows.append(cell("LTE", 204, 4, 200, 2 ** 28, 52.3676, 4.9041))             # identity beyond 28 bits: dropped
    rows.append(cell("LTE", 204, 4, 200, 19, 53.5, 4.9041))                     # 130 km away: not near
    rows.append(cell("LTE", 242, 1, 500, 21, 80.0, 20.0))                       # Edge, Norway
    rows.append(cell("LTE", 242, 1, 500, 22, 80.001, 20.0))
    rows.append(cell("LTE", 214, 1, 600, 23, 39.0, -3.0))                       # Exactly
    for i, bad in enumerate((100, 199, 800)):                                   # only out-of-range mcc near Nowhere: the city stays dropped
        rows.append(cell("LTE", bad, 1, 700, 30 + i, 10.0, 10.0))
    return "radio,mcc,net,area,cell,unit,lon,lat,range,samples,changeable,created,updated,averageSignal\n" + "\n".join(rows) + "\n"


def lines(text):
    return [l.split("\t") for l in text.splitlines()]


def check_java(places, cells):
    """The parsers of the policy core accept both files."""
    with tempfile.TemporaryDirectory() as d:
        with open(d + "/Check.java", "w") as f:
            f.write("""package com.android.server.compartments;
import java.nio.file.*;
public final class Check {
    public static void main(String[] a) throws Exception {
        Places p = Places.parse(new String(Files.readAllBytes(Paths.get(a[0])), "UTF-8"));
        CellSubset c = CellSubset.parse(new String(Files.readAllBytes(Paths.get(a[1])), "UTF-8"));
        System.out.println(p.size() + " " + c.cellsFor(1).size());
    }
}
""")
        with open(d + "/places.tsv", "w", encoding="utf-8", newline="\n") as f:
            f.write(places)
        with open(d + "/cells.tsv", "w", encoding="utf-8", newline="\n") as f:
            f.write(cells)
        srcs = [os.path.join(SRC, n) for n in ("Places.java", "CellSubset.java", "Geo.java")]
        subprocess.run(["javac", "-d", d + "/out"] + srcs + [d + "/Check.java"], check=True, capture_output=True)
        r = subprocess.run(["java", "-cp", d + "/out", "com.android.server.compartments.Check", d + "/places.tsv", d + "/cells.tsv"],
                           capture_output=True, text=True)
        assert r.returncode == 0, r.stderr
        return r.stdout.split()


def run_all(bpd):
    places, cells = bpd.build(CITIES, cells_fixture())
    again = bpd.build(CITIES, cells_fixture())
    assert (places, cells) == again, "a second run gives other bytes"
    p = lines(places)
    names = [x[1] for x in p]
    # kept: Barcelona, Exactly, Madrid, Zaragoza (ES), Amsterdam, Rotterdam (NL), Edge (NO); dropped: the other five
    # ids ascend from 1 in the order of (country, name, latitude): ES (Barcelona, Exactly, Madrid, Zaragoza), NL, NO
    assert names == ["Barcelona", "Exactly", "Madrid", "Zaragoza", "Amsterdam", "Rotterdam", "Edge"], names
    assert [x[0] for x in p] == [str(i) for i in range(1, 8)], "ids"
    by = {x[1]: x for x in p}
    assert by["Madrid"][2] == "ES" and by["Madrid"][5] == "Europe/Madrid" and by["Madrid"][6] == "214", by["Madrid"]
    assert by["Amsterdam"][6] == "204" and by["Rotterdam"][6] == "204", "the country's mcc is the common one, not 202"
    assert by["Edge"][6] == "242" and by["Edge"][3] == "80.00000", by["Edge"]
    assert "Tiny" not in names, "population 14999 is dropped"
    assert "Exactly" in names, "population 15000 is kept"
    assert "Polar" not in names, "latitude 80.5 is dropped"
    assert not any("Bad" in n for n in names), "a control character is dropped"
    assert "Nowhere" not in names, "a country without a cell is dropped"
    assert "Odd Zone" not in names, "a time zone with a space is dropped"
    c = lines(cells)
    madrid = by["Madrid"][0]
    mc = [x for x in c if x[0] == madrid]
    assert len(mc) == 20, "at most 20 cells per place: %d" % len(mc)
    cids = [int(x[4]) for x in mc]
    # nearest first: the four cells at 0.0004 degrees come first (two north/south by 44 m, two east/west by 34 m)
    assert cids[0:2] == [9000, 9001], "the nearer pair first, the lower cid first: %s" % cids[:4]
    assert cids[2] == 5000, "then the cells to the north in order of distance: %s" % cids[:4]
    assert cids[3:] == sorted(cids[3:]) and cids[3] == 5001, "growing distance: %s" % cids
    assert 5018 not in cids and 5017 in cids, "the 20 nearest and no farther one: %s" % cids
    amsterdam = [x for x in c if x[0] == by["Amsterdam"][0]]
    assert sorted(int(x[4]) for x in amsterdam) == [11, 12, 13], "Amsterdam: LTE, the country's mcc, valid tac and identity, within 25 km: %s" % amsterdam
    assert [x for x in c if x[0] == by["Zaragoza"][0]] == [], "a place without cells is kept without cells"
    assert all(200 <= int(x[1]) <= 799 for x in c), "mcc range"
    assert [int(x[0]) for x in c] == sorted(int(x[0]) for x in c), "cells in the order of the places"
    assert len([x for x in c if x[0] == by["Edge"][0]]) == 2, "Edge has its two cells"
    assert places.endswith("\n") and cells.endswith("\n")
    assert all(len(x) == 7 for x in p) and all(len(x) == 5 for x in c)
    parsed = check_java(places, cells)
    assert parsed[0] == "7", parsed


def main():
    run_all(load())
    print("build-persona-data tests passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
