from __future__ import annotations


def build_blind_corpus_v2() -> list[dict[str, object]]:
    """Second blind corpus. This module must never be imported by dataset.py."""
    cases: list[dict[str, object]] = []

    def add(group: str, intent: str, utterance: str, scope: str = "assistant", **payload: object) -> None:
        cases.append({
            "id": f"blind2-{group}-{len(cases) + 1:03d}",
            "scope": scope,
            "utterance": utterance,
            "intent": intent,
            **payload,
        })

    reminder_tasks = [
        "сдать показания электросчётчика", "забрать результаты МРТ", "продлить читательский билет",
        "заказать пропуск для гостя", "отнести куртку в ателье", "проверить давление в колёсах",
        "вернуть инструменты соседу", "пополнить транспортную карту", "сменить воду в аквариуме",
        "подтвердить бронь гостиницы",
    ]
    reminder_patterns = [
        "Мне нельзя забыть {task}",
        "Наметь на завтра {task}",
        "Внеси в мои планы {task}",
        "Пусть будет задача {task} послезавтра",
        "Запиши мне в план на пятницу {task}",
        "Мне предстоит {task}, напомни об этом завтра",
        "Верни меня к задаче {task} через три часа",
    ]
    for pattern in reminder_patterns:
        for task in reminder_tasks:
            add("create-reminder", "create_reminder", pattern.format(task=task), text=task)

    periods = [
        "сегодня после обеда", "на грядущую неделю", "на ближайшую субботу",
        "до конца месяца", "на послезавтра",
    ]
    list_patterns = [
        "Озвучь, что запланировано {period}",
        "Сверь мои дела {period}",
        "Что стоит в моём расписании {period}",
        "Дай сводку по задачам {period}",
        "Что висит у меня в планах {period}",
        "Есть назначенные дела {period}",
        "Покажи мою загрузку {period}",
        "Какие обязательства назначены {period}",
        "Перечисли всё, что предстоит {period}",
    ]
    for pattern in list_patterns:
        for period in periods:
            add("list-reminders", "list_reminders", pattern.format(period=period))

    deletion_targets = [
        "сдать показания электросчётчика", "поездку за город", "бронь гостиницы",
        "продление читательского билета", "визит к ортодонту", "получение нового пропуска",
        "замену воды в аквариуме", "возврат инструментов соседу",
    ]
    deletion_patterns = [
        "Считай напоминание {target} отменённым",
        "Исключи из планов {target}",
        "Сотри напоминание {target}",
        "Отменить задачу {target}",
        "Я отказываюсь от напоминания {target}",
        "Удали пункт {target} из напоминаний",
        "Больше не держи в планах {target}",
        "Удали напоминание с формулировкой {target}",
    ]
    for pattern in deletion_patterns:
        for target in deletion_targets:
            add("delete-reminder", "delete_reminder", pattern.format(target=target), target=target)
    for utterance in [
        "Считай последнее напоминание отменённым",
        "Последний пункт планов больше не актуален",
        "Исключи последнее из моих напоминаний",
    ]:
        add("delete-last", "delete_reminder", utterance, targetLast=True)

    note_bodies = [
        "код сейфа 481903", "серия счётчика ЕК-7721", "контакт страхового агента 89021112233",
        "размер ниши 84 на 53 сантиметра", "идея сортировать документы по сроку",
        "номер читательского билета 006491", "рецепт маринада две ложки уксуса",
        "адрес мастерской Трактовая 17Б", "артикул подшипника BRG-6204",
        "мысль проверить доступность без сети",
    ]
    note_patterns = [
        "Занеси в записи {body}",
        "Оформи заметку: {body}",
        "Оставь себе запись {body}",
        "Сделай пометку {body}",
        "Хочу сохранить сведения: {body}",
        "Положи в заметки {body}",
        "Запиши дословно: {body}",
    ]
    for pattern in note_patterns:
        for body in note_bodies:
            add("create-note", "create_note", pattern.format(body=body), text=body)

    folders = [
        "Документы на квартиру", "Обслуживание велосипеда", "Семейные рецепты 2027",
        "Курсы повышения квалификации", "Планы благоустройства",
    ]
    create_folder_patterns = [
        "Сформируй папку {folder}",
        "Добавь каталог {folder}",
        "Новая папка должна называться {folder}",
        "Заведи категорию {folder}",
        "Создай хранилище заметок {folder}",
        "Учреди раздел {folder}",
    ]
    delete_folder_patterns = [
        "Ликвидируй папку {folder}",
        "Сотри каталог {folder}",
        "Раздел {folder} пора удалить",
        "Закрой каталог {folder}",
        "Удали категорию {folder}",
        "Папка {folder} утратила актуальность",
    ]
    for pattern in create_folder_patterns:
        for folder in folders:
            add("create-folder", "create_folder", pattern.format(folder=folder), folder=folder)
    for pattern in delete_folder_patterns:
        for folder in folders:
            add("delete-folder", "delete_folder", pattern.format(folder=folder), folder=folder)

    unrelated_tasks = [
        "сдать показания электросчётчика", "забрать результаты МРТ", "заказать гостевой пропуск",
        "вернуть инструменты", "пополнить транспортную карту", "сменить воду в аквариуме",
        "подтвердить бронь", "продлить читательский билет",
    ]
    unknown_patterns = [
        "Я сомневаюсь, надо ли {task}",
        "Подскажи, как правильно {task}",
        "Вчера я успел {task}",
        "Кому поручить {task}",
        "Я отказался {task}",
        "Насколько срочно нужно {task}",
        "Обсудим, зачем {task}",
        "Не уверен, что хочу {task}",
    ]
    for pattern in unknown_patterns:
        for task in unrelated_tasks:
            add("unknown", "unknown", pattern.format(task=task))
    for utterance in [
        "Удаляй", "Отмена операции", "Не сохраняй это", "Покажи содержимое папки",
        "Очисти все заметки", "Исправь текст последней заметки", "Передвинь задачу на вторник",
        "Экспортируй мои данные", "Восстанови резервную копию", "Позвони в мастерскую",
        "Где находится ближайший банкомат", "Прочитай последнее сообщение",
        "Сравни две заметки", "Объедини папки", "Расскажи о моих расходах",
    ]:
        add("unknown-fixed", "unknown", utterance)

    # Scope conflicts: the semantic classifier may recognize these, but the
    # application must reject them on a section-specific tab.
    add("scope", "unknown", "Сформируй папку Договоры аренды", scope="reminders")
    add("scope", "unknown", "Сотри напоминание продлить абонемент", scope="notes")
    add("scope", "unknown", "Занеси в записи номер парковочного места 318", scope="reminders")

    return cases
