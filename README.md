# «НеЗабудьКа»: устройство проекта

Этот документ объясняет проект с нуля: где находится код, как данные проходят через
приложение, какие файлы можно менять и что обязательно проверять после изменений.

## Что делает приложение

«НеЗабудьКа» — локальное Android-приложение для заметок, папок и напоминаний. Оно
поддерживает текстовые и голосовые команды, системное распознавание речи, локальный
Whisper, несколько облачных STT-провайдеров, обычные уведомления и отдельный режим
будильника. Заметки и напоминания хранятся в Room на устройстве.

Минимальная версия — Android 10 (API 29), целевая — Android 16 (API 36).
Идентификатор release-приложения и корневой Kotlin-пакет — `ru.nezabudka.app`;
debug-сборка устанавливается как `ru.nezabudka.app.debug`.

## Самая простая модель архитектуры

Код разделён не по экранам, а по ответственности:

```text
Пользователь
    ↓
UI / ViewModel
    ↓
application — сценарии приложения
    ↓
ports — интерфейсы, через которые сценарии запрашивают данные и системные функции
    ↓
data / notifications / speech / llm — Android-реализации
    ↓
Room, AlarmManager, WorkManager, микрофон, Whisper, сеть
```

`domain` расположен в центре. Он знает только о правилах и моделях и не зависит от
Android. Благодаря этому правила дат, повторов и команд можно проверять обычными
быстрыми unit-тестами.

## Структура репозитория

```text
NeZabudKa-Android/
├── README.md                    этот документ
├── settings.gradle.kts          список Gradle-модулей и их расположение
├── build.gradle.kts             версии Android/Kotlin Gradle-плагинов
├── gradle.properties            общие параметры Gradle
├── gradlew, gradlew.bat         Gradle Wrapper; использовать его для сборки
├── android/
│   ├── app/                     основное Android-приложение
│   │   ├── build.gradle.kts     SDK, варианты сборки и зависимости приложения
│   │   ├── schemas/             экспортированные схемы Room для migration-тестов
│   │   └── src/
│   │       ├── main/            рабочий код и ресурсы
│   │       ├── test/            JVM/unit-тесты
│   │       └── androidTest/     тесты, которым нужен Android или устройство
│   └── baselineprofile/         генератор Baseline Profile для ускорения запуска
├── branding/                    исходник фирменного значка
├── tools/nlu/                   Python-утилиты обучения и оценки локального NLU
└── gradle/                      файлы Gradle Wrapper
```

Каталоги `.gradle`, `.idea`, `.kotlin`, `build` и `android/**/build` генерируются
инструментами. Это не исходный код приложения; их не редактируют вручную.

## Основной модуль `android/app`

### Точки входа

- `AndroidManifest.xml` объявляет разрешения, Activity, receiver, service, provider и
  виджет. Если Android должен самостоятельно создать компонент, он должен быть здесь.
- `AssistantApplication.kt` создаёт `AppContainer` — единственное место, где
  связываются репозитории, сценарии, распознавание речи и системные реализации. Здесь
  также запускается отложенное обслуживание, не мешающее первому кадру.
- `MainActivity.kt` управляет runtime-разрешениями, системными Intent и навигацией
  Compose. Бизнес-правила сюда добавлять нельзя.

Kotlin-код приложения расположен в `src/*/java/ru/nezabudka/app`. Путь каталога и
строка `package ru.nezabudka.app...` должны оставаться согласованными.

### `domain` — модели и чистые правила

- `Models.kt` — `Reminder`, `Note`, `NoteFolder`, команды помощника и их типы.
- `RussianTemporalParser.kt` — разбор русских дат и выражений времени.
- `ReminderRecurrence.kt` — правила обычных и пользовательских повторов.
- `SettingsModels.kt`, `SettingsRules.kt`, `CustomThemeSettings.kt` — модель настроек,
  допустимые значения и нормализация.
- `ModelSelectionRules.kt` — выбор модели с учётом памяти и возможностей устройства.
- `NoteCommandText.kt` — очистка и нормализация текста команд заметок.

В этом каталоге запрещены импорты `android.*`, Compose, Room, сети и файловой системы.

### `application` — законченные пользовательские сценарии

