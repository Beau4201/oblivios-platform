# Exit daemon — Source Provenance

## arm64/exitd

| Item | Value |
| --- | --- |
| Source | `oblivios-services` (then named `freeholdos-services`), directory `exit-daemon`, commit `c9139fe`; the commit is on the `master` of `oblivios-services` |
| Toolchain | AOSP `prebuilts/rust-toolchain` 1.93.1, target `aarch64-linux-android`, `cargo build --release --locked --offline`, `panic = "abort"`, `lto`, `opt-level = "z"`, the AOSP clang as linker (`tools/env.sh`) |
| Build command | `exit-daemon/tools/build-android.sh` (copies the tree to the build server, builds, then checks the binary itself) |
| SHA-256 | `d5dca624755f9a40f9cc3bcf19b2cf4fd485f1a1c4b5c21e8fd3f917d1119e63` |
| Size | 19 366 456 bytes |
| Control protocol | 5 |
| Built | 2026-11-17, from the source of the commit above for three advisories (`exit-daemon/RISKS.md`): `tokio` 1.53.3, `rustix` 1.1.6 and `ctutils` 0.4.4 replace 1.53.1, 1.1.4 and 0.4.2 in `Cargo.lock`. Between the commit of the build of 2026-11-10 (`7beab16`) and this one the daemon's source changed only by the rename to ObliviOS (comments, the description in `Cargo.toml`, the constant `oblivios-vendored-849a8d14d165` in `build.rs`) and by these three versions, so the control protocol and the behaviour are those of the build before. Host tests on the build server: 344 passed (the mutation run was not repeated). This binary has not been run on a phone; the device rows of the exit service (`NET-*`, `MSK-*`) are to be run on the first image that carries it. Earlier build: 2026-11-10, for the company ids of 32 bits and a mask list of up to 262144 rows (ADR-223, `Proposed`): control protocol 5 writes the company in `SetOverrides`, `Release`, `Failures` and `Promoted` as four bytes, `masklist.rs` reads ids up to 4294967295, `MAX_ENTRIES` is 262144 and `MAX_FILE` 16 MiB. The shipped list is still the 3699-row file below, so the behaviour with a list of about 91,500 rows (memory of `exitd`, start-up time) is not measured; not run on a phone. Host tests on the build server: 293 + 7 + 1 passed, 93 of 93 mutations of `control` and the mask list killed. Before this build: 2026-10-30, for the exit country of an Isolated app (ADR-208): control protocol 4 adds `SetCountry`, which the exit app sends for each app whose persona place is in another country; the daemon picks the Tor exit country of that app from it. Before this build: 2026-10-29, after `NET-009` and `IMG-018` of `lynx` `2026102303`, the route helper reads an interface index by `RTM_GETLINK` on its netlink socket and not by `if_nametoindex`, which opened an `AF_INET` socket in a domain that has none (ADR-199); `strace` of that binary on the phone showed no `AF_INET` socket in an apply. The Tor exit country of this build is not run on a phone yet (a device row). |
| Reproduced | built twice from the files of the commit above (the working tree of the build equals the commit in every file of `exit-daemon` that the build reads), into two cargo target directories: same SHA-256 and size (`d5dca624…`, 19 366 456 bytes; 2026-11-17, targets `target-adv-a` and `target-adv-b`). The build of 2026-11-10 (`ddf5ddd4…`, 19 369 744 bytes) was reproduced the same way |
| Needed libraries | `libc.so`, `libdl.so`, `libm.so`, interpreter `/system/bin/linker64` (`llvm-readelf`, read by the build script) |
| Contents | the Tor client (`tor-proto`), the packet layer (`smoltcp`), `aws_lc` and the daemon itself; no build-machine path (the build script reads the binary for each) |
| Dependencies | `exit-daemon/Cargo.lock`; versions, checksums and licences in `exit-daemon/NOTICE` and `RISKS.md`; the licence texts of the registry packages of the lock in `../THIRD-PARTY-LICENSES.txt`, made by `exit-daemon/tools/third-party-licences.py` from the crates of the lock (548 packages, 2026-11-17) |

`check-prebuilt.sh` compares the file with the hash above and fails on a mismatch, so the binary cannot change without this
record changing. A new `exitd` is made only by `build-android.sh` from a commit of `oblivios-services`; this file then gets
the new commit, hash and size, and the comparison of two builds is repeated.

