# SafarVPN 1.4.14 / code52

- Added automatic top-up status checks while the Account screen is resumed, including the full-screen billing pages. Checks pause in the background and resume immediately on return. Poll interval backs off from 4 to 10 and then 30 seconds.
- Uses the existing authenticated cabinet POST /balance/topup, which returns the local payment ID, then GET /balance/pending-payments/{method}/{id}. Existing backend routes enforce payment ownership. No use of latest-payment lookup, balance-delta guesses, payment-webhook forwarding or client-side payment completion.
- Persists outstanding payment method, ID and amount per account before opening checkout. Outstanding payments survive restart and are removed only on acknowledgement of confirmed credit or a terminal failure response. No credential-bearing checkout URLs are persisted.
- Success sheet follows the approved reference: checkmark, «Баланс пополнен», «Оплата прошла успешно», credited amount, refreshed server balance, «Готово» and «К подписке». Subscription navigation never automatically debits the balance.
- Confirms exact method + local ID + amount + server is_paid flag. Fetches the current server balance before displaying success. A different payment or balance change cannot trigger success. Errors remain pending and are retried.
- Added five status-matching/backoff tests, retaining the 17 existing tests.

No SSH/server connection, tmux interaction, backend change or production deployment. Reuses existing cabinet payment/status APIs; another Codex CLI is working on the panel. No ADB, real payment, device installation or GitHub publication in this task. Real checkout and phone success-sheet QA remain unverified.

Validation: release/R8 build succeeded; 22 Android unit tests passed (5 account profiles, 6 auth protocol, 6 amount calculations, 5 top-up status). Package ru.safarvpn.com, version 1.4.14/code52 and original signing certificate verified. Evidence: artifacts/SafarVPN-1.4.14-verification.json. No server or tmux interaction. Real payment, lifecycle/device and success-sheet visual QA remain unverified. Payments initiated before this version have no persisted payment ID and cannot receive the new per-payment success confirmation retroactively.
