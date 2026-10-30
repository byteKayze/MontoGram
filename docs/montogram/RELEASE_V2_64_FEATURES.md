# Montogram: release contract v2 — минимум 60 самостоятельных функций

Дата: 2026-10-29. Целевой список — 64 кандидата, не 64 готовые функции.
Этот контракт заменяет количество и способ публикации прежнего alpha3-контракта;
его ограничения аккаунтов, приватности и честной отчётности сохраняются.
Текущий APK20048 — промежуточная alpha с прежним набором, не этот релиз.

Java/View/C++ остаются основой. Переписывание всего клиента на другом языке без
замеров не доказывает экономию RAM, батареи или ускорение. Новые зависимости и
платные сервисы не требуются. Штатный Telegram включён по умолчанию.

Каждый пункт считается только после реализации отдельного полезного поведения,
доступного пользователю. Цветовые варианты, отдельные CRUD-кнопки, перенос
штатной функции в другое меню и варианты одной анимации отдельно не считаются.
Если проверка основы обнаружит уже существующую равнозначную функцию, кандидат
получает DUPLICATE и заменяется, а не засчитывается модификацией Montogram.

## Уровни доказательств и общие ограничения

PLANNED → SOURCE → CHECKED → BUILT → DEVICE. SOURCE означает код с точкой входа;
CHECKED требует приложенных конкретных проверок. BUILT не означает DEVICE.
Частичная реализация обозначается PARTIAL независимо от наличия APK.
В числовую приёмку входят только BUILT плюс выполненные критерии конкретного
ID. SOURCE/CHECKED и незакрытые Android/Keystore критерии не засчитываются.
Перед подсчётом смежных M20/M23, M26/M46, M14/M33/M34 и M50–M55 требуется
письменно зафиксировать самостоятельные действие и результат каждого пункта.

Контексты ниже: UI — оформление без чтения сообщений; OWN — только введённый
пользователем текст, в памяти процесса, без сети/логирования; MSG — только явно
выбранные доступные сообщения, актуальный аккаунт и права. Для MSG исключены
secret/TTL/protected/paid/spoiler и недоступное/удалённое содержимое, пока отдельная
реализация не докажет безопасный сценарий. LOCAL — данные `(slot,selfUserId)`,
память процесса по умолчанию, шифрованное no-backup хранение лишь по согласию.
Ни один инструмент не отправляет сообщения автоматически. Изменение черновика
всегда имеет preview и явное применение с проверкой неизменности исходника.

Для каждого нового пункта до реализации обязательны поиск штатного аналога,
точные hook/owner и отрицательные сценарии. Статус PLANNED этого не заменяет.

## Ранее реализованные или частичные пункты

Точные ограничения и доказательства M01–M21 остаются в
[предыдущем контракте](ALPHA3_RELEASE_CONTRACT.md). Здесь сохраняется один ID на
поведение, без разбиения старых пунктов ради количества.

