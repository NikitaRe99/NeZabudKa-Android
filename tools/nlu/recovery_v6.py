"""Corrective training data. The user's October 11 trial is now SEEN regression data.

No evaluation corpus is imported here. Unpunctuated speech is a first-class surface.
"""
from __future__ import annotations
import json
import re
from pathlib import Path
from dataset import Example, marked


def speech_surface(example: Example) -> Example:
    # Keep an index map so removing quotes/separators never invalidates labels.
    chars = []
    positions = [0]
    for ch in example.text:
        chars.append(" " if ch in ',.:;!?—–-«»"“”' else ch)
        positions.append(len(chars))
    raw = "".join(chars)
    boundaries = [0] * (len(raw) + 1)
    clean = ""
    for index, ch in enumerate(raw):
        boundaries[index] = len(clean)
        if not ch.isspace() or (clean and not clean.endswith(" ")):
            clean += ch
    boundaries[len(raw)] = len(clean)
    spans = tuple((boundaries[a], boundaries[b], s) for a, b, s in example.spans)
    return Example(clean.rstrip(), example.intent, tuple((a, min(b, len(clean.rstrip())), s) for a, b, s in spans))


def build_recovery_examples() -> list[Example]:
    values = set()
    folders = ["Работа", "Личные проекты", "Материалы ремонта", "Планы поездки", "Садовые записи", "Работа добавь"]
    notes = [
        "запасной кабель лежит в синей сумке", "нижний ящик закрывается двумя защёлками",
        "длина крепёжного винта двадцать пять миллиметров", "номер договора записан на обороте",
        "ключ от мастерской хранится у охранника", "сообщение от меня передай завтра",
        "для сборки понадобятся шайбы и две гайки", "на верхней полке лежат белые конверты",
    ]
    tasks = ["проверить крепления двери", "записать показания счётчика", "убрать коробки в шкаф", "купить бумагу для принтера", "полить рассаду", "отправить копию договора"]
    for folder in folders:
        for prefix in ["заведи отдельную папку под названием", "заведи папку с названием", "выдели под мои записи папку", "создай новую папку", "добавь папку", "организуй папку для заметок"]:
            values.add(marked("create_folder", prefix, (folder, "FOLDER")))
        for prefix in ["давай уберём папку", "сотри папку", "удали папку", "убери папку", "мне больше не нужна папка"]:
            for suffix in ["", "она была нужна только для проверки", "она больше не нужна"]:
                values.add(marked("delete_folder", prefix, (folder, "FOLDER"), suffix))
        for body in notes:
            for action in ["сохрани следующую мысль", "сохрани запись", "добавь заметку", "запиши"]:
                values.add(marked("create_note", "для папки", (folder, "FOLDER"), action, (body, "TEXT")))
                values.add(marked("create_note", "в папку", (folder, "FOLDER"), action, (body, "TEXT")))
            for action in ["закинь в", "сохрани в", "добавь в", "помести в"]:
                values.add(marked("create_note", action, (folder, "FOLDER"), "запись о том что", (body, "TEXT")))
    for body in notes:
        for prefix in ["хочу оставить себе такую запись", "внеси в мои заметки", "сохрани следующую мысль", "зафиксируй в заметках", "добавь запись о том что", "запомни", "запиши сообщение"]:
            values.add(marked("create_note", prefix, (body, "TEXT")))
    dates = ["завтра", "послезавтра", "в следующий вторник", "через три часа", "через сорок пять минут"]
    for body in tasks:
        for date in dates:
            for before, after in [
                ("пусть приложение предупредит меня", "что пора"),
                ("хочу получить напоминание", ""),
                ("верни эту задачу в поле зрения", ""),
                ("сообщи мне", "что пора"),
                ("хочу чтобы ты напомнил", ""),
                ("напомни", ""),
            ]:
                values.add(marked("create_reminder", before, (date, "DATE"), after, (body, "TEXT")))
        for hour in ["десять", "двадцать один", "двадцать два"]:
            for minute in ["двадцать пять", "тридцать пять", "сорок два", "ноль пять"]:
                values.add(marked("create_reminder", "напомни", ("завтра", "DATE"), (f"в {hour} {minute}", "TIME"), (body, "TEXT")))
        for repeat in ["каждый день", "по будням", "каждую неделю", "каждый месяц", "каждый год", "каждые три дня", "каждые две недели", "по понедельникам и средам", "каждый вторник", "по субботам и воскресеньям"]:
            for time in ["в девять утра", "в двадцать один тридцать пять", "в 18:40"]:
                for prefix in ["напомни", "создай напоминание", "хочу получать напоминание", "предупреждай меня"]:
                    values.add(marked("create_reminder", prefix, (repeat, "RECURRENCE"), (time, "TIME"), (body, "TEXT")))
        for prefix in ["сотри из напоминаний пункт", "отмени напоминание", "убери из списка напоминаний", "удали задачу"]:
            values.add(marked("delete_reminder", prefix, (body, "TARGET")))
    for period in ["завтра", "послезавтра", "ближайшие три дня", "ближайшие пять дней", "в следующий вторник"]:
        for prefix in ["покажи мне чем заняты мои", "собери в один список напоминания которые должны сработать", "есть у меня на", "перечисли напоминания на", "что мне предстоит", "какие задачи назначены на"]:
            values.add(marked("list_reminders", prefix, (period, "DATE")))
        values.add(marked("list_reminders", "есть у меня на", (period, "DATE"), "что нибудь незавершённое"))
    for body in tasks + folders:
        for prefix in ["не создавай напоминание про", "отмени удаление папки", "отмени создание напоминания", "переименуй папку", "перекинь заметку", "перемести заметку", "расскажи про", "почему нужно"]:
            values.add(marked("unknown", prefix, body))
    values.update(marked("unknown", text) for text in [
        "перекинь заметку с чертежом из одной папки в другую",
        "перенеси заметку в другую папку", "объясни чем заметка отличается от напоминания",
        "не добавляй эту заметку", "не удаляй папку Работа", "отмени удаление напоминания",
        "отмени создание папки", "не сохраняй запись", "от меня пришло сообщение",
    ])
    # Include the now observed trial with explicit provenance; don't mislabel it as blind.
    trial = json.loads((Path(__file__).resolve().parents[2] / "docs/nlu/manual-unseen-examples-2026-10-11.json").read_text(encoding="utf-8"))
    intents = {"folder": "create_folder", "note": "create_note", "reminder": "create_reminder", "list": "list_reminders", "delete_reminder": "delete_reminder", "delete_folder": "delete_folder", "unknown": "unknown"}
    by_id = {1: "create_folder", 2: "create_folder", 3: "create_note", 4: "create_note", 5: "create_note", 6: "create_note", 7: "create_reminder", 8: "create_reminder", 9: "create_reminder", 10: "create_reminder", 11: "create_reminder", 12: "list_reminders", 13: "list_reminders", 14: "list_reminders", 15: "delete_reminder", 16: "delete_folder"}
    for case in trial["cases"]:
        intent = by_id.get(case["id"], "unknown")
        text = case["utterance"]
        spans = []
        payload = case.get("payload")
        if payload and intent != "unknown":
            start = text.casefold().find(payload.casefold())
            if start >= 0:
                slot = "FOLDER" if intent.endswith("folder") else "TARGET" if intent == "delete_reminder" else "TEXT"
                spans.append((start, start + len(payload), slot))
        if case["id"] in (3, 4):
            folder = "Тестовый блокнот"
            a = text.index(folder)
            spans.append((a, a + len(folder), "FOLDER"))
        if intent in ("create_reminder", "list_reminders"):
            for m in re.finditer(r"послезавтра|завтра|в следующий вторник|через сорок пять минут|через три часа|ближайшие три дня", text, re.I):
                spans.append((m.start(), m.end(), "DATE"))
            for m in re.finditer(r"в \d{1,2}:\d{2}|утром", text, re.I):
                spans.append((m.start(), m.end(), "TIME"))
        values.add(Example(text, intent, tuple(sorted(spans))))
    values.update(speech_surface(e) for e in list(values))
    values.update(Example(e.text[:1].upper() + e.text[1:], e.intent, e.spans) for e in list(values))
    return sorted(values, key=lambda e: (e.intent, e.text, e.spans))
