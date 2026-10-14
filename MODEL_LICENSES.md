# Модели и лицензии

Модели Whisper и Classic NLU пользователь загружает или импортирует отдельно.
Модель пунктуации RUPunct Small также скачивается отдельно. В APK входят только
её токенизатор, описание меток и лицензионная информация.

## Whisper Small RU Q5

`ggml-small-ru-v2-q5_1.bin` получена преобразованием в формат `whisper.cpp` и
квантованием модели
[`artyomboyko/whisper-small-ru-v2`](https://huggingface.co/artyomboyko/whisper-small-ru-v2).
Исходная модель является дообученной версией `openai/whisper-small` для русского
языка и опубликована под лицензией Apache-2.0.

- размер: 190 085 504 байта;
- SHA-256: `e5763843891d13e6d3553ac39b9d815fa3dcaa04357a5d9d2db57aefd2d18dce`;
- лицензия: [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0).

## Classic NLU INT8

`classic_nlu_int8.onnx` дообучена для классификации команд «НеЗабудьКа» на основе
[`cointegrated/rubert-tiny2`](https://huggingface.co/cointegrated/rubert-tiny2),
после чего экспортирована в ONNX и квантована в INT8. Исходная модель опубликована
под лицензией MIT; её текст и атрибуция также находятся в
`android/app/src/main/assets/nlu/NOTICE.txt`.

- размер: 29 265 839 байт;
- SHA-256: `76bb09f071e3d9659d01d6ef63240b236a69e00e687fe0a11fdd5fb70304166f`;
- лицензия исходной модели: [MIT](https://opensource.org/license/mit).

### Classic NLU v7 INT8

`classic_nlu_v7_int8.onnx` — дообучение установленной Classic NLU с сохранением
прежних классификаторов и дистилляцией на исходных обучающих примерах. Новые
команды, варианты без пунктуации и повторы обучаются с исправленной разметкой.
Модель доступна отдельной загрузкой из GitHub Release `models-v2`; в APK её веса не входят. Прежний файл сохранён
для сравнения через «Активная NLU-модель» в настройках.

- размер: 29 265 839 байт;
- SHA-256: `9f684131355751923109798bcf8ed38927d63a816f366fde9f7c5cfa73d9931a`;
- лицензия исходной модели: MIT, атрибуция в `assets/nlu/NOTICE.txt`;
- обучение и проверки: `docs/nlu/Дообучение NLU 2026-10-11.md`.

Контрольные суммы должны проверяться после скачивания. При замене файлов описание,
размеры и SHA-256 необходимо обновить одновременно.

### Classic NLU v8 INT8

`classic_nlu_v8_int8.onnx` — проверенная модель команд с сохранением совместимости
меток и токенизатора. Публикация в `models-v3`; сведения о дообучении и ограничениях:
`docs/nlu/nlu-v8-review.md`. Размер 29 265 839 байт,
SHA-256 `5de0ba03db948763d5567a278e9cf0843e68c5181160a4cbe35f69926ec8e35f`.
MIT, атрибуция и полный текст в `assets/nlu/NOTICE.txt` и релизном `MODEL_LICENSES.md`.

## GigaAM v1/v2/v3 CTC INT8

Производные ONNX-модели [salute-developers/GigaAM](https://github.com/salute-developers/GigaAM),
MIT, upstream `7447938d791c4f3e643386ee22c33777004293a5`.
Скачиваются отдельно из релиза `models-v3`; в APK нет весов.
В каждый ONNX встроены log-mel frontend, словарь символов и версия формата
`gigaam-waveform-ctc-1`. Квантованы линейные MatMul, остальные операции FP32.
Пунктуация и текстовые подсказки в этих CTC-моделях отсутствуют.
Точные размеры, SHA-256 и полный текст MIT с атрибуцией находятся в
[релизе моделей](https://github.com/NikitaRe99/NeZabudKa-Android/releases/tag/models-v3).
Проверки: `docs/gigaam/evaluation-2026-10-14.md` и
`docs/gigaam/versions-v1-v2-v3-export.json`.

## RUPunct Small INT8

Скачиваемая модель `models/punctuation/rupunct_small_int8.onnx`
— INT8 ONNX-экспорт [RUPunct/RUPunct_small](https://huggingface.co/RUPunct/RUPunct_small),
опубликованный [ekhodzitsky](https://huggingface.co/ekhodzitsky/rupunct-small-onnx).
В приложении используются только предсказания запятых; слова, регистр и остальные
знаки сохраняются из транскрипции. Выбор по умолчанию оставляет обработку отключённой.

- размер: 29 259 673 байта;
- SHA-256: `b105da023474d98aa13ba18953ae67b04b17bd0595034bc06030c17536893933`;
- лицензия: MIT; атрибуция и текст в `assets/punctuation/NOTICE.txt`.
