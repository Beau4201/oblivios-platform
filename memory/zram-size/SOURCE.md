# Source record

## Base

GrapheneOS `vendor/adevtool`, build-server branch `sovereignos-airpods-identity`, commit
`9b0e6172643f76714f76b1be8c6ad986ae4dde35` (read 2026-10-08). The patch was authored in a
disposable local git repository that held only the two touched files, whose blob hashes were
compared with `git rev-parse HEAD:<path>` on the build server before the edit
(`sysprop-inclusion.yml` `b01fc417`, `device-common.mk` `30d2fc16`). Landed on the build server
with `git am` on 2026-10-08 as `ccc1d8db81aea787eb81c7bb14d9f2ec58f13986`; the two files have the
same blob hashes as the worktree (`ab73e1e5` and `53817507`). The generated-file and pin steps
below were applied for `lynx` and `stallion` in the same session. Once landed, any build of
either device needs both, so they belong together.

`0002` was authored the same way on top of `ccc1d8db` (the file blob `53817507` matched the
server's before the edit) and landed with `git am` on 2026-10-09 as
`c0fa4e994d2a783acad6772aa86eeac02168e24a`; `device-common.mk` has the blob `2e94e040` in the
worktree and on the server. It only changes the value, so `product.prop` needs no further edit. The
pin of `lynx` and `stallion` was updated to `c0fa4e99` (copies kept as
`adevtool-version-check.mk.pre-zram100`).

## Patches

| Patch | Subject | Files | Why it diverges from upstream |
| --- | --- | --- | --- |
| `0001` | raise the zram swap capacity to 80% of RAM | `config/device/common/sysprop-inclusion.yml` (two lines removed), `config/mk/google_devices/common/device-common.mk` (one property and a comment) | The stock value is `mmd.zram.size=50%`. Secure app spawning makes every app process about 43 MB larger and not shareable, so the swap fills about 30 s after unlock and lmkd kills apps. |
| `0002` | raise the zram swap capacity from 80% to 100% of RAM | `config/mk/google_devices/common/device-common.mk` (the value and a comment) | lmkd counts free swap below 10% of the total as low, so only 90% of the capacity is usable. With the same load 80% was below that line 55% of the time and 100% never, at the same physical zram (see Verification). |

## Apply

On the build server, with no build running:

```sh
cd vendor/adevtool && git am <path>/memory/zram-size/patches/vendor-adevtool/000[12]-*.patch
```

The adevtool output under `vendor/google_devices/<device>/sysprop/product.prop` is generated and
untracked, so it still carries the stock line. A second assignment with a different value fails
the build (`post_process_props.py`, see Verification). Remove the stock line from every device
that is built; this is what a fresh `adevtool generate-all` produces with the changed
`sysprop-inclusion.yml`, which is not run because it needs tens of GB of factory images:

```sh
for d in lynx stallion; do
  f=vendor/google_devices/$d/sysprop/product.prop
  test "$(grep -c '^mmd.zram.size=50%$' "$f")" = 1 && sed -i '/^mmd.zram.size=50%$/d' "$f"
  grep -c '^mmd.zram.size' "$f"   # must print 0
done
```

Every commit in `vendor/adevtool` also stales the pin of each generated
`vendor/google_devices/<device>/adevtool-version-check.mk` (it holds the exact `HEAD` of
`vendor/adevtool`; a stale pin fails the build within a second with "vendor module is
outdated"). Update the pin of every device that is built, keeping a copy
(`docs/research/build-and-signing-engineering-notes.md` in `sovereignos-docs`, "adevtool version pinning"):

```sh
N=$(git -C vendor/adevtool rev-parse HEAD); OLD=<the previous HEAD>
for d in lynx stallion; do
  f=vendor/google_devices/$d/adevtool-version-check.mk
  test "$(grep -c $OLD $f)" = 1 && cp -p $f $f.pre-zram80 && sed -i "s/$OLD/$N/" $f
done
```

After a build, `grep '^mmd.zram.size' <product-out>/product/etc/build.prop` must print exactly one
line, `mmd.zram.size=100%` (`80%` for an image built from `0001` alone). The build script must run the package check and that grep only when
the build exited 0: a failed build leaves the previous outputs in place and the check then
reports a false pass (it did on 2026-10-08, after the stale pin).

## Rebase notes

Two lines of the inclusion list and one appended block. On a new base, check that the stock
`mmd.zram.size` is still the only source of the value (`grep -rn mmd.zram.size` in the generated
`product.prop` files and in `build.prop` of an earlier build) and that `mmd` still reads
`mmd.zram.size` through `PerDeviceStringProp::ZramSize` (`system/memory/mmd/src/mmd_setup.rs`).
The two entries removed from the inclusion list also cover `bluejay` and `oriole`
(`3221225472`); those devices are not built here and would get 100% too.

## Verification

- `git am` of the patch on the base reproduces the worktree tree byte for byte.
- `override_optional_props` of `build/make/tools/post_process_props.py`, run on a copy of the
  `lynx` `product/etc/build.prop`: a plain second assignment `mmd.zram.size=80%` next to the
  stock `50%` fails with "found duplicate sysprop assignments"; the stock line removed and ours
  added passes and keeps `80%`; the unmodified file passes.
- Mechanism on a device: on `lynx` (`userdebug`), `persist.device_config.mmd_native.zram.size=80%`
  made `SwapTotal` 6112248 kB instead of 3820152 kB after a reboot, with the physical zram at
  661 MB against 631 and 673 MB for two baselines at 420 s after boot, and free swap at 3189 MB
  against 1135 and 887 MB. `lynx` never ran out of swap in either configuration, so a reduction
  of lmkd kills is not measured. `stallion` is a `user` build and refuses the same override
  (`mmd_native/zram.size` is not on the DeviceConfig allowlist), so its effect needs a build.
- Flash writeback: the images set `mmd.zram.writeback.limit.enabled=false`, so mmd applies no
  per-run limit (`system/memory/mmd/src/zram/writeback.rs`: `update_writeback_limit` returns at
  once, and `calculate_idle_writeback_limit`, which scales with used over total zram, is unused).
  The 1 GiB backing file is the only bound. Whether 100% writes more to flash than 50% is not
  measured (`MEM-005`). The comment of patch `0001` and its commit message still say the ceiling
  does not grow with the size; that wording is inaccurate and is left in the landed commit
  (changing it would stale the `adevtool` pin and need a rebuild); correct it the next time a
  patch touches `device-common.mk`.
- `0002` was built with `memory/cached-process-cap` into `lynx-cur-userdebug` `2026101001` and
  flashed on `lynx` on 2026-10-10: `SwapTotal` 7640312 kB against `MemTotal` 7640316 kB
  (`MEM-012`, `MEM-013`).
- Build: `lynx-cur-userdebug`, `BUILD_NUMBER=2026100801`, `OFFICIAL_BUILD=true`, exit 0 after
  about 3.5 minutes; `check-required-packages.sh` printed `all required packages present`;
  `product/etc/build.prop` line 152 is the only `mmd.zram.size`, `=80%`. The first attempt failed
  at once on the stale pin (above).
- Device: image flashed to `lynx` without a wipe (`flashall` runs in fastbootd, reached with
  `fastboot reboot fastboot`, not in the bootloader). Live, 69 s after boot:
  `ro.build.version.incremental=2026100801`, `SwapTotal` 6112248 kB (80% of 7640316 kB) with
  the DeviceConfig override removed beforehand (`cmd device_config get mmd_native zram.size` is
  `null`), so the size comes from `build.prop`. Dropbox: no crash, ANR, tombstone or wtf entry
  (strictmode only), 0 `am_kill` since boot.
- `0002`, load experiment on `lynx` (`userdebug`, `adb root`, 2026-10-09). The zram was reset live
  (`swapoff`, `reset`, `disksize`, `swapon` with the `lz77eh` algorithm) to a percentage of
  `MemTotal`, a freestanding test program pushed 4 GB of never-touched data to swap with
  `MADV_PAGEOUT` (the stand-in for the dead boot-image copies of idle exec-spawned processes) and
  held 400 MiB hot, and twelve apps were launched in turn for 300 s. One run per cell:

  | zram | minimum free swap | time under 10% free | PSI full max | PSI some mean | physical zram |
  | --- | --- | --- | --- | --- | --- |
  | 50% (3730 MB) | 0 MB | 100% | 4.29 | 1.07 | 933 MB |
  | 80% (5968 MB) | 219 MB | 55% | 1.67 | 0.17 | 1405 MB |
  | 100% (7461 MB) | 1414 MB | 0% | 1.32 | 0.11 | 1452 MB |

  With a 5.5 GB load every size was full, 64% of the time at 100%. Major faults were higher at 100%
  (109942 against 57013); one run per cell cannot tell whether that is noise. The kill counts are
  1 in every run: `lynx` has too few cached apps for lmkd to kill, so the effect on kills is not
  measured there, only the swap-low state that triggers them.
- Not run: a `stallion` build and boot with `0002`, the daily kill rate, and how much swap
  `stallion` needs over a day (the plateau of `SwapFree` decides whether 100% is enough).
