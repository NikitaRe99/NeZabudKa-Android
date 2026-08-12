from __future__ import annotations

from dataclasses import dataclass
import random

from paraphrase_v3 import build_paraphrase_v3_specs
from paraphrase_v4 import build_paraphrase_v4_specs


INTENTS = [
    "create_reminder",
    "list_reminders",
    "delete_reminder",
    "create_note",
    "create_folder",
    "delete_folder",
    "unknown",
]
SLOT_TYPES = ["TEXT", "TARGET", "FOLDER", "DATE", "TIME", "RECURRENCE"]
SLOT_LABELS = ["O"] + [f"{prefix}-{slot}" for slot in SLOT_TYPES for prefix in ("B", "I")]


@dataclass(frozen=True)
class Example:
    text: str
    intent: str
    spans: tuple[tuple[int, int, str], ...] = ()


def marked(intent: str, *parts: str | tuple[str, str]) -> Example:
    text = ""
    spans: list[tuple[int, int, str]] = []
    for part in parts:
        value, slot = part if isinstance(part, tuple) else (part, None)
        if not value:
            continue
        if text and value not in ".,!?;:":
            text += " "
        start = len(text)
        text += value
        if slot is not None:
            spans.append((start, len(text), slot))
    return Example(text=text, intent=intent, spans=tuple(spans))


