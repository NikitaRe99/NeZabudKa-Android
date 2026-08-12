from __future__ import annotations

from collections import Counter, defaultdict
import json
from pathlib import Path
import re

import numpy as np
import onnxruntime as ort
from transformers import BertTokenizerFast

from train_nlu import MAX_LENGTH
from heldout_corpus import build_heldout_corpus


ROOT = Path(__file__).resolve().parents[2]
MODEL = ROOT / "tools/nlu/output/classic_nlu_int8.onnx"
METADATA = ROOT / "tools/nlu/output/metadata.json"
CORPUS = ROOT / "android/app/src/test/resources/llm/command_corpus_ru.json"
EXTENDED_CORPUS = ROOT / "android/app/src/test/resources/llm/command_corpus_ru_extended.json"
REGRESSION_CASES = [
    ("надо не забыть выбросить мусор", "create_reminder", "выбросить мусор"),
    ("нужно не забыть выкинуть мусор", "create_reminder", "выкинуть мусор"),
    ("главное не забыть убрать мусор", "create_reminder", "убрать мусор"),
    ("удали напоминание выбросить мусор", "delete_reminder", "выбросить мусор"),
]


def softmax(values):
    shifted = values - np.max(values)
    exp = np.exp(shifted)
    return exp / exp.sum()


def allowed(intent: str, scope: str) -> bool:
    if intent == "unknown" or scope == "assistant":
        return True
    if scope == "reminders":
        return intent in {"create_reminder", "list_reminders", "delete_reminder"}
    return intent in {"create_note", "create_folder", "delete_folder"}


EXPLICIT_DELETION = re.compile(
    r"удал|убер|убир|убра|отмен|аннул|исключ|вычерк|сотр|ликвид|расформ|упразд|прекрат|"
    r"отзов|деактив|останов|не\s+отслеж|не\s+использ|больше\s+не\s+использ|(?<!\w)не\s+нуж|"
    r"избав|закрой|сним|откаж|не\s+напоминай|не\s+актуал|не\s+действ|утрат\w*\s+актуальност",
    re.I,
)
FOLDER_NOUN = re.compile(r"(?:папк|раздел|каталог|категор|коллекц|секци|хранилищ|подборк|рубр|групп|блок|полк)\w*", re.I)
REMINDER_CONTEXT = re.compile(
    r"(?:напомин|задач|план|распис)\w*|"
    r"(?:будущ|запланирован|календар|контрол|отслежив)\w*\s+(?:пункт|действ|дел|обязательств|очеред)\w*|"
    r"(?:пункт|действ|дел|обязательств|очеред)\w*\s+(?:будущ|запланирован|календар|контрол|отслежив)\w*",
    re.I,
)
REMINDER_ONLY_ACTION = re.compile(r"(?:отмен\w*|не\s+напоминай|больше\s+не\s+напоминай)", re.I)
SHORT_DELETE_ACTION = re.compile(r"^\s*(?:удал[иь]\w*|убер[иь]\w*)\s+", re.I)
NON_REMINDER_DELETE_OBJECT = re.compile(
    r"(?:документ|файл|экран|окно|страниц|журнал|истори|загрузк|форм[ауеы]|запрос|сообщени|"
    r"заметк|папк|каталог|рубр|групп|блок|контакт|фото|видео)\w*",
    re.I,
)
DELETE_FOLDER_ARGUMENT = re.compile(
    r"^(?:удал|убер|закрой|ликвид|расформ|исключ|сотр|упразд)\w*\s+"
    r"(?:папк|раздел|каталог|категор|коллекц|секци|хранилищ|подборк|рубр|групп|блок)\w*\s+.+|"
    r"^(?:папк|раздел|каталог|категор|коллекц|секци|хранилищ|подборк|рубр|групп|блок)\w*\s+.+\s+"
    r"(?:не\s+нуж|можно\s+(?:удал|убр)|пора\s+удал|утрат\w*\s+актуальност)",
    re.I,
)
DIRECT_LAST_REMINDER_NOT_NEEDED = re.compile(
    r"^\s*мне\s+(?:больше\s+)?не\s+нужн[оа]\s+(?:мо[её]\s+)?последн\w*\s+напоминани\w*\s*[.!]?\s*$",
    re.I,
)
CREATE_FOLDER_MARKER = re.compile(
    r"созд|добав|сдел|организ|завед|сформ|подготов|собер|выдел|учред|откр|размест|нужна\s+папк|"
    r"хочу\s+(?:новую\s+)?папк|пусть\s+будет\s+папк|нов(?:ая|ый|ую)\s+(?:папк|раздел)", re.I,
)
REMINDER_MUTATION_MARKER = re.compile(r"^\s*(?:измен|перенес|передвин|отлож)", re.I)
NON_COMMAND_QUESTION = re.compile(r"^\s*(?:стоит\s+ли|можно\s+ли|как|почему|зачем|кто|я\s+уже|не\s+хочу|объясни)", re.I)
POSITIVE_REMINDER_MARKER = re.compile(
    r"не\s+забыть|не\s+дай(?:\s+мне)?\s+забыть|не\s+забыл[а-яё]*|(?<!\w)напомни(?!\w)|"
    r"(?:создай|добавь|поставь|оставь)\s+(?:мне\s+)?напоминани\w*", re.I,
)
NOTE_CONTENT_NOUN = re.compile(r"(?:заметк|папк|каталог|рубр|групп)\w*", re.I)


