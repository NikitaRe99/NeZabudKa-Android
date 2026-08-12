# Модели и лицензии

Модели не входят в APK и не хранятся в истории Git. Пользователь загружает или
импортирует их отдельно.

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

Контрольные суммы должны проверяться после скачивания. При замене файлов описание,
размеры и SHA-256 необходимо обновить одновременно.
