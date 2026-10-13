# Address data sources and licences

ADR-161 (server places). `ipmap.bin` is the read-only file that the Data Map collector reads to give a contacted address a hosting country, a city, a network operator and an anycast flag. This file records which data feeds it, how it is made, what it is pinned to and what is known to be weak. The design is `2026-10-13-server-places-design.md` and the measurement `datamap-ip-location-data.md` in the foundation repository.

## Sources (fetched 2026-10-13)

| Part | Source | Licence as stated | Input file | Bytes | SHA-256 |
| --- | --- | --- | --- | --- | --- |
| Country, IPv4 | `sapics/ip-location-db` `server-country` | PDDL-1.0 | `server-country-ipv4.csv` | 8 646 731 | in `PINS.sha256` |
| Country, IPv6 | the same | PDDL-1.0 | `server-country-ipv6.csv` | 15 455 033 | in `PINS.sha256` |
| ASN and operator, IPv4 | `sapics/ip-location-db` `origin-asn` | PDDL-1.0 (one packaged release lists CC BY 4.0: read again before each pin) | `origin-asn-ipv4.csv` | 27 115 227 | in `PINS.sha256` |
| ASN and operator, IPv6 | the same | the same | `origin-asn-ipv6.csv` | 11 355 129 | in `PINS.sha256` |
| City, IPv4 | `sapics/ip-location-db` `dbip-city` (DB-IP Lite) | CC BY 4.0, attribution required | `dbip-city-ipv4.csv.gz` | 47 846 680 | in `PINS.sha256` |
| Anycast, IPv4 | `UT-DACS/Anycast-Census` | MPL-2.0 | `IPv4-latest.csv` as `anycast-v4.csv` | 567 523 | in `PINS.sha256` |
| Anycast, IPv6 | the same | MPL-2.0 | `IPv6-latest.csv` as `anycast-v6.csv` | 442 068 | in `PINS.sha256` |
| Anchors | Wikidata, by `tools/fetch-anchors.py` | CC0-1.0 | `data/anchors.tsv` | 5 077 | `f4645113c6647f61b04f2b664416ba754331d1d5a2c30240b3f306b4221a2d7b` |

Rejected: MaxMind GeoLite2 (EULA), the RIR WHOIS databases for geography (their acceptable-use policies forbid it), `bgptools/anycast-prefixes` (no licence file), Tracker Radar, TrackerDB and Disconnect (non-commercial). The checksum release of `ip-location-db` carries stale checksums for the DB-IP files (dated 2026-08-01 for files of 2026-10-02); the pin is the hash of the file that was measured.

## How the file is made

`tools/make-ipmap.sh INPUT_DIR OUT.bin` checks the seven inputs against `PINS.sha256`, runs `tools/build-ipmap.py` and prints the SHA-256 of the result. The layout is in the docstring of the tool. The build is deterministic (two builds are byte-identical). The "latest" release of `ip-location-db` and the census change every day, so a pin can only be met from an archived copy of the inputs, kept on the build server outside this repository.

Result of the first build, 2026-10-13: `ipmap.bin`, 47 942 775 bytes, SHA-256 `0f3729b07e380e8fdf2943f14e80790f3d306fd72e4ac030a4ae10f0d6a8d247`. Sections: country IPv4 1.7 MB, ASN IPv4 3.8 MB, city IPv4 28.4 MB with 2.9 MB of locations, country IPv6 3.2 MB, ASN IPv6 2.3 MB, operator names 4.6 MB (768 KB index), anycast 0.16 MB, 50 steered networks.

## Rules of the data

1. An address has one answer: anycast first (a /24 or /48 the census lists), then a network that steers by DNS (an operator whose name holds `Amazon`, `Akamai` or `Microsoft Corporation`), then the city when the country of the city data and of `server-country` agree, `UNCERTAIN` when they differ, the anchor of the country when only the country is known.
2. IPv6 is kept to its first 64 bits; when ranges meet inside one /64 the earlier range keeps it. IPv6 has no city (IPv4 only, D6).
3. A coordinate is rounded to 0.01 degree.
4. The file says where an address lies in the data, never where a company is based; every text that shows it says "hosted in".

## Known weak points

- Many geofeeds that feed the PDDL part have no explicit licence (accepted, ADR-161 (6)).
- DB-IP Lite is updated monthly and the country part daily: the file is as old as the image.
- The census finds anycast only; DNS-steered networks are named by a short list, and their country is that of the registration.
- Country agreement between the two sources is 93% for ordinary addresses and 35% for anycast ones (measured on 1877 resolved domains, `datamap-ip-location-data.md`).
- Not measured: the phone's own resolver, IPv6 city data, and the cost of a lookup in `system_server` (`MAP-032`).
