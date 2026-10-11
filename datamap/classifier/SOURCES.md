# Classifier sources and licences

ADR-157 (the data), ADR-158 (the choice of sources; written at the close of the plan). The classifier is part of the signed system image and is a database of facts: who operates a service, where the company is based, and which documented relation joins two companies. This file records which sources feed it, which were rejected and why, and how a record is checked.

## Decision

The first version is a **list made by hand from primary sources**: the company itself (its legal entity, its filings, its developer documentation) and public registers. No third-party tracker list is imported. Owner decision of 2026-10-12 (plan decision D5).

Why: every tracker list that carries owners and categories and that was examined is either non-commercial (it cannot ship in this product) or share-alike on the derived database. A small list of facts, each with its own primary source, has no licence to carry and can be checked record by record. The cost is coverage: the first version knows the large operators, not the long tail. A service that is not listed stays `unknown`, and the app says so.

## Candidates examined (read on 2026-10-12)

| Source | Licence as stated by the source | Supplies | Result |
| --- | --- | --- | --- |
| Disconnect tracker protection lists (`disconnectme/disconnect-tracking-protection`) | CC BY-NC-SA 4.0 (the README offers a commercial licence on request) | domains, company, category | Rejected: non-commercial. |
| DuckDuckGo Tracker Radar (`duckduckgo/tracker-radar`) | CC BY-NC-SA 4.0 (the README offers a commercial licence on request) | domains, owner, category | Rejected: non-commercial. |
| Exodus Privacy tracker database | The database and API results under ODbL, the rights in single contents under DbCL; code under AGPL-3.0 | tracker names, categories | Not chosen: a classifier derived from it would be a derivative database under ODbL, with a notice for every record; it carries no owner, base or domain list that this product can use without further sources. Possible later with an ADR. |
| Public Suffix List | MPL-2.0 (`publicsuffix/list` `LICENSE`; the web page states no licence) | registrable domains | Not used: the built-in two-level heuristic of `ServiceKeys` stands. Possible later as a separate file. |
| Company filings, registers, developer documentation | Facts only | legal name, base, domains, relations | Chosen. Each fact carries its own source record. |
| Natural Earth populated places | Public domain (stated on the site) | coordinates of a city | Chosen for the coordinates, after a download that the owner approves (name, source, size asked first). |

The licence texts were read in the README or `LICENSE` of each source on the day above. The sentences are not copied here.

## Rules for a record

1. Every owner, service, publisher and relation names a source id, and every source id has an https URL that the author read on the day recorded in `data/sources.tsv`. The builder refuses a record without a source.
2. The licence of every source is on `licences.txt`. A source whose licence forbids commercial use, requires share-alike on the whole product, or cannot be read is not added.
3. A company without a documented single base is not an owner row; its services have owner `-`.
4. A relation is one of `owns`, `merged_with`, `partner_of`, `buys_ad_space_from`, states a fact and a date in its note, and never a motive.
5. A domain is listed for a company only when the company documents it for its own service, or a primary registrar or filing ties it to the company. A domain that is only observed in traffic is not a source.
6. The size of the file stays far below the limits of the device parser (20 000 lines, 4 MiB).

## Check as a reviewer (Task 7 step 6)

Ten owner rows and ten relation rows are picked at random and followed to their sources. The result is recorded here with the day of the check.
