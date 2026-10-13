# SafarVPN 1.4.5 recovery baseline

Saved on 2026-10-13. Tag: `safarvpn-1.4.5`. Application ID: `ru.safarvpn.com`; versionCode: 43.

This snapshot includes SafarVPN branding, Windows build scripts, account UI, Telegram/Google native login, WDTT/VK TURN subscription status, and logout. Manual VPN profiles remain supported. The upstream license and attribution are preserved.

Telegram and Google login were confirmed on a real device by the owner. Six Android unit tests, the release build, and signature verification passed during the original implementation. Full device/VPN and offline/process-death QA remain incomplete. Saving this snapshot does not repeat those tests.

## Existing signed APK

`SafarVPN-1.4.5-universal-release.apk` (38,600,099 bytes), available in the GitHub release:

- SHA256: `2AD8CB38C2B7B39E4B26DF45D447C007264F55B81F39DBB2DBDF91D2106A45A6`
- Signing certificate SHA256: `8D16AE71E2423D5D599060E1D32C18CE52BB233C29732940313AEE4BE4F2CE68`
- ABIs: arm64-v8a, armeabi-v7a, x86_64; minSdk 28; targetSdk 35.

The permanent private signing key is kept outside Git in `../safarvpn-signing`. Keep its offline backup; do not generate a replacement key for updates. Build caches and credentials are excluded from this repository.

## Restore source

Use a separate checkout to preserve any later local work:

```sh
git clone --branch safarvpn-1.4.5 https://github.com/xsaidi/SafarVPN.git SafarVPN-1.4.5
```

Use the Windows build instructions in README.md and the original signing key to rebuild. The existing APK is the exact saved binary; a new build is not guaranteed to be byte-identical. Android may reject installation over a newer version; preserve user data before any device rollback.

This is the Android source snapshot. Production backend/nginx recovery is documented separately in the private workspace handoff and deployment receipts. This tag does not roll back production services.
