# SafarVPN 1.4.15 / code53

Fixes the 1.4.14 checkout regression. The cabinet Platega top-up response prefers the provider transaction UUID in payment_id; Android incorrectly parsed that value as a numeric database row ID and aborted before opening the already-created checkout.

Android no longer requires payment_id to be numeric. It validates the returned HTTPS checkout URL, persists a SHA-256 checkout fingerprint with method and amount, and opens the provider page. The existing authenticated latest-payment API is used only to resolve a local row when its exact checkout fingerprint, method and amount match. A different latest payment, even for the same amount, is never used as proof. Once resolved, polling uses the exact local payment ID and the existing server-confirmed is_paid flag. Checkout URLs are not persisted in plaintext.

Outstanding numeric IDs from 1.4.14 remain readable. Three regression tests cover UUID checkout matching, a newer same-amount payment mismatch, and required method/amount/positive local ID.

No server connection/change, tmux interaction, backend deployment, ADB, real payment or GitHub publication. Real checkout and success-sheet QA require a phone; not performed in this task.

Validation: release/R8 build succeeded; all 25 Android unit tests passed. Package ru.safarvpn.com, version 1.4.15/code53 and original signing certificate verified. Evidence: artifacts/SafarVPN-1.4.15-verification.json. Phone checkout/payment-success QA remains unverified.
