# Exit daemon — Source Provenance

## arm64/exitd

| Item | Value |
| --- | --- |
| Source | `freeholdos-services`, directory `exit-daemon`, commit `9f17f2b22d6a135c2840c3b644bf8f8d89d00fe4` (`master`) |
| Toolchain | AOSP `prebuilts/rust-toolchain` 1.93.1, target `aarch64-linux-android`, `cargo build --release --locked --offline`, `panic = "abort"`, `lto`, `opt-level = "z"`, the AOSP clang as linker (`tools/env.sh`) |
| Build command | `exit-daemon/tools/build-android.sh` (copies the tree to the build server, builds, then checks the binary itself) |
| SHA-256 | `64966a7625cd6f6761d0532750dc4277826179d8ea967826839b850c10c4b964` |
| Size | 19 351 600 bytes |
| Built | 2026-10-21 (commit `16c8d5f`); rebuilt 2026-10-22 from the commit above |
| Reproduced | built twice from the same tree into two cargo target directories: same SHA-256 (the first build of the earlier commit, into two fresh directories, also gave one hash). The commits after `16c8d5f` change only tests, the patch text and records (`git diff 16c8d5f HEAD` shows a `#[test]` in the vendored `dns.rs`, `RISKS.md`, `vectors.txt`), and the rebuild from the head gave the same SHA-256 and size |
| Needed libraries | `libc.so`, `libdl.so`, `libm.so`, interpreter `/system/bin/linker64` (`llvm-readelf`, read by the build script) |
| Contents | the Tor client (`tor-proto`), the packet layer (`smoltcp`), `aws_lc` and the daemon itself; no build-machine path (the build script reads the binary for each) |
| Dependencies | `exit-daemon/Cargo.lock`; versions, checksums and licences in `exit-daemon/NOTICE` and `RISKS.md` |

`check-prebuilt.sh` compares the file with the hash above and fails on a mismatch, so the binary cannot change without this
record changing. A new `exitd` is made only by `build-android.sh` from a commit of `freeholdos-services`; this file then gets
the new commit, hash and size, and the comparison of two builds is repeated.

## exit-mask-list.tsv

| Item | Value |
| --- | --- |
| Made by | `../tools/make-mask-list.py` from the three inputs below; the output is deterministic (two runs compared) |
| Mask list SHA-256 | `205037a09ad6e7af4d98425d506c583ed2b60d8d2648695bdced3b83706b97b9` |
| Rows | 3698, of which 3062 masked and 636 protected |
| Classifier | `../../datamap/classifier/classifier.tsv`, SHA-256 `210d617f1918420cda4dc1cd66a86b7f7ddf911eeba14863632a337d3257e570` |
| Deny list | `../mask-denylist.txt`, SHA-256 `63817000ede374866ded5b1afc31aac578ebf01a1f1f57f70a010872c9ea0a6b` |
| Allow list | `../mask-allow-platform.txt`, SHA-256 `3c23fe1647a53905d37e68347e981666af4314c991b789240ed93e075cc20eb2` |

The deny list and the allow list are hand-written and are read by the owner before an image (Task 21 of the close-out plan).
The daemon loaded this file in the host test run of 2026-10-21 (status 1 at the configuration, not 2 at the list).

## exit.rc

The init definitions of the two services. The user ids 2961 and 2962 are in the OEM range (2900 to 2999) and are
`[assumption]` unused on the device: `NET-009` reads `ps -Z`. The hard limit (`memcg.limit_in_bytes` 160 MiB) is
`[assumption]` until `NET-009` shows a kill and a restart.
