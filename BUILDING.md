# Сборка Montogram

Репозиторий содержит Android-основу Telegram в корне, закреплённую на upstream
`0aae959806c57be8ad457980f2a5c6277ff18635`, изменения Montogram и 19 закреплённых
подмодулей. Клонируйте с `git clone --recurse-submodules`; автоматический архив
GitHub не содержит содержимое подмодулей. Лицензии и исходные notices сохранены.

## Инструменты

Linux, JDK 17, Gradle 8.13, AGP 8.13.2, Kotlin 2.1.0, Android SDK/target 36,
build-tools 36.0.0, NDK 27.2.12479018, CMake 3.22.1. Минимальная версия Android 23.
`gradlew-montogram` использует `setup/toolchains/gradle-8.13`, `setup/android-sdk`
и JDK из `MONTOGRAM_JAVA_HOME` либо `setup/toolchains/jdk-17.0.21+9`.
Toolchains, SDK, кеши и лицензии SDK устанавливаются отдельно.

## Личная конфигурация

Скопируйте `montogram.properties.example` в игнорируемый `montogram.properties`
в корне. Укажите собственные `api.id`/`api.hash` с my.telegram.org, права0600.
Они входят в APK и извлекаемы из неё; это не ключи сессии пользователя.
Не коммитьте конфигурацию, `.env`, ключ подписи, пароли или auth/session данные.

Создайте свою `.montogram-private/` с правами0700: PKCS12 `montogram.p12`,
RSA alias `montogram`, и `signing.password`, оба0600. Передавайте пароль keytool
через `-storepass:file`/`-keypass:file`. Своя подпись не обновит APK автора.

## Удалённая сборка

Ручной workflow `.github/workflows/montogram-build.yml` принимает точный SHA
проверенного commit и уникальный request_id. Только стандартный публичный runner
ubuntu-24.04, read-only GITHUB_TOKEN, два Telegram app значения из Secrets.
Ключ релизной подписи автора в CI не загружается. CI создаёт временную подпись;
артефакт APK+metadata хранится один день и сам по себе НЕ является релизом.
Финальная подпись и проверки релиза выполняются владельцем отдельно.

Build использует compact Gradle wrapper с точным маскированием API значений:
`ci/vendor/gradle_run.py`; изменённый Apache-2.0 wrapper и лицензия сохранены.
Основная задача: `:TMessagesProj_AppStandalone:assembleAfatStandalone`, параметры
`-PmontogramAbi=universal -PmontogramMinify=false -PmontogramHeapMiB=2560`,
`--configure-on-demand --max-workers=1 --no-parallel --no-daemon`, строгая
проверка зависимостей. Universal здесь означает arm64-v8a и x86_64; ARM32 нет.
Это unminified alpha, без обещания оптимального размера.

Перед native assembly CI проверяет реальные артефакты четырёх production classpath.
Для двух обычных JavaCompile задач core/app отдельно настроены fork=true,
compiler heap4096MiB, SerialGC/metaspace512MiB. Перед ними отдельная verification
задача пишет конфигурацию компилятора и фактический max heap процесса Gradle;
это не замер RSS worker. Gradle остаётся2560MiB: Java preflight native jobs1,
assemble native jobs4. Используются только штатные Gradle up-to-date checks;
outputs не копируются. Эта настройка compiler fork действует только явно в
GitHub Actions репозитория byteKayze/MontoGram; desktop profile не меняется.

## Ограничения и проверка

На ноутбуке с 4 ГБ RAM прежние полные сборки останавливала защита ресурсов.
Текущая alpha.7, build 20078, собрана в GitHub Actions и подписана локально;
её точный исходный commit — `73517983d2474564dac6835b5d140574c2dca196`,
тег — `v0.2.0-alpha.7`. Последующие изменения README/CHANGELOG не меняют
этот тег и не требуют пересборки приложения.

В APK указана версия `0.2.0-alpha.7-internal-unminified`. Проверены package/version,
подпись, отсутствие debug-режима, ZIP/ELF alignment, ABI и связь с исходным commit.
Успех CI, целевые проверки и Android runtime отмечаются отдельно: запуск этой
APK на устройстве и локальная воспроизводимость ещё не подтверждены. Полные
60+ функций и MD3 остаются незавершёнными. Ограничения сохранены в release notes.
