# Source record

## Base

- `vendor/adevtool`, build-server branch `sovereignos-airpods-identity`, on top of `c0fa4e99`
  (`memory/zram-size` `0002`). The file `config/device/common/overlay-inclusion.yml` has the blob
  `fc186bd7` before and `09ef7cf5` after.
- `frameworks/base`, build-server branch `sovereignos-2026091900-r2`, on top of `887d69072797`. The
  file `core/res/res/values/config.xml` has the blob `d7a4004a` before and `fe141dfd` after.

Both patches were authored in disposable local git repositories that held only the one touched file
(`sovereignos-platform/.worktrees/cap-adevtool` and `cap-framework-config`), whose blob hashes
were compared with `git rev-parse HEAD:<path>` on the build server before the edit. They were landed
with `git am` on 2026-10-10 as `d9665a565a5620ec9da499fd9195b08401e1c83b` (`vendor/adevtool`) and
`eb4138d4688d328d7358adc8e6c6c22c44224146` (`frameworks/base`), and the exported files in this
package come from those landed commits. `git am` of each exported patch on the baseline worktree
reproduces the blob hashes above. The build server had no build running and both trees were clean
at the expected `HEAD`.

## Patches

| Patch | Subject | Files | Why it diverges from upstream |
| --- | --- | --- | --- |
| `vendor-adevtool/0001` | do not carry the Pixel overlay value of the cached process cap | `config/device/common/overlay-inclusion.yml` (one entry replaced by a comment) | The Pixel vendor overlay sets `config_customizedMaxCachedProcesses` to 1024 on `lynx` and `stallion`, and a vendor overlay overrides the framework default. |
| `frameworks-base/0001` | lower the maximum number of cached processes from 1024 to 48 | `core/res/res/values/config.xml` (the value and a comment) | Secure app spawning makes every app process about 43 MB larger and not shareable with the zygote; nothing removes a cached process until lmkd must. See ADR-151. |

The two belong together: `frameworks-base/0001` alone has no effect while the generated vendor
overlay carries 1024.

## Apply

On the build server, with no build running:

```sh
cd vendor/adevtool && git am <path>/memory/cached-process-cap/patches/vendor-adevtool/0001-*.patch
cd frameworks/base && git am <path>/memory/cached-process-cap/patches/frameworks-base/0001-*.patch
```

The adevtool output under `vendor/google_devices/<device>/overlays/framework-res__<device>__auto_generated_rro_vendor/res/values/values.xml`
is generated and untracked, so it still carries the stock entry. Remove it from every device that is
built; this is what a fresh `adevtool generate-all` would produce with the changed inclusion list
(not run here: it needs tens of GB of factory images):

```sh
mkdir -p /srv/sovereignos/tmp-primary/cap48-backups
for d in lynx stallion; do
  f=vendor/google_devices/$d/overlays/framework-res__${d}__auto_generated_rro_vendor/res/values/values.xml
  test "$(grep -c 'config_customizedMaxCachedProcesses">1024<' $f)" = 1
  cp -p $f /srv/sovereignos/tmp-primary/cap48-backups/$d-values.xml.pre-cap48 && sed -i '/config_customizedMaxCachedProcesses">1024</d' $f
  grep -c config_customizedMaxCachedProcesses $f   # must print 0
done
```

Never keep the backup inside the overlay's `res/` directory: Soong compiles every file under it as a
resource and the build fails with `invalid file path '...values.xml.pre-cap48'` (found 2026-10-10,
first `2026101001` attempt).

Every commit in `vendor/adevtool` also stales the pin of each generated
`vendor/google_devices/<device>/adevtool-version-check.mk`. Update it for every device that is built
(`docs/research/build-and-signing-engineering-notes.md`, "adevtool version pinning"):

```sh
N=$(git -C vendor/adevtool rev-parse HEAD); OLD=c0fa4e994d2a783acad6772aa86eeac02168e24a
for d in lynx stallion; do
  f=vendor/google_devices/$d/adevtool-version-check.mk
  test "$(grep -c $OLD $f)" = 1 && cp -p $f $f.pre-cap48 && sed -i "s/$OLD/$N/" $f
done
```

The steps above were applied for `lynx` and `stallion` on 2026-10-10: afterwards
`grep -c config_customizedMaxCachedProcesses` is 0 in every `overlays/*/res/values/values.xml` of
both devices, both pins hold `d9665a56`, and the backups differ from the live files by exactly that line and
that hash. The overlay backups were first written next to the file and moved out of `res/` after the
build failed on them; they are in `/srv/sovereignos/tmp-primary/cap48-backups/`.

## Verification

Built 2026-10-10 into `lynx-cur-userdebug` `2026101001` with `memory/zram-size` (`OFFICIAL_BUILD=true`,
exit 0, `check-required-packages.sh` printed `all required packages present`). Read in the output
directory: `framework-res.apk` has `config_customizedMaxCachedProcesses` 48, the vendor overlay APK
`framework-res__lynx__auto_generated_rro_vendor.apk` has no such resource, `product/etc/build.prop` has
one `mmd.zram.size=100%`. The first run failed on the backup file in `res/` (above); the second built
`vendor` and `vbmeta`. Flashed on `lynx` on 2026-10-10 (`MEM-013` read the first bullet below,
`MEM-014` the third). Checks on a device:

- `dumpsys activity settings` shows `mCustomizedMaxCachedProcesses=48` and
  `CUR_MAX_CACHED_PROCESSES=48`, with no `max_cached_processes` flag set
  (`cmd device_config get activity_manager max_cached_processes` is `null`).
- The built vendor overlay has no `config_customizedMaxCachedProcesses`
  (`aapt2 dump resources` of the framework-res overlay APK in the product output).
- The twelve recently used apps stay alive after opening them in turn (`MEM-008`).

The evidence for the value is `MEM-007` to `MEM-010` in the validation matrix of
`sovereignos-foundation`. The churn and the real gain on `stallion` are `MEM-011`, not measured.

## Rebase notes

One entry of the inclusion list and one resource value. On a new base, check that
`ActivityManagerConstants.updateMaxCachedProcesses` still reads `config_customizedMaxCachedProcesses`
and that the Pixel vendor overlay still sets it (`grep -rn config_customizedMaxCachedProcesses
vendor/google_devices/*/overlays`). If the entry returns to the inclusion list, the vendor value wins
again and the build shows 1024.

The inclusion list is an allow-list passed to the prebuilt `arsclib` (`src/blobs/overlays2.ts`),
whose source is not in the tree. A plain `adevtool generate-all` with this change was never run
(it needs the factory images), so it is not verified that the entry stays out of the generated
overlay, nor how a later factory image with another value of this resource (`= 512`, say) is
treated (silently dropped or an error). After a new `generate-all`, repeat the `grep -c` above.
The no-kill window of 600 s restarts at every user unlock, not only the first
(`UserController` calls `setLastUserUnlockingUptime`).
