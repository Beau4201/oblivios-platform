# Source record

## Commit (verbatim from `git format-patch`, real build-server history)

### `packages/apps/Updater`

- `7a0d44da85a709329f4c803ea911e4213be1b6b4` — "sovereignos: point Updater at
  the private lynx OTA server" (`patches/packages-apps-updater/0001`)
- Base commit: `d7e29f110890de68b63bb8c256b4d249a2d29cf9`

Generated via `git format-patch`, author identity rewritten to a generic
`SovereignOS Build <build@sovereignos.local>` identity before committing here (same
convention as `credential/duress-unlock-dummy/SOURCE.md`). Verified to
reproduce the real branch tree byte-for-byte via `git am` against the base
commit in a disposable worktree before being trusted.

## What the change does

Single-line change to `res/values/config.xml`:
`https://releases.grapheneos.org/` -> the URL of the private OTA server.

The published patch differs from the commit above in two places: the host is the reserved placeholder
`https://ota.example.invalid/` (the real host is not published), and the `index` line is left out
because the blob of the real change is not the blob of this text. A build that wants the private channel
sets the real URL in that one line before `git am`; the commit above is the landed one and is not reproduced
by the published text.

## Apply

```sh
cd packages/apps/Updater && git am ../../release/ota-server-url/patches/packages-apps-updater/*.patch
```

## Verification trail

- ADR-045 (initial deployment), ADR-047 (OFFICIAL_BUILD fix that made the
  Updater app actually present in the built image), ADR-052 (this
  citation).
- Real device confirmation: the internal record "lynx ota server flash rollback corruption incident" (not published)
  and ADR-047's own text — `app.seamlessupdate.client` present via
  `pm list packages`, "up to date" check passing against the private
  server end to end. Since amended by ADR-049/050: the otacerts trust-store
  gap is fully resolved and a real app-driven install has completed.
