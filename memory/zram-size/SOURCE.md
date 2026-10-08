# Source record

## Base

GrapheneOS `vendor/adevtool`, build-server branch `sovereignos-airpods-identity`, commit
`9b0e6172643f76714f76b1be8c6ad986ae4dde35` (read 2026-10-08). The patch was authored in a
disposable local git repository that held only the two touched files, whose blob hashes were
compared with `git rev-parse HEAD:<path>` on the build server before the edit
(`sysprop-inclusion.yml` `b01fc417`, `device-common.mk` `30d2fc16`). It has not been landed on
the build server yet: landing happens together with the generated-file step below, right before
the next candidate build, so no other build sees a half-applied change.

## Patches

| Patch | Subject | Files | Why it diverges from upstream |
| --- | --- | --- | --- |
| `0001` | raise the zram swap capacity to 80% of RAM | `config/device/common/sysprop-inclusion.yml` (two lines removed), `config/mk/google_devices/common/device-common.mk` (one property and a comment) | The stock value is `mmd.zram.size=50%`. Secure app spawning makes every app process about 43 MB larger and not shareable, so the swap fills about 30 s after unlock and lmkd kills apps. |

## Apply

On the build server, with no build running:

```sh
cd vendor/adevtool && git am <path>/memory/zram-size/patches/vendor-adevtool/0001-*.patch
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

After a build, `grep '^mmd.zram.size' <product-out>/product/etc/build.prop` must print exactly one
line, `mmd.zram.size=80%`.

## Rebase notes

Two lines of the inclusion list and one appended block. On a new base, check that the stock
`mmd.zram.size` is still the only source of the value (`grep -rn mmd.zram.size` in the generated
`product.prop` files and in `build.prop` of an earlier build) and that `mmd` still reads
`mmd.zram.size` through `PerDeviceStringProp::ZramSize` (`system/memory/mmd/src/mmd_setup.rs`).
The two entries removed from the inclusion list also cover `bluejay` and `oriole`
(`3221225472`); those devices are not built here and would get 80% too.

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
- The flash writeback ceiling does not grow with the zram size
  (`system/memory/mmd/src/zram/writeback.rs`, `calculate_idle_writeback_limit`: the limit is
  `used / total * max_bytes`, capped by `max_bytes` and the backing file).
- Not run: a build with this patch, the check of the resulting `build.prop`, a `stallion` boot
  with it, and the daily kill rate.
