# Source record

## Base

GrapheneOS `vendor/adevtool`, build-server branch `freeholdos-2026100600`, commit
`e333ec2cfa932a8a58db2a108f86274e5f66668c` (read 2026-10-23). The patch was authored in a
disposable local git repository that held only `config/mk/google_devices/common/device-common.mk`,
whose blob hash (`71495311`) equalled `git rev-parse HEAD:<path>` on the build server before the
edit. Landed on the build server with `git am` on 2026-10-26 as `9e39466c` (trees clean, no build running), followed by `memory/zram-size/0003` as `e7276cf6`; `device-common.mk` has the blob `4926f4c8`, as checked in the disposable repositories. The pins of `lynx` and `stallion` name `e7276cf6` (copies `adevtool-version-check.mk.pre-decoy-zram3`).

## Patches

| Patch | Subject | Files | Why it diverges from upstream |
| --- | --- | --- | --- |
| `0001` | stop a background user when the device switches away from it | `config/mk/google_devices/common/device-common.mk` (one property and a comment) | The Duress decoy user is meant to stay stopped between uses (`docs/superpowers/specs/2026-08-21-duress-decoy-user-switch-design.md`). The return path (`ReturnToRealUserHelper.performRealUserSwitch`) only calls `switchToUserSilently(0)`, and stock `UserController.stopUserOnSwitchIfEnforced` (called from `continueUserSwitch`) stops the old user only if `DISALLOW_RUN_IN_BACKGROUND` is set, `setStopUserOnSwitch` was called, or `fw.stop_bg_users_on_switch` is 1, falling back to `config_multiuserDelayUserDataLocking` (false). On `stallion` the decoy stayed `RUNNING_UNLOCKED` 19 hours after its last visit with 8 processes and 602 MB RSS, and every broadcast restarted its processes (2026-10-23). The patch sets the stock property to 1; no framework code changes. |

Applies on the base above, and together with `memory/zram-size/0003` in either order: both
orders give the blob `4926f4c8` with both properties present (checked 2026-10-23 in disposable
repositories). The anchor is the `persist.vendor.intelligence=off` line, away from the zram block.

## Amendment 2026-11-16: only the decoy is stopped (review A3, ADR-238)

`0001` above set `fw.stop_bg_users_on_switch=1`, which stops every secondary user when the device switches away from it. The owner
chose on 2026-11-16 that only the decoy is stopped. Two new patches, not landed, not built:

| Patch | Subject | Files | Change |
| --- | --- | --- | --- |
| `frameworks-base/0001` | stop only the Duress decoy user when the device switches away from it | `services/core/java/com/android/server/am/UserController.java` | `stopUserOnSwitchIfEnforced` stops the old user also when its `userType` is `USER_TYPE_FULL_OBLIVIOS_DECOY` (read before `mLock`, as `hasUserRestriction` is); the stock rules stay. |
| `vendor-adevtool/0002` | drop `fw.stop_bg_users_on_switch`, the decoy is stopped by code | `config/mk/google_devices/common/device-common.mk` | Removes the property and its comment block (applies after `0001` and with `memory/zram-size/0003`; the anchors are away from the zram block). |

Authored in two scratch repositories from the files of the build server (`frameworks/base` `7bc79c21e815`, `vendor/adevtool`
`20d95769`, both clean, read 2026-11-16). `typecheck-duress-frameworks.sh`: `typecheck: OK` (0 errors in base and head). The other
user of `isStopUserOnSwitchEnabled` (the package kill at the start of a switch) already returns false for the decoy and returns the
stock default for any other user once the property is gone. No host test: the rule is one boolean in `UserController`, which has no
unit test that reaches it. Landing `vendor-adevtool/0002` changes `device-common.mk`, so the `adevtool` pin of every built device needs the
update described in `memory/zram-size/SOURCE.md` (\"Apply\"). Checks on a device: the decoy is `User is not started` after a visit (as
in Verification 2) and a second secondary user, if one exists, stays running after a switch away from it.

## Apply

On the build server, with no build running:

```sh
cd vendor/adevtool && git am <path>/memory/decoy-stop-on-switch/patches/vendor-adevtool/0001-*.patch
```

`device-common.mk` is part of the `adevtool` pin, so the pin of every built device needs the
update described in `memory/zram-size/SOURCE.md` ("Apply").

## Verification

Not run on a build. Required before the package is marked `Accepted`:

1. After a `stallion` build, `grep '^fw.stop_bg_users_on_switch' <product-out>/product/etc/build.prop`
   prints `fw.stop_bg_users_on_switch=1`.
2. On the device: enter the Duress decoy and return with the real credential (the owner does the
   physical unlock), then `am get-started-user-state <decoy>` reports `User is not started` and no
   `u<decoy>_` process remains.
3. Open question, unmeasured: stopping the decoy locks its credential-encrypted storage again
   (`mDelayUserDataLocking` is false), so the next Duress entry starts it cold, as after every
   reboot. Compare the entry in both states (the `DUR-*` rows) before accepting.

A runtime check without a build: `cmd activity set-stop-user-on-switch true` sets the same
behaviour in memory until the next reboot.

## Regenerated without Guardian (2026-11-16, ADR-240)

`vendor-adevtool/0001` and `0002` were regenerated when Guardian left the product: the block that shipped Guardian and every mention of it left `device-common.mk`, so the context of the patches changed (they insert and remove a block that sat in front of Guardian's). The change is textual. The `index` lines of the changed hunks were removed, because the blob hashes of the new chain exist only after it is landed. The twelve `vendor-adevtool` patches that touch that file apply in landed order with `git am` on an offline skeleton of the file, and the final file equals the old final file minus the Guardian block (checked 2026-11-16). The hashes and landing commits recorded above describe the earlier landing. Not done: landing on a build-server tree, which needs the new blob hashes and the pin update of `adevtool-version-check.mk`.
