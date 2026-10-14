# Assistant copy and help — 0.2.72

Approved changes:
- Recognition path now points to “Настройки → Речь и команды → Распознавание речи”.
- Shortened time-of-day, punctuation, reminder mode, permission and alarm-window descriptions.
- Welcome title: “Сохраняйте идеи на лету”. Help icon moved a further 8 dp up without changing its touch target size.
- NLU_ENABLE is the only NLU education card and is offered on an unrecognized command while NLU is disabled or missing. Removed extra offers when enabling/using NLU.
- DEVICE_BACKGROUND merged into REMINDER_DELIVERY. On phones with unreadable vendor autostart/background permissions, the combined card stays until acknowledged; it does not claim these grants are verified. The manual-check explanation remains next to the setting.
- Old pending and offered NLU_USAGE/DEVICE_BACKGROUND IDs migrate to canonical IDs. Duplicate pending cards become one; previous acknowledgements remain respected.
- Error cards have their title and message only. No acknowledgement button, setting path or navigation action. A subsequent execution/recognition attempt clears the old error. Separate educational cards retain their navigation.
- Restored general help, removed recording instructions and unsupported deletion example. Added the five approved NLU examples, verified in the separate help-selection-2026-10-14 evaluation.
- Settings retain “Распознавание команд” and the switch “Расширенное распознавание команд”, matching the agreed card and help text.

Validation: 357 unit tests; 353 passed, 4 optional evaluation tests skipped. Added merged-history migration tests and expanded standard help command checks with OptionalCommandUnderstanding disabled. No model weights, parser rules or training data changed.

Signed final release and lintVitalRelease passed. APK SHA-256: f806bf82d80ef919676fa2544d9ddc0002e754dd8ce20eab2f5f7ea207d7cfe7.

Installed final release 0.2.72 / versionCode 95 through the ordinary Android installer; device lastUpdateTime 2026-10-14 11:53:10. UI verified restored general help and all five approved NLU examples, updated welcome title and raised help icon. Help closed back to Assistant after verification.
