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

Перед native assembly CI разрешает реальные внешние артефакты четырёх production
classpath и запускает обычные JavaCompile задачи core/app. Только Java preflight
получает `-PmontogramHeapMiB=8192`, metaspace1024MiB и native jobs1. Assemble
сохраняет heap2560MiB/native jobs4 и использует штатные Gradle up-to-date checks.
Локальный профиль остаётся heap2560MiB/native jobs1; 8192 разрешён только явно
в GitHub Actions репозитория byteKayze/MontoGram. Никакие outputs не копируются.

## Ограничения и проверка

На ноутбуке4ГБ полная новая сборка была остановлена защитой ресурсов. Повторять
её одновременно с браузером/эмулятором нельзя. Последний доставленный APK20048
не содержит свежие изменения. Успех CI, unit/source checks и Android runtime
отмечаются отдельно. У новой APK перед релизом проверяются package/version,
подпись, non-debuggable, ZIP/ELF alignment, ABI и связь с исходным commit.
Непроведённые проверки остаются в release notes.
