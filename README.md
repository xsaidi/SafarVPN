# SafarVPN local Android fork

This local fork is based on qWDTT by SpaceNeuroX, which derives from the original WDTT project by amurcanov. Upstream source revision: `a296c57eaba69bb9479a24f9157856490890e47d`.

- Display name: SafarVPN; application ID: `ru.safarvpn.com`.
- Original logo, color palettes, VPN and profile functionality retained.
- Existing `qwdtt://`, `wdtt://`, `.qwdtt`, WireGuard and subscription import formats retained.
- One support item in Settings: `https://t.me/safarvpn_bot`.
- Upstream automatic/manual update lookup, APK downloader, installer and installer permission removed. Subscription/profile refresh remains available.
- GNU GPL v3 applies; see [LICENSE](LICENSE). Original authors retain their copyright.

## Windows build

Use Android Studio's bundled JBR, Android SDK platform 35, build-tools 35/36, Android NDK 28.2.13676358 and Go 1.26+. Set `sdk.dir` in the ignored `local.properties`. This prepared checkout automatically uses Go and NDK from `.build-tools`, including when tasks run from Android Studio. For an external toolchain, set `ANDROID_NDK_HOME` and put Go on PATH. Linux uses the original shell build.

Generate a permanent private key once with `scripts/create-signing-key.ps1`. It is stored in `../safarvpn-signing` outside this Git checkout; keep an offline backup of that private folder. Release signing fails without this key rather than silently using debug credentials.

For this prepared Windows checkout:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/download-go-dependencies.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/build-release-windows.ps1
```

Repository: https://github.com/xsaidi/SafarVPN. The `safarvpn-1.4.5` tag preserves the Android 1.4.5 (versionCode 43) recovery baseline. See [RELEASE-1.4.5.md](RELEASE-1.4.5.md).

Modified locally on 2026-10-13. Upstream provenance: https://github.com/SpaceNeuroX/proxy-turn-vk-android and https://github.com/amurcanov/proxy-turn-vk-android. The unchanged LICENSE includes the full GNU GPL v3 terms.

The release script configures the bundled JBR and local caches. Fresh Windows checkouts can prepare Go/SDK with `scripts/bootstrap-windows.ps1` followed by `scripts/install-sdk-packages.ps1`. The Gradle wrapper and Maven repositories use official download endpoints; module checksums remain enabled.

## Android Studio

Open the repository root `safarvpn-android`, not its `app` subfolder. Kotlin sources are in `app/src/main/java/com/wdtt/client`, and resources are in `app/src/main/res`. The internal namespace remains `com.wdtt.client`; the installed application's ID is `ru.safarvpn.com`. Android Studio's SDK location on this computer is `%LOCALAPPDATA%/Android/Sdk`, pointing to this checkout's `.build-tools/android-sdk`. If moving the checkout, update the SDK path in Studio and `local.properties`.
