from __future__ import annotations


def build_blind_corpus_v3() -> list[dict[str, object]]:
    """Third frozen corpus. It is not imported by any training module."""
    cases: list[dict[str, object]] = []

    def add(group: str, intent: str, utterance: str, scope: str = "assistant", **payload: object) -> None:
        cases.append({
            "id": f"blind3-{group}-{len(cases) + 1:03d}",
            "scope": scope,
            "utterance": utterance,
            "intent": intent,
            **payload,
        })

    reminder_tasks = [
        "записать ребёнка в бассейн", "забрать заказ из пункта выдачи", "проверить срок действия карты",
        "передать ключи управляющему", "заказать поверку водомера", "отправить оригиналы договора",
        "внести оплату за домофон", "подготовить лекарства в дорогу",
    ]
    reminder_patterns = [
        "Поставь дело {task} на контроль до завтра",
        "Мне понадобится {task}; напомни ближе к вечеру",
        "Запланируй для меня действие: {task}",
        "Добавь задачу на понедельник — {task}",
        "Не позволь упустить: {task}",
        "К утру надо будет {task}",
    ]
    for pattern in reminder_patterns:
        for task in reminder_tasks:
            add("create-reminder", "create_reminder", pattern.format(task=task), text=task)

    periods = ["на текущий день", "на ближайшие три дня", "к воскресенью", "до середины месяца", "на будущий месяц"]
    list_patterns = [
        "Собери перечень моих задач {period}",
        "Что числится назначенным {period}",
        "Проведи обзор расписания {period}",
        "Какие пункты ожидают выполнения {period}",
        "Покажи календарные обязательства {period}",
        "Дай перечень будущих дел {period}",
        "Что осталось по плану {period}",
        "Чем заполнен мой график {period}",
    ]
    for pattern in list_patterns:
        for period in periods:
            add("list-reminders", "list_reminders", pattern.format(period=period))

    deletion_targets = [
        "записать ребёнка в бассейн", "получение посылки в терминале", "поверку водомера",
        "оплату домофона", "передачу ключей управляющему", "отправку оригиналов договора",
        "подготовку лекарств", "продление банковской карты",
    ]
    deletion_patterns = [
        "Аннулируй напоминание {target}",
        "Вычеркни из расписания {target}",
        "Сними с контроля задачу {target}",
        "Пометь напоминание {target} как отменённое",
        "Это напоминание больше не действует: {target}",
        "Удали из перечня задач {target}",
        "Откажись от задачи {target}",
    ]
    for pattern in deletion_patterns:
        for target in deletion_targets:
            add("delete-reminder", "delete_reminder", pattern.format(target=target), target=target)
    add("delete-last", "delete_reminder", "Аннулируй самый свежий пункт напоминаний", targetLast=True)
    add("delete-last", "delete_reminder", "Последнюю задачу следует снять с контроля", targetLast=True)

    note_bodies = [
        "идентификатор клиента 74-991-AB", "точные размеры стола 138 на 72",
        "контакт экскурсовода плюс семь 914 555 08 44", "номер заявления МФЦ 203817",
        "идея хранить чеки по гарантийному сроку", "состав смеси пять частей воды одна часть средства",
        "адрес сервисного центра Байкальская 202", "модель аккумулятора INR21700-50E",
    ]
    note_patterns = [
        "Внеси сведения в память приложения: {body}",
        "Составь запись со словами {body}",
        "Создай памятку: {body}",
        "Зафиксируй факт — {body}",
        "Добавь текстовую запись {body}",
        "Сохрани данные без сокращений: {body}",
    ]
    for pattern in note_patterns:
        for body in note_bodies:
            add("create-note", "create_note", pattern.format(body=body), text=body)

    folders = [
        "Гарантии на технику", "Документы для школы", "Обслуживание дома 2028",
        "Маршруты выходного дня", "Материалы по страхованию",
    ]
    create_folder_patterns = [
        "Подготовь папку {folder}",
        "Создай секцию {folder}",
        "Добавь подборку {folder}",
        "Собери каталог {folder}",
        "Выдели раздел {folder}",
    ]
    delete_folder_patterns = [
        "Расформируй папку {folder}",
        "Удалить секцию {folder}",
        "Закрой подборку {folder}",
        "Исключи каталог {folder}",
        "Раздел {folder} следует удалить",
    ]
    for pattern in create_folder_patterns:
        for folder in folders:
            add("create-folder", "create_folder", pattern.format(folder=folder), folder=folder)
    for pattern in delete_folder_patterns:
        for folder in folders:
            add("delete-folder", "delete_folder", pattern.format(folder=folder), folder=folder)

    unrelated_tasks = [
        "записать ребёнка в бассейн", "забрать заказ из терминала", "проверить банковскую карту",
        "передать ключи управляющему", "заказать поверку водомера", "оплатить домофон",
        "подготовить лекарства",
    ]
    unknown_patterns = [
        "Я только что закончил {task}",
        "Мне объяснили, каким способом {task}",
        "Кто сможет вместо меня {task}",
        "Есть ли смысл {task}",
        "Я пока не решил, буду ли {task}",
        "Оцени, насколько важно {task}",
        "Почему все советуют {task}",
    ]
    for pattern in unknown_patterns:
        for task in unrelated_tasks:
            add("unknown", "unknown", pattern.format(task=task))
    for utterance in [
        "Аннулируй", "Вычеркни это", "Закрой текущий экран", "Покажи архив заметок",
        "Сотри историю поиска", "Отредактируй последнюю запись", "Сдвинь встречу на час",
        "Синхронизируй папки", "Отправь резервную копию", "Набери номер управляющего",
        "Как проехать к сервисному центру", "Открой банковское приложение",
    ]:
        add("unknown-fixed", "unknown", utterance)

    add("scope", "unknown", "Подготовь папку Школьные документы", scope="reminders")
    add("scope", "unknown", "Аннулируй напоминание продлить карту", scope="notes")

    return cases