- `ExecuteTextCommand.kt` получает текст, при необходимости подключает NLU и передаёт
  проверенную команду диспетчеру.
- `Dispatcher.kt` выполняет команды над заметками и напоминаниями и возвращает эффект
  для UI.
- `ReminderActions.kt` синхронно изменяет запись напоминания и соответствующее
  системное расписание. Здесь же восстанавливаются расписания после перезагрузки.

Сценарии зависят только от `domain` и `ports`, а не от Android-реализаций.

### `ports` — договоры между слоями

- `Ports.kt` содержит интерфейсы репозиториев, планировщика, настроек, записи аудио,
  предобработки и STT.
- `PortableStatePorts.kt` описывает переносимые настройки моделей и облачных сервисов.

Если сценарию нужна новая системная возможность, сначала добавляется небольшой port,
затем его Android-реализация во внешнем слое.

### `data` — постоянные данные и фоновые операции

- `AppDatabase.kt` — Room database версии 8, DAO и все миграции 1→8.
- `RoomModels.kt` — таблицы Room и результаты сложных запросов.
- `RoomRepositories.kt` — преобразование Room-сущностей в модели `domain`.
- `SettingsStore.kt` — настройки приложения и быстрые снимки параметров уведомлений.
- `UserDataBackup.kt`, `BackupModels.kt`, `BackupRestorePlan.kt` — внешняя резервная
  копия через Storage Access Framework с совместимым форматом.
- `UserModelsStorage.kt`, `AndroidModelLibrary.kt` — пользовательский каталог моделей,
  временные native-копии и состояние загрузок.
- `ModelDownloadWorker.kt` — уникальная фоновая загрузка моделей через WorkManager.
- `NoteTrashCleanupWorker.kt` — ежедневное обслуживание корзины заметок.
- `ManualOrder.kt` — безопасный пересчёт ручного порядка карточек.
- `DeviceCapabilities.kt` — оценка слабого устройства для облегчённого интерфейса.

Room-схему нельзя менять без миграции, обновления `schemas/` и migration-теста.

### `notifications` — напоминания и будильник

- `ReminderNotifications.kt` содержит `AndroidReminderScheduler`, каналы уведомлений,
  фабрику уведомлений, alarm/action/boot receivers, повторы сигнала и защиту от двух
  одновременно звучащих будильников.
- `ReminderAlertActivity.kt` рисует полноэкранный будильник на заблокированном экране
  и резервное компактное Activity.
- `ReminderOverlayService.kt` показывает компактное окно поверх приложений на
  разблокированном устройстве и воспроизводит сигнал как foreground service.
- `DeviceAutoStartSettings.kt` безопасно выбирает доступный системный экран фоновой
  работы/автозапуска. Название производителя используется только как подсказка;
  каждый Intent предварительно проверяется через PackageManager.

Схема режима будильника:

```text
AlarmManager → ReminderAlarmReceiver → уведомление в системной шторке
                                      ├─ экран разблокирован + overlay разрешён
                                      │      → компактное окно
                                      └─ экран заблокирован
                                             → full-screen alarm Activity
```

Если компактный service не стартовал, приложение заменяет его тихое служебное
уведомление обычным звучащим heads-up уведомлением.

### `speech` — запись и распознавание речи

- `WavAudio.kt` — AudioRecord, WAV, системный `SpeechRecognizer` и предобработка.
- `WhisperCpp.kt` — каталог моделей, политика разбиения аудио и Kotlin/JNI-обёртка
  Whisper.
- `PcmWavSegmenter.kt`, `PcmWavBlockProcessor.kt`, `Pcm16StreamingResampler.kt` —
  потоковая обработка PCM без загрузки длинного файла целиком в память.
- `WhisperVadModelProvider.kt` — встроенная VAD-модель Silero.
- `SpeechTextNormalization.kt` — удаление подтверждённых повторов в транскрипции.
- `CloudSpeech.kt` — Yandex, Google и VK Cloud STT; токены хранятся отдельно от backup.

`src/main/cpp/whisper_jni.cpp` связывает Kotlin с whisper.cpp. Каталог
`cpp/third_party/whisper.cpp` — vendored-зависимость: его не редактируют при обычных
задачах. Переименование корневого Kotlin-пакета требует синхронно обновить JNI-имена.

### `llm` — локальное понимание команд

