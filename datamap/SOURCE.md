# Data Map — Source Provenance

ADR-157. Observe-only collector in `system_server`, a read-only classifier file in the system image, and the privileged app `DataMapApp`.

State: **landed on the build server on 2026-10-12 (section "Landed"), built as modules (section "Module build"), not an image, not on a device.**

## Baselines

The patches were authored in disposable worktrees (`.worktrees/datamap-authored/worktrees/`, gitignored) that hold only the fetched files, read-only from the build-server trees.

| Tree | Baseline HEAD (abbreviated) | Blob of each modified file at that HEAD |
| --- | --- | --- |
| `frameworks/base` | `fda11b7a2c9d` | `core/java/android/net/INetdEventCallback.aidl` `27fac25`; `services/core/java/com/android/server/connectivity/NetdEventListenerService.java` `68f9a5f`; `core/res/AndroidManifest.xml` `5c9e490`; `core/res/res/values/strings.xml` `4d56413`; `services/core/java/com/android/server/EventLogTags.logtags` `c604779`; `services/java/com/android/server/SystemServer.java` `d34efa7` |
| `system/sepolicy` | `261adac69c6d` | `private/seapp_contexts` `8633389`; `private/service.te` `7e738f8`; `private/service_contexts` `de00043`; `private/system_app.te` `b75dfe4`; `private/traceur_app.te` `029405b`; `build/soong/service_fuzzer_bindings.go` `dd83ae0` (fetched from `27527dbd2`, after patches 0001 and 0002) |
| `vendor/adevtool` | `d9665a565a56` | `config/mk/google_devices/common/device-common.mk` `2e94e04` |

Task 4 step 2 compared these blobs with the HEAD of each tree before anything was applied: all twelve were identical.

## Patches

| Patch | Why it diverges from upstream | Files |
| --- | --- | --- |
| `frameworks-base/0001` | The pure core of the service (name validation, classifier parser, bounded per-user store, link derivation, history codec, per-user settings). No Android class, so it is checked on the host. | New: `services/core/java/com/android/server/datamap/{Category,ClassifierException,Classifier,DataMapStore,HistoryCodec,HostnameValidator,LinkDeriver,RecentAnswers,ServiceKeys,UserSettings}.java` |
| `frameworks-base/0002` | Adds `CALLBACK_CALLER_DATA_MAP = 3`. The three existing slots are taken (connectivity service, device policy, network watchlist) and a slot holds one callback, so the service cannot share one. | `core/java/android/net/INetdEventCallback.aidl`, `services/core/java/com/android/server/connectivity/NetdEventListenerService.java` |
| `frameworks-base/0003` | The Binder interface as structured parcelables, so that the app compiles the same files and no hand-written `Parcelable` is needed; the permission `org.sovereignos.permission.READ_DATA_MAP` (`signature\|privileged`). | New: `core/java/com/android/internal/datamap/*.aidl`; `core/res/AndroidManifest.xml`, `core/res/res/values/strings.xml` |
| `frameworks-base/0004` | The service and its collector, started from `SystemServer`: names and counts of DNS lookups and TCP connects per user, in memory unless the user keeps a history; links derived on read. | New: `DataMapCollector.java`, `DataMapService.java`; `services/core/java/com/android/server/EventLogTags.logtags`, `services/java/com/android/server/SystemServer.java` |
| `system-sepolicy/0001` | Service type `datamap_service` and domain `datamap_app`; only `datamap_app` may find the service. | New: `private/datamap_app.te`; `private/seapp_contexts`, `private/service_contexts`, `private/service.te` |
| `vendor-adevtool/0001` | `PRODUCT_PACKAGES += datamap-classifier.tsv` on every Pixel target. The module is added by Task 3; any commit in `vendor/adevtool` stales the version pin (Task 4 step 5). | `config/mk/google_devices/common/device-common.mk` |

| `frameworks-base/0005` | Review findings R1, R2, R7 (below). The collector runs on the thread of netd's event callbacks with the monitor of `NetdEventListenerService` held, and upstream requires that it never blocks; it asked the activity manager (`mProcLock`) and the package manager from there. The event now goes to the service thread, at most 4096 at a time, and a user whose settings are not read yet is not collected. | New: `PendingLimit.java`; `DataMapService.java`, `UserSettings.java` |
| `frameworks-base/0006` | Review finding on `clear()`: events that waited for the service thread were recorded after the store was emptied. `clear` empties the store again behind them. | `DataMapService.java` |