NON_ARGUMENT_SLOT = re.compile(r"^(?:это|вс[её]|его|е[её]|их|текущ(?:ее|ий|ую)|данн(?:ое|ый|ую))$", re.I)


def confirmed_slot(slots: dict[str, str], name: str, text: str) -> str | None:
    value = normalized(slots.get(name))
    if not value or NON_ARGUMENT_SLOT.fullmatch(value):
        return None
    match = re.search(rf"(?<!\w){re.escape(value)}(?!\w)", normalized(text), re.I)
    return value if match else None


def passes_intent_guard(intent: str, text: str, slots: dict[str, str]) -> bool:
    if intent == "create_reminder":
        return (
            REMINDER_MUTATION_MARKER.search(text) is None and
            NON_COMMAND_QUESTION.search(text) is None and
            (EXPLICIT_DELETION.search(text) is None or POSITIVE_REMINDER_MARKER.search(text) is not None)
        )
    if intent == "list_reminders":
        return (
            NOTE_CONTENT_NOUN.search(text) is None and
            REMINDER_MUTATION_MARKER.search(text) is None and EXPLICIT_DELETION.search(text) is None
        )
    if intent == "create_folder":
        return CREATE_FOLDER_MARKER.search(text) is not None and (
            FOLDER_NOUN.search(text) is not None or confirmed_slot(slots, "FOLDER", text) is not None
        )
    if intent == "delete_reminder":
        return EXPLICIT_DELETION.search(text) is not None and (
            re.search(r"(?<!\w)последн\w*", text, re.I) is not None or
            ((REMINDER_CONTEXT.search(text) is not None or REMINDER_ONLY_ACTION.search(text) is not None or
              (SHORT_DELETE_ACTION.search(text) is not None and NON_REMINDER_DELETE_OBJECT.search(text) is None)) and
             (confirmed_slot(slots, "TARGET", text) is not None or has_meaningful_argument(text)))
        )
    if intent == "delete_folder":
        return (
            EXPLICIT_DELETION.search(text) is not None and FOLDER_NOUN.search(text) is not None and
            (confirmed_slot(slots, "FOLDER", text) is not None or DELETE_FOLDER_ARGUMENT.search(text) is not None or
             has_meaningful_argument(text))
        )
    return True


def has_meaningful_argument(text: str) -> bool:
    return len([part for part in re.split(r"[^\w]+", text, flags=re.UNICODE) if part]) >= 3


def threshold_for(metadata, intent: str, explicitly_destructive: bool) -> float:
    if intent in {"delete_reminder", "delete_folder"}:
        key = "explicit_destructive_intent_threshold" if explicitly_destructive else "destructive_intent_threshold"
        return float(metadata[key])
    return float(metadata.get("intent_thresholds", {}).get(intent, metadata["intent_threshold"]))