| ID | Поведение / точка входа | Default / контекст | Приёмка | Статус на начало v2 |
|---|---|---|---|---|
| M01 | Компоненты MD3 / Оформление | Off / UI | layout, RTL, fontScale, контраст; stock/custom provider сохранены | PARTIAL;20048 + source-only topbars/one-two-line Settings cells |
| M02 | Форма аватаров / Оформление | Circle / UI | маски и scrolling без новых allocations | BUILT alpha.7/20078; Android runtime acceptance pending. Earlier targeted evidence: SOURCE;20048, DEVICE pending |
| M03 | Плотность диалогов / Оформление | Native / UI | все типы строк и targets>=48dp | PARTIAL;20048 |
| M04 | Поиск собственных настроек / Montogram | Query empty / UI | Unicode, bounds, переход к пункту без изменения значения | BUILT alpha.7/20078; Android runtime acceptance pending. Earlier targeted evidence: CHECKED;20048 |
| M05 | Export/import настроек / Montogram | Manual / UI | allowlist, limit, отмена/ошибка не меняет состояние | BUILT alpha.7/20078; Android runtime acceptance pending. Earlier targeted evidence: SOURCE;20048 |
| M06 | Сброс с diff / Montogram | Manual / UI | отмена/сбой записи атомарны | BUILT alpha.7/20078; Android runtime acceptance pending. Earlier targeted evidence: CHECKED;20048 |
| M07 | Метаданные сообщения / меню сообщения | Off / MSG | snapshot, guards, без body/path | BUILT alpha.7/20078; Android runtime acceptance pending. Earlier targeted evidence: SOURCE;20048 |
| M08 | ID профиля / профиль | Off / MSG | только доступные поля, account switch | BUILT alpha.7/20078; Android runtime acceptance pending. Earlier targeted evidence: CHECKED;20048 |
| M09 | Секунды времени / сообщения | Off / MSG | timezone, measurement, edited timestamps | BUILT alpha.7/20078; Android runtime acceptance pending. Earlier targeted evidence: CHECKED;20048 |
| M10 | Скрытие story strip / Оформление | Off / UI | поиск, архив, восстановление, insets | BUILT alpha.7/20078; Android runtime acceptance pending. Earlier targeted evidence: CHECKED;20048 |
| M11 | Подтверждение звонка / Calls | Off / MSG | one-shot, owner/lifecycle, native permissions | BUILT alpha.7/20078; Android runtime acceptance pending. Earlier targeted evidence: CHECKED;20048, DEVICE pending |
| M12 | Подтверждение стикера / Chat tools | Off / MSG | все 6 gesture families, cancel/one-send | PARTIAL;4/6,20048 |
| M13 | Preview голосового / Chat tools | Off / MSG | touch/locked/raise-to-ear/story, cancel/retry | PARTIAL;2 touch branches,20048 |
| M14 | Очистка tracking URL / Chat tools | Manual / OWN | preview; подписанные/неоднозначные URL сохранены | BUILT alpha.7/20078; Android runtime acceptance pending. Earlier targeted evidence: CHECKED;20048 |
| M15 | Локальные быстрые ответы / Montogram + composer | Empty / LOCAL | account isolation, insert only, storage failure | BUILT alpha.7/20078; Android runtime acceptance pending. Earlier targeted evidence: CHECKED;20048, Keystore pending |
| M16 | Локальные закладки-ссылки / меню + Montogram | Empty / LOCAL+MSG | ID-only, deleted/protection проверены при открытии | BUILT alpha.7/20078; Android runtime acceptance pending. Earlier targeted evidence: CHECKED;20048 |
| M17 | Локальные заметки чата / чат + Montogram | Empty / LOCAL | revoke, bounds, storage failure, без зеркала сообщений | BUILT alpha.7/20078; Android runtime acceptance pending. Earlier targeted evidence: CHECKED;20048 |
| M18 | Состав и порядок меню сообщения / Chat tools | Native / UI | обязательные действия/guards сохранены | BUILT alpha.7/20078; Android runtime acceptance pending. Earlier targeted evidence: CHECKED;20048 |
| M19 | Управление haptics / Оформление | Native / UI | system disabled, off/reduced; calls untouched | BUILT alpha.7/20078; Android runtime acceptance pending. Earlier targeted evidence: CHECKED;20048 |
| M20 | Счётчик composer / Chat tools | Off / OWN | UTF16/entity limit, IME/record/RTL | BUILT alpha.7/20078; Android runtime acceptance pending. Earlier targeted evidence: CHECKED;20048 |
| M21 | Не отправлять текстовый typing / Chat tools | Off / OWN | off/cancel/non-text native, encrypted contexts | BUILT alpha.7/20078; Android runtime acceptance pending. Earlier targeted evidence: CHECKED;20048 |

## Новые кандидаты — сначала проверка штатных аналогов

Каждый вход ниже предполагается отдельным именованным действием в Chat tools,
composer или профильном экране. В alpha.5 включены M23/M26/M27/M28/M29/M37/M38 и частичный M53; Android pending.
M24/M32/M34/M46/M58 добавлены после alpha.5 и ожидают общей CI-сборки.
Актуальные отдельные счётчики кода, частичных пунктов и APK — в `setup/evidence/telegram-fork/feature-budget-status.json`.