| `frameworks-base/0007` | The independent review (next section): the empty-day crash in `DataMapStore.restore`, hostnames stored in full instead of service keys, IPv4 connects counted again as `::ffff:` addresses, answers of one app pushing out those of another and surviving a clear, an unbounded reply to the app, the history read at every call, the final write lost at user stop, an unlocked read-modify-write of the settings. | `DataMapService.java`, `DataMapStore.java`, `LinkDeriver.java`, `RecentAnswers.java`, `ServiceKeys.java` |
| `system-sepolicy/0002` | `system_app` and `traceur_app` are app domains with an allow rule for every service type except a fixed list, which conflicts with the neverallow that only `datamap_app` may find `datamap_service`; the policy would not compile. Both lists gain `-datamap_service`. | `private/system_app.te`, `private/traceur_app.te` |

| `frameworks-base/0008` | Found by the first module build: a permission declared in `core/res/AndroidManifest.xml` becomes a public `Manifest.permission` constant, and `check_current_api` refuses an API change that `current.txt` does not list. `READ_DATA_MAP` is for one app of this system only, so the comment above it gets `@hide`. | `core/res/AndroidManifest.xml` |
| `system-sepolicy/0003` | Found by the first module build: `fuzzer_bindings_test` refuses a new service that has no fuzzer on file. A Java service has no automatic fuzzer (b/287102710); the build names `service_fuzzer_bindings.go` as the place for the exception. | `build/soong/service_fuzzer_bindings.go` |

A loose file `0005-datamap-import-IpConnectivityLog-...patch` in the authoring directory is folded into `0004` and is not part of the series.

## Checks (2026-10-11, from this package)

- Server host checks: all pass; mutation check 29 of 29 killed (`service/tools/materialize-core.sh`, `run-host-checks.sh`, `run-mutations.py`).
- App host checks: all pass; mutation check 26 of 26 killed; `check-resources.py` and `check-static.sh` pass.
- Classifier builder checks: all pass; mutation check 3 of 3 killed (the field-count check is an equivalent mutant because the device parser repeats it).
- AIDL copies in the app equal the ones in patch `frameworks-base/0003`.

## Review of the server patches

Own read, 2026-10-12, against the tree the patches apply to (`frameworks/base` `fda11b7a2c9d`, `system/sepolicy` `261adac69c6d`; read with `git show HEAD:<path>`, no working copy). Each result is `[verified-in-source]` with the path read.

| Item | Result |
| --- | --- |
| R1 `getUidProcessState` | **Defect, fixed by 0005.** `ActivityManagerInternal.getUidProcessState` calls `ActivityManagerService.getUidState`, which holds `synchronized (mProcLock)` (`services/core/java/com/android/server/am/ActivityManagerService.java`). `NetdEventListenerService.onDnsEvent` and `onConnectEvent` are `synchronized` and call each callback with that monitor held; their comments say the methods "must not block or perform long-running operations" (`services/core/java/com/android/server/connectivity/NetdEventListenerService.java`). The collector called it for every event. |
| R2 `getPackagesForUid` | Same defect, same fix. The call filters by `Binder.getCallingUid()` (`pm/ComputerEngine.java`); on the callback thread that is netd, which runs as root (`system/netd/server/netd.rc`), and `AppsFilterBase.shouldFilterApplication` returns false for a caller app id below `FIRST_APPLICATION_UID`. Attribution was therefore correct, by the accident of netd's uid. On the service thread the caller is `system_server`. |
| R3 history read on a binder thread | Accepted risk. `getSnapshot` reads up to `MAX_HISTORY_BYTES` (64 MiB) as bytes and as one string. The store holds at most 4000 rows, so a day is far below that; the cap is a ceiling for a damaged file. No decoded cache is kept, because it would be resident memory in `system_server`. `[assumption]` for the size of a real history; measured by row `MAP-008` (event cost and heap). |
| R4 callback array | No defect. `mNetdEventCallbackList` is sized by `ALLOWED_CALLBACK_TYPES.length` and `isValidCallerType` iterates the same array, so adding the fourth type (0002) is enough. |
| R5 who may add a callback | No defect. `IpConnectivityMetrics.enforceNetdEventListeningPermission` accepts only `Process.SYSTEM_UID`; the service calls from its own handler thread, outside a Binder transaction, so the caller is `system_server`. `mNetdListener` is created in `IpConnectivityMetrics.onBootPhase(PHASE_SYSTEM_SERVICES_READY)`; `SystemServer` starts `IpConnectivityMetrics` before the Data Map service, so its boot-phase callback runs first and the registration posted by `systemReady` finds it. A refused registration is logged and not retried. Accepted: it depends on that start order. |
| R6 SELinux | No blocking rule found. `datamap_app` copies `logviewer_app` (`private/logviewer_app.te`). No `allow` in `public` or `private` lets an app domain find `system_server_service` as a class, and no `neverallow` of `app_neverallows.te` covers a new system service type, so `neverallow { appdomain -datamap_app } datamap_service:service_manager find` conflicts with nothing found. `shell` is not an `appdomain`. The compile is the proof and is a build step (Task 9). |
| R7 users | **Defect, fixed by 0005.** `collecting(userId)` returned true for a user without a settings entry, that is before the first unlock (settings are in CE storage) and after the user stopped, so a stored "off" did not apply until unlock. Observers are keyed by the calling user (`RemoteCallbackList` cookie) and `getSnapshot` reads the calling user's store, so the decoy user receives nothing of the owner's. |
| R8 restore and stop | **Own read was wrong, corrected by the reviewer and fixed by 0007.** `UserController.finishUserStopped` calls `onUserStopped` and then, synchronously, `dispatchUserLocking` (the CE storage is locked). The write that `onUserStopped` only posted therefore ran after the lock and was lost. The write now runs in `onUserStopping`, and a receiver for `ACTION_SHUTDOWN` writes on the service thread when the device shuts down, as the spec promises. |
| R9 permission site | No defect. The permission is declared in `core/res/AndroidManifest.xml` (package `android`) because the checking code is `system_server` itself; `MANAGE_AIRPODS_IDENTITY` is declared in the Bluetooth module's manifest because Bluetooth is the checking code there. |
| R10 `dump` and logs | No defect. `dump` prints counts after `DUMP`; no `Slog` call carries a package, service or owner name. The event log tags carry the user id, flags and day count only. |

