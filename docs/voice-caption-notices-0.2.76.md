# Voice action captions and notice appearance — 0.2.76

User confirmed appearance changes only, with particular concern about short messages such as “Напоминание удалено”.

- Assistant, Notes and Reminders now use one centered voice content component: outlined microphone/send icon, medium-weight body caption, matching spacing and processing spinner. Button height is at least 58dp, with room to grow for wrapped text. Captions and recording gestures are unchanged.
- Short Snackbar feedback uses a compact card row with message, optional close button and action. Undo is labelled “Отмена” and remains rightmost. Text wraps without ellipsis; close is shown only when requested by Snackbar visuals. Existing dismissal/undo callbacks and timeouts are unchanged.
- Command result notices use the same theme card background, outline and elevation as other app cards. They retain full-width text, close above text and trailing actions. Interior side padding is 16dp.
- Theme preview and color setting labels now match the surfaces used by messages. Recognition-text confirmation still uses the selected-element tint.

Validation: initial release assembly and lintVitalRelease passed. Final build includes theme preview/label alignment; phone installation and visual assessment pending. Existing phone version was verified as 0.2.75 before changes. No user reminders/notes were changed for verification.

Final signed release assembly and lintVitalRelease passed; git diff --check passed. Opened normal Android installer for version 0.2.76 (versionCode 99). Installation confirmation pending.

Device verification: user confirmed installation; dumpsys reports versionName 0.2.76 and versionCode 99. Visually checked centered voice caption and outlined microphone on Reminders and Assistant (build/tmp/voice76.png, build/tmp/assistant76.png). Existing records left unchanged; transient delete/complete notifications were not triggered against user data.
