from __future__ import annotations


def build_blind_corpus_v5() -> list[dict[str, object]]:
    """Fifth frozen corpus. Never import this module from training code."""
    cases: list[dict[str, object]] = []

    def add(group: str, intent: str, utterance: str, scope: str = "assistant", **payload: object) -> None:
        cases.append({
            "id": f"blind5-{group}-{len(cases) + 1:03d}",
            "scope": scope,
            "utterance": utterance,
            "intent": intent,
            **payload,
        })

    tasks = [
        "получить акт сверки у поставщика", "проверить датчик протечки под ванной",
        "забрать заказанные шторы из салона", "передать копию паспорта нотариусу",
        "записать заводской номер стабилизатора", "продлить абонемент на парковку",
        "заказать профилактику газовой плиты", "сверить расход воды по квитанции",
    ]
    for pattern in [
        "Оставь контрольную точку: {task}", "Мне потребуется вовремя {task}",
        "Пусть в календаре появится действие {task}", "Подай мне сигнал перед тем, как {task}",
        "Возьми на заметку как будущее дело — {task}", "К завтрашнему дню важно {task}",
    ]:
        for task in tasks:
            add("create-reminder", "create_reminder", pattern.format(task=task), text=task)

    periods = ["до пятницы", "на следующие семь дней", "на конец месяца", "на сегодня"]
    for pattern in [
        "Выведи таблицу ожидающих действий {period}", "Что осталось выполнить {period}",
        "Как выглядит очередь задач {period}", "Сделай выжимку из календаря {period}",
        "Какие обязательства ещё открыты {period}", "Покажи перечень контрольных точек {period}",
        "Чем занят мой график {period}", "Что назначено на исполнение {period}",
    ]:
        for period in periods:
            add("list-reminders", "list_reminders", pattern.format(period=period))

    targets = [
        "получение акта сверки", "проверку датчика протечки", "поездку за шторами",
        "передачу паспорта нотариусу", "запись номера стабилизатора", "продление парковки",
        "профилактику газовой плиты", "проверку расхода воды",
    ]
    for pattern in [
        "Останови контроль задачи {target}", "Удали из календарной очереди {target}",
        "Переведи напоминание {target} в отменённые", "Не отслеживай больше действие {target}",
        "Сними отметку о будущем деле {target}",
    ]:
        for target in targets:
            add("delete-reminder", "delete_reminder", pattern.format(target=target), target=target)
    add("delete-last", "delete_reminder", "Останови контроль наиболее свежей задачи", targetLast=True)
    add("delete-last", "delete_reminder", "Самое недавнее дело переведи в отменённые", targetLast=True)

    bodies = [
        "номер договора поставщика SP-48019", "значение датчика влажности 47.8 процента",
        "контакт салона штор 89046661221", "реестровый номер нотариуса 38/114-н",
        "модель стабилизатора ACH-12000/1", "номер парковочного абонемента PK:01841",
        "адрес газовой службы Коммунальная 9", "идея ежемесячно фотографировать квитанции",
    ]
    for pattern in [
        "Сложи в справочные данные: {body}", "Оформи карточку с содержимым {body}",
        "Оставь неизменённую строку — {body}", "Запиши в личную памятку {body}",
        "Добавь в информационный архив: {body}",
    ]:
        for body in bodies:
            add("create-note", "create_note", pattern.format(body=body), text=body)

    folders = [
        "Квитанции водоснабжения", "Газовое оборудование", "Документы нотариуса",
        "Парковочные разрешения", "Датчики безопасности",
    ]
    for pattern in [
        "Заведи группу {folder}", "Создай тематическую полку {folder}",
        "Добавь рубрику записей {folder}", "Открой блок материалов {folder}",
    ]:
        for folder in folders:
            add("create-folder", "create_folder", pattern.format(folder=folder), folder=folder)
    for pattern in [
        "Удали группу {folder}", "Откажись от тематической полки {folder}",
        "Закрой рубрику записей {folder}", "Больше не используй блок материалов {folder}",
    ]:
        for folder in folders:
            add("delete-folder", "delete_folder", pattern.format(folder=folder), folder=folder)

    unrelated = [
        "получить акт сверки", "проверить датчик протечки", "забрать шторы",
        "передать паспорт нотариусу", "записать номер стабилизатора", "продлить парковку",
        "заказать профилактику плиты", "сверить расход воды",
    ]
    for pattern in [
        "Покажи инструкцию, как {task}", "Я закончил {task}", "Кого нанять, чтобы {task}",
        "Нужно ли специалисту {task}", "Я рассматриваю возможность {task}",
        "Сколько стоит {task}", "Какие документы нужны, чтобы {task}",
    ]:
        for task in unrelated:
            add("unknown", "unknown", pattern.format(task=task))
    for utterance in [
        "Останови воспроизведение", "Удали загруженный документ", "Закрой панель материалов",
        "Не отслеживай местоположение", "Покажи информационный архив", "Очисти форму заявления",
        "Измени группу контактов", "Отправь квитанцию поставщику", "Открой сайт нотариуса",
        "Сложи числа в таблице", "Сверь две фотографии счётчика", "Запиши видео с камеры",
    ]:
        add("unknown-fixed", "unknown", utterance)

    add("scope", "unknown", "Создай тематическую полку Квитанции", scope="reminders")
    add("scope", "unknown", "Останови контроль задачи оплатить воду", scope="notes")
    return cases