Further findings of the own read, all fixed in 0005 and 0006 or accepted:
- `clear()` emptied the store on the binder thread while events could still wait for the service thread (0006).
- Accepted: the observer notification is throttled globally and has no trailing notification, so a last event inside a burst can leave the screen stale until the next event; the app also refreshes when it starts.
- Accepted: a damaged settings file gives the defaults, that is collecting on.

## Independent review

One fresh reviewer read the six patches against the tree (2026-10-12). Every finding below was checked against the source by the author before it was accepted; none is taken on the reviewer's word.

| # | Finding | Check by the author | Outcome |
| --- | --- | --- | --- |
| 1 | `restore()` ends with `users.get(userId).version++`; an empty day in the history file (left by `clear()` or by turning the history on with nothing recorded) makes the next unlock throw on the service thread, which takes down `system_server`. | Reproduced on the host first: `restore(7, [])` into a new store threw `NullPointerException` at `DataMapStore.restore`. | Fixed in 0007, check and mutation added. |
| 2 | The sepolicy neverallow conflicts with `system_app.te` and `traceur_app.te`. A uid 1000 caller passes `enforceCallingPermission` (the activity manager grants every permission to app id 1000), so SELinux is the only gate. | Read both files in the tree: each allows `find` for every `service_manager_type` minus a fixed list, and both are app domains. The other wide rules are neverallows or belong to non-app domains (`atrace`, `shell`, `dumpstate`). | Fixed in `system-sepolicy/0002`. Compile still to be proven (Task 9). Recorded for the threat model: the permission does not separate uid 1000 from the app. |
| 3 | The reply to the app is unbounded (a Binder transaction is about 1 MB); the history is read and decoded at every call; the writer rewrites the whole file every minute. | `LinkDeriver.derive` returns a link for every pair of apps that share any tracker, with no cap; `getSnapshot` read the file at every call. | Fixed in 0007: at most 2000 entries and 300 links, the earlier days cached until the file changes and dropped when the app unregisters. Accepted: the writer still rewrites the file once a minute when something changed; to be measured (`MAP-006`, `MAP-008`). |
| 4 | Full host names were stored, not service keys; `ServiceKeys.keyFor` had no caller. | Confirmed by `grep`: the collector passed `host` to `record`. | Fixed in 0007: a DNS contact is stored under its service key. |
| 5 | Collection against a stored "off" before unlock. | Same as the own finding R7. | Fixed in 0005. |
| 6 | An IPv4 connect is counted again because netd reports it as `::ffff:a.b.c.d`. | netd formats the address with `getnameinfo(NI_NUMERICHOST)` (`system/netd/server/FwmarkServer.cpp`). `[assumption]` that Java sockets are always IPv6 sockets; a device test (`MAP-012`) settles it. The fix is harmless if the assumption is wrong. | Fixed in 0007: both forms are one address. |
| 7 | Blocking calls under the `NetdEventListenerService` monitor. | Same as the own finding R1. | Fixed in 0005. |
| 8 | The final write at user stop is lost; no write at shutdown. | Confirmed in `UserController.finishUserStopped` (R8 above). | Fixed in 0007. |
| 9 | The recent answers (names) survive `clear()` and a user stop. | Confirmed: no purge existed. | Fixed in 0007. |
| 10 | Notification throttle is global and has no trailing notification; rows recorded between `snapshot()` and `clearUser()` at day rollover are lost; the settings are read, changed and written without a lock. | Confirmed by reading. | Settings fixed in 0007. Accepted for now: the throttle, and the rollover loss (at most the events of one tick). |

