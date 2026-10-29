# Exit daemon — Source Provenance

## arm64/exitd

| Item | Value |
| --- | --- |
| Source | `freeholdos-services`, directory `exit-daemon`, commit `f38113c` (`master`) |
| Toolchain | AOSP `prebuilts/rust-toolchain` 1.93.1, target `aarch64-linux-android`, `cargo build --release --locked --offline`, `panic = "abort"`, `lto`, `opt-level = "z"`, the AOSP clang as linker (`tools/env.sh`) |
| Build command | `exit-daemon/tools/build-android.sh` (copies the tree to the build server, builds, then checks the binary itself) |
| SHA-256 | `0e3d50b943b5f37aa216e9b88e98b295a3d206d0da57b54d7747ff896e4b1323` |
| Size | 19 375 800 bytes |
| Built | 2026-10-29, after `NET-009` and `IMG-018` of `lynx` `2026102303`: the route helper reads an interface index by `RTM_GETLINK` on its netlink socket and not by `if_nametoindex`, which opened an `AF_INET` socket in a domain that has none (ADR-199). `strace` of this binary on the phone: no `AF_INET` socket in an apply, 14 rules, the link up, two addresses, and a clear that restores `ip rule` and `ip route`. |
| Reproduced | built twice from the files of the commit above, from a clean tree, into two cargo target directories: same SHA-256 and size (`0e3d50b9…`, 19 375 800 bytes) |
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
`[assumption]` unused on the device: `NET-009` reads `ps -Z`. There is no hard limit: `memcg.limit_in_bytes`
(160 MiB, D-K) made `init` fail `createProcessGroup` with `-EINVAL` because `ro.config.per_app_memcg` is unset on a phone
`[verified-in-source: system/core/libprocessgroup/processgroup.cpp:747]`, `[measured]` by `NET-009` on image `2026102301`, so neither
service started, and the line is removed. The Arti quota of 64 MiB (`--memory-max`) is the only cap until another mechanism is chosen.
`exitd` (not `exitd_route`) runs with the group `inet` as well as its own: on image `2026102302` its self-check ended with `Os { code: 13 }` at
`TcpListener::bind("127.0.0.1:0")` with no SELinux denial, and the same uid and gid with only the group 3003 added opened the
socket `[measured: NET-009, a debug build of exitd run as uid and gid 2961 with and without the group]`.
It also runs with the group `vpn` (1016): `/dev/tun` is `system:vpn` `0660` on the phone, so without the group `open` of `/dev/net/tun`
returns EACCES before SELinux is asked and the daemon ends with `tunnel could not be created` `[measured: NET-009, image 2026102303]`.
`../tools/check-syscall-policy.py` reads the groups of each service from this file and fails when one is missing.
