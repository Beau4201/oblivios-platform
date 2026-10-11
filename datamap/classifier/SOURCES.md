# Classifier sources and licences

ADR-157 (the data), ADR-158 (the choice of sources; written at the close of the plan). The classifier is part of the signed system image and is a database of facts: who operates a service, where the company is based, and which documented relation joins two companies. This file records which sources feed it, which were rejected and why, and how a record is checked.

## Decision

Owner decisions of 2026-10-12. The first plan was a list made by hand from primary sources (D5); it was too slow
and too small, and the owner pointed to an existing repository of companies instead. The classifier is now built
from three layers:

1. **AdGuard Companies DB** (`AdguardTeam/companiesdb`, CC-BY-SA-4.0, built on whotracks.me, CC-BY-4.0): which
   domain belongs to which tracker and company, and its category. Imported by `tools/import-companiesdb.py`.
2. **Wikidata** (CC0-1.0): where a company is based. `tools/fetch-wikidata-bases.py` looks the company up by its
   official website, and for the largest companies without a hit by name, accepting a candidate only when its
   official website belongs to the company. A company without one clear place gets no owner row.
3. **Facts checked by hand against primary sources** (`data/curated/`): the owners, services and relations that were
   read in company filings and documentation. A curated owner or service wins over the same one of the database.

A service is given an owner only when the company has a base (rule 3 below); otherwise its owner is `-` and the
app lists it as a service with no known owner. A domain the database does not list stays `unknown`.

The licence of the result is CC-BY-SA-4.0 (ShareAlike of layer 1); `NOTICE` carries the attribution.

## Candidates examined (read on 2026-10-12)

| Source | Licence as stated by the source | Supplies | Result |
| --- | --- | --- | --- |
| AdGuard Companies DB (`AdguardTeam/companiesdb`) | CC-BY-SA-4.0 (GitHub licence record); its README says it began as a conversion of whotracks.me | tracker domains, companies, 17 categories; no country or base | Chosen (layer 1). |
| whotracks.me (`whotracksme/whotracks.me`) | content CC-BY-4.0, code MIT (its README) | the origin of layer 1 | Attribution only. |
| Wikidata | CC0-1.0 | base of a company, coordinates of a city | Chosen (layer 2). |
| Disconnect tracker protection lists | CC BY-NC-SA 4.0 (the README offers a commercial licence on request) | domains, company, category | Rejected: non-commercial. |
| DuckDuckGo Tracker Radar | CC BY-NC-SA 4.0 (the README offers a commercial licence on request) | domains, owner, category | Rejected: non-commercial. |
| Ghostery TrackerDB (`ghostery/trackerdb`) | CC-BY-NC-SA-4.0 (README) | organizations with a country, patterns | Rejected: non-commercial. |
| Exodus Privacy tracker database | ODbL for the database, DbCL for its contents, AGPL-3.0 for the code | tracker names, categories | Not needed. |
| Public Suffix List | MPL-2.0 (`publicsuffix/list` `LICENSE`) | registrable domains | Not used: the built-in two-level heuristic of `ServiceKeys` stands. |
| Company filings, registers, developer documentation | Facts only | legal name, base, domains, relations | Chosen for layer 3. |

The licence texts were read in the README or `LICENSE` of each source on the day above. The sentences are not copied here.

## Flags (decision D4)

The flag of a country is the emoji of the system font (two regional indicator symbols of the country code,
`app/src/.../model/FlagEmoji.java`). No flag artwork is shipped, so there is no licence to carry and no country can
lack its flag; the `--flags` option of `build-classifier.py` is therefore not used. The cost: the look of a flag is
the look of the font, and a device without an emoji font shows two letters.

## Rules for a record

1. Every owner, service, publisher and relation names a source id, and every source id has an https URL. The builder
   refuses a record without a source. Layer 1 and layer 2 records cite the database (`ADG`, with its commit) and the
   Wikidata item (`WD-Q...`); layer 3 records cite the page that was read, with the day in its title.
2. The licence of every source is on `licences.txt`. A source whose licence forbids commercial use, requires
   share-alike on something it does not cover, or cannot be read is not added.
3. A company without a documented single base is not an owner row; its services have owner `-`.
4. A relation is one of `owns`, `merged_with`, `partner_of`, `buys_ad_space_from`, states a fact and a date in its
   note, and never a motive. It is shown as current, so `partner_of` and `buys_ad_space_from` need a source that
   shows the arrangement is current; an old announcement or a contract whose term has ended is not enough.
5. Layer 3 lists a domain for a company only when the company documents it for its own service. A domain that is only
   observed in traffic is not a source. A domain that serves many unrelated things (an asset host) is not listed
   under one category as a whole.
6. The size of the file stays far below the limits of the device parser (20 000 lines, 4 MiB).

## Check as a reviewer (2026-10-12)

- Ten generated owners, picked at random (seeded), were compared with their Wikidata item: the headquarters city and
  the official website. Ten of ten match. This shows the data is true to the source, not that Wikidata is right.
- The first build showed what the checks had missed: two records of one company under one Wikidata item gave a
  duplicate source (the device parser refused it), and 22 owners had a Wikidata code (`Q123`) as legal name or
  city where an item has no English label. Both are now handled in the importer and covered by its check.
- The 7 relations and 55 services of layer 3 come from primary pages read during the research; 4 of the 28 owners
  (X Corp, Yahoo, Snap, Pinterest) and 2 services (Mobvista brands, Digital Turbine base) were re-read by the author.
  Left out after the review: the three `partner_of` relations of group A (not shown to be current), `nativex.com`
  (category only implied), `unity3d.com` as a whole (it also serves assets), Adjust and Chartboost and Smaato
  (no document that states a base, or no longer owned).

## Coverage and what is missing

3698 services, of which 1177 (31%) have an owner with a place; they include the largest operators (Google 309
domains, Verizon, Microsoft, Amazon, Yandex, Cloudflare, Adobe, Oracle). The other 2521 have a category but no
owner: Wikidata has no item with a headquarters for about 1600 of the 2357 companies of the database, mostly small
advertising companies. A service whose company has no base is shown with its domain and category and the owner
"unknown". Bases for these companies need another source (company registers) and are open work.

Known weak rows: Criteo is placed in Luxembourg after its 2026 redomiciliation and plans to move to the US in
January 2027; ironSource and Zynga carry the base of a filing from before they were acquired; Liftoff has its
registered address only.