Areas the reviewer and the author checked and found sound: the callback array and its fourth slot, the registration order, the permission check of `IpConnectivityMetrics`, per-user isolation of the store, files and observers, the content of `dumpsys` and of the event log, the `datamap_service` type.

## Landed

2026-10-12, with no build running and the three trees clean. The patches were applied with `git am` in series order; the SHAs differ from the worktree commits, as `git am` makes them.

| Tree (branch) | Patch | Landed commit |
| --- | --- | --- |
| `frameworks/base` (`sovereignos-2026091900-r2`) | 0001 | `8d6a040c46af` |
| | 0002 | `38e3ce3241dd` |
| | 0003 | `c4e6a7e17aac` |
| | 0004 | `d37e1cf7c494` |
| | 0005 | `be2a93b3faad` |
| | 0006 | `04218f8e1943` |
| | 0007 | `659ad140a8c4` |
| `system/sepolicy` (`sovereignos-2026091900`) | 0001 | `39ab9fee2` |
| | 0002 | `27527dbd2` |
| `vendor/adevtool` (`sovereignos-airpods-identity`) | 0001 | `924c1ffa` |
| `vendor/sovereignos/datamap` (new repository, `master`) | app and classifier, without `tests/` and `tools/` | `2ed0a6b` |

Verification: for every file the patches touch (34 files) the git blob id in the build-server tree equals the blob id of the worktree commit; for `vendor/sovereignos/datamap`, `sha256sum` of all 41 files equals the package. The three trees were clean afterwards.

The pin `vendor/google_devices/{lynx,stallion}/adevtool-version-check.mk` now names `vendor/adevtool` HEAD `924c1ffa318637506e39d93fd22ecb1a0d28c6c3`; the previous files are kept beside them as `adevtool-version-check.mk.pre-datamap`. A second commit in `vendor/adevtool` (patch `vendor-adevtool/0002`, Task 10) moves the pin again.

Not built. The Soong and sepolicy compile is proven only by a build (Task 9).

Later landings, by `git am` after the first module build: `system/sepolicy` 0003 (`fd1b880f3`) and `frameworks/base` 0008 (`e53353ced37f`); the blobs of `build/soong/service_fuzzer_bindings.go` (`cff8582`) and `core/res/AndroidManifest.xml` (`a421805`) are identical to the worktree commits. `vendor/sovereignos/datamap` gained the finished app screens (`17a3a03`).

## Module build

Three builds on 2026-10-12 of `DataMapApp datamap-classifier.tsv selinux_policy services` for `lynx-cur-userdebug` (detached, with the disk guard; no image, no signing). The first two found what no type-check could:

1. `fuzzer_bindings_test` refused the new service until it was listed in `service_fuzzer_bindings.go` (patch `system-sepolicy/0003`).
2. `check_current_api` refused the permission as a new public API constant (patch `frameworks-base/0008`, `@hide`).

The third build exited 0 with no failed step. Checked in the output, not from the exit code:
- `DataMapApp.apk` asks for `org.sovereignos.permission.READ_DATA_MAP` and `android.permission.QUERY_ALL_PACKAGES` only; `INTERNET` appears nowhere; the manifest has one activity and no service, receiver or provider (`aapt2 dump`).
- `system/etc/datamap/classifier.tsv` is the 21-byte empty file with its header, at the path the service reads.
- `plat_sepolicy.cil` contains the new type and domain (48 mentions), `plat_service_contexts` the `datamap` entry, `plat_seapp_contexts` the app entry. The policy compile, with its neverallow checks, passed.
- `services.jar` holds all 13 classes of `com.android.server.datamap` (found in `classes.dex`).

Still open: nothing has run on a device.

## Shipping the app (Task 10)

2026-10-12, no build running, tree clean: `vendor/adevtool` patch 0002 landed as `0d91ffbe` (`device-common.mk` blob `1992526` identical to the worktree commit). The pins of `lynx` and `stallion` name `0d91ffbe2cef3de1ccbbf92ef43c2ed1811871...` (full hash in the file); the previous files are kept as `adevtool-version-check.mk.pre-datamap-app`. `vendor/sovereignos/datamap` holds the finished app and the classifier with its data (`f5d3628`, 48 files, `sha256sum` identical to the package; the `data/`, `tests/` and `tools/` directories are not deployed).

The package gate `tools/build-vm/check-required-packages.sh` (foundation repository) now requires `DataMapApp.apk`, both system files and `datamap/classifier.tsv`. The live copy was identical to the repository version before the change, was replaced (old file kept as `.pre-datamap`) and its `sha256` equals the repository's. Run against the previous `lynx` output it prints the four `MISSING:` lines and exits 1; it will pass only for an image built with patch 0002.