def decode_slots(text: str, offsets, slot_logits, labels: list[str]) -> dict[str, str]:
    segments: list[dict[str, object]] = []
    current: dict[str, object] | None = None

    def finish() -> None:
        nonlocal current
        if current is not None:
            segments.append(current)
            current = None

    for token_index, token_scores in enumerate(slot_logits):
        start, end = (int(v) for v in offsets[token_index])
        if start == end:
            continue
        slot_id = int(token_scores.argmax())
        label = labels[slot_id]
        if label == "O":
            if current is not None and all(char.isspace() or char in ".,:/+-" for char in text[start:end]):
                continue
            finish()
            continue
        prefix, slot_type = label.split("-", 1)
        gap = text[int(current["end"]):start] if current is not None else ""
        whitespace_gap = current is not None and start >= int(current["end"]) and all(
            char.isspace() or char in ".,:/+-" for char in gap
        )
        if current is not None and prefix == "I" and current["type"] == slot_type and whitespace_gap:
            current["end"] = end
            current["count"] = int(current["count"]) + 1
            current["score"] = float(current["score"]) + float(token_scores[slot_id])
        else:
            finish()
            current = {"type": slot_type, "start": start, "end": end, "count": 1, "score": float(token_scores[slot_id])}
    finish()

    result = {}
    for slot_type in {str(segment["type"]) for segment in segments}:
        candidates = [segment for segment in segments if segment["type"] == slot_type]
        best = max(candidates, key=lambda segment: (int(segment["count"]), float(segment["score"]) / int(segment["count"])))
        value = text[int(best["start"]):int(best["end"])].strip(" «»\".,:;!?—–-")
        if value:
            result[slot_type] = value
    return result


def predict(session, tokenizer, metadata, text: str, scope: str):
    encoded = tokenizer(
        text,
        max_length=MAX_LENGTH,
        padding="max_length",
        truncation=True,
        return_offsets_mapping=True,
        return_tensors="np",
    )
    offsets = encoded.pop("offset_mapping")[0]
    token_type_ids = encoded.get("token_type_ids", np.zeros_like(encoded["input_ids"]))
    intent_logits, slot_logits = session.run(None, {
        "input_ids": encoded["input_ids"].astype(np.int64),
        "attention_mask": encoded["attention_mask"].astype(np.int64),
        "token_type_ids": token_type_ids.astype(np.int64),
    })
    probabilities = softmax(intent_logits[0])
    intent = metadata["intent_labels"][int(probabilities.argmax())]
    raw_intent = intent
    if scope != "notes" and DIRECT_LAST_REMINDER_NOT_NEEDED.search(text):
        intent = "delete_reminder"
    confidence = float(probabilities.max())
    explicitly_destructive = EXPLICIT_DELETION.search(text) is not None
    slots = decode_slots(text, offsets, slot_logits[0], metadata["slot_labels"])
    threshold = threshold_for(metadata, intent, explicitly_destructive)
    if confidence < threshold or not allowed(intent, scope) or not passes_intent_guard(intent, text, slots):
        intent = "unknown"
    return intent, confidence, slots, raw_intent


def predict_batch(session, tokenizer, metadata, cases):
    texts = [case["utterance"] for case in cases]
    encoded = tokenizer(
        texts,
        max_length=MAX_LENGTH,
        padding="max_length",
        truncation=True,
        return_offsets_mapping=True,
        return_tensors="np",
    )
    offsets = encoded.pop("offset_mapping")
    token_type_ids = encoded.get("token_type_ids", np.zeros_like(encoded["input_ids"]))
    intent_logits, slot_logits = session.run(None, {
        "input_ids": encoded["input_ids"].astype(np.int64),
        "attention_mask": encoded["attention_mask"].astype(np.int64),
        "token_type_ids": token_type_ids.astype(np.int64),
    })
    results = []
    for case_index, case in enumerate(cases):
        text = case["utterance"]
        probabilities = softmax(intent_logits[case_index])
        intent = metadata["intent_labels"][int(probabilities.argmax())]
        raw_intent = intent
        if case["scope"] != "notes" and DIRECT_LAST_REMINDER_NOT_NEEDED.search(text):
            intent = "delete_reminder"
        confidence = float(probabilities.max())
        explicitly_destructive = EXPLICIT_DELETION.search(text) is not None
        slots = decode_slots(text, offsets[case_index], slot_logits[case_index], metadata["slot_labels"])
        threshold = threshold_for(metadata, intent, explicitly_destructive)
        if confidence < threshold or not allowed(intent, case["scope"]) or not passes_intent_guard(intent, text, slots):
            intent = "unknown"
        results.append((intent, confidence, slots, raw_intent))
    return results


def normalized(value):
    return re.sub(r"\s+", " ", value.strip(" «»\".,:;!?—–-").lower()) if isinstance(value, str) else value


