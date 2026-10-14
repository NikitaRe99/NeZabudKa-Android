# Navigation and picker feedback — 0.2.75

Removed the default rectangular press indication from the full-width navigation tabs. Selection keeps its rounded icon background and color change; tab semantics and full-column touch targets remain.

Picker selection feedback now uses Android CLOCK_TICK with KEYBOARD_TAP fallback when the view rejects CLOCK_TICK, rather than the text-handle feedback type that can be suppressed independently on phones. Both calls respect system settings, the app vibration preference and the existing 40ms deduplication gate. No forced vibration or audio clicks are introduced.

Validation: testDebugUnitTest succeeded (364 tests, 360 passed, 4 skipped, no failures). git diff --check passed. The connected phone reports system haptic_feedback_enabled=1. Physical feedback strength needs user assessment after installation.

Signed release assembly and lintVitalRelease passed. Opened the phone installer for 0.2.75 (versionCode 98); installation confirmation pending.
