# Passwords app — logo pack sources

The logos of the entries come from three places, in this order (spec 4.2): the icon of an installed app the entry is linked to
(read from the system, nothing bundled), a shape of the bundled pack, else the generated pattern. Nothing is fetched at run time.

## Bundled pack

| Item | Value |
| --- | --- |
| Source | Simple Icons, github.com/simple-icons/simple-icons: `data/simple-icons.json` and `icons/*.svg` |
| Licence | CC0-1.0 for the icon data; the logos themselves are trademarks of their owners (see NOTICE) |
| Pinned version | Simple Icons 16.35.0, commit `ed9a5192b24ad2f169bf1009931da0df27d5a49a` (2026-10-11); the release tarball (2 419 838 bytes) has SHA-256 `851b24438577ab3bee0b814b746cc66c46981e9fc8ead394efbcebeb0b361fdd`; downloaded 2026-10-22 with the owner's yes |
| Tool | `build-pack.py` (tests: `python3 -I tests/test_build_pack.py`) writes `domains.tsv` and `icons.tsv`, which go to `../app/assets/logos/` |
| Rows | A row comes from the icon's `source` URL only when the icon is named after the registrable domain (`github` for github.com); a source that is a shared host or another brand's page (30 icons name github.com, 127 wikimedia.org) gave the wrong brand for several of the top 1000 domains in the first run (twitter.com showed LibraryThing, cloudfront.net showed Envoy, readthedocs.io showed Black), so every other row is in `overrides.tsv`, reviewed by hand against the top 1000: the brand's own domain, never a CDN, tracker or hosting host |
| Output | `domains.tsv` (1959 domains, SHA-256 `58ffaacd4e324956a5bdb97c93b47225238b181acb35e234194be1f3707c6c4c`) and `icons.tsv` (1921 icons, only those a domain points at, 2 481 605 bytes, SHA-256 `e2949b72d0c828ae19df93ef30e833f214adbcace0d3e597911b9cc535b3592b`), in `../app/assets/logos/` |
| Coverage | measured with the Tranco list of 2026-10-22 (SHA-256 of the archive `0ab0fa09d91361b5281aad70baec395600a28cd4c3b6619b9ad21fe16443ef3e`; used for the measurement only, not shipped): 261 of the top 1000 domains (26%) and 746 of the top 10 000 (7%). The top 1000 holds many CDN, tracker and infrastructure hosts nobody signs in to; the rest gets the generated pattern |

Only the shape is used. The app draws it in the text colour of the theme on a neutral tile, so a brand colour never has to be
legible on both themes, and no brand colour or wordmark is shipped.

## Decided and not decided

- The pack stays as built (owner, 2026-10-23, ADR-192): no extension with the own icons of the most used sites.
- The app has no logo of its own: the rename plan 2 decides the identity.