REMINDER_TEMPORAL = [
    re.compile(r"\bчерез\s+(?:(?:\d+|[а-яё]+(?:\s+[а-яё]+){0,2})\s+)?(?:минут\w*|час\w*|дн\w*|сут\w*|недел\w*|месяц\w*|год\w*|лет)\b", re.I),
    re.compile(r"\b(?:сегодня|завтра|послезавтра)\b", re.I),
    re.compile(r"\b(?:в|во)\s+(?:следующ\w+\s+)?(?:понедельник|вторник|среду|четверг|пятницу|субботу|воскресенье)\b", re.I),
    re.compile(r"\b\d{1,2}\s+(?:января|февраля|марта|апреля|мая|июня|июля|августа|сентября|октября|ноября|декабря)(?:\s+\d{4})?\b", re.I),
    re.compile(r"\b(?:первого|второго|третьего|четв[её]ртого|пятого|шестого|седьмого|восьмого|девятого|десятого)\s+(?:января|февраля|марта|апреля|мая|июня|июля|августа|сентября|октября|ноября|декабря)\b", re.I),
    re.compile(r"\bв\s+(?:[01]?\d|2[0-3])(?::[0-5]\d)?(?:\s+час\w*)?(?:\s+(?:утра|дня|вечера|ночи))?\b", re.I),
    re.compile(r"\bв\s+(?:один|одну|два|две|три|четыре|пять|шесть|семь|восемь|девять|десять|одиннадцать|двенадцать)(?:\s+час\w*)?(?:\s+(?:утра|дня|вечера|ночи))?\b", re.I),
    re.compile(r"\b(?:утром|днём|днем|вечером|ночью|после\s+обеда|(?:в|на)\s+полдень|(?:в|на)\s+полночь)\b", re.I),
]
REMINDER_PREFIX = re.compile(
    r"^\s*(?:пожалуйста[\s,]+)?(?:"
    r"(?:хорошо\s+бы|главное)(?:\s+мне)?\s+не\s+забыть|"
    r"(?:мне\s+)?(?:обязательно\s+)?(?:надо|нужно)\s+(?:обязательно\s+)?(?:не\s+забыть\s+)?|"
    r"проследи\s*,?\s*чтобы\s+я\s+не\s+забыл(?:а)?|не\s+дай(?:\s+мне)?\s+забыть|"
    r"(?:оставь|поставь|создай|добавь|запланируй)(?:\s+мне)?(?:\s+напоминани\w*)?|"
    r"маякни(?:\s+мне)?(?:\s*,?\s*чтобы)?|напомни(?:\s+мне)?(?:\s+пожалуйста)?|"
    r"не\s+забыть|обязательно)\s*", re.I,
)
NOTE_PREFIX = re.compile(
    r"^\s*(?:пожалуйста[\s,]+)?(?:мне\s+(?:важно|нужно)\s+(?:запомнить|сохранить|записать)|"
    r"зафиксируй|запомни(?:\s+пожалуйста)?|сохрани(?:\s+как\s+заметку)?(?:\s+без\s+изменений)?|запиши(?:\s+себе)?|"
    r"(?:создай|добавь)\s+заметку|"
    r"оставь\s+заметку|сделай\s+запись|нужно\s+записать|добавь\s+в\s+заметки|"
    r"пусть\s+в\s+заметках\s+будет)(?=\s|[,:;.!?]|$)\s*", re.I,
)


def clean_reminder_payload(text: str) -> str | None:
    if delimited := command_delimited_payload(text):
        for pattern in REMINDER_TEMPORAL:
            delimited = pattern.sub(" ", delimited)
        return normalized(delimited) or None
    value = text
    for pattern in REMINDER_TEMPORAL:
        value = pattern.sub(" ", value)
    value = REMINDER_PREFIX.sub("", value)
    value = re.sub(r"^\s*(?:(?:о|об|про|на|чтобы)\s+)+", "", value, flags=re.I)
    value = re.sub(r"\s+(?:на|в|к)\s*$", "", value, flags=re.I)
    return normalized(value) or None


def clean_note_payload(text: str) -> str | None:
    if delimited := command_delimited_payload(text):
        return delimited
    match = NOTE_PREFIX.search(text)
    if not match:
        return None
    suffix = text[match.end():].strip(" «»\".,:;!?—–-")
    return normalized(re.sub(r"^\s*что\s+", "", suffix, flags=re.I)) or None


