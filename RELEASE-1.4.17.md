# SafarVPN Android 1.4.17 (55)

Compatibility update for the WDTT server rollout of 15 October 2026.

- Account devices use the authenticated `/auth/mobile/server/v1` API. Both Account and Tunnel expose the global device list; revoking devices continue occupying a slot.
- Unbind is a durable, account-owned UUID operation. HTTP 202 is acceptance; only `applied` plus a refreshed global inventory produces success. A lost response or app restart reuses the original UUID. Polling runs while the app is resumed, every 3 seconds for pending operations, with backoff up to 30 seconds on transient failures.
- Unbinding this device stops VPN and blocks another start while the operation remains pending. Other devices can be selected in the device list. Device IDs are not displayed.
- Manual profiles retain HMAC/challenge requests. Their legacy HTTP 202 shows pending instead of failure/success; a local node status is never treated as confirmation of global release.
- Account profiles use `node_id`; upgrading a profile with the same address preserves selection and traffic via migration from its previous address-derived ID. Later address/name changes retain its identity. Older configurations remain supported.
- Tunnel and legacy profile APIs share one device identity, preserving a valid historical Android ID and persisting a fallback when needed. Identity is excluded from backup/device transfer. Stable VPN device ID logging removed from native client.
- New structured server errors are handled only for server/v1; existing auth/billing error contracts and payment behavior remain unchanged. Cached profiles survive configuration failures.

Validation: 33 Android unit tests, signed universal release build with R8, original signing certificate, package/version verification, and packaged native client hashes matched against all three rebuilt ABIs. Exact evidence: `artifacts/SafarVPN-1.4.17-verification.json` (generated after final build).

APK size: 38,633,556 bytes. SHA-256: `a5f395bc6b2e078053597da8fb3b275eac1e24a79c73eabf7cec7cd40a37860a`. Native comparisons use release-stripped outputs; unstripped build hashes are also recorded.

Not tested: real phone VPN traffic, live multi-device unbind with an unavailable node, actual payment, accessibility/layout on hardware. No ADB, production mutations, commit or push performed.

Server reference: `/root/wdtt-remediation-20261015/CLIENT-HANDOFF.md`, `FINAL-REPORT.md`, `release.json` on panel 31.59.150.96. Server source checked read-only. Active image last observed `sha256:d52fbcc511938416664f6b12845b4474f68d12dd91b279a78874366ed676f3b7`, ledger schema 2. Never invoke older rollback recipes or overwrite production with the repository's legacy embedded standalone server asset. Use the remediation release's compatible recovery procedure.
