# Assistant cards — 0.2.71

- Contextual educational cards persist until acknowledged. Permissions rank above configuration tips; errors are separate cards.
- Android-checkable permission cards hide while the required grants are present and reappear on revocation. Checks cover global notifications, current mode notification channels, exact alarms, battery optimization exemption, and full-screen/overlay access for alarm windows.
- Vendor autostart/background window permissions cannot be read reliably through Android APIs. These remain a separate manual-check card and do not claim permission was granted.
- Cards use existing theme selection/background and accent border tokens; no new independent palette color. A temporary “Новая подсказка” label and scroll to newly offered tips make additions visible.
- Buttons: acknowledgement first, settings link last. Settings links show the whole category and scroll to the relevant card; back returns to the preserved Assistant navigation entry.
- Welcome setting is “Начальная подсказка”; visible title is “Запомните голосом”. Help contains concrete standard commands instead of model/recording explanations.
- Commands in help have a regression check with NLU unavailable: reminder create/list/delete, monthly repeat, folder create/delete, note creation with exact folder and content extraction.
- Unit validation: 353 tests, 351 passed, 2 skipped. Signed release build (including lintVitalRelease) passed. Vivo device check confirmed the persisted card after process restart, whole-category settings navigation, the target card visible, back returning to the source card, and acknowledgement/settings button order.

## Educational triggers

Missing Whisper model/menu → recognition alternatives. First successful Whisper → speed benchmark. First voice note → punctuation. First named time → time-of-day settings. First folder → folder layout. Unrecognized command while NLU disabled → enabling NLU; actual NLU use/enabling → role explanation. First reminder → delivery mode/test. Missing reminder access → checkable permissions. Known vendor settings screen → manual-check card. Enabling alarm window → alarm explanation. Hiding welcome examples → restore path.

No automatic catalogue/backup educational cards. Immediate permission/model-choice dialogs remain for actions that cannot proceed without them.

The new label fades without collapsing its reserved space, so action buttons do not move during interaction. Microphone error cards also clear on return after the system grants microphone access.

Final release 0.2.71 (versionCode 94) installed through the normal Android installer after Vivo rejected ADB replacement. Device lastUpdateTime: 2026-10-14 04:22:18. Final UI verified stable card button position after the new label fades and help icon raised to the app heading. APK SHA-256: 5682EDFF6494A015D1D2DCEE8A1EFADD6E1032E995DE5EB1FB3814624DF23F70.
