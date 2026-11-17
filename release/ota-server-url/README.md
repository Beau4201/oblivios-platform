# OTA server URL (ObliviOS platform integration)

Points GrapheneOS's real Updater app at ObliviOS's own private lynx OTA
server (host not published; the patch carries the reserved placeholder
`ota.example.invalid`) instead of the upstream default
(`releases.grapheneos.org`), per
the internal record "lynx ota update server" (not published) (Task 5) and
the internal record "lynx ota update server design" (not published).

## Boundary

Do not apply this package to a Phase 1 (or later) baseline tree without a
corresponding ADR naming this exact patch set
(`CONTRIBUTING.md`'s hard rule). That ADR is **ADR-052**
(2026-08-26, amending ADR-045).

## Why this is under `release/`, not `credential/`

This concerns the update channel, not a device-security feature. The internal record "repository topology" (not published) designates a
separate `oblivios-release` repository for this class of concern, but that
repository does not yet exist despite its own creation-order rule saying it
should (before any signed alpha artifact — one has already shipped). This
package is the pragmatic real location until that gap is separately
addressed; moving it later is a small, mechanical follow-up.

Provenance: `SOURCE.md`.
