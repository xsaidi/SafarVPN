# SafarVPN 1.4.13 / code51

Implemented the approved account/balance, subscription payment and balance top-up references in native Compose.

- Account now has a permanent balance card with «Пополнить» and «История операций», separate from the subscription card.
- Subscription purchase/renewal is a dedicated full-screen page: available balance, selectable terms, amount to debit and remaining balance. Insufficient funds lead to the separate top-up page; payment still requires explicit confirmation after returning.
- Top-up is a dedicated full-screen page: current balance, decimal amount input, 100/300/500 RUB presets, enabled payment choices, provider limits, predicted post-credit balance and one checkout action. Actual credit remains server-confirmed; the app never treats opening a payment page as payment success.
- Added paginated account transaction history through the existing authenticated /balance/transactions API, without exposing internal transaction identifiers or raw backend descriptions.
- Kept durable per-account purchase request UUIDs, stale-owner fences, retry after uncertain response and original logout/profile/VPN behavior. Corrupt saved purchase state blocks new debits instead of silently discarding the request.
- Billing catalog/balance refresh on app resume, including return from provider checkout. Manual refresh is available. No automatic subscription purchase after top-up.
- Layouts use existing theme tokens, native control semantics, scroll, keyboard/safe-area insets and bounded content width.

No server connection, deployment, tmux interaction or production mutation in this task. User advised that another Codex CLI agent is working on the panel in tmux; server work is intentionally untouched. Existing mobile billing API from the previous deployment is reused. No ADB, phone installation, real payment or GitHub publication.

Added six monetary calculation tests for exact kopeks, decimal input, invalid/out-of-range input, gateway limits, sufficient/insufficient balances and arithmetic overflow. Device UI/payment end-to-end QA remains unverified.

Validation: final release/R8 build succeeded; all 17 Android unit tests passed (AccountProfilesTest 5, MobileAuthProtocolTest 6, BillingAmountsTest 6). Package ru.safarvpn.com, version 1.4.13/code51 and original signing certificate verified. Evidence: artifacts/SafarVPN-1.4.13-verification.json. Selected tariff is retained through top-up navigation, and reopening an existing invoice displays its original amount. Phone visual/payment/transaction-history QA remains unverified.
