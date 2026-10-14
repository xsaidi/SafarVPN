# SafarVPN 1.4.6 — account subscription profiles

Version1.4.6/code44, package `ru.safarvpn.com`. Signed universal APK is saved in `artifacts/SafarVPN-1.4.6-universal-release.apk`; signing certificateSHA256 remains `8D16AE71E2423D5D599060E1D32C18CE52BB233C29732940313AEE4BE4F2CE68`. Artifact hash and checks are in `artifacts/SafarVPN-1.4.6-verification.json`.

After Google/Telegram login and when the app returns to foreground, the account's active WDTT subscription appears automatically under Profiles. Its card includes account ownership and bot purchasing text, expiry description and refresh action. Purchases still happen through the bot. An unlinked Google account and Telegram account remain separate; use Telegram login or the Google identity already linked in the cabinet to the purchasing account.

Account profiles use an isolated folder/subscription namespace and deterministic owner+peer profile IDs. Refresh updates passwords/hashes without duplicates or lost traffic counters. Manual profiles and manually imported subscriptions remain separate, including folders with the same visible name. Credentials are encrypted using the existing Android Keystore storage. Configuration is received only over the authenticated API; tokens and subscription URLs are not added to profile links.

Logout, account switching, confirmed expiry/revoke/absence remove account-owned profiles and clear their selected connection settings. Auth generation guards and serialized account/profile persistence reject late completions after logout/login. Valid saved profiles remain available if a network/backend request fails; cached expired access cannot be selected. When its account folder disappears, the filter returns to All. APK1.4.5 remains saved separately.

Backend endpoint `GET /auth/mobile/subscription/profiles` was deployed to the existing cabinet API. Production receipts and exact image provenance are documented outside this repository in `auth-integration/account-profiles-20261014/DEPLOYED.md` within the administration workspace.

Validation:11 Android JVM tests, signed release/R8 build, package/version/ABIs and original certificate verified;5 backend contract tests locally and in both candidate images, runtime health/native regressions/public authentication boundary and live aggregate configuration delivery. On14.10.2026 the owner confirmed the account subscription feature works on the phone. No ADB or formal full phone QA matrix was performed. Bot APK distribution/cache was not updated.

Saved release/tag: `safarvpn-1.4.6` in `xsaidi/SafarVPN`. APK SHA256: `2c812d635e7384859c6676d86c8504e6bf53eec12d89029f1eef40b8362851b9` (38,608,950 bytes).
