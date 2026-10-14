# Navigation blink — 0.2.78

The full-width navigation still animated outgoing/incoming icon color and selection background over 150ms and switched label font weight. Removed these effects; selection colors now update directly from the route and captions retain Medium weight. Press ripple remains disabled. Screen transitions were already disabled and were not changed.

Prepared docs/final-command-check-2026-10-14.md with 24 numbered manual commands, expected outcomes and the exact speech-text/result reporting format. The set contains known regression cases, not a blind holdout; it was not used for training and was not executed against user data.

Validation: git diff --check passed. Release build and device assessment pending. This change targets tab-selection visuals; elimination of the user's observed flicker still needs confirmation on the updated phone.

Signed release assembly and lintVitalRelease passed. Opened normal Android installer for version 0.2.78 (versionCode 101); confirmation pending.

User confirmed installation; verified installed package version in dumpsys. Physical perception of navigation flicker awaits user assessment.
