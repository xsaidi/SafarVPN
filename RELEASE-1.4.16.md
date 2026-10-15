# SafarVPN 1.4.16 / code54

- Restyled Tunnel to match approved Account references: theme-aware dark/light surfaces, lavender accents, white/foreground heading, bounded content width and consistent spacing.
- Replaced the large multi-ring connection card with a compact circular power action, a separate text/dot status and a selected-server label. Connected/connecting state still comes from TunnelManager connectionReady/running/reconnecting; existing click grace, stop/start gating and reconnect behavior are unchanged.
- Subscription information and servers share an Account-style section. Account subscription status/expiry use real repository values; manual subscription descriptions/traffic and refresh/delete controls remain supported. No example dates, pings or names from the mockup are hardcoded.
- Server cards have selected radio/border treatment, name/country marker, device counts, a separate ping action and visible unlink action with divider/icon. Existing confirmation/proof request, profile selection and long-press ordering remain.
- Support uses the shared Telegram paper-plane link. Advanced settings are a quiet rounded disclosure row at the bottom; settings contents remain unchanged.
- Import/FAB, subscription refresh and ping-all remain available.

No server connection, tmux interaction, production modification, ADB, phone installation or publication. Existing 25 Android tests are the validation suite; phone visual/layout/connectivity and touch behavior still require device QA.

Validation: release/R8 build succeeded; all 25 Android unit tests passed. Package ru.safarvpn.com, version 1.4.16/code54 and original signing certificate verified. Evidence: artifacts/SafarVPN-1.4.16-verification.json. Phone visual/gesture/connectivity QA remains unverified. Server/tmux not accessed.
