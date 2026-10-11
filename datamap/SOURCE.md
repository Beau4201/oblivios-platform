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

A loose file `0005-datamap-import-IpConnectivityLog-...patch` in the authoring directory is folded into `0004` and is not part of the series.

## Checks (2026-10-11, from this package)

- Server host checks: all pass; mutation check 19 of 19 killed (`service/tools/materialize-core.sh`, `run-host-checks.sh`, `run-mutations.py`).
- App host checks: all pass; mutation check 26 of 26 killed; `check-resources.py` and `check-static.sh` pass.
- Classifier builder checks: all pass; mutation check 3 of 3 killed (the field-count check is an equivalent mutant because the device parser repeats it).
- AIDL copies in the app equal the ones in patch `frameworks-base/0003`.

## Review of the server patches

Not done. Task 2 of the plan records R1 to R10 here.
