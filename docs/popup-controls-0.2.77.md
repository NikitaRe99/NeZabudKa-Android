# Popup controls — 0.2.77

- Gallery variants 13 (creation cancelled), 15 (empty reminders) and 16 (matching reminder not found) place the close control on the same row as the message. Other result-card layouts are unchanged.
- Variant 29, reminder creation from the note editor, now requests the close action in its compact Snackbar row.
- Added “Не показывать всплывающие уведомления” under Interface → Behavior, default false for new and existing installations and historical backups. The preference is stored, exported and restored with other settings. Popup-duration selection is hidden while popups are disabled.
- Disables optional action/result notices and editor/reminder Snackbars through the theme composition local. Hidden Snackbars are dismissed so queued indefinite undo messages cannot remain pending or appear stale when re-enabled. Reminder date clarification is explicitly essential and remains visible, as do assistant error cards and Android reminder notifications/alarms.

Validation: testDebugUnitTest passed after final changes; backup round trip preserves true and missing-field legacy backup defaults to false. Signed release build and installation pending. User records were not modified.

Final release assembly and lintVitalRelease passed. 364 unit tests: 360 passed, 4 skipped, no failures. Opened normal Android installer for 0.2.77 (versionCode 100), installation confirmation pending.

User confirmed installation; device reports versionName 0.2.77, versionCode 100. Opened Interface settings and verified the new switch and explanatory text are present. Returned to Assistant; user records/preferences left unchanged.
