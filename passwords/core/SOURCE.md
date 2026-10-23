# Passwords core — Source Provenance

## arm64/libvault_core.so

| Item | Value |
| --- | --- |
| Source | `freeholdos-services`, directory `vault-core`, tree of commit `7e92361` (the last commit that touches `vault-core`; the services working tree was clean when it was copied to the build server, `master` `63d190e`) |
| Toolchain | AOSP `prebuilts/rust-toolchain` 1.93.1, target `aarch64-linux-android`, `cargo build --release --locked --offline`, `panic = "abort"`, `lto`, `opt-level = "z"`, the AOSP clang as linker (`vault-core/tools/env.sh`) |
| Build command | `vault-core/tools/build-android.sh` (copies the tree to the build server, builds, then checks the library itself) |
| SHA-256 | `0a02361b52edf6bcc144f35381e6fb4ffe7ad66c4b51aa5fd40327a480a29d19` |
| Size | 1 854 288 bytes |
| Built | 2026-10-23 |
| Reproduced | built twice from the clean working tree (`rsync` copy without `.git` and `target`), into two cargo target directories: same SHA-256. The previous library (`90332487ac05...`, 1 806 616 bytes, 22 functions, built 2026-10-21 from `cccf460`) lacked the CSV import. |
| Needed libraries | `libc.so`, `libdl.so`, `libm.so` (`readelf -d`, read on the workstation) |
| Exports | the 23 `Java_org_freeholdos_passwords_core_VaultCore_*` functions of `jni.rs` and nothing else (checked by `build-android.sh`) |
| Imports | no network function (`socket`, `connect`, `getaddrinfo` and the like); checked by the build script |
| Dependencies | `vault-core/Cargo.lock` (73 crates); versions, checksums and licences in `vault-core/SOURCE.md` |

`check-prebuilt.sh` compares the file with the hash above and fails on a mismatch, so the library cannot change without
this record changing. A new `libvault_core.so` is made only by `build-android.sh` from a commit of `freeholdos-services`;
this file then gets the new commit, hash and size, and the two-build comparison is repeated.

The licences of the crates (MIT, Apache-2.0, BSD-3-Clause) and the notice of the SecLists word list (MIT) ship with the
app; Task 17 of the plan adds them to the notices.
