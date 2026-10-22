# Exit daemon — Source Provenance

## arm64/exitd

| Item | Value |
| --- | --- |
| Source | `freeholdos-services`, directory `exit-daemon`, commit `1b4774f869137ad1ae46b27e8bc52edb8f8fceca` (`master`) |
| Toolchain | AOSP `prebuilts/rust-toolchain` 1.93.1, target `aarch64-linux-android`, `cargo build --release --locked --offline`, `panic = "abort"`, `lto`, `opt-level = "z"`, the AOSP clang as linker (`tools/env.sh`) |
| Build command | `exit-daemon/tools/build-android.sh` (copies the tree to the build server, builds, then checks the binary itself) |
| SHA-256 | `0ce3c5a4790b3c602bbfce97655ce8a7fbe507286d03c98a1c7d6566b6ad79e6` |
| Size | 19 374 496 bytes |
| Built | 2026-10-22 (rustls 0.23.46 and rustls-webpki 0.103.16, a limit of 128 flows per app and a class table of at most 255, after the review of 2026-10-22; earlier the speed guard, ADR-188) |
| Reproduced | built twice from the same tree (the files of the commit above; the commit differs from the built tree in documentation only) into two cargo target directories: same SHA-256 and size |
| Needed libraries | `libc.so`, `libdl.so`, `libm.so`, interpreter `/system/bin/linker64` (`llvm-readelf`, read by the build script) |
| Contents | the Tor client (`tor-proto`), the packet layer (`smoltcp`), `aws_lc` and the daemon itself; no build-machine path (the build script reads the binary for each) |
| Dependencies | `exit-daemon/Cargo.lock`; versions, checksums and licences in `exit-daemon/NOTICE` and `RISKS.md` |

`check-prebuilt.sh` compares the file with the hash above and fails on a mismatch, so the binary cannot change without this
record changing. A new `exitd` is made only by `build-android.sh` from a commit of `freeholdos-services`; this file then gets
the new commit, hash and size, and the comparison of two builds is repeated.

## exit-mask-list.tsv

| Item | Value |
| --- | --- |
| Made by | `../tools/make-mask-list.py` from the four inputs below; the output is deterministic (two runs compared) and the shipped file is what the generator makes from them (`test-make-mask-list.py` compares the two) |
| Mask list SHA-256 | `791345bfea0e111b45f383b16c7c740c4b46baf86ca6b124c156bbb4581f3c5c` |
| Rows | 3699, of which 2823 masked and 876 protected |
| Classifier | `../../datamap/classifier/classifier.tsv`, SHA-256 `210d617f1918420cda4dc1cd66a86b7f7ddf911eeba14863632a337d3257e570` |
| Deny list | `../mask-denylist.txt`, SHA-256 `bff2211e7be432de21e47717e1feca8108a81b0db0c5c4596fa0d06de02ee2d6` |
| Allow list | `../mask-allow-platform.txt`, SHA-256 `cc92550852864446d1be64c40b6d9d01eb6d0a68d8745c7d5ae2f2cb23653994` |
| Company ids | `../mask-company-ids.tsv`, SHA-256 `be659fb14e2224f5b21821e10b2a23ea8cf4065a9e1e5a726f28ff560931fe6c`, 2417 companies. Written by the generator and committed: a company keeps its id for good, a new one gets the next free id, a company that leaves keeps its id reserved. Seeded on 2026-10-22 from the ids of the list above, which it reproduces byte for byte. The exit app stores a choice by company id, so an id that moved with a regenerated list would point at another company (review of 2026-10-22, I1). |

The deny list and the allow list are hand-written and are read by the owner before an image (Task 21 of the close-out plan). On 2026-10-22 (ADR-188) the deny list grew from 37 to 277 domains after every masked row was read: sites and services that the classifier files whole (marketplaces, portals, business suites, device services), video ad servers, affiliate redirects, content delivery hosts and video sites. A protected parent keeps its tracker subdomains masked (the longest match wins).
The daemon loaded this file in the host test run of 2026-10-21 (status 1 at the configuration, not 2 at the list).

## exit.rc

The init definitions of the two services. The user ids 2961 and 2962 are in the OEM range (2900 to 2999) and are
`[assumption]` unused on the device: `NET-009` reads `ps -Z`. The hard limit (`memcg.limit_in_bytes` 160 MiB) is
`[assumption]` until `NET-009` shows a kill and a restart.
