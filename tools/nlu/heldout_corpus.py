from __future__ import annotations


def build_heldout_corpus() -> list[dict[str, object]]:
    """Deterministic evaluation phrases that are never imported by training."""
    cases: list[dict[str, object]] = []

    def add(prefix: str, intent: str, utterance: str, **payload: object) -> None:
        cases.append({
            "id": f"held-{prefix}-{len(cases) + 1:03d}",
            "scope": "assistant",
            "utterance": utterance,
            "intent": intent,
            **payload,
        })

    reminder_tasks = [
        "проверить давление", "заменить батарейки в датчике", "забрать обувь из ремонта",
        "передать документы бухгалтеру", "заказать корм собаке", "позвонить участковому",
        "отключить автоплатёж", "получить результаты обследования",
    ]
    reminder_patterns = [
        "Хорошо бы не забыть {task}",
        "Проследи чтобы я не забыл {task}",
        "Мне обязательно надо не забыть {task}",
        "Оставь мне напоминание {task}",
        "Маякни мне чтобы {task}",
        "Напомни пожалуйста {task}",
        "Поставь мне напоминание {task}",
        "Запланируй напоминание {task}",
        "Завтра утром мне надо {task}",
        "Через сорок минут мне нужно {task}",
        "В пятницу вечером не забыть {task}",
        "Послезавтра обязательно {task}",
    ]
    for pattern in reminder_patterns:
        for task in reminder_tasks:
            add("cr", "create_reminder", pattern.format(task=task), text=task)

    list_periods = ["сегодня", "завтра", "на выходных", "в понедельник", "в конце месяца", "на следующей неделе"]
    list_patterns = [
        "Расскажи о моих планах {period}",
        "Что мне предстоит {period}",
        "Проверь расписание {period}",
        "Есть что-нибудь запланированное {period}",
        "Какие задачи у меня стоят {period}",
        "Чем предстоит заняться {period}",
        "Покажи список дел {period}",
        "Что я собирался сделать {period}",
        "Напомни мои планы {period}",
        "Выведи запланированные дела {period}",
        "Есть ли напоминания {period}",
        "Какое у меня расписание {period}",
    ]
    for pattern in list_patterns:
        for period in list_periods:
            add("lr", "list_reminders", pattern.format(period=period))

    delete_targets = [
        "проверить давление", "поездку к родителям", "заказ корма", "звонок участковому",
        "получить результаты", "заменить батарейки", "встречу с курьером", "оплату страховки",
    ]
    delete_patterns = [
        "Отмени {target}",
        "Убери напоминание {target}",
        "Сними напоминание про {target}",
        "Мне больше не нужно напоминание {target}",
        "Напоминание {target} можно удалить",
        "Задачу {target} можно убрать",
        "Не напоминай больше про {target}",
        "Удали из планов {target}",
        "Откажись от напоминания {target}",
        "Убери из расписания {target}",
        "Можно отменить {target}",
        "Закрой напоминание {target}",
    ]
    for pattern in delete_patterns:
        for target in delete_targets:
            add("dr", "delete_reminder", pattern.format(target=target), target=target)
    for utterance in [
        "Отмени последнее", "Удали самую последнюю задачу", "Убери последнее из планов",
        "Последнее напоминание можно удалить", "Мне не нужно последнее напоминание",
    ]:
        add("dr-last", "delete_reminder", utterance, targetLast=True)

    note_bodies = [
        "код ворот 7391", "номер полиса 460027", "мысль сначала проверить питание",
        "рецепт соуса три ложки масла", "адрес склада Советская 41", "артикул детали ZX-904",
        "телефон диспетчера 88005553535", "идея добавить автономный поиск",
    ]
    note_patterns = [
        "Зафиксируй {body}",
        "Запомни пожалуйста {body}",
        "Мне важно сохранить {body}",
        "Оставь заметку {body}",
        "Сделай запись {body}",
        "Запиши себе {body}",
        "Сохрани без изменений {body}",
        "Нужно записать {body}",
        "Добавь в заметки {body}",
        "Пусть в заметках будет {body}",
        "Сохрани как заметку {body}",
        "Запомни что {body}",
    ]
    for pattern in note_patterns:
        for body in note_bodies:
            add("cn", "create_note", pattern.format(body=body), text=body)

    folders = ["Домашний ремонт", "Документы семьи", "Летняя поездка", "Идеи на будущее", "Медицина 2026", "Рабочие встречи"]
    create_folder_patterns = [
        "Организуй папку {folder}", "Заведи раздел {folder}", "Мне нужна папка {folder}",
        "Добавь новый раздел {folder}", "Сделай папку {folder}", "Создай каталог {folder}",
        "Хочу новую папку {folder}", "Пусть будет папка {folder}",
    ]
    delete_folder_patterns = [
        "Избавься от папки {folder}", "Закрой раздел {folder}", "Папка {folder} больше не нужна",
        "Раздел {folder} можно удалить", "Убери папку {folder}", "Удали каталог {folder}",
        "Мне не нужен раздел {folder}", "Можно убрать папку {folder}",
    ]
    for pattern in create_folder_patterns:
        for folder in folders:
            add("cf", "create_folder", pattern.format(folder=folder), folder=folder)
    for pattern in delete_folder_patterns:
        for folder in folders:
            add("df", "delete_folder", pattern.format(folder=folder), folder=folder)

    unknown_actions = [
        "проверить давление", "выбросить мусор", "позвонить врачу", "забрать документы",
        "купить продукты", "отправить отчёт", "проверить почту", "полить цветы",
    ]
    unknown_patterns = [
        "Я уже успел {task}",
        "Почему мне надо {task}",
        "Как лучше {task}",
        "Не хочу {task}",
        "Объясни как {task}",
        "Стоит ли сегодня {task}",
        "Кто должен {task}",
        "Я передумал {task}",
        "Расскажи зачем {task}",
        "Можно ли не {task}",
    ]
    for pattern in unknown_patterns:
        for task in unknown_actions:
            add("un", "unknown", pattern.format(task=task))
    for utterance in [
        "Покажи заметки", "Удали заметку про полис", "Переименуй папку Архив",
        "Измени последнее напоминание", "Перенеси встречу на завтра", "Открой папку Работа",
        "Сколько времени", "Включи музыку", "Найди аптеку", "Позвони маме",
        "Напиши сообщение", "Какая погода завтра", "Расскажи новости", "Открой календарь",
        "Сохрани", "Создай", "Удали всё", "Отмени", "Напомни", "Запиши",
    ]:
        add("un-fixed", "unknown", utterance)

    return cases