def clean_folder_payload(intent: str, text: str) -> str | None:
    patterns = [
        r"^\s*(?:создай|добавь|сделай|организуй|заведи)(?:\s+(?:новую|новый))?\s+(?:папку|раздел|каталог)\s+(.+)$",
        r"^\s*(?:мне\s+нужна|хочу\s+(?:новую\s+)?|пусть\s+будет)\s*папк[ау]\s+(.+)$",
    ] if intent == "create_folder" else [
        r"^\s*(?:удали|убери|закрой|избавься\s+от)\s+(?:папку|раздел|каталог)\s+(.+)$",
        r"^\s*(?:папка|раздел|каталог)\s+(.+?)\s+(?:больше\s+не\s+нуж(?:ен|на|но|ны)|можно\s+(?:удалить|убрать))\s*[.!]?\s*$",
        r"^\s*мне\s+не\s+нуж(?:на|ен)\s+(?:папка|раздел|каталог)\s+(.+)$",
        r"^\s*можно\s+убрать\s+(?:папку|раздел|каталог)\s+(.+)$",
    ]
    for pattern in patterns:
        match = re.search(pattern, text, re.I)
        if match:
            return normalized(match.group(1))
    return None


def clean_delete_payload(text: str) -> str | None:
    if delimited := command_delimited_payload(text):
        return delimited
    structured_patterns = [
        r"^\s*(?:вычерк|удал|убер|исключ|сотр)\w*\s+из\s+"
        r"(?:расписани\w*|план\w*|перечн\w*\s+(?:дел|задач)\w*|будущ\w*\s+действи\w*)\s+(.+)$",
        r"^\s*сним[иь]\w*\s+с\s+(?:контрол\w*|исполнени\w*)\s+"
        r"(?:(?:задач|напоминани|пункт)\w*\s+)?(.+)$",
        r"^\s*помет\w*\s+(?:(?:задач|напоминани|пункт)\w*\s+)?(.+?)\s+"
        r"как\s+(?:отмен[её]нн|неактивн)\w*\s*$",
        r"^\s*(?:аннулир|отмен|отзов|деактив|сотр|удал|убер|закрой|прекрат)\w*\s+"
        r"(?:(?:запланированн|ожидающ)\w*\s+)?(?:(?:задач|напоминани|пункт|действи)\w*\s+)(.+)$",
        r"^\s*больше\s+не\s+(?:учитывай|отслеживай|держи)\w*"
        r"(?:\s+(?:в\s+расписании|в\s+планах))?\s+(.+)$",
    ]
    for pattern in structured_patterns:
        match = re.search(pattern, text, re.I)
        if match:
            return normalized(re.sub(
                r"^\s*(?:(?:напоминани\w*|задач\w*)\s+)?"
                r"(?:(?:о|об|про|с\s+текстом|на\s+тему)\s+)?",
                "",
                match.group(1),
                flags=re.I,
            ))
    anchored = re.search(
        r"^.+(?:напоминани|задач|пункт|действи|дел|очеред|контрол)\w*\s+(.+)$",
        text,
        re.I,
    )
    if anchored:
        target = re.sub(
            r"\s+(?:(?:как|в)\s+(?:отмен[её]нн|неактивн)\w*|из\s+напоминани\w*)\s*$",
            "",
            anchored.group(1),
            flags=re.I,
        )
        return normalized(re.sub(
            r"^\s*(?:(?:о|об|про|с\s+текстом|на\s+тему)\s+)?",
            "",
            target,
            flags=re.I,
        ))
    patterns = [
        r"^\s*(?:напоминани\w*|задач\w*)?\s*(.+?)\s+(?:больше\s+не\s+нужн\w*|можно\s+(?:удалить|убрать))\s*[.!]?\s*$",
        r"^\s*мне\s+(?:больше\s+)?не\s+нужн\w*\s+(?:напоминани\w*|задач\w*)?\s*(.+)$",
        r"^\s*(?:можно\s+)?(?:удали(?:ть)?|убери(?:ть)?|отмени(?:ть)?|сними(?:ть)?|закрой|не\s+напоминай(?:\s+больше)?(?:\s+про)?|откажись\s+от)"
        r"(?:\s+из\s+(?:планов|расписания))?\s+(?:мо[её]\s+)?(?:напоминани\w*|задач\w*)?\s*(.*)$",
        r"^\s*больше\s+не\s+напоминай(?:\s+про)?\s+(.+)$",
    ]
    for pattern in patterns:
        match = re.search(pattern, text, re.I)
        if match:
            return normalized(re.sub(r"^\s*(?:о|об|про|с\s+текстом|на\s+тему)\s+", "", match.group(1), flags=re.I)) or None
    return None