## exit-mask-list.tsv

| Item | Value |
| --- | --- |
| Made by | `../tools/make-mask-list.py` from the inputs below (ADR-223, 2026-11-10); the output is deterministic (two runs compared, list and ids identical) and the shipped file is what the generator makes from them (`test-make-mask-list.py` compares the classifier part row by row and, with `EASYLIST_DIR` set to a directory with the two EasyList files, the whole file) |
| Mask list SHA-256 | `d5c04f8061f66cdd33bdc653f8c8737091e69ecdb9c34ffc9f39dad4410bcc3c` |
| List size | 4 166 795 bytes |
| Rows | 91609, of which 90732 masked and 877 protected: the 3699 rows of the classifier part (unchanged, compared by domain, id and name; `onelink.me` is the one protected row added) and 87910 hosts of EasyPrivacy and EasyList |
| Classifier | `../../datamap/classifier/classifier.tsv`, SHA-256 `210d617f1918420cda4dc1cd66a86b7f7ddf911eeba14863632a337d3257e570` |
| Deny list | `../mask-denylist.txt`, SHA-256 `2b8dc7016b109878ef2c9fad4c99c6a263745f1f71a5526b3f585a2495b3603c` (`onelink.me` added, a deep-link host like `adj.st` and `app.link`, ADR-223) |
| Allow list | `../mask-allow-platform.txt`, SHA-256 `cc92550852864446d1be64c40b6d9d01eb6d0a68d8745c7d5ae2f2cb23653994` |
| EasyPrivacy and EasyList | `https://easylist.to/easylist/easyprivacy.txt` (1 515 983 bytes, SHA-256 `d48bcfc36444acb16834625d00d40f9dd9a303d15e53969eb0a7aebd200a7d9a`) and `https://easylist.to/easylist/easylist.txt` (2 031 166 bytes, SHA-256 `9d11f7e98bfc2e260b01e9215c17bbe833e714e03af466c734c074d218fba0ff`), both version `202611101626`, downloaded 2026-11-10 on the owner's yes and archived by hash on the build server (`datamap-inputs/2026-11-10-easylist/`, read-only, hashes compared after the copy). `../tools/import-easylist.py` makes `easylist-hosts.txt` from them (91345 hosts, 2 113 498 bytes, SHA-256 `5a3e776c113e9a6501b9eafd7bd246cd2a71d9361853a8bac4a36d4295379849`, two runs identical), the sixth argument of the generator. Licence: GPL-3.0-or-later or CC BY-SA 3.0 or later (`easylist.to/pages/licence.html`); taking the host list under CC BY-SA 4.0 like the classifier is the author's reading and has had no licence review (ADR-223). `NOTICE` carries the attribution |
| Company ids | `../mask-company-ids.tsv`, SHA-256 `cbccbae86196ec98bd070e7c19095e997b7192e81947757b8d5c7218feb10c66`, 90326 companies (2417 until 2026-11-10; the 2417 keep their ids, the hosts of EasyPrivacy and EasyList got the next free ids, a host is its own company). Written by the generator and committed: a company keeps its id for good, a new one gets the next free id, a company that leaves keeps its id reserved. Seeded on 2026-10-22 from the ids of the list above, which it reproduces byte for byte. The exit app stores a choice by company id, so an id that moved with a regenerated list would point at another company (review of 2026-10-22, I1). |

The deny list and the allow list are hand-written and are read by the owner before an image (Task 21 of the close-out plan). The 87910 hosts of EasyPrivacy and EasyList have not been read by the owner: a listed host is masked unless a row of the classifier, the deny list or the allow list is at or above it, and hosts that lie under a protected row are skipped by default (1808 of them at version `202611101405`, read by nobody, not recounted for `202611101626`); the device rows `MSK-013` and `MSK-014` are the check for a masked host that an app waits for. On 2026-10-22 (ADR-188) the deny list grew from 37 to 277 domains after every masked row was read: sites and services that the classifier files whole (marketplaces, portals, business suites, device services), video ad servers, affiliate redirects, content delivery hosts and video sites. A protected parent keeps its tracker subdomains masked (the longest match wins).
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
