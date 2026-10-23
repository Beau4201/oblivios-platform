# Passwords core — Source Provenance

## arm64/libvault_core.so

| Item | Value |
| --- | --- |
| Source | `freeholdos-services`, directory `vault-core`, tree of commit `cb1c7e0` (the last commit that touches `vault-core`; the services working tree was clean when it was copied to the build server, `master` `cb1c7e0`) |
| Toolchain | AOSP `prebuilts/rust-toolchain` 1.93.1, target `aarch64-linux-android`, `cargo build --release --locked --offline`, `panic = "abort"`, `lto`, `opt-level = "z"`, the AOSP clang as linker (`vault-core/tools/env.sh`) |
| Build command | `vault-core/tools/build-android.sh` (copies the tree to the build server, builds, then checks the library itself) |
| SHA-256 | `bfff899f8e21afa580ea6344660dd5cafaf2b39045b8c4e46612d102eec8635a` |
| Size | 1 880 168 bytes |
| Built | 2026-10-23 |
| Reproduced | built twice from the clean working tree (`rsync` copy without `.git` and `target`), into two cargo target directories: same SHA-256. The previous library (`0a02361b52ed...`, 1 854 288 bytes, from `7e92361`) lacked the field caps of a CSV record and of a CSV file, the size check of an export, the lock around the temp file of a write and the overflow checks of the release profile (review of 2026-10-23, P-14, P-19, P-21, P-22). |
| Needed libraries | `libc.so`, `libdl.so`, `libm.so` (`readelf -d`, read on the workstation) |
| Exports | the 23 `Java_org_freeholdos_passwords_core_VaultCore_*` functions of `jni.rs` and nothing else (checked by `build-android.sh`) |
| Imports | no network function (`socket`, `connect`, `getaddrinfo` and the like); checked by the build script |
| Dependencies | `vault-core/Cargo.lock` (73 crates); versions, checksums and licences in `vault-core/SOURCE.md` |

`check-prebuilt.sh` compares the file with the hash above and fails on a mismatch, so the library cannot change without
this record changing. A new `libvault_core.so` is made only by `build-android.sh` from a commit of `freeholdos-services`;
this file then gets the new commit, hash and size, and the two-build comparison is repeated.

The licences of the crates (MIT, Apache-2.0, BSD-3-Clause) and the notice of the SecLists word list (MIT) ship with the
app; Task 17 of the plan adds them to the notices.
