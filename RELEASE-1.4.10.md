# SafarVPN 1.4.10 / code48

- Reworked Account into separate profile and subscription cards, following the approved visual concept with rounded shapes, subtle theme-aware gradients and a separate logout action.
- Removed account ID and last-checked timestamp from the UI; renamed the subscription heading to «Подписка».
- Added a locally generated geometric avatar and «Сменить аватар». The selected artwork seed is saved per account on this device, including across app restarts and logout/login. No network or image permissions are required.
- Preserved Google/Telegram login, login cancellation, subscription refresh and its loading/error/empty states, and logout confirmation through AccountRepository.
- Added a stacked profile layout for narrow screens or large system fonts and capped the account content width for larger displays. Existing app navigation and its safe-area clearance are preserved.

No production changes, ADB, device installation, commit, push or publication. Device/visual QA remains unverified.

Validation: release/R8 build successful; 11 existing Android unit tests passed (AccountProfilesTest 5, MobileAuthProtocolTest 6), with zero failures/errors. Package ru.safarvpn.com, version 1.4.10/code48 and original signing certificate verified. Evidence: artifacts/SafarVPN-1.4.10-verification.json. APK SHA256: 68bfa75c33a14df4265b32e5dad1574b7439e8aa313eed9ed40ab4bb66157487. Device UI, actual avatar persistence, login/logout and VPN on a phone remain unverified.
