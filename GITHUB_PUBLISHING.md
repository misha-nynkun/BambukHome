# Публикация BambukHome на GitHub

Пошаговый чек-лист. Все команды выполняются из папки `LayerPilot`.

## 1. Что положить в репозиторий

Обязательно:

- `README.md` — главная страница проекта.
- `LICENSE` — MIT (можно заменить на GPL-3.0, если хочешь copyleft).
- `PRIVACY.md` — политика конфиденциальности.
- `STORE_DESCRIPTION.md` — описание для магазинов и 4PDA.
- `GITHUB_RELEASE_1.0.5.md` — текст релиза.
- `screenshots/` — скриншоты.
- Исходники `app/`, `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`.
- `BambukHome-1.0.5.apk` — **не в git**, а в Assets релиза.

Не коммитить:

- `local.properties`;
- `keystore.properties`;
- `keystore/*.jks`;
- `*.apk` / `*.aab`;
- `.gradle/`, `build/`, `.idea/`, `.kotlin/`.

В проекте уже есть `.gitignore` с этими правилами.

## 2. Создать репозиторий

На GitHub: **New repository**.

- **Name:** `BambukHome`
- **Description:** `Local Wi-Fi control for Bambu Lab 3D printers. No cloud, ads or subscriptions.`
- **Visibility:** Public.
- Не добавляй README/LICENSE с GitHub — они уже есть локально.

Затем:

```bash
cd "/home/qqq4/Documents/Проект по умолчанию/LayerPilot"
git init
git branch -M main
git add .
git commit -m "BambukHome 1.0.5"
git remote add origin https://github.com/ВАШ_АККАУНТ/BambukHome.git
git push -u origin main
```

Если Git просит авторизацию — используй GitHub CLI (`gh auth login`) или Personal Access Token.

## 3. Заполнить About

В правой панели репозитория:

**Description:**

```text
Local Wi-Fi control for Bambu Lab 3D printers. No cloud, ads or subscriptions.
```

**Website:** можно оставить пустым или указать страницу релиза.

**Topics:**

```text
3d-printing
bambu-lab
bambu
p1s
p1p
x1c
a1
h2d
ams
mqtt
ftps
android
kotlin
jetpack-compose
material-you
```

## 4. Создать релиз v1.0.5

```bash
git tag -a v1.0.5 -m "BambukHome 1.0.5"
git push origin v1.0.5
```

На GitHub: **Releases → Draft a new release**.

- **Choose a tag:** `v1.0.5`
- **Release title:** `BambukHome 1.0.5 — локальное управление Bambu-принтером`
- **Describe this release:** вставь содержимое `GITHUB_RELEASE_1.0.5.md`.
- **Attach binaries:** `BambukHome-1.0.5.apk`.
- Поставь **Set as the latest release**.
- Опубликуй.

Проверь, что ссылка на APK работает:

```text
https://github.com/ВАШ_АККАУНТ/BambukHome/releases/download/v1.0.5/BambukHome-1.0.5.apk
```

## 5. Проверить README

В README должны быть:

- название и краткое описание;
- бейджи Android/Kotlin/Compose/версия/лицензия;
- ссылка на последний релиз;
- скриншоты;
- список возможностей;
- инструкция подключения;
- ограничения и дисклеймер;
- ссылки на `PRIVACY.md` и `LICENSE`.

Если ссылки на релиз в README используют `ВАШ_АККАУНТ` — замени на свой GitHub username.

## 6. Проверить перед публикацией

- [ ] `BambukHome-1.0.5.apk` собран в release, подписан и установлен на чистом устройстве.
- [ ] Версия в APK: `1.0.5`, versionCode `6`.
- [ ] SHA-256 в релизе совпадает с файлом:
  `7bf72593f2349dda332db3a3df618d4763576b627c24ec5e1912dd815533258d`.
- [ ] В git нет `keystore.properties`, `.jks`, `local.properties`, APK.
- [ ] Проверены ссылки на скриншоты.
- [ ] В README и релизе есть дисклеймер «не связан с Bambu Lab».
- [ ] Указан контакт для поддержки / ссылка на Issues.
- [ ] Проверена политика конфиденциальности.
- [ ] Проверены тексты на русском и английском (хотя бы короткое описание).

## 7. После публикации

- Включи **Issues**.
- По желанию включи **Discussions** для вопросов и идей.
- Добавь шаблон issue для багов: модель принтера, прошивка, версия Android, шаги, логи.
- Не обещай поддержку всех прошивок: протокол неофициальный.
- Для обновлений меняй `versionCode` и `versionName`, создавай новый тег `v1.0.6` и прикрепляй новый APK.
- Старые релизы не удаляй: пользователи могут откатиться.

## 8. Google Play — отдельно

Публикация в Google Play возможна, но есть нюансы:

- **Торговая марка.** «Bambu Lab» — чужая торговая марка. Не используй официальный логотип и не выдавай приложение за официальное. В описании и названии лучше подчеркнуть «independent / unofficial client».
- **Privacy Policy URL.** Нужен публичный URL на `PRIVACY.md` (GitHub Pages или страница сайта).
- **Data safety.** Укажи: локальные данные, опциональный аккаунт, шифрование токена, отсутствие рекламы/аналитики.
- **Foreground service.** В манифесте уже указан `specialUse` с подтипом `monitoring_local_3d_printer`; при заполнении формы объясни, что сервис нужен для мониторинга печати и уведомлений.
- **Доступ для рецензента.** В приложении есть демо-режим — укажи это в инструкции для проверяющего, чтобы ему не нужен был реальный принтер.
- **Разрешения.** `INTERNET`, состояние сети/Wi-Fi, уведомления, foreground-service. Никаких контактов, геолокации, камеры телефона и микрофона.
- **Аккаунт.** Вход в Bambu Lab опционален; локальные функции работают без аккаунта.

Если Google Play отклонит приложение из-за торговой марки — публикуй APK на GitHub Releases, 4PDA и в профильных сообществах. Это нормальная практика для независимых клиентов.

## 9. Рекомендуемая структура репозитория

```text
BambukHome/
├── app/
├── screenshots/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── README.md
├── LICENSE
├── PRIVACY.md
├── STORE_DESCRIPTION.md
├── GITHUB_RELEASE_1.0.5.md
├── GITHUB_PUBLISHING.md
├── .gitignore
└── BambukHome-1.0.5.apk   ← только в Assets релиза, не в git
```

## 10. Контакт и поддержка

Добавь в README и `PRIVACY.md` один из вариантов:

- `https://github.com/ВАШ_АККАУНТ/BambukHome/issues`
- email автора;
- Telegram/чат сообщества.

Без контакта пользователи не смогут сообщить о проблеме с принтером или прошивкой.
