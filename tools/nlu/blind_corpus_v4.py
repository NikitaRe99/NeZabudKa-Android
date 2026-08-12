from __future__ import annotations


def build_blind_corpus_v4() -> list[dict[str, object]]:
    """Fourth frozen corpus. Never import this module from training code."""
    cases: list[dict[str, object]] = []

    def add(group: str, intent: str, utterance: str, scope: str = "assistant", **payload: object) -> None:
        cases.append({
            "id": f"blind4-{group}-{len(cases) + 1:03d}",
            "scope": scope,
            "utterance": utterance,
            "intent": intent,
            **payload,
        })

    tasks = [
        "запросить выписку из лицевого счёта", "проверить крепление детского кресла",
        "забрать готовые линзы из оптики", "передать документы председателю",
        "сфотографировать серийный номер бойлера", "продлить доступ к электронной библиотеке",
        "заказать диагностику стиральной машины", "сверить начисления за отопление",
        "отправить заявление в управляющую компанию", "подготовить доверенность для курьера",
    ]
    reminder_patterns = [
        "Зарезервируй в памяти будущее дело: {task}",
        "Мне важно вернуться к действию {task} завтра",
        "Поставь отметку на будущее — {task}",
        "Когда наступит вечер, подтолкни меня {task}",
        "Сделай так, чтобы я вовремя {task}",
        "Включи в завтрашний график: {task}",
    ]
    for pattern in reminder_patterns:
        for task in tasks:
            add("create-reminder", "create_reminder", pattern.format(task=task), text=task)

    periods = ["на эту неделю", "к следующему вторнику", "до конца квартала", "на завтра", "на ближайшие выходные"]
    list_patterns = [
        "Отобрази очередь обязательств {period}", "Как распределены будущие задачи {period}",
        "Подведи итог по ожидающим делам {period}", "Что внесено в календарь {period}",
        "Покажи незавершённые пункты {period}", "Дай картину моей занятости {period}",
        "Какие действия стоят на очереди {period}", "Составь сводный перечень планов {period}",
    ]
    for pattern in list_patterns:
        for period in periods:
            add("list-reminders", "list_reminders", pattern.format(period=period))

    targets = [
        "получение выписки из лицевого счёта", "проверку детского кресла", "визит в оптику за линзами",
        "передачу документов председателю", "осмотр серийного номера бойлера",
        "продление электронной библиотеки", "диагностику стиральной машины",
        "проверку начислений за отопление", "заявление в управляющую компанию", "доверенность для курьера",
    ]
    delete_patterns = [
        "Прекрати отслеживать задачу: {target}", "Убери из будущих действий {target}",
        "Сделай неактивным напоминание {target}", "Отзови запланированный пункт {target}",
        "Больше не учитывай в расписании {target}",
    ]
    for pattern in delete_patterns:
        for target in targets:
            add("delete-reminder", "delete_reminder", pattern.format(target=target), target=target)
    add("delete-last", "delete_reminder", "Прекрати отслеживать самую недавнюю задачу", targetLast=True)
    add("delete-last", "delete_reminder", "Свежий пункт расписания больше не учитывать", targetLast=True)

    note_bodies = [
        "номер пломбы водомера 843019", "контакт мастера по окнам 89045557766",
        "артикул ролика сушильной машины DR-880", "размер москитной сетки 96 на 142",
        "код получения заказа 730184", "адрес лаборатории Депутатская 18",
        "схема раствора двадцать миллилитров на ведро", "идея подписывать кабели с обеих сторон",
    ]
    note_patterns = [
        "Помести в справочник: {body}", "Создай информационную карточку — {body}",
        "Оставь для справки следующий текст: {body}", "Внеси в личный архив {body}",
        "Сохрани буквальную запись: {body}", "Заполни памятку данными {body}",
    ]
    for pattern in note_patterns:
        for body in note_bodies:
            add("create-note", "create_note", pattern.format(body=body), text=body)

    folders = [
        "Счета за отопление", "Ремонт крупной техники", "Документы управляющей компании",
        "Оптика и зрение", "Доверенности и заявления", "Измерения для дома",
    ]
    create_folder_patterns = [
        "Создай рубрику {folder}", "Размести записи в новой группе {folder}",
        "Открой тематический блок {folder}", "Добавь отдельную коллекцию {folder}",
    ]
    delete_folder_patterns = [
        "Упраздни рубрику {folder}", "Больше не используй группу {folder}",
        "Закрой тематический блок {folder}", "Удали отдельную коллекцию {folder}",
    ]
    for pattern in create_folder_patterns:
        for folder in folders:
            add("create-folder", "create_folder", pattern.format(folder=folder), folder=folder)
    for pattern in delete_folder_patterns:
        for folder in folders:
            add("delete-folder", "delete_folder", pattern.format(folder=folder), folder=folder)

    unrelated = [
        "запросить выписку из лицевого счёта", "проверить крепление кресла", "забрать линзы из оптики",
        "передать документы председателю", "сфотографировать бойлер", "заказать диагностику машины",
        "сверить начисления за отопление", "подготовить доверенность",
    ]
    unknown_patterns = [
        "Я читаю инструкцию о том, как {task}", "Мне уже удалось {task}",
        "Посоветуй специалиста, который может {task}", "Насколько сложно самостоятельно {task}",
        "Я ещё выбираю, стоит ли {task}", "Что понадобится, чтобы {task}",
        "Расскажи о последствиях, если не {task}",
    ]
    for pattern in unknown_patterns:
        for task in unrelated:
            add("unknown", "unknown", pattern.format(task=task))
    for utterance in [
        "Прекрати текущую загрузку", "Закрой эту страницу", "Сотри введённый запрос",
        "Удали журнал действий", "Покажи сохранённые заметки", "Открой тематический блок",
        "Переименуй группу документов", "Отправь выписку председателю", "Позвони мастеру по окнам",
        "Сравни начисления за два месяца", "Проверь доступ в интернет", "Заполни заявление на сайте",
    ]:
        add("unknown-fixed", "unknown", utterance)

    add("scope", "unknown", "Создай рубрику Домовые документы", scope="reminders")
    add("scope", "unknown", "Прекрати отслеживать задачу оплатить отопление", scope="notes")

    return cases