def command_delimited_payload(text: str) -> str | None:
    match = re.search(r"\s+[—–-]\s+", text)
    if match is None:
        candidate = re.search(r"[:;]\s+", text)
        carrier = re.compile(
            r"(?:напомин|дел|задач|пункт|запис|замет|памят|текст|данн|сведен|информац|"
            r"справоч|факт|действ|план|формулиров|слова)\w*\s*$",
            re.I,
        )
        temporal_end = re.compile(
            r"(?:сегодня|завтра|послезавтра|утром|дн[её]м|вечером|ночью|понедельник|вторник|"
            r"среду|четверг|пятницу|субботу|воскресенье)\s*$",
            re.I,
        )
        match = candidate if candidate and (
            carrier.search(text[:candidate.start()]) or temporal_end.search(text[:candidate.start()])
        ) else None
    return normalized(text[match.end():]) if match else None


def mapped_payload(intent: str, text: str, slots: dict[str, str]):
    raw = {
        "create_reminder": slots.get("TEXT"), "create_note": slots.get("TEXT"),
        "delete_reminder": slots.get("TARGET"), "create_folder": slots.get("FOLDER"),
        "delete_folder": slots.get("FOLDER"),
    }.get(intent)
    if intent == "create_reminder":
        model_text = confirmed_slot(slots, "TEXT", text)
        if model_text and not any(pattern.search(model_text) for pattern in REMINDER_TEMPORAL):
            return model_text
        return clean_reminder_payload(text)
    if intent == "create_note":
        folder = slots.get("FOLDER")
        confirmed_text = confirmed_slot(slots, "TEXT", text)
        if confirmed_text and (re.search(r"\bзаметк\w*", text, re.I) or confirmed_slot(slots, "FOLDER", text)):
            return confirmed_text
        return clean_note_payload(text) or confirmed_text
    if intent == "delete_reminder":
        return None if re.search(r"\bпоследн\w*", text, re.I) else clean_delete_payload(text) or confirmed_slot(slots, "TARGET", text)
    if intent in {"create_folder", "delete_folder"}:
        return clean_folder_payload(intent, text) or confirmed_slot(slots, "FOLDER", text)
    return raw


def macro_f1(expected: list[str], actual: list[str], labels: list[str]) -> tuple[float, dict[str, dict[str, float]]]:
    details = {}
    for label in labels:
        tp = sum(left == label and right == label for left, right in zip(expected, actual))
        fp = sum(left != label and right == label for left, right in zip(expected, actual))
        fn = sum(left == label and right != label for left, right in zip(expected, actual))
        precision = tp / (tp + fp) if tp + fp else 0.0
        recall = tp / (tp + fn) if tp + fn else 0.0
        f1 = 2 * precision * recall / (precision + recall) if precision + recall else 0.0
        details[label] = {"precision": precision, "recall": recall, "f1": f1}
    return sum(value["f1"] for value in details.values()) / len(labels), details


