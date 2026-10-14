# SafarVPN 1.4.7 (45) — Android UI

- Fresh installations default to User mode; stored mode is preserved.
- Tunnel: existing server/profile selector, circular connect control driven by TunnelManager, exact labels «Подключиться», «Подключение...», «Подключено».
- WG / Raw / SOCKS5, secrets, peer, hashes, power and VK authorization/mode are inside the collapsed Advanced section at the bottom.
- Account logout asks for confirmation and then calls the existing AccountRepository.logout.
- Servers tab replaces Profiles. Folder filters, folder management/move and profile export UI removed. Internal subscription namespaces and manual profiles preserved.
- Ping all servers next to subscription refresh uses the existing PingHelper, shows progress/cancel and rejects duplicate runs. Card actions: ping and edit.
- Multiple configuration imports create new manual profiles without replacing existing groups. Shared admin QR generator preserved separately.
- Ping logs no longer print hashes, authentication command or raw child output.

Build: scripts/build-release-windows.ps1 -RunTests.
APK verification: scripts/verify-ui-release.ps1 (1.4.7/code45, original signing certificate).
No production changes, ADB, device installation, GitHub push or publication performed.
Phone visual QA and real tunnel/auth/logout/ping smoke tests remain to be performed on a device.

Verification completed 2026-10-14:
- Release/R8 build successful; 11 unit tests, 0 failures/errors.
- APK: artifacts/SafarVPN-1.4.7-universal-release.apk, 38,577,498 bytes.
- SHA256: 4b3951807cc3340ce085efe763796d100ab11373f99e339cdc96986422bee79d.
- Package ru.safarvpn.com, version 1.4.7/code45, original signing certificate verified.
- Evidence: artifacts/SafarVPN-1.4.7-verification.json.
- git diff --check passed. Device/visual QA remains unverified.
