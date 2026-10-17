# SafarVPN Android 1.4.21

- Launcher icon uses the supplied blue SafarVPN artwork on its original cream background.
- Adaptive circle and rounded-square launchers show the complete logo. The optional themed monochrome layer is omitted because the supplied artwork has an opaque background.
- Version code 59 updates the existing 1.4.20/code58 installation using the same permanent release certificate.

Build with `scripts/build-release-windows.ps1`. APK and verification evidence are in `artifacts/SafarVPN-1.4.21-universal-release.apk` and `artifacts/SafarVPN-1.4.21-verification.json`.

Regenerate density assets with Pillow using `scripts/generate-launcher-icons.py <original.png>`. The supplied original and mask previews are retained in `artifacts/SafarVPN-1.4.21-icon-source.png` and `artifacts/SafarVPN-1.4.21-icon-preview.png`.

No phone installation or runtime VPN test was performed. No production changes, publication, commit, or push.
