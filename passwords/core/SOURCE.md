# Passwords core — Source Provenance

## arm64/libvault_core.so

| Item | Value |
| --- | --- |
| Source | `freeholdos-services`, directory `vault-core`, commit `8ff3220a6abc7995bb69a99b4e88cbd4e3b2a945` (`master`) |
| Toolchain | AOSP `prebuilts/rust-toolchain` 1.93.1, target `aarch64-linux-android`, `cargo build --release --locked --offline`, `panic = "abort"`, `lto`, `opt-level = "z"`, the AOSP clang as linker (`vault-core/tools/env.sh`) |
| Build command | `vault-core/tools/build-android.sh` (copies the tree to the build server, builds, then checks the library itself) |
| SHA-256 | `5da536752c4cd2a74aa1c78776189c496a6a4814de6916669e396d1610ae0ba5` |
| Size | 1 801 448 bytes |
| Built | 2026-10-21 |
| Reproduced | built twice from a `git archive` of that commit, into two cargo target directories: same SHA-256 |
| Needed libraries | `libc.so`, `libdl.so`, `libm.so` (`readelf -d`, read on the workstation) |
| Exports | the 20 `Java_org_freeholdos_passwords_core_VaultCore_*` functions and nothing else |
| Imports | no network function (`socket`, `connect`, `getaddrinfo` and the like); checked by the build script |
| Dependencies | `vault-core/Cargo.lock` (73 crates); versions, checksums and licences in `vault-core/SOURCE.md` |

`check-prebuilt.sh` compares the file with the hash above and fails on a mismatch, so the library cannot change without
this record changing. A new `libvault_core.so` is made only by `build-android.sh` from a commit of `freeholdos-services`;
this file then gets the new commit, hash and size, and the two-build comparison is repeated.

The licences of the crates (MIT, Apache-2.0, BSD-3-Clause) and the notice of the SecLists word list (MIT) ship with the
app; Task 17 of the plan adds them to the notices.
