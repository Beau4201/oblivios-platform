# Data Map — Source Provenance

ADR-157. Observe-only collector in `system_server`, a read-only classifier file in the system image, and the privileged app `DataMapApp`.

State: **not landed on the build server, not built, not on a device.** Task 4 of the implementation plan lands the patches; this file is updated then.

## Baselines

The patches were authored in disposable worktrees (`.worktrees/datamap-authored/worktrees/`, gitignored) that hold only the fetched files, read-only from the build-server trees.

| Tree | Baseline HEAD (abbreviated) | Blob of each modified file at that HEAD |
| --- | --- | --- |
| `frameworks/base` | `fda11b7a2c9d` | `core/java/android/net/INetdEventCallback.aidl` `27fac25`; `services/core/java/com/android/server/connectivity/NetdEventListenerService.java` `68f9a5f`; `core/res/AndroidManifest.xml` `5c9e490`; `core/res/res/values/strings.xml` `4d56413`; `services/core/java/com/android/server/EventLogTags.logtags` `c604779`; `services/java/com/android/server/SystemServer.java` `d34efa7` |
| `system/sepolicy` | `261adac69c6d` | `private/seapp_contexts` `8633389`; `private/service.te` `7e738f8`; `private/service_contexts` `de00043` |
| `vendor/adevtool` | `d9665a565a56` | `config/mk/google_devices/common/device-common.mk` `2e94e04` |

Task 4 step 2 compares these blobs with the HEAD of each tree before anything is applied.

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

A loose file `0005-datamap-import-IpConnectivityLog-...patch` in the authoring directory is folded into `0004` and is not part of the series.

## Checks (2026-10-11, from this package)

- Server host checks: all pass; mutation check 22 of 22 killed (`service/tools/materialize-core.sh`, `run-host-checks.sh`, `run-mutations.py`).
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
| R8 restore and stop | No defect. `UserController.finishUserStopped` calls `onUserStopped` before it locks the user, so the final write in `onUserStopped` still has CE storage. A history file from a previous day is kept and today starts empty. |
| R9 permission site | No defect. The permission is declared in `core/res/AndroidManifest.xml` (package `android`) because the checking code is `system_server` itself; `MANAGE_AIRPODS_IDENTITY` is declared in the Bluetooth module's manifest because Bluetooth is the checking code there. |
| R10 `dump` and logs | No defect. `dump` prints counts after `DUMP`; no `Slog` call carries a package, service or owner name. The event log tags carry the user id, flags and day count only. |

Further findings of the own read, all fixed in 0005 and 0006 or accepted:
- `clear()` emptied the store on the binder thread while events could still wait for the service thread (0006).
- Accepted: the observer notification is throttled globally and has no trailing notification, so a last event inside a burst can leave the screen stale until the next event; the app also refreshes when it starts.
- Accepted: a damaged settings file gives the defaults, that is collecting on.

Independent reviewer: see the next section.
