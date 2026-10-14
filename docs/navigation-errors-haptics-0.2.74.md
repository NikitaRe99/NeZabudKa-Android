# Navigation, error text, first-install defaults and haptics — 0.2.74

- Error cards display one body text in the theme danger color with a danger outline. No heading, setting path, acknowledgement button, or navigation action.
- assistantErrorText maps known speech, microphone-device, missing-model, memory, timeout, network, credentials and HTTP failures to short user text. Technical details remain in the caller log. Genuine microphone permission denial is handled by the existing permission flow; mentioning “микрофону” in a no-speech message no longer classifies it as permission denial. Normal cancellation has no error card.
- Full-width navigation uses four outlined icons, compact labels, theme colors, rectangular rounded selection background, thin top divider, 150ms color transitions (snapped in reduced-effects mode) and no icon movement. Baseline height 68dp expands for large label line height; touch areas remain full-column tabs. Android navigation inset remains separate.
- First install uses the agreed defaults. Runtime NLU now defaults off until the user chooses/imports/downloads a model. Existing explicit settings and legacy default-on installations are migrated without disabling their NLU. Older backup files that omit the NLU flag retain the historical backup-schema default. Current backups explicitly round-trip false.
- Added an independent uiHapticsEnabled interface preference, default true, with backup/restore support and Settings → Interface → Behavior switch. It gates all Compose LocalHapticFeedback calls under the common theme, including existing drag/completion effects. The delegate still respects system haptic settings. Notification/alarm vibration remains independent.
- Recording feedback follows global recording-state transitions, using stronger LongPress at start and lighter GestureEnd at stop. Removed duplicate pointer-down/pointer-up pulses.
- Added light hour/minute/date selection feedback, confirmation pulses, reminder completion feedback and deletion-threshold feedback. A 40ms gate coalesces near-simultaneous custom/material picker pulses.

## First-install defaults

Light theme; labels and initial hint on; performance auto; technical log off. Whisper with explicit model choice on first voice use; accurate/raw modes off; audio preprocessing on; automatic threads; keep-model-loaded off; immediate command execution off; extra punctuation off until selected. NLU off until model choice. Notification mode, system sound and reminder vibration on, wake screen off; date sorting. Notes one column, updated sorting, folder carousel, trash 30 days. GitHub metadata check on in supported builds; no automatic APK downloading. UI haptics on.

## Validation

Unit checks cover no-speech vs permission distinction, cloud technical-detail suppression, missing model, haptic disabling/rate gating, backup compatibility and existing NLU settings migration. Final build/device checks follow.

Final validation: 364 unit tests, 360 passed and 4 optional skipped. Signed release assembly and lintVitalRelease passed, including the final settings time-picker feedback and navigation font-scaling adjustment. git diff --check passed.

Installed on Vivo 10CF2E0RZL001EX: versionName 0.2.74, versionCode 97, lastUpdateTime 2026-10-14 13:16:29. Visually verified full-width navigation and the no-speech error card: red body/outline, no heading or actions (android/app/build/tmp/nav74.png). Opened Interface settings and verified the vibration preference and description are present; did not change the preference. Returned to Assistant. Physical vibration strength requires user assessment. First-install defaults and legacy preference preservation were checked by unit tests without clearing phone data.
