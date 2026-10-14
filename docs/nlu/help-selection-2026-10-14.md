# NLU help command selection — 2026-10-14

Application UI, parser, model weights and phone data were not changed. This is a text evaluation, not a Whisper or microphone test.

## Method

34 frozen candidates: 16 previous manual examples plus 18 help candidates/controls. Previous manual examples were already used during earlier fixes; this is not a blind quality estimate.
1. Run production ExecuteTextCommand with an understanding port that always returns UNKNOWN to identify commands supported without NLU.
2. Tokenize normalized model input with the actual app ClassicWordPieceTokenizer and assets/nlu/vocab.txt.
3. Run real v7/v8 ONNX models on desktop using the exact exported app token IDs.
4. Feed raw ONNX logits into the unchanged private ClassicNluClient.decode through a JVM test harness; this includes confidence thresholds, guards and slot extraction. Only the decoder shell is allocated without its Android service constructor. No network, model selection, repository or scheduler is invoked.
5. Run the unchanged production routing and mapper. Compare intent plus all specified text/folder/target/date/range/recurrence fields against frozen expectations.
6. Assert every selected help command calls NLU, is UNKNOWN without it and has correct payloads on both models. Assert the two standard-parser controls never call the model.

## Models and results

- v7: SHA-256 `9f684131355751923109798bcf8ed38927d63a816f366fde9f7c5cfa73d9931a`; 33/34 matching full expected fields. 31/32 NLU-only candidates passed.
- v8: SHA-256 `5de0ba03db948763d5567a278e9cf0843e68c5181160a4cbe35f69926ec8e35f`; 34/34 matching full expected fields. 32/32 NLU-only candidates passed.

The v7 failure is “Дай посмотреть расписание на завтра”: confidence is below threshold. v8 accepts it. It is excluded from cross-version help selection.

## Selected examples

- Оставь запись в папке Лунный архив о том что запасной ремешок лежит возле часов — `create_note`; full payload verified on v7 and v8.
- Сигнализируй каждый вечер что нужно закрыть теплицу — `create_reminder`; full payload verified on v7 and v8.
- Главное не забыть проверить давление завтра утром — `create_reminder`; full payload verified on v7 and v8.
- Зафиксируй мысль: запасной ключ лежит в синем футляре — `create_note`; full payload verified on v7 and v8.
- Организуй папку Домашний ремонт — `create_folder`; full payload verified on v7 and v8.
- Есть ли у меня задачи на завтра — `list_reminders`; full payload verified on v7 and v8.

## Exclusions and limitations

- “Что у меня намечено на завтра?” and “В папку Домашний ремонт запиши: заказать новые ручки для шкафа” already work through standard routing and are not NLU-only examples.
- “Больше не напоминай про оплату парковки” parses as DELETE_REMINDER with target “оплату парковки” in this evaluation on both models. This does not prove end-to-end deletion works: Dispatcher calls ReminderRepository.find, RoomReminderRepository calls ReminderDao.findText, and SQL matches a literal text fragment against active reminders. A different wording of the stored task can therefore prevent a match. The actual cause on the user’s phone is not established; no phone data were read. Do not include this phrase as verified working deletion help.
- No command was dispatched. For deletion, database matching and user-visible effects were deliberately not simulated as successful.
- Named times use parser defaults (morning 09:00 in this run); user settings may choose a different hour.
- These samples were not passed to training or used to add parser shortcuts.

## Reproduce

PowerShell, repository root:
```powershell
$env:NLU_HELP_PHASE="prepare"
.\gradlew.bat :app:testDebugUnitTest --tests ru.nezabudka.app.llm.NluHelpSelectionEvaluationTest --offline --rerun
.\.nlu-venv\Scripts\python.exe tools/nlu/evaluate_help_logits.py
$env:NLU_HELP_PHASE="evaluate"
.\gradlew.bat :app:testDebugUnitTest --tests ru.nezabudka.app.llm.NluHelpSelectionEvaluationTest --offline --rerun
```

Both phases passed. Raw token/logit exchange files are in android/app/build/tmp; complete final results are in help-selection-2026-10-14-results.json.