| ID | Отдельное поведение / планируемый вход | Default / контекст | Минимальная проверяемая приёмка | Статус |
|---|---|---|---|---|
| M22 | Паспорт получателя перед рискованной отправкой / composer | Off / OWN | account+recipient фиксированы; смена peer аннулирует подтверждение; paid native | PLANNED |
| M23 | Статистика введённого текста: letter/digit runs, codepoints, UTF16, UTF8, lines / Chat tools → Text tools | Manual / OWN | emoji, combining, RTL, empty, bounded input; не лингвистический word count | BUILT alpha.7/20078; Android runtime acceptance pending. Earlier targeted evidence: SOURCE+JVM; canonical R/9-type diagnostic PASS; APK/Android pending |
| M24 | Разбиение длинного текста на части с preview / composer tools | Manual / OWN | только plain text; UTF-16/surrogate/CRLF, точный join round-trip, без отправки | BUILT alpha.7/20078; Android runtime acceptance pending. Earlier targeted evidence: SOURCE; JVM5090 PASS; RU/EN resources checked; batch CI/Android pending |
| M25 | Локальная отмена последнего преобразования черновика / composer tools | Empty / OWN | undo только при совпадении owner+current draft; pause/logout revoke | PLANNED |
| M26 | Инспектор невидимых и bidi-символов / Chat tools → Text tools | Manual / OWN | видимые U+XXXX, codepoint positions, cap128; без тихого удаления | BUILT alpha.7/20078; Android runtime acceptance pending. Earlier targeted evidence: SOURCE+JVM; canonical R/9-type diagnostic PASS; APK/Android pending |
| M27 | Проверка незакрытых парных кавычек/скобок / composer tools | Manual / OWN | не исправляет автоматически, ложные срабатывания явно advisory | BUILT alpha.7/20078; Android runtime acceptance pending. Earlier targeted evidence: SOURCE+JVM22197/API-fake12; fresh R/SDK20 PASS; APK/Android pending, не засчитывается |
| M28 | Проверка повторных строк перед вставкой / composer tools | Manual / OWN | preview точных дубликатов; порядок остальных сохранён | BUILT alpha.7/20078; Android runtime acceptance pending. Earlier targeted evidence: SOURCE+JVM22197/API-fake12; fresh R/SDK20 PASS; APK/Android pending, не засчитывается |
| M29 | Офлайн преобразование ошибочной RU/EN раскладки / Chat tools → Text tools | Manual / OWN | case, punctuation, unmapped preserved, preview→explicit copy; никакой сети | BUILT alpha.7/20078; Android runtime acceptance pending. Earlier targeted evidence: SOURCE+JVM; canonical R/9-type diagnostic PASS; APK/Android pending |
| M30 | Подготовка plain-text вставки с просмотром удаляемого форматирования / composer tools | Manual / OWN | text/entities согласованы, hyperlinks не маскируются, undo | PLANNED |
| M31 | Локальные версии текущего черновика / composer tools | Empty / OWN | bounded ring, owner isolation, explicit restore diff, no disk | PLANNED |
| M32 | Сравнение двух введённых текстов / Chat tools | Manual / OWN | bounded Unicode diff, no content logging, accessible result | BUILT alpha.7/20078; Android runtime acceptance pending. Earlier targeted evidence: SOURCE; JVM5090 PASS; RU/EN resources checked; batch CI/Android pending |
| M33 | Проверка ссылок черновика: видимый текст и реальная цель / composer tools | Manual / OWN | entity URLs, IDN, control chars; не обещает безопасность сайта | PLANNED |
| M34 | Разбор URL на домен/путь/параметры / Chat tools | Manual / OWN | no navigation/network; auth/query не логируются; explicit copy | BUILT alpha.7/20078; Android runtime acceptance pending. Earlier targeted evidence: SOURCE; JVM5090 PASS; RU/EN resources checked; batch CI/Android pending |
| M35 | Создание ссылки к цитате обычного доступного сообщения / message menu | Manual / MSG | только разрешённый native link, preview quote; no protected export | PLANNED |
| M36 | Локальные папки закладок / Bookmarks | Empty / LOCAL | принадлежность, move/delete semantics, bounded folder count | BUILT alpha.7/20078; Android runtime acceptance pending. Earlier targeted evidence: SOURCE; immutable MBK1/MBK2 folders; JVM86 + SDK own-source PASS; batch CI/Android pending |
| M37 | Поиск локальных заметок / Notes | Query empty / LOCAL | owner only, normalized bounded query, zero network; focus/click/lock/device responsiveness | BUILT alpha.7/20078; Android runtime acceptance pending. Earlier targeted evidence: SOURCE+JVM8218/SDK11 PASS; APK/Android pending, не засчитывается |
| M38 | Офлайн поиск быстрых ответов с ранжированием / Quick replies picker | Query empty / LOCAL | exact/prefix ordering, original-index clicks, owner invalidation, insert-only, Android IME/focus/scroll | BUILT alpha.7/20078; Android runtime acceptance pending. Earlier targeted evidence: SOURCE+JVM/SDK13 PASS; APK/Android pending, не засчитывается |
| M39 | Безопасный экспорт собственных заметок с preview / Notes | Manual / LOCAL | explicit SAF consent, no IDs иных owner, IO/cancel no loss | BUILT alpha.7/20078; Android runtime acceptance pending. Earlier targeted evidence: SOURCE; explicit SAF disclosure/final confirmation; shared IO24616 + JVM86 PASS; batch CI/Android pending |
| M40 | Preview/diff импорта собственных быстрых ответов / Quick replies | Manual / LOCAL | strict schema/limits, duplicate policy, atomic apply, no auth data | BUILT alpha.7/20078; Android runtime acceptance pending. Earlier targeted evidence: SOURCE; strict portable import + digest/revision preview + atomic merge; JVM86/SAF24616 PASS; batch CI/Android pending |
| M41 | Выявление недоступных локальных закладок по запросу / Bookmarks | Manual / LOCAL+MSG | bounded requests, rate/cancel; no auto-delete; no body storage | PLANNED |
| M42 | Панель локальных данных Montogram / Settings privacy | Manual / LOCAL | counts/bytes without bodies; clear specific owner, error states | BUILT alpha.7/20078; Android runtime acceptance pending. Earlier targeted evidence: SOURCE; counts/exact serialized model bytes + own manager links; SDK own-source PASS; batch CI/Android pending |
| M43 | Щит показа экрана для содержимого Montogram / Privacy | Off / UI | local redaction, a11y label, штатные secure policies не ослаблены | PLANNED |
| M44 | Предупреждение о смене аккаунта при вставке шаблона / composer | Off / OWN+LOCAL | stale picker не вставляет в нового owner, confirmation not bypass | PLANNED |
| M45 | Временное копирование из Reader с проверкой владения буфером | Manual / MSG | fresh opaque UUID+exact MIME/item/payload before best-effort clear; inactive creation denied, failed replacement preserves old expiry; no history-erasure guarantee | BUILT alpha.7/20078; Android runtime acceptance pending. Earlier targeted evidence: SOURCE; JVM2927/SDK5/aggregate RU-EN220 PASS; full ChatActivity/Settings CI and Android pending |
| M46 | Проверка текста на похожие смешанные алфавиты / composer tools | Manual / OWN | bounded advisory, URL/domain separate, no false safety verdict | BUILT alpha.7/20078; Android runtime acceptance pending. Earlier targeted evidence: SOURCE; JVM5090 PASS; RU/EN resources checked; batch CI/Android pending |
| M47 | Локальное чтение длинного разрешённого текста / message menu | Manual / MSG | accessible type/width, no retained copy, owner/lifecycle revoke | BUILT alpha.7/20078; Android runtime acceptance pending. Earlier targeted evidence: SOURCE; JVM2927/SDK5/aggregate RU-EN220 PASS; full ChatActivity/Settings CI and Android pending |
| M48 | Поиск внутри открытого режима чтения / Reader | Query empty / MSG | Unicode match, next/prev, bounds; blocked contexts unchanged | BUILT alpha.7/20078; Android runtime acceptance pending. Earlier targeted evidence: SOURCE; JVM2927/SDK5/aggregate RU-EN220 PASS; full ChatActivity/Settings CI and Android pending |
| M49 | Таблица выделенных сообщений перед массовым действием / selection tools | Manual / MSG | sender/type/date summary, current rights refreshed, no auto-action | PLANNED |
| M50 | Индикатор расхода выбранных эффектов без фонового мониторинга / Appearance | Off / UI | qualitative costs labelled estimates; no fake RAM/FPS values | PLANNED |
| M51 | Preview оформления до применения с rollback / Appearance | Manual / UI | cancel/process lifecycle не меняют persisted prefs; apply atomic | PLANNED |
| M52 | Быстрое временное отключение декоративных эффектов / Appearance | Off / UI | session-only policy; no network/call/notification behavior changes | PLANNED |
| M53 | Настройка M3 motion с системным override / Appearance | Standard / UI | reduced/off prevail, interrupted transitions settle, no input delay, whole scoped-component coverage | PARTIAL SOURCE: switch Standard/Off+systemdisabled only; JVM57+133/SDK5core PASS; newR/settingsUI SDK20 PASS; Reduced/othercomponents/APK/Android pending, не засчитывается |
| M54 | Демонстрационный стенд собственных MD3 компонентов / Appearance preview | Manual / UI | реальные production components, dark/RTL/fontScale/disabled | PLANNED |
| M55 | Preview доступности оформления / Appearance | Manual / UI | реальные large-font/contrast targets, без подмены системных настроек | PLANNED |
| M56 | Сравнение текущих настроек с ранее экспортированными / Settings backup | Manual / UI | parse allowlist, display-only diff, no implicit import | PLANNED |
| M57 | Объяснение изменённых настроек и возврат отдельных значений / Settings | Manual / UI | exact defaults, one-key reset atomic, unrelated values unchanged | PLANNED |
| M58 | Безопасный отчёт о сборке для поддержки / About | Manual / UI | version/ABI/settings-schema only, no IDs/tokens/paths/drafts | BUILT alpha.7/20078; Android runtime acceptance pending. Earlier targeted evidence: SOURCE; JVM5090 PASS; RU/EN resources checked; batch CI/Android pending |
| M59 | Согласованный набор сочетаний клавиш для собственных инструментов / Settings keyboard | Off / UI | conflicts/native keys respected, visible alternative, keyboard focus | PLANNED |
| M60 | Навигатор по изменениям альфы с переходом к настройке / About | Manual / UI | только реально присутствующие features, no enabled-by-navigation | PLANNED |
| M61 | Локальный checklist перед публикацией большого текста / composer tools | Manual / OWN | user-authored checks, preview only, no content sent/retained | PLANNED |
| M62 | Экспорт пользовательских меток закладок без текста сообщений / Bookmarks | Manual / LOCAL | explicit disclosure IDs, current owner only, SAF error/cancel | BUILT alpha.7/20078; Android runtime acceptance pending. Earlier targeted evidence: SOURCE; explicit dialog/message refs/labels/folders only; JVM86/SAF24616 PASS; batch CI/Android pending |
| M63 | Атомарное удаление выбранных собственных заметок / Notes | Manual / LOCAL | selection visible; exact note count/peer refs; current owner/revision; one immutable commit, no partial deletion | BUILT alpha.7/20078; Android runtime acceptance pending. Earlier targeted evidence: SOURCE; multi-select atomic notes deletion with exact records preview; JVM86/SDK own-source PASS; batch CI/Android pending |
| M64 | Сессионный список незавершённых собственных инструментов / Chat tools | Empty / OWN | bounded task state, no hidden draft persistence, explicit clear/logout | PLANNED |