- `ClassicNluClient.kt` загружает выбранную ONNX-модель из `models/nlu` и запускает её
  только для нестандартных формулировок.
- `ClassicWordPieceTokenizer.kt` превращает текст в токены модели.
- `ClassicNluCommandMapper.kt` переводит результат классификации в строгую команду
  `domain`; модель не получает доступ к базе или Android API.

Используемые assets:

- `assets/nlu/vocab.txt` и `metadata.json` — словарь и метаданные локального
  классификатора; сама ONNX-модель хранится в пользовательском `models/nlu`;
- `assets/models/ggml-silero-v6.2.0.bin` — VAD, а не модель распознавания Whisper;
- Whisper- и NLU-модели пользователь загружает или импортирует отдельно, в APK они
  не входят. Словарь и лицензии NLU остаются в assets, потому что они малы.

### `ui` и `ui/screens` — состояние и Compose

- `AssistantViewModel.kt` — фасад UI: состояния экранов, голосовые сессии и вызов
  application-сценариев.
- `NotesFeature.kt`, `ReminderFeature.kt` — изолированные состояния и события списков.
- `AppTheme.kt`, `AppColorSchemes.kt`, `AppLayout.kt` — тема и параметры интерфейса.
- `ReminderTimeText.kt`, `ReminderRecurrenceUi.kt` — подготовленное для UI отображение
  времени и повторов.
- `screens/AssistantScreen.kt` — экран помощника.
- `screens/NotesScreen.kt`, `NoteEditorScreen.kt`, `FolderManagerScreen.kt` — список,
  редактор и папки заметок.
- `screens/RemindersScreen.kt`, `ReminderEditorScreen.kt`,
  `ReminderSnoozeDialog.kt` — список и редактирование напоминаний.
- `screens/SettingsScreen.kt`, `CustomThemeEditorDialog.kt` — настройки.
- `screens/SharedUiComponents.kt` — только реально общие Compose-компоненты.

Экран не должен напрямую читать Room, SharedPreferences, SAF или SpeechRecognizer.
Он отправляет событие ViewModel/feature и получает готовое состояние через StateFlow.

### `widget`

`VoiceNoteWidgetProvider.kt` — виджет быстрого перехода к записи голосовой заметки.
Его внешний вид находится в `res/layout/voice_note_widget.xml`, конфигурация — в
`res/xml/voice_note_widget_info.xml`.

## Где хранятся данные

- заметки, папки и напоминания — Room-файл `reminders.db` в приватных данных приложения;
- обычные настройки — SharedPreferences;
- токены облачного STT — EncryptedSharedPreferences и не включаются в backup;
- без ручного выбора пользовательские модели находятся в памяти телефона по пути
  `Android/data/<applicationId>/files/НеЗабудьКа/models/{whisper,nlu}`;
- старый каталог `LocalAssistant` при первом обращении переименовывается в
  `НеЗабудьКа` без копирования больших файлов;
- чтобы каталог был виден обычному файловому менеджеру, в настройках можно выбрать
  общую папку через системный Storage Access Framework;
- временные аудио и native-копии моделей — в cache и могут быть безопасно удалены;
- внешняя резервная копия — только в явно выбранном пользователем каталоге.

Смена `applicationId` создаёт для Android другое приложение. Приватная Room-база,
настройки, выданные разрешения, будильники и виджеты старого пакета автоматически не
переезжают. Для переноса данных используется экспорт из старой версии и импорт в новую.

## Как найти место для изменения

| Нужно изменить | Начать с |
|---|---|
| Правило даты, повтора или команды | `domain` и соответствующий unit-тест |
| Последовательность действий над данными | `application` |
| SQL, Room или миграцию | `data/AppDatabase.kt`, repository и migration-тест |
| Экран или карточку | конкретный файл в `ui/screens` |
| Состояние экрана | `NotesFeature`, `ReminderFeature` или `AssistantViewModel` |
| Системное уведомление/будильник | `notifications` |
| Запись и обработку звука | `speech` |
| ONNX-классификацию команды | `llm` |
| Разрешение или системный Intent | `MainActivity.kt`, Manifest и capability helper |
| Зависимость или вариант APK | `android/app/build.gradle.kts` |

## Сборка

