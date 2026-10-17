# SafarVPN Android 1.4.22

Built on 17 October 2026 at the user's request. Version code **60**; package `ru.safarvpn.com`; Android 9+; universal APK for arm64-v8a, armeabi-v7a and x86_64. Signed with the same permanent certificate as 1.4.21, allowing an in-place update.

Includes the device-limit error handling, preserved expired account profiles and renewal message, readable dark-mode refresh text, redesigned device-management sheet, optional GitHub latest APK updates, trial-status label and one-time default concrete-service bypass lists described in `APP-CHANGES-20261017-PENDING.md`. The free once-per-account 48-hour/one-device trial is already enabled on the backend.

- APK: `artifacts/SafarVPN-1.4.22-universal-release.apk`
- Size: 38,695,519 bytes
- SHA-256: `7ae2c82792a86863316069bd410eccffe866626c44200b773277516cb44d7812`
- Signer SHA-256: `8d16ae71e2423d5d599060e1d32c18ce52bb233c29732940313aee4be4f2ce68`
- Verification: `artifacts/SafarVPN-1.4.22-verification.json`
- Packaged icons: `artifacts/SafarVPN-1.4.22-icon-verification.json`

`scripts/build-release-windows.ps1` completed successfully, including fresh native clients for all three architectures, bundled Linux server and R8 optimization. APK verification checked signature, package/version, SDK levels, native binaries, bundled server, absence of obsolete promotional/update links, and the expected GitHub updater endpoint/installer permission. All 15 launcher bitmap variants, both adaptive icons and manifest references matched their sources. The previously completed source suite passed 55 Android unit tests; it was not repeated for this version-only increment. No phone installation/runtime test or bot download replacement was performed. This document records build verification; GitHub release publication follows separately.