## MD3 и движение

Дополнение по прямому запросу пользователя29.10.2026:

| ID | Дополнительная функция | Default / scope | Приёмка | Статус |
|---|---|---|---|---|
| M65 | Отдельный creator action с логотипом M и нижним уведомлением | Показ только exact resolved user5672308380L; явный запрос пользователя, без toggle | Numeric64bit identity, native status unchanged, single item refresh, header budgets, exact popup, a11y/runtime | BUILT alpha.7/20078; Android runtime acceptance pending. Earlier targeted evidence: SOURCE+JVM149 PASS; R/Profile SDK/Android/APK pending; не засчитывается |

Новый бренд/баннеры/полное оформление MontoGram и prepublish security — отдельные
deliverables. Они не раздувают число функций и не заменяют прежние64кандидата.

M01 — одна функция, а не количество перекрашенных компонентов. M53 — единая
политика движения, а не отдельная функция для каждого duration/easing.
Системное отключение анимаций и reduced motion имеют приоритет. Существующий
custom ResourcesProvider сохраняет приоритет; MD3 не включается скрыто.
Нужны прерывание/reverse/cancel, RTL, fontScale, focus, TalkBack, dark/light и
проверка реальных измерений. JVM и SDK typing не заменяют Android screenshots.

## Публикация

