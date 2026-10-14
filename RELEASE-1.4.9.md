# SafarVPN 1.4.9 / code47

- Removed server swipe-to-delete, its confirmation path and swipe hint.
- Server cards display name, device count and latency; profile edit, IP/domain, expiry/error details and device-unbind action are no longer shown on cards.
- Removed the Servers heading and its help/ping/sort/more toolbar. Existing subscription-level ping-all and refresh remain.
- Added a Support button in the subscription card, opening https://t.me/xsaidi through Android URI handling.
- Existing profile selection/reconnect, account namespace and server data storage remain intact.

No production changes, ADB, device installation or publication. Device/visual QA remains unverified.

Validation: release/R8 build successful; 11 Android unit tests passed; package ru.safarvpn.com, version 1.4.9/code47 and original signing certificate verified. Evidence: artifacts/SafarVPN-1.4.9-verification.json. Phone visual QA remains unverified.
