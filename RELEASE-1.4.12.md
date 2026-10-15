# SafarVPN 1.4.12 / code50

- Account heading is now «Аккаунт», white in dark themes.
- Added support opening https://t.me/xsaidi with a paper-plane icon.
- Restored «Отвязать устройство» on tunnel server cards using the existing authenticated proof/confirmation flow for the current device.
- Added subscription purchase/renewal sheet: server-provided periods/prices, account balance, configured payment methods, external HTTPS provider checkout, explicit balance purchase confirmation and automatic balance/status refresh on return.
- Persistent per-account purchase intents allow checking/retrying uncertain requests without charging twice. Prices and owner are verified server-side; Google/Telegram identities are not silently combined.
- Kept «Обновить статус»: refreshes profile, subscription and account server configuration. Foreground refresh also remains; manual refresh handles delayed payment callbacks or temporary service failures.

Backend deployment details and recovery: ../auth-integration/mobile-billing-20261015/DEPLOYED.md.
No ADB, real paid transaction, device installation or GitHub publication. Phone UI and payment end-to-end QA remain unverified.

Validation: final release/R8 build succeeded, 11 existing Android unit tests passed, original signing certificate and package/version 1.4.12/code50 verified. Backend: 16 offline tests per candidate image, accepted healthy deployment, 4 public unauthorized/no-store probes. Evidence: artifacts/SafarVPN-1.4.12-verification.json and ../auth-integration/mobile-billing-20261015/evidence.json. Actual paid checkout/credit/purchase and device UI remain unverified.
