# Source record

## Commit (verbatim from `git format-patch`, real build-server history)

### `packages/apps/Updater`

- `7a0d44da85a709329f4c803ea911e4213be1b6b4` — "aegisos: point Updater at
  the private lynx OTA server" (`patches/packages-apps-updater/0001`)
- Base commit: `d7e29f110890de68b63bb8c256b4d249a2d29cf9`

Generated via `git format-patch`, author identity rewritten to a generic
`AegisOS Build <build@aegisos.local>` identity before committing here (same
convention as `credential/duress-unlock-dummy/SOURCE.md`). Verified to
reproduce the real branch tree byte-for-byte via `git am` against the base
commit in a disposable worktree before being trusted.

## What the change does

Single-line change to `res/values/config.xml`:
`https://releases.grapheneos.org/` -> `https://update.beautenberge.dev/`.

## Apply

```sh
cd packages/apps/Updater && git am ../../release/ota-server-url/patches/packages-apps-updater/*.patch
```

## Verification trail

- ADR-045 (initial deployment), ADR-047 (OFFICIAL_BUILD fix that made the
  Updater app actually present in the built image), ADR-052 (this
  citation).
- Real device confirmation: `docs/devices/lynx-ota-server-flash-rollback-corruption-incident.md`
  and ADR-047's own text — `app.seamlessupdate.client` present via
  `pm list packages`, "up to date" check passing against the private
  server end to end. Since amended by ADR-049/050: the otacerts trust-store
  gap is fully resolved and a real app-driven install has completed.
