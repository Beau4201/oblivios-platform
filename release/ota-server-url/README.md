# OTA server URL (SovereignOS platform integration)

Points GrapheneOS's real Updater app at SovereignOS's own private lynx OTA
server (`update.beautenberge.dev`) instead of the upstream default
(`releases.grapheneos.org`), per
`docs/superpowers/plans/2026-08-23-lynx-ota-update-server.md` (Task 5) and
`docs/superpowers/specs/2026-08-23-lynx-ota-update-server-design.md` in
`sovereignos-foundation`.

## Boundary

Do not apply this package to a Phase 1 (or later) baseline tree without a
corresponding ADR naming this exact patch set
(`sovereignos-platform/AGENTS.md`'s hard rule). That ADR is **ADR-052**
(2026-08-26, amending ADR-045).

## Why this is under `release/`, not `credential/` or `guardian/`

This concerns the update channel, not a device-security feature or
Guardian. `docs/architecture/repository-topology.md` designates a
separate `sovereignos-release` repository for this class of concern, but that
repository does not yet exist despite its own creation-order rule saying it
should (before any signed alpha artifact — one has already shipped). This
package is the pragmatic real location until that gap is separately
addressed; moving it later is a small, mechanical follow-up.

Provenance: `SOURCE.md`.