def build_training_examples(seed: int = 20260808, per_intent: int = 800, unknown_count: int = 850) -> list[Example]:
    rng = random.Random(seed)
    values: set[Example] = set()
    tasks = [
        "позвонить врачу", "купить молоко", "забрать посылку", "выключить духовку",
        "отправить отчёт", "оплатить интернет", "проверить почту", "принять лекарство",
        "передать показания", "забрать документы", "позвонить родителям", "полить цветы",
        "проверить страховку", "заехать в аптеку", "получить справку", "вынести мусор",
        "проверить резервную копию", "купить билеты", "написать мастеру", "вернуть книгу",
        "позвонить Свете", "записаться к стоматологу", "зарядить наушники", "оплатить квартиру",
        "проверить счётчик", "отнести ключи соседу", "забрать заказ", "подготовить презентацию",
        "проверить документы", "заказать воду", "встретить курьера", "ответить клиенту",
        "обновить приложение", "сделать резервную копию", "купить корм коту", "проверить расписание",
        "поменять фильтр", "поздравить маму", "отправить заявление", "проверить результаты анализов",
        "встреча с директором", "совещание", "звонок мастеру", "визит в банк",
        "выбросить мусор", "выкинуть мусор", "убрать мусор",
        "проверить давление", "проветрить комнату", "записать показания воды", "закрыть окно",
        "проверить заряд телефона", "покормить рыбок", "забрать вещи из химчистки",
        "перезвонить заказчику", "отправить квитанцию", "проверить срок подписки",
    ]
    dates = [
        "завтра", "послезавтра", "сегодня вечером", "завтра утром", "в понедельник",
        "во вторник в девять", "в среду вечером", "в четверг днём", "в пятницу в 18 часов",
        "через пять минут", "через полчаса", "через два часа", "через три дня", "через неделю",
        "10 августа", "12 августа в 10", "20 августа вечером", "первого сентября утром",
        "на следующей неделе", "в конце месяца", "в середине следующего месяца",
        "в девять утра", "в 18:30", "в полдень", "через четыре дня в 15 часов",
        "в следующую пятницу в 16:00", "завтра вечером", "послезавтра вечером",
        "в следующую среду утром", "утром", "вечером", "днём",
    ]
    reminder_prefixes = [
        "напомни", "напомни мне", "создай напоминание", "добавь напоминание",
        "поставь напоминание", "не дай забыть", "мне нужно не забыть", "оставь напоминание",
        "запланируй", "напомню себе", "надо не забыть", "пожалуйста напомни", "маякни мне",
        "поставь мне напоминание", "оставь мне напоминание", "проследи чтобы я не забыл",
        "хорошо бы не забыть", "мне обязательно надо не забыть", "напомни пожалуйста",
    ]
    recurrences = [
        "каждый день", "ежедневно", "по будням", "каждую неделю", "раз в неделю",
        "каждый понедельник", "каждый месяц", "ежегодно", "каждые три дня",
    ]
    # These constructions are semantically positive reminders. Without explicit
    # contrastive examples a tiny encoder may overfit to words such as "убрать"
    # and confuse them with deletion targets.
    positive_reminder_starts = [
        "надо не забыть", "нужно не забыть", "мне надо не забыть",
        "мне нужно не забыть", "главное не забыть", "не дай мне забыть",
        "хорошо бы не забыть", "мне обязательно надо не забыть", "проследи чтобы я не забыл",
    ]
    positive_reminder_tasks = [
        "выбросить мусор", "выкинуть мусор", "вынести мусор", "убрать мусор",
        "позвонить врачу", "купить молоко", "оплатить интернет", "забрать посылку",
    ]
    positive_reminder_examples = {
        marked("create_reminder", prefix, (task, "TEXT"))
        for prefix in positive_reminder_starts
        for task in positive_reminder_tasks
    }
    positive_reminder_examples.update(
        marked("create_reminder", prefix, (task, "TEXT"))
        for prefix in [
            "напомни", "напомни пожалуйста", "оставь напоминание", "оставь мне напоминание",
            "маякни мне", "не дай забыть", "поставь мне напоминание", "запланируй напоминание",
        ]
        for task in tasks
    )
    positive_reminder_examples.update(
        marked("create_reminder", "мне", (date, "DATE"), "надо", (task, "TEXT"))
        for date in ["завтра утром", "завтра вечером", "в пятницу", "через два часа"]
        for task in tasks[-12:]
    )
    positive_reminder_examples.update(
        marked("create_reminder", (date, "DATE"), prefix, (task, "TEXT"))
        for date in ["завтра утром", "послезавтра", "через сорок минут", "в пятницу вечером"]
        for prefix in ["мне нужно", "мне надо", "обязательно", "не забыть"]
        for task in tasks[-16:]
    )
    positive_reminder_examples.update(
        marked("create_reminder", prefix, (task, "TEXT"), (date, "DATE"))
        for prefix in ["создай напоминание", "добавь напоминание", "поставь напоминание", "напомни"]
        for date in ["завтра в 18:30", "через неделю в 8 утра", "20 августа в 18 часов", "28 августа"]
        for task in tasks[-16:]
    )
    positive_reminder_examples.update(
        marked("create_reminder", "запланируй на", (date, "DATE"), (task, "TEXT"))
        for date in ["12 августа в 10", "25 августа в 11", "следующую пятницу в 16:00"]
        for task in tasks[-16:]
    )
    values.update(positive_reminder_examples)
    while sum(item.intent == "create_reminder" for item in values) < max(per_intent, len(positive_reminder_examples)):
        prefix, date, task, pattern = rng.choice(reminder_prefixes), rng.choice(dates), rng.choice(tasks), rng.randrange(4)
        if rng.random() < 0.18:
            item = marked("create_reminder", prefix, (task, "TEXT"))
        elif pattern == 0:
            item = marked("create_reminder", prefix, (date, "DATE"), (task, "TEXT"))
        elif pattern == 1:
            item = marked("create_reminder", (date, "DATE"), (task, "TEXT"))
        elif pattern == 2:
            item = marked("create_reminder", prefix, (task, "TEXT"), (date, "DATE"))
        else:
            item = marked("create_reminder", prefix, (rng.choice(recurrences), "RECURRENCE"), (date, "DATE"), (task, "TEXT"))
        values.add(item)

    list_prefixes = [
        "покажи напоминания", "покажи мои планы", "что у меня запланировано", "какие у меня планы",
        "перечисли напоминания", "выведи расписание", "что мне нужно сделать", "чем я занят",
        "есть ли у меня планы", "покажи все напоминания", "какие дела запланированы",
        "что предстоит сделать", "что нужно успеть", "есть планы", "выведи всё запланированное",
        "какие задачи стоят", "напомни какие у меня планы",
        "расскажи о моих планах", "проверь расписание", "покажи список дел",
        "что я собирался сделать", "выведи запланированные дела", "есть ли напоминания",
        "какое у меня расписание", "чем предстоит заняться",
    ]
    list_dates = dates + ["в начале месяца", "в конце августа", "на 28 число", "на сегодня", "на завтра"]
    fixed_list_examples = {
        marked("list_reminders", prefix, (date, "DATE"))
        for prefix in ["что у меня", "покажи мои напоминания", "покажи дела", "что у меня намечено"]
        for date in list_dates[-10:]
    }
    fixed_list_examples.update(
        marked("list_reminders", prefix, (date, "DATE"))
        for prefix in [
            "проверь расписание", "покажи список дел", "что я собирался сделать",
            "расскажи о моих планах", "выведи запланированные дела", "какое у меня расписание",
        ]
        for date in ["сегодня", "завтра", "в понедельник", "в конце месяца", "на следующей неделе"]
    )
    values.update(fixed_list_examples)
    while sum(item.intent == "list_reminders" for item in values) < max(per_intent, len(fixed_list_examples)):
        prefix = rng.choice(list_prefixes)
        item = marked("list_reminders", prefix) if rng.random() < 0.28 else marked(
            "list_reminders", prefix, (rng.choice(list_dates), "DATE")
        )
        values.add(item)

    delete_prefixes = [
        "удали напоминание про", "убери напоминание про", "отмени напоминание",
        "удали задачу", "убери из планов", "мне больше не нужно напоминание",
        "можно удалить напоминание", "отмени", "удали напоминание с текстом",
        "отмени задачу про", "сними напоминание о", "откажись от напоминания",
        "больше не напоминай про", "удали из планов", "убери из расписания",
        "закрой напоминание", "не напоминай больше про", "можно отменить",
    ]
    targets = tasks + [
        "звонок врачу", "покупку билетов", "оплату интернета", "встречу с Анной",
        "визит в банк", "поездку", "справку", "резервную копию", "страховку",
    ]
    fixed_delete_examples = {
        marked("delete_reminder", prefix, (target, "TARGET"))
        for prefix in delete_prefixes
        for target in targets
    }
    fixed_delete_examples.update(
        marked("delete_reminder", "задачу", (target, "TARGET"), "можно убрать")
        for target in targets
    )
    fixed_delete_examples.update(
        marked("delete_reminder", phrase)
        for phrase in [
            "отмени последнее", "удали самое последнее", "убери последнее из списка",
            "последнее напоминание можно удалить", "мне не нужно последнее напоминание",
        ]
    )
    mandatory_delete_examples = {
        marked("delete_reminder", prefix, (target, "TARGET"))
        for prefix in ["удали", "убери", "отмени", "не напоминай больше про"]
        for target in targets
    }
    mandatory_delete_examples.update(
        marked("delete_reminder", phrase)
        for phrase in [
            "отмени последнее", "удали последнее", "убери последнее",
            "удали самое последнее", "убери последнее из списка",
        ]
    )
    mandatory_delete_examples.update(
        marked("delete_reminder", noun, (target, "TARGET"), suffix)
        for noun, suffix in [
            ("напоминание", "можно удалить"),
            ("задачу", "можно убрать"),
            ("напоминание", "больше не нужно"),
        ]
        for target in targets
    )
    mandatory_delete_examples.update(
        marked("delete_reminder", "мне больше не нужно напоминание", (target, "TARGET"))
        for target in targets
    )
    target_delete_count = max(per_intent, 700)
    optional_delete_examples = fixed_delete_examples - mandatory_delete_examples
    remaining_delete_count = max(0, target_delete_count - len(mandatory_delete_examples))
    fixed_delete_examples = mandatory_delete_examples | set(
        rng.sample(
            sorted(optional_delete_examples, key=lambda item: item.text),
            min(remaining_delete_count, len(optional_delete_examples)),
        )
    )
    values.update(fixed_delete_examples)
    while sum(item.intent == "delete_reminder" for item in values) < max(per_intent, len(fixed_delete_examples)):
        pattern = rng.randrange(6)
        if pattern == 0:
            item = marked("delete_reminder", rng.choice([
                "удали последнее напоминание", "отмени последнее напоминание",
                "убери моё последнее напоминание", "удали последнее", "убери последнее",
            ]))
        elif pattern == 1:
            item = marked("delete_reminder", rng.choice(["удали", "убери", "отмени"]), (rng.choice(targets), "TARGET"))
        elif pattern == 2:
            item = marked("delete_reminder", "напоминание", (rng.choice(targets), "TARGET"), "можно удалить")
        elif pattern == 3:
            item = marked("delete_reminder", rng.choice(["мне больше не нужно", "можно убрать"]), "напоминание", (rng.choice(targets), "TARGET"))
        else:
            item = marked("delete_reminder", rng.choice(delete_prefixes), (rng.choice(targets), "TARGET"))
        values.add(item)

    note_bodies = [
        "код домофона 357", "адрес Ленина 10", "номер договора 1245", "пароль от роутера qwerty",
        "телефон мастера 89140000000", "размер окна 120 на 140", "серийный номер AB-123",
        "идея для поездки на Байкал", "проверить договор", "купить фильтр для воды",
        "список вещей в дорогу", "два яйца и стакан молока", "почта test@example.com",
        "номер места 18А", "заменить лампу", "проверить сборку", "позвонить поставщику",
        "ссылка на документацию", "результаты измерений", "важная мысль о проекте",
        "название лекарства амоксициллин", "код заказа 90817", "размер обуви 42",
        "логин от кабинета user123", "модель фильтра A200", "номер автомобиля А123ВС",
        "мысль о новом проекте", "идея использовать офлайн режим", "рецепт блинов два яйца и молоко",
        "наблюдение о производительности", "план поездки на выходные", "результат разговора с врачом",
        "код калитки 2468", "номер талона 50731", "артикул QX-208", "телефон 88001234567",
        "мысль сначала проверить сеть", "рецепт супа две моркови и луковица",
    ]
    folders = [
        "Работа", "Поездки", "Дом", "Проект X", "Важные документы", "Рецепты",
        "Идеи приложений", "Архив 2026", "Личные записи", "Покупки", "Здоровье",
        "Старые проекты", "Тестовые заметки", "Учёба", "Автомобиль", "Финансы",
        "Домашний ремонт", "Документы семьи", "Летняя поездка", "Идеи на будущее",
        "Медицина 2026", "Рабочие встречи", "Книги и фильмы", "Гарантийные талоны",
    ]
    note_prefixes = [
        "запомни", "сохрани", "запиши", "создай заметку", "добавь заметку",
        "сохрани как заметку", "мне нужно сохранить", "мне важно запомнить", "зафиксируй",
        "запомни пожалуйста", "оставь заметку", "сделай запись", "запиши себе",
        "нужно записать", "добавь в заметки", "пусть в заметках будет", "запомни что",
    ]
    fixed_note_examples = {
        marked("create_note", "в папку", (folder, "FOLDER"), "добавь заметку", (body, "TEXT"))
        for folder in folders
        for body in note_bodies[-6:]
    }
    values.update(fixed_note_examples)
    while sum(item.intent == "create_note" for item in values) < max(per_intent, len(fixed_note_examples)):
        body = rng.choice(note_bodies)
        if rng.random() < 0.35:
            folder = rng.choice(folders)
            pattern = rng.randrange(3)
            if pattern == 0:
                item = marked("create_note", rng.choice(note_prefixes), "в папку", (folder, "FOLDER"), (body, "TEXT"))
            elif pattern == 1:
                item = marked("create_note", "добавь в папку", (folder, "FOLDER"), "заметку", (body, "TEXT"))
            else:
                item = marked("create_note", rng.choice(note_prefixes), (body, "TEXT"), "в папку", (folder, "FOLDER"))
        else:
            item = marked("create_note", rng.choice(note_prefixes), (body, "TEXT"))
        values.add(item)

    create_folder_prefixes = [
        "создай папку", "добавь папку", "создай раздел", "мне нужна папка",
        "хочу создать папку", "сделай папку", "организуй папку", "заведи папку",
        "создай новый раздел", "добавь новый раздел",
    ]
    polite = ["", "пожалуйста", "прошу", "мне нужно"]
    fixed_create_folders = {
        marked("create_folder", prefix, (folder, "FOLDER"))
        for prefix in create_folder_prefixes[-4:]
        for folder in folders
    }
    fixed_create_folders.update(
        marked("create_folder", prefix, (folder, "FOLDER"))
        for prefix in ["хочу новую папку", "пусть будет папка", "мне нужна папка", "организуй папку"]
        for folder in folders
    )
    values.update(fixed_create_folders)
    while sum(item.intent == "create_folder" for item in values) < max(per_intent, len(fixed_create_folders)):
        values.add(marked("create_folder", rng.choice(polite), rng.choice(create_folder_prefixes), (rng.choice(folders), "FOLDER")))

    delete_folder_prefixes = [
        "удали папку", "убери папку", "удали раздел", "можно удалить папку",
        "мне больше не нужна папка", "избавься от папки", "закрой папку", "мне не нужен раздел",
    ]
    fixed_delete_folders = {
        marked("delete_folder", prefix, (folder, "FOLDER"))
        for prefix in delete_folder_prefixes[-3:]
        for folder in folders
    }
    fixed_delete_folders.update(
        marked("delete_folder", "раздел", (folder, "FOLDER"), "можно убрать")
        for folder in folders
    )
    values.update(fixed_delete_folders)
    while sum(item.intent == "delete_folder" for item in values) < max(per_intent, len(fixed_delete_folders)):
        folder = rng.choice(folders)
        if rng.random() < 0.35:
            item = marked("delete_folder", "папка", (folder, "FOLDER"), rng.choice(["больше не нужна", "можно удалить", "мне не нужна"]))
        else:
            item = marked("delete_folder", rng.choice(polite), rng.choice(delete_folder_prefixes), (folder, "FOLDER"))
        values.add(item)

    unknown_fixed = [
        "какая сегодня погода", "расскажи анекдот", "как приготовить борщ", "позвони врачу",
        "открой настройки телефона", "сколько будет дважды два", "включи музыку", "переведи текст",
        "напиши письмо Ивану", "удалить всё", "измени последнее напоминание", "перенеси встречу",
        "покажи заметки", "удали заметку про пароль", "переименуй папку Работа", "доброе утро",
        "спасибо", "что ты умеешь", "мне нужно подумать о поездке", "врач сказал больше отдыхать",
        "создай", "сохрани", "напомни", "привет", "как дела", "который сейчас час",
        "найди ближайшую аптеку", "открой браузер", "поставь музыку", "прочитай новости",
        "убери заметку про пароль", "удали заметку с адресом", "покажи мои заметки",
        "переименуй заметку", "измени заметку про поездку", "удали последнюю заметку",
        "какая погода будет завтра", "какой прогноз на пятницу", "сколько времени в Москве",
        "который час в Иркутске", "измени время последнего напоминания",
        "перенеси встречу на пятницу", "перенеси напоминание на завтра",
        "переименуй папку Дом", "назови папку Работа иначе", "открой папку Документы",
        "я уже выбросил мусор", "почему мне надо выбросить мусор", "как лучше убрать мусор",
        "не хочу звонить врачу", "объясни как оплатить интернет", "стоит ли купить билеты",
        "я передумал забирать посылку", "кто должен отправить отчёт", "можно ли не поливать цветы",
        "отмени", "пожалуйста отмени", "можно отменить", "просто отмени", "удали", "пожалуйста удали",
        "убери", "просто убери", "сними напоминание", "удали напоминание", "отмени напоминание",
        "больше не напоминай", "не напоминай", "не надо напоминать", "удали задачу", "отмени задачу",
    ]
    fixed_unknown_examples = {marked("unknown", text) for text in unknown_fixed}
    values.update(fixed_unknown_examples)
    unknown_starts = [
        "расскажи про", "найди информацию о", "объясни", "что означает", "как работает",
        "почему", "можно ли", "открой", "позвони", "напиши", "покажи фотографию",
        "подскажи про", "прочитай про", "поищи", "помоги понять", "хочу узнать про",
    ]
    unknown_topics = [
        "погоду", "нейронные сети", "историю Иркутска", "новости", "курс валют",
        "рецепт пирога", "настройки телефона", "мою почту", "видео", "музыку",
        "дорогу домой", "результаты матча", "расписание автобусов", "этот экран",
        "правила парковки", "новый телефон", "адрес магазина", "время работы банка",
        "инструкцию к лекарству", "прогноз погоды", "последнее сообщение",
    ]
    bare_actions = tasks + ["встретиться с Анной", "сходить к врачу", "подумать о проекте"]
    while sum(item.intent == "unknown" for item in values) < max(unknown_count, len(fixed_unknown_examples)):
        choice = rng.randrange(4)
        if choice == 0:
            item = marked("unknown", rng.choice(unknown_starts), rng.choice(unknown_topics))
        elif choice == 1:
            item = marked("unknown", rng.choice(bare_actions))
        elif choice == 2:
            item = marked(
                "unknown",
                rng.choice(["хочу", "мне надо", "было бы хорошо", "думаю", "планирую", "собираюсь", "не уверен нужно ли"]),
                rng.choice(bare_actions),
            )
        else:
            item = marked("unknown", rng.choice(["удали заметку про", "покажи заметку про", "измени заметку про"]), rng.choice(tasks))
        values.add(item)

    values.update(
        marked(intent, *parts)
        for intent, parts in build_paraphrase_v3_specs()
    )
    values.update(
        marked(intent, *parts)
        for intent, parts in build_paraphrase_v4_specs()
    )

    # SpeechRecognizer and keyboards frequently capitalize the first word and add
    # terminal punctuation. ruBERT-tiny2 is cased, so these must be real training
    # variants rather than an assumption in post-processing.
    surface_variants: set[Example] = set()
    for item in values:
        capitalized = item.text[:1].upper() + item.text[1:]
        if capitalized != item.text:
            surface_variants.add(Example(capitalized, item.intent, item.spans))
        punctuation = "?" if item.intent == "list_reminders" else "."
        surface_variants.add(Example(capitalized + punctuation, item.intent, item.spans))
    values.update(surface_variants)

    result = list(values)
    rng.shuffle(result)
    return result
