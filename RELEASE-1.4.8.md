# SafarVPN 1.4.8 / code46

Changes requested on 2026-10-14:
- Connection button stays in «Подключение...» after the Go process starts. A separate connectionReady signal becomes true only after successful WireGuard/RAW startup or SOCKS listener readiness. Active worker statistics and the optional log pipeline do not set this signal.
- Disconnect cooldown is 1 second instead of 5. The existing start gate still waits for actual teardown before starting another transport.
- Tunnel is a single scrolling page: connection button, servers/subscriptions with existing ping/edit/import/refresh controls, then Advanced settings. The separate server selection dropdown is removed.
- Selecting an already selected server does nothing; duplicate selections are blocked during apply/connect/reconnect. Selecting another server while connected reapplies the existing profile and reconnects.
- Account replaces the former Servers navigation tab. Account identity, subscription details, Google/Telegram sign-in, refresh and confirmed logout now live there.
- File imports open Tunnel and use the existing import path.

No production changes, ADB, device installation or publication.
Phone visual QA and real VPN/auth smoke testing remain unverified.

Verification completed:
- Release/R8 successful, 11 Android unit tests, zero failures/errors.
- Package ru.safarvpn.com, version 1.4.8/code46, original signing certificate verified.
- APK: artifacts/SafarVPN-1.4.8-universal-release.apk (38,572,507 bytes).
- SHA256: 7ccb4298830f7edda4536299a2a32705489fd0d6a115fae6f58994ec34f6695e.
- Evidence: artifacts/SafarVPN-1.4.8-verification.json; git diff --check passed.