1. Реализовано не менее 60 самостоятельных недублирующих функций; оставшиеся
   PLANNED/PARTIAL не считаются. Проверки перечислены по ID, без общего «всё OK».
2. Gate1/2/3 постоянного architecture-critic; Blocker/High устранены либо явно
   объявлены как ограничения незавершённого scope. Не объявлять full release
   при невыполненном количестве или недоступном обязательном ревью.
3. Один пакет правок → freeze → уже проверенные low-RAM core/DEX/assemble.
   Не строить по одной функции, не выполнять частый polling. Проверить actual
   signature/package/version/ABI/alignment; Android runtime отделить от build.
4. GitHub: подтвердить exact owner/repo, private visibility и права. Пока
   connector не видит repository, не придумывать URL и не создавать public repo.
5. Exact source commit/tag соответствует APK: полные соответствующие исходники,
   pinned submodules, GPL/другие licenses, build instructions, placeholder config.
   Старый source-overlay без base недостаточен. Secret scan до передачи любых
   файлов; исключены env/local properties/auth/keystore/Gradle caches/user data.
   В .env остаются только локальные значения: сам .env в git не загружается.
6. GitHub prerelease alpha с APK/checksums/source instructions/ограничениями;
   readback asset size/identity. Не обещать «без ошибок», perfect parity или
   экономию ресурсов без замеров. Drive20048 остаётся исторической поставкой.
