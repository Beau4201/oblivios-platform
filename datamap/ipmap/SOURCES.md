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

Result of the build of 2026-10-13 (the rule of ADR-164, the seven inputs above unchanged since the first fetch: the pins of `PINS.sha256` were met): `ipmap.bin`, 47 943 083 bytes, SHA-256 `3ea58abab9e7892dfb206958e2fe4b9c7dc569dedc480a6a9cb50243aa6a90e2`, the same twice. The first build of that day (47 942 775 bytes, `0f3729b0...`) used the earlier rule (three operator names as substrings; 50 networks). The audit of the steered networks (`tools/audit-steered.py`) matched 127 of 96 065 AS numbers and 20 373 of 571 162 ranges. About fourteen of them are not edge or cloud operators (Apple Bank for Savings, Apple Wealth Securities, a broadband provider and a telecom called Apple, Gelicon-Apple, Google Fiber, two Brazilian telecoms called Amazon): small networks that lose their place, which is the safe side. Edge or cloud operators the rule does not match: Alibaba Cloud, Tencent, Oracle, TikTok and Twitter; they run origin hosting with feeds of their own and are `[assumption]` left out. The Java reader and the Python reader agree on 77 411 addresses of the file (3000 random IPv4, 3000 IPv6, the first address of every 50th anycast and city range).

## Rules of the data

1. An address has one answer: anycast first (a /24 or /48 the census lists), then a network that steers by DNS (an operator whose name holds, as whole words in any case, one of `STEERED_NAMES` of the tool: amazon, akamai, microsoft, google, cloudflare, fastly, facebook, meta platforms, apple, edgecast, edgio, limelight, cdn77, stackpath, imperva, incapsula; ADR-164), then the city when the country of the city data and of `server-country` agree, or `server-country` names none, `UNCERTAIN` when they differ, the anchor of the country when only the country is known.
2. IPv6 is kept to its first 64 bits; when ranges meet inside one /64 the earlier range keeps it. IPv6 has no city (IPv4 only, D6).
3. A coordinate is rounded to 0.01 degree.
4. The file says where an address lies in the data, never where a company is based; every text that shows it says "hosted in".

## Known weak points

- Many geofeeds that feed the PDDL part have no explicit licence (accepted, ADR-161 (6)).
- DB-IP Lite is updated monthly and the country part daily: the file is as old as the image.
- The census finds anycast only; DNS-steered networks are named by the words of the operator name, and their country is that of the registration. `tools/audit-steered.py` prints the networks that match; the list is read against the inputs before a file is shipped, and the choice of the names is `[assumption]` until then.
- The inputs are archived on the build server (`<build-root>/datamap-inputs/2026-10-13/`, checked against `PINS.sha256`). The "latest" release of `ip-location-db` changes every day: a later build takes new pins, a new table above and a new measurement.
- An IPv4 server of a NAT64 network shows as an address of `64:ff9b::/96`; the reader takes the IPv4 address from it. Another prefix of that kind is not unwrapped.
- Country agreement between the two sources is 93% for ordinary addresses and 35% for anycast ones (measured on 1877 resolved domains, `datamap-ip-location-data.md`).
- Not measured: the phone's own resolver, IPv6 city data, and the cost of a lookup in `system_server` (`MAP-032`).
