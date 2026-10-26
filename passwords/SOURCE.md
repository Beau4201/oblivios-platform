# Passwords app — Source Provenance

ADR-185. The local password manager of FreeholdOS: the app `PasswordsApp` (`app/`, Kotlin and Compose), the prebuilt Rust core
`libvault_core` (`core/`, built from `freeholdos-services/vault-core`, see `core/SOURCE.md`), the autofill service and the credential
provider service of the app, and two patches that make it part of the image and its default provider.

State: **landed on the build server and built as modules (2026-10-23); not on a device.**

## Patches

Authored in a disposable worktree (`.worktrees/passwords-authored/`, gitignored) that holds only the fetched files, read-only from the
build-server trees, as the repository rules require; exported with `git format-patch`.

| Tree | Baseline HEAD | Blob of the modified file at that HEAD | Patch | Why it diverges from upstream |
| --- | --- | --- | --- | --- |
| `vendor/adevtool` | `e4aca7568bc7` | `config/mk/google_devices/common/device-common.mk` `fe456150e5c6` | `vendor-adevtool/0001` | `PRODUCT_PACKAGES += PasswordsApp` on every Pixel target, in the common file like Guardian, AirPods and the Data Map. The module is defined in `vendor/freeholdos/passwords`. |
| `frameworks/base` | `3a3e6af27a53` | `core/res/res/values/config.xml` `9124ee97ec80` | `frameworks-base/0001` | Three resource values name the app as default: `config_defaultAutofillService`, `config_enabledCredentialProviderService` and `config_primaryCredentialProviderService` (read in `SettingsProvider.java` and `AutofillManagerServiceImpl.java` of the same tree). |

Both patches were checked with `git apply --check` against the live trees on 2026-10-22; nothing was changed there. The `vendor/adevtool` patch was authored on `fe4c53c7b8c8` first and re-authored on 2026-10-22 on `e4aca7568bc7` (the Compartments commit that added `exitd` and `ExitApp` to the same file moved the hunk); a later commit in that tree needs the same again.

## Landing

Landed on the build server on 2026-10-23, on the owner's word for the `lynx` image, with no build running and the trees clean:

| Tree | Landed as | What |
| --- | --- | --- |
| `vendor/freeholdos/passwords` (new repository, branch `freeholdos-2026100600`) | `7e3ac04` | `app/` and `core/` of this package without `tests/` and `tools/` (91 files); the `sha256sum` of every file equals the package. |
| `vendor/adevtool` | `e333ec2c` (base `e4aca7568bc7`) | patch `vendor-adevtool/0001` by `git am`. The pins `vendor/google_devices/{lynx,stallion}/adevtool-version-check.mk` name it now (the previous files are kept as `adevtool-version-check.mk.pre-passwords`). |
| `frameworks/base` | `94e9f26848c4` (base `3a3e6af27a53`) | patch `frameworks-base/0001` by `git am`. |

Three more commits of `vendor/freeholdos/passwords` landed on 2026-10-26 with `git am` (trees clean, no build running; the authored files were fetched from the server first and their sha256 equalled this repository's earlier state): `6af111a` (the app's own screens out of autofill and the save offer, platform `7e75d18`), `ba763ce` (stock action bar gone, one spacing rhythm, cards in the checkup, user name on the detail screen, motion, inset of the passphrase field, platform `cea99ae`) and `6f7dc25` (every Activity excludes its views from autofill in `SecureActivity`, the save request refuses the app's own package, checkup spacing as in the settings, platform `0095b6a`). The sha256 of the 12 touched files equals this repository at `0095b6a`; module build exit code 0 (`PasswordsApp`, 1:09 for the last one).

The module build (`tools/build-vm/build-passwords-modules.sh` of `freeholdos-foundation`: `libvault_core PasswordsApp ExitApp DataMapApp`, `OFFICIAL_BUILD=true`, ccache) passed with exit code 0 in 10 minutes 22 seconds: Soong parsed both `Android.bp` files, the installed `libvault_core.so` has the SHA-256 of `core/SOURCE.md`, and the APK declares `USE_BIOMETRIC` and `USE_FINGERPRINT` (added by a library), the signature permission `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` (androidx) and no network or radio permission. The merged manifest has three exported components of the app (the launcher activity and the two services, each behind its `BIND_*` permission) and one of a library: `androidx.profileinstaller.ProfileInstallReceiver`, behind `android.permission.DUMP`. `check-required-packages.sh` requires `PasswordsApp.apk` and `libvault_core.so` since the same day.

The defaults the frameworks patch sets apply when the secure setting is still empty (a new user or a new install): on an existing device the owner picks the app once under Settings, Passwords and accounts.

## Not shipped

`app/tests/` (host checks, guard tests) and `app/tools/` (the checks, the type check, the mutation run) stay in this repository.
