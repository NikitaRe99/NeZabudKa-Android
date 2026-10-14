"""Evaluate request morphology with the deployed model, without retraining."""
import json
from pathlib import Path
import onnxruntime as ort
from transformers import AutoTokenizer
from train_nlu import BASE_MODEL
from evaluate_nlu import predict_batch

root = Path(__file__).resolve().parents[2]
model_dir = root / "tools/nlu/output/accuracy-v8-retained"
samples = [
    ("Отобразить дела на завтра", "покажи дела на завтра", "list_reminders", {"period_start": "2026-10-14T00:00", "period_end": "2026-10-15T00:00"}),
    ("Отобразить напоминания на следующий месяц", "покажи напоминания на следующий месяц", "list_reminders", {"period_start": "2026-11-01T00:00", "period_end": "2026-12-01T00:00"}),
    ("Показать мои напоминания на сегодня", "покажи мои напоминания на сегодня", "list_reminders", {"period_start": "2026-10-13T00:00", "period_end": "2026-10-14T00:00"}),
    ("Вывести список задач на послезавтра", "выведи список задач на послезавтра", "list_reminders", {"period_start": "2026-10-15T00:00", "period_end": "2026-10-16T00:00"}),
    ("Зафиксируем мысль завтра зафиксируем результат", "зафиксируй мысль завтра зафиксируем результат", "create_note", {"text": "завтра зафиксируем результат"}),
    ("Зафиксировать мысль ключ находится возле очков", "зафиксируй мысль ключ находится возле очков", "create_note", {"text": "ключ находится возле очков"}),
    ("Запишем следующую мысль крышку нужно проверить", "запиши следующую мысль крышку нужно проверить", "create_note", {"text": "крышку нужно проверить"}),
    ("Сохраним заметку о том, что часы остановились", "сохрани заметку о том, что часы остановились", "create_note", {"text": "часы остановились"}),
    ("Не отобразить дела", "Не отобразить дела", "unknown", {}),
    ("Можно ли зафиксировать мысль", "Можно ли зафиксировать мысль", "unknown", {}),
    ("Показать расписание автобусов", "покажи расписание автобусов", "unknown", {}),
    ("Отобразить фотографии", "Отобразить фотографии", "unknown", {}),
]
cases = [{"id": i, "utterance": original, "scope": "assistant", "intent": intent, **expected}
         for i, (original, normalized, intent, expected) in enumerate(samples, 1)]
inputs = [{**case, "utterance": sample[1]} for case, sample in zip(cases, samples)]
tokenizer = AutoTokenizer.from_pretrained(BASE_MODEL, local_files_only=True)
options = ort.SessionOptions()
options.intra_op_num_threads = 4
session = ort.InferenceSession(str(model_dir / "classic_nlu_int8.onnx"), options, providers=["CPUExecutionProvider"])
metadata = json.loads((model_dir / "metadata.json").read_text(encoding="utf-8"))
results = predict_batch(session, tokenizer, metadata, inputs)
rows = [{"case": case, "model_input": sample[1], "now": "2026-10-13T12:00",
         "intent": intent, "confidence": confidence, "slots": slots, "raw_intent": raw}
        for case, sample, (intent, confidence, slots, raw) in zip(cases, samples, results)]
destination = root / "docs/nlu/verb-forms-onnx.json"
destination.write_text(json.dumps(rows, ensure_ascii=False, indent=2), encoding="utf-8")
print("Accepted intents:", sum(c["intent"] == r[0] for c, r in zip(cases, results)), "/", len(cases))
print("Output:", destination)