Все команды выполняются из корня репозитория через Wrapper и JBR Android Studio:

```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :app:compileDebugKotlin
.\gradlew.bat :app:lintDebug :app:testDebugUnitTest :app:assembleDebug
```

Публичные адреса моделей заданы в `gradle.properties`. Для зеркала или локальной
сборки их можно переопределить одноимёнными Gradle properties:

```powershell
.\gradlew.bat :app:assembleRelease `
  -PwhisperSmallRuUrl="https://.../ggml-small-ru-v2-q5_1.bin" `
  -PclassicNluModelUrl="https://.../classic_nlu_int8.onnx"
```

Если URL удалить, соответствующая загрузка выключится, но импорт локального файла
останется доступен.

Модели публикуются как отдельные файлы GitHub Release, а не добавляются в историю
репозитория:

- `ggml-small-ru-v2-q5_1.bin` — преобразованная в формат `whisper.cpp` и
  квантованная версия
  [`artyomboyko/whisper-small-ru-v2`](https://huggingface.co/artyomboyko/whisper-small-ru-v2),
  лицензия Apache-2.0, SHA-256
  `e5763843891d13e6d3553ac39b9d815fa3dcaa04357a5d9d2db57aefd2d18dce`;
- `classic_nlu_int8.onnx` — созданная для «НеЗабудьКа» INT8-модель классификации
  команд, дообученная на основе
  [`cointegrated/rubert-tiny2`](https://huggingface.co/cointegrated/rubert-tiny2),
  лицензия исходной модели MIT, SHA-256
  `76bb09f071e3d9659d01d6ef63240b236a69e00e687fe0a11fdd5fb70304166f`.

При замене файла модели контрольную сумму в документации и описании релиза нужно
обновить одновременно. Условия и атрибуция NLU также находятся в
`android/app/src/main/assets/nlu/NOTICE.txt`.

Debug APK создаются отдельно для каждой архитектуры, чтобы не складывать три копии
native-библиотек в один огромный файл:

```text
android/app/build/outputs/apk/debug/app-arm64-v8a-debug.apk   большинство телефонов
android/app/build/outputs/apk/debug/app-armeabi-v7a-debug.apk старые 32-битные телефоны
android/app/build/outputs/apk/debug/app-x86_64-debug.apk      эмулятор x86_64
```

`debug` имеет суффикс `.debug` и может устанавливаться рядом с release. `release`
включает R8 и удаление неиспользуемых ресурсов. `benchmark` нужен модулю Baseline
Profile и остаётся profileable, но не debug-сборкой.

## Тесты

- `src/test` — быстрые JVM-тесты правил, сценариев, парсеров, аудиоалгоритмов и backup.
- `src/androidTest` — Room, миграции, Manifest/backup rules, уведомления, native Whisper
  и измерения на Android.
- `android/baselineprofile` — сценарии запуска, переходов и прокрутки для профиля ART.

После локального изменения сначала запускают самый узкий связанный тест. Перед
передачей сборки обязательны `lintDebug`, `testDebugUnitTest`, `assembleDebug`; после
изменений Android/Room/native — также профильный instrumented-тест на устройстве.

`ReminderDeviceScenarioInstrumentedTest` создаёт настоящие напоминания и поэтому
пропускается при обычном `connectedDebugAndroidTest`. Для ручной проверки один его
метод запускают через `adb shell am instrument`, передавая аргумент
`-e run_device_scenarios true` и полное имя нужного метода через `-e class`.

## Что нельзя удалять как «лишнее»

- `schemas/` — нужен для доказательства безопасных миграций Room;
- `baseline-prof.txt` и модуль `baselineprofile` — ускоряют запуск и критичные экраны;
- `cpp/third_party` — исходники native-зависимости, из которых собирается Whisper;
- ONNX, vocab и VAD assets — используются во время работы;
- receiver/service/provider в Manifest — Android создаёт их без прямого вызова из UI;
- ProGuard rules и consumer metadata — могут использоваться только release-сборкой.

Перед удалением символа нужно подтвердить отсутствие обычных вызовов, ссылок из
Manifest/XML, JNI, Room/KSP, сериализации, WorkManager и тестов. Успешная debug-сборка
сама по себе не доказывает, что reflection/JNI или release-вариант не сломан.
