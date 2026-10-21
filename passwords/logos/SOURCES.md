# Passwords app — logo pack sources

The logos of the entries come from three places, in this order (spec 4.2): the icon of an installed app the entry is linked to
(read from the system, nothing bundled), a shape of the bundled pack, else the generated pattern. Nothing is fetched at run time.

## Bundled pack

| Item | Value |
| --- | --- |
| Source | Simple Icons, github.com/simple-icons/simple-icons: `data/simple-icons.json` and `icons/*.svg` |
| Licence | CC0-1.0 for the icon data; the logos themselves are trademarks of their owners (see NOTICE) |
| Pinned commit and SHA-256 | **not yet downloaded**: needs the owner's yes; the commit, the size and the hash of the archive are recorded here when it is |
| Tool | `build-pack.py` (tests: `python3 -I tests/test_build_pack.py`) writes `domains.tsv` and `icons.tsv`, which go to `../app/assets/logos/` |
| Coverage | not measured yet; `build-pack.py --top FILE` prints it for a list of domains. The list and its source need the owner's yes too |

Only the shape is used. The app draws it in the text colour of the theme on a neutral tile, so a brand colour never has to be
legible on both themes, and no brand colour or wordmark is shipped.

## Not decided

- Extending the pack with the own icons of the most used sites needs the owner's yes for the downloads, a domain list and a
  trademark review before shipping; it does not block building (the plan, Task 15).
- The app has no logo of its own: the rename plan 2 decides the identity.