def main():
    metadata = json.loads(METADATA.read_text(encoding="utf-8"))
    base_corpus = json.loads(CORPUS.read_text(encoding="utf-8"))
    extended_corpus = json.loads(EXTENDED_CORPUS.read_text(encoding="utf-8"))
    heldout_corpus = build_heldout_corpus()
    corpus = base_corpus + extended_corpus + heldout_corpus
    tokenizer = BertTokenizerFast(
        vocab=str(MODEL.parent / "vocab.txt"),
        do_lower_case=bool(metadata.get("do_lower_case", False)),
    )
    session = ort.InferenceSession(MODEL, providers=["CPUExecutionProvider"])
    intent_correct = raw_intent_correct = raw_payload_correct = payload_correct = false_destructive = 0
    raw_false_destructive = 0
    by_intent = Counter()
    correct_by_intent = Counter()
    failures = []
    expected_intents = []
    actual_intents = []
    raw_intents = []
    confusion = defaultdict(Counter)
    batch_size = 8
    predictions = [
        prediction
        for start in range(0, len(corpus), batch_size)
        for prediction in predict_batch(session, tokenizer, metadata, corpus[start:start + batch_size])
    ]
    for case, (intent, confidence, slots, raw_intent) in zip(corpus, predictions):
        expected_intent = case["intent"]
        expected_intents.append(expected_intent)
        actual_intents.append(intent)
        raw_intents.append(raw_intent)
        confusion[expected_intent][intent] += 1
        raw_intent_correct += int(raw_intent == expected_intent)
        if (
            raw_intent in {"delete_reminder", "delete_folder"} and
            raw_intent != expected_intent and
            allowed(raw_intent, case["scope"])
        ):
            raw_false_destructive += 1
        by_intent[expected_intent] += 1
        if intent == expected_intent:
            intent_correct += 1
            correct_by_intent[expected_intent] += 1
        if intent in {"delete_reminder", "delete_folder"} and intent != expected_intent:
            false_destructive += 1
        expected_payload = case.get("text") or case.get("target") or case.get("folder")
        raw_actual_payload = {
            "create_reminder": slots.get("TEXT"),
            "create_note": slots.get("TEXT"),
            "delete_reminder": slots.get("TARGET"),
            "create_folder": slots.get("FOLDER"),
            "delete_folder": slots.get("FOLDER"),
        }.get(intent)
        actual_payload = mapped_payload(intent, case["utterance"], slots)
        raw_payload_ok = expected_payload is None or normalized(expected_payload) == normalized(raw_actual_payload)
        payload_ok = expected_payload is None or normalized(expected_payload) == normalized(actual_payload)
        raw_payload_correct += int(intent == expected_intent and raw_payload_ok)
        payload_correct += int(intent == expected_intent and payload_ok)
        if intent != expected_intent or not payload_ok:
            failures.append({
                "id": case["id"], "text": case["utterance"], "expected": expected_intent,
                "actual": intent, "raw_intent": raw_intent, "confidence": round(confidence, 4), "expected_payload": expected_payload,
                "actual_payload": actual_payload, "raw_actual_payload": raw_actual_payload, "slots": slots,
            })
    labels = metadata["intent_labels"]
    intent_macro_f1, per_intent_metrics = macro_f1(expected_intents, actual_intents, labels)
    quality_gate_passed = (
        false_destructive == 0 and
        intent_correct / len(corpus) >= 0.97 and
        payload_correct / len(corpus) >= 0.95
    )
    report = {
        "total": len(corpus),
        "base_total": len(base_corpus),
        "extended_total": len(extended_corpus),
        "heldout_total": len(heldout_corpus),
        "intent_correct": intent_correct,
        "raw_intent_correct": raw_intent_correct,
        "payload_correct": payload_correct,
        "raw_payload_correct": raw_payload_correct,
        "false_destructive": false_destructive,
        "raw_false_destructive": raw_false_destructive,
        "intent_accuracy": intent_correct / len(corpus),
        "raw_intent_accuracy": raw_intent_correct / len(corpus),
        "intent_macro_f1": intent_macro_f1,
        "payload_accuracy": payload_correct / len(corpus),
        "raw_payload_accuracy": raw_payload_correct / len(corpus),
        "entity_exact_match": payload_correct / len(corpus),
        "per_intent_metrics": per_intent_metrics,
        "confusion_matrix": {
            expected: {actual: confusion[expected][actual] for actual in labels}
            for expected in labels
        },
        "quality_gate": {"intent_accuracy": 0.97, "entity_exact_match": 0.95, "false_destructive": 0},
        "quality_gate_passed": quality_gate_passed,
        "by_intent": {key: f"{correct_by_intent[key]}/{value}" for key, value in sorted(by_intent.items())},
        "failures": failures,
    }
    output = ROOT / "tools/nlu/output/corpus_report.json"
    output.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps({key: value for key, value in report.items() if key != "failures"}, ensure_ascii=False, indent=2))
    print(f"failures={len(failures)} report={output}")
    regression_failures = []
    for text, expected_intent, expected_payload in REGRESSION_CASES:
        intent, confidence, slots, _ = predict(session, tokenizer, metadata, text, "assistant")
        payload = slots.get("TEXT") if intent == "create_reminder" else slots.get("TARGET")
        print(f"regression: {text} => {intent} {confidence:.4f} {payload!r}")
        if intent != expected_intent or normalized(payload) != normalized(expected_payload):
            regression_failures.append(text)
    if regression_failures:
        raise SystemExit(f"NLU regressions failed: {regression_failures}")
    if "--require-quality-gate" in __import__("sys").argv and not quality_gate_passed:
        raise SystemExit("NLU quality gate failed; model was evaluated but must not be accepted")


if __name__ == "__main__":
    main()
