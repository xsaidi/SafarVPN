# SafarVPN Android 1.4.18 (56)

- Device management is a separate block above subscriptions/server cards in Tunnel. Account device management is retained. Unbind controls are removed from all server cards; legacy manual profiles are selected from the separate device block.
- Subscription purchase/renewal uses the same filled primary color as the wallet Top up button.
- Device-limit selector: 1 included, maximum 5, 70 RUB per additional device per 30 days, prorated to the remaining subscription time. Reduction has no refund and requires excess devices to be unbound first. Server returns the actual quote; confirmation explicitly shows the wallet charge.
- Limit purchase UUID is encrypted and persisted before POST, scoped to the signed-in account. Accepted purchases poll status without resubmitting POST; restart/lost POST responses safely reuse the UUID. Success appears only after applied delivery and a refreshed global device list. Balance/catalog refresh after changes; renewal prices include selected additional devices.

Validation: 38 Android unit tests, signed universal release/R8, original certificate, package/version check, packaged native hashes for arm64-v8a/armeabi-v7a/x86_64. APK SHA-256 `45c7c68eca6845fb505e158016d8bcc97bbe19aea71184c4a0241e6ca5177d66`, 38,648,822 bytes. Evidence: `artifacts/SafarVPN-1.4.18-verification.json`.

Backend release: `../auth-integration/device-limits-20261015/DEPLOYED.md`, schema 0108 / node ledger 2. 30 isolated PostgreSQL/backend tests. No ADB, real customer payments, hardware layout/accessibility QA or real phone VPN traffic tested. No commit/push.
