# SafarVPN 1.4.11 / code49

- Set the Account SafarVPN heading explicitly to white in dark themes and onBackground in light themes.
- Removed the change-avatar button and regeneration action.
- Generate and save an artwork seed once on the first signed-in profile display. Reuse it on subsequent visits, app restarts and sign-ins. Preserve any seed already selected in 1.4.10.
- Account auth, subscription, logout, profiles and VPN logic remain unchanged.

No production changes, ADB, device installation or publication. Device/visual QA remains unverified.

Validation: release/R8 build succeeded, 11 existing Android unit tests passed, original signing certificate and package/version 1.4.11/code49 verified. Evidence: artifacts/SafarVPN-1.4.11-verification.json. Phone UI and avatar persistence have not been tested on a device.
