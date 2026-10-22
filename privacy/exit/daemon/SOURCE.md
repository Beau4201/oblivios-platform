# Exit daemon — Source Provenance

## arm64/exitd

| Item | Value |
| --- | --- |
| Source | `freeholdos-services`, directory `exit-daemon`, commit `5de3475d3240b188907158ad1e40999321eadf4b` (`master`) |
| Toolchain | AOSP `prebuilts/rust-toolchain` 1.93.1, target `aarch64-linux-android`, `cargo build --release --locked --offline`, `panic = "abort"`, `lto`, `opt-level = "z"`, the AOSP clang as linker (`tools/env.sh`) |
| Build command | `exit-daemon/tools/build-android.sh` (copies the tree to the build server, builds, then checks the binary itself) |
| SHA-256 | `b15cd7922b830c7ce274166305cf2c52af5de75ea74f19318bb5bb582d476321` |
| Size | 19 367 032 bytes |
| Built | 2026-10-22 (the speed guard, ADR-188; earlier 2026-10-21 and the head of the same day) |
| Reproduced | built twice from the same tree (the committed head) into two cargo target directories, the second a fresh one: same SHA-256 and size |
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
| Mask list SHA-256 | `791345bfea0e111b45f383b16c7c740c4b46baf86ca6b124c156bbb4581f3c5c` |
| Rows | 3699, of which 2823 masked and 876 protected |
| Classifier | `../../datamap/classifier/classifier.tsv`, SHA-256 `210d617f1918420cda4dc1cd66a86b7f7ddf911eeba14863632a337d3257e570` |
| Deny list | `../mask-denylist.txt`, SHA-256 `bff2211e7be432de21e47717e1feca8108a81b0db0c5c4596fa0d06de02ee2d6` |
| Allow list | `../mask-allow-platform.txt`, SHA-256 `cc92550852864446d1be64c40b6d9d01eb6d0a68d8745c7d5ae2f2cb23653994` |

The deny list and the allow list are hand-written and are read by the owner before an image (Task 21 of the close-out plan). On 2026-10-22 (ADR-188) the deny list grew from 37 to 277 domains after every masked row was read: sites and services that the classifier files whole (marketplaces, portals, business suites, device services), video ad servers, affiliate redirects, content delivery hosts and video sites. A protected parent keeps its tracker subdomains masked (the longest match wins).
The daemon loaded this file in the host test run of 2026-10-21 (status 1 at the configuration, not 2 at the list).

## exit.rc

The init definitions of the two services. The user ids 2961 and 2962 are in the OEM range (2900 to 2999) and are
`[assumption]` unused on the device: `NET-009` reads `ps -Z`. The hard limit (`memcg.limit_in_bytes` 160 MiB) is
`[assumption]` until `NET-009` shows a kill and a restart.
