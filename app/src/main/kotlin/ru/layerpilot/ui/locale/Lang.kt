package ru.layerpilot.ui.locale

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Лёгкая локализация интерфейса без переписывания всех экранов:
 * Text() пропускает строку через словарь. Ключ — русская строка,
 * поэтому исходники остаются читаемыми, а переводы можно дополнять.
 */
object Lang {

    val supported = listOf("ru", "en", "de", "fr", "es", "it", "pt", "pl", "tr", "zh")

    var code: String = "ru"
        private set

    fun setCode(value: String) {
        code = if (value in supported) value else "ru"
    }

    fun translate(text: String, language: String = code): String {
        if (language == "ru") return text
        val index = codes.indexOf(language)
        if (index < 0) return text
        dictionary[text]?.let { row -> return row.getOrNull(index) ?: row.firstOrNull() ?: text }
        dynamic(text, language)?.let { return it }
        // «ОСТАЛОСЬ» → «Осталось»: подписи часто капсом, переводим и их.
        val wasUpper = text == text.uppercase()
        val lower = text.lowercase()
        val title = lower.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        dictionary[title]?.let { row ->
            val translated = row.getOrNull(index) ?: row.firstOrNull() ?: return text
            return if (wasUpper) translated.uppercase() else translated
        }
        englishOnly[text]?.let { if (language != "ru") return it }
        val trimmed = text.trim()
        if (trimmed != text) {
            dictionary[trimmed]?.let { row ->
                return row.getOrNull(index) ?: row.firstOrNull() ?: text
            }
            englishOnly[trimmed]?.let { if (language != "ru") return it }
        }
        return text
    }

    private fun one(word: String, language: String): String {
        val index = codes.indexOf(language)
        if (index < 0) return word
        return dictionary[word]?.getOrNull(index) ?: word
    }

    private val codes = listOf("en", "de", "fr", "es", "it", "pt", "pl", "tr", "zh")

    private fun row(vararg values: String) = values.toList()

    /** Короткие динамические фразы: слои, время, остаток, температура. */
    private fun dynamic(text: String, language: String): String? {
        val layerOf = Regex("^Слой (\\d+) из (\\d+)$").find(text)
        if (layerOf != null) {
            val (a, b) = layerOf.destructured
            return when (language) {
                "en" -> "Layer $a of $b"
                "de" -> "Schicht $a von $b"
                "fr" -> "Couche $a sur $b"
                "es" -> "Capa $a de $b"
                "it" -> "Strato $a di $b"
                "pt" -> "Camada $a de $b"
                "pl" -> "Warstwa $a z $b"
                "tr" -> "Katman $a / $b"
                "zh" -> "第 $a 层，共 $b 层"
                else -> null
            }
        }
        val layerSlash = Regex("^Слой (\\d+) / (\\d+)$").find(text)
        if (layerSlash != null) {
            val (a, b) = layerSlash.destructured
            return when (language) {
                "en" -> "Layer $a / $b"
                "de" -> "Schicht $a / $b"
                "fr" -> "Couche $a / $b"
                "es" -> "Capa $a / $b"
                "it" -> "Strato $a / $b"
                "pt" -> "Camada $a / $b"
                "pl" -> "Warstwa $a / $b"
                "tr" -> "Katman $a / $b"
                "zh" -> "层 $a / $b"
                else -> null
            }
        }
        Regex("^(\\d+) ч (\\d+) мин$").find(text)?.let { m ->
            val (h, min) = m.destructured
            return when (language) {
                "en" -> "$h h $min min"
                "de" -> "$h Std. $min Min."
                "fr" -> "$h h $min min"
                "es" -> "$h h $min min"
                "it" -> "$h h $min min"
                "pt" -> "$h h $min min"
                "pl" -> "$h godz. $min min"
                "tr" -> "$h sa $min dk"
                "zh" -> "$h 小时 $min 分"
                else -> null
            }
        }
        Regex("^(\\d+) ч$").find(text)?.let { m ->
            val h = m.groupValues[1]
            return when (language) {
                "en" -> "$h h"
                "de" -> "$h Std."
                "fr" -> "$h h"
                "es" -> "$h h"
                "it" -> "$h h"
                "pt" -> "$h h"
                "pl" -> "$h godz."
                "tr" -> "$h sa"
                "zh" -> "$h 小时"
                else -> null
            }
        }
        Regex("^(\\d+) мин$").find(text)?.let { m ->
            val min = m.groupValues[1]
            return when (language) {
                "en" -> "$min min"
                "de" -> "$min Min."
                "fr" -> "$min min"
                "es" -> "$min min"
                "it" -> "$min min"
                "pt" -> "$min min"
                "pl" -> "$min min"
                "tr" -> "$min dk"
                "zh" -> "$min 分"
                else -> null
            }
        }
        Regex("^Пропустить \\((\\d+)\\)$").find(text)?.let { m ->
            val count = m.groupValues[1]
            return when (language) {
                "en" -> "Skip ($count)"
                "de" -> "Überspringen ($count)"
                "fr" -> "Ignorer ($count)"
                "es" -> "Omitir ($count)"
                "it" -> "Salta ($count)"
                "pt" -> "Pular ($count)"
                "pl" -> "Pomiń ($count)"
                "tr" -> "Atla ($count)"
                "zh" -> "跳过（$count）"
                else -> null
            }
        }
        Regex("^Код отправлен на (.+)$").find(text)?.let { m ->
            return when (language) {
                "en" -> "Code sent to ${m.groupValues[1]}"
                "de" -> "Code gesendet an ${m.groupValues[1]}"
                else -> null
            }
        }
        Regex("^Пластина (\\d+)$").find(text)?.let { m ->
            return when (language) {
                "en" -> "Plate ${m.groupValues[1]}"
                "de" -> "Platte ${m.groupValues[1]}"
                else -> null
            }
        }
        Regex("^(\\d+) моделей$").find(text)?.let { m ->
            return when (language) {
                "en" -> "${m.groupValues[1]} models"
                "de" -> "${m.groupValues[1]} Modelle"
                else -> null
            }
        }
        Regex("^(\\d+) ч (\\d+) мин$").find(text)?.let { m ->
            val (h, min) = m.destructured
            return when (language) {
                "en" -> "$h h $min min"
                "de" -> "$h Std. $min Min."
                else -> null
            }
        }
        Regex("^(\\d+) г$").find(text)?.let { m ->
            return when (language) {
                "en" -> "${m.groupValues[1]} g"
                else -> null
            }
        }
        Regex("^(\\d+) треугольников · (\\d+) деталей$").find(text)?.let { m ->
            val (triangles, parts) = m.destructured
            return when (language) {
                "en" -> "$triangles triangles · $parts parts"
                "de" -> "$triangles Dreiecke · $parts Teile"
                else -> null
            }
        }
        Regex("^Скачиваем (\\d+) %$").find(text)?.let { m ->
            return when (language) {
                "en" -> "Downloading ${m.groupValues[1]}%"
                "de" -> "Herunterladen ${m.groupValues[1]} %"
                else -> null
            }
        }
        Regex("^(\\d+) сохранено$").find(text)?.let { m ->
            val count = m.groupValues[1]
            return when (language) {
                "en" -> "$count saved"
                "de" -> "$count gespeichert"
                "fr" -> "$count enregistré(s)"
                "es" -> "$count guardado(s)"
                "it" -> "$count salvato(i)"
                "pt" -> "$count salvo(s)"
                "pl" -> "zapisano: $count"
                "tr" -> "$count kayıtlı"
                "zh" -> "已保存 $count"
                else -> null
            }
        }
        Regex("^«(.+)» исчезнет из списка\\. Настройки самого принтера не изменятся\\.$").find(text)?.let { m ->
            val name = m.groupValues[1]
            return when (language) {
                "en" -> "“$name” will be removed from the list. The printer settings themselves are not changed."
                "de" -> "„$name“ wird aus der Liste entfernt. Die Einstellungen des Druckers selbst bleiben erhalten."
                "fr" -> "« $name » sera retiré de la liste. Les réglages de l'imprimante ne changent pas."
                "es" -> "«$name» se quitará de la lista. La configuración de la impresora no cambia."
                "it" -> "«$name» verrà rimosso dall'elenco. Le impostazioni della stampante non cambiano."
                "pt" -> "«$name» será removido da lista. As configurações da impressora não mudam."
                "pl" -> "„$name” zniknie z listy. Ustawienia samej drukarki nie zmienią się."
                "tr" -> "„$name” listeden kaldırılacak. Yazıcının kendi ayarları değişmez."
                "zh" -> "“$name”将从列表中移除。打印机本身的设置不会改变。"
                else -> null
            }
        }
        Regex("^влажность (.+)$").find(text)?.let { m ->
            val rest = m.groupValues[1]
            return when (language) {
                "en" -> "humidity $rest"
                "de" -> "Luftfeuchtigkeit $rest"
                "fr" -> "humidité $rest"
                "es" -> "humedad $rest"
                "it" -> "umidità $rest"
                "pt" -> "umidade $rest"
                "pl" -> "wilgotność $rest"
                "tr" -> "nem $rest"
                "zh" -> "湿度 $rest"
                else -> null
            }
        }
        Regex("^финиш ≈ (.+)$").find(text)?.let { m ->
            val rest = m.groupValues[1]
            return when (language) {
                "en" -> "finish ≈ $rest"
                "de" -> "Ende ≈ $rest"
                "fr" -> "fin ≈ $rest"
                "es" -> "fin ≈ $rest"
                "it" -> "fine ≈ $rest"
                "pt" -> "fim ≈ $rest"
                "pl" -> "koniec ≈ $rest"
                "tr" -> "bitiş ≈ $rest"
                "zh" -> "完成 ≈ $rest"
                else -> null
            }
        }
        Regex("^Слой (\\d+) / (\\d+) · (.+)$").find(text)?.let { m ->
            val a = m.groupValues[1]
            val b = m.groupValues[2]
            val rest = m.groupValues[3]
            val parts = rest.split(" ", limit = 2)
            val speed = one(parts[0], language)
            val tail = parts.getOrElse(1) { "" }
            val layer = when (language) {
                "en" -> "Layer $a / $b"
                "de" -> "Schicht $a / $b"
                "fr" -> "Couche $a / $b"
                "es" -> "Capa $a / $b"
                "it" -> "Strato $a / $b"
                "pt" -> "Camada $a / $b"
                "pl" -> "Warstwa $a / $b"
                "tr" -> "Katman $a / $b"
                "zh" -> "层 $a / $b"
                else -> "Слой $a / $b"
            }
            return "$layer · $speed${if (tail.isBlank()) "" else " $tail"}"
        }
        Regex("^(\\d+) катушек · AMS$").find(text)?.let { m ->
            val n = m.groupValues[1]
            return when (language) {
                "en" -> "$n spools · AMS"
                "de" -> "$n Spulen · AMS"
                "fr" -> "$n bobines · AMS"
                "es" -> "$n bobinas · AMS"
                "it" -> "$n bobine · AMS"
                "pt" -> "$n carretéis · AMS"
                "pl" -> "$n szpul · AMS"
                "tr" -> "$n makara · AMS"
                "zh" -> "$n 卷 · AMS"
                else -> null
            }
        }
        Regex("^осталось (\\d+)%$").find(text)?.let { m ->
            val n = m.groupValues[1]
            return when (language) {
                "en" -> "$n% left"
                "de" -> "$n% übrig"
                "fr" -> "$n% restants"
                "es" -> "$n% restante"
                "it" -> "$n% rimasti"
                "pt" -> "$n% restantes"
                "pl" -> "pozostało $n%"
                "tr" -> "$n% kaldı"
                "zh" -> "剩余 $n%"
                else -> null
            }
        }
        Regex("^цель (\\d+)°$").find(text)?.let { m ->
            val n = m.groupValues[1]
            return when (language) {
                "en" -> "target $n°"
                "de" -> "Ziel $n°"
                "fr" -> "cible $n°"
                "es" -> "objetivo $n°"
                "it" -> "obiettivo $n°"
                "pt" -> "alvo $n°"
                "pl" -> "cel $n°"
                "tr" -> "hedef $n°"
                "zh" -> "目标 $n°"
                else -> null
            }
        }
        Regex("^(\\d+) из (\\d+) слотов$").find(text)?.let { m ->
            val (a, b) = m.destructured
            return when (language) {
                "en" -> "$a of $b slots"
                "de" -> "$a von $b Plätzen"
                "fr" -> "$a sur $b emplacements"
                "es" -> "$a de $b ranuras"
                "it" -> "$a di $b slot"
                "pt" -> "$a de $b slots"
                "pl" -> "$a z $b slotów"
                "tr" -> "$a / $b yuva"
                "zh" -> "$a/$b 槽位"
                else -> null
            }
        }
        return null
    }

    /** Английский fallback для длинных текстов, которые ещё не переведены на все языки. */
    private val englishOnly: Map<String, String> = mapOf(
        "локальное управление принтером" to "local printer control",
        "Один раз укажите адрес принтера — дальше всё работает по домашнему Wi-Fi, без облака и аккаунтов." to
            "Set the printer address once — everything else runs over your home Wi-Fi, no cloud or accounts.",
        "ПОДКЛЮЧЕНИЕ" to "CONNECTION",
        "IP-адрес принтера" to "Printer IP address",
        "Код доступа" to "Access code",
        "Серийный номер" to "Serial number",
        "Модель" to "Model",
        "Название принтера" to "Printer name",
        "Подключиться по Wi-Fi" to "Connect over Wi-Fi",
        "или" to "or",
        "Посмотреть приложение без принтера" to "Explore the app without a printer",
        "Открыть демо с печатью" to "Open print demo",
        "ГДЕ ЭТО ВЗЯТЬ" to "WHERE TO FIND IT",
        "IP-адрес" to "IP address",
        "Экран принтера → Настройки → Сеть → IP. Лучше закрепить адрес в роутере." to
            "Printer screen → Settings → Network → IP. Better to reserve the address in your router.",
        "Экран принтера → Настройки → Сеть → Код доступа (8 символов)." to
            "Printer screen → Settings → Network → Access code (8 characters).",
        "На наклейке сзади или в Bambu Handy → Настройки устройства." to
            "On the sticker on the back or in Bambu Handy → Device settings.",
        "Приложение общается с принтером напрямую: MQTT 8883, FTPS 990, камера 6000. Данные не покидают дом." to
            "The app talks to the printer directly: MQTT 8883, FTPS 990, camera 6000. Data never leaves your home.",
        "Скачать на телефон" to "Download to phone",
        "читаем…" to "reading…",
        "объект(ов)" to "items",
        "Поток идёт напрямую по TLS на порт 6000. Снимки сохраняются в галерею в папку BambukHome." to
            "The stream goes directly over TLS to port 6000. Snapshots are saved to the BambukHome gallery folder.",
        "Thingiverse отдаёт файлы через API. Токен бесплатный: Settings → Apps → API." to
            "Thingiverse provides files via API. The token is free: Settings → Apps → API.",
        "MakerWorld и Printables защищены Cloudflare и входом. Для них приложение показывает превью и открывает страницу — 3MF скачивается вручную." to
            "MakerWorld and Printables are protected by Cloudflare and login. The app shows a preview and opens the page — download the 3MF manually.",
        "Перевод применяется сразу. Технические подсказки и названия моделей могут остаться на языке оригинала." to
            "The translation applies immediately. Technical hints and model names may stay in their original language.",
        "Ndot 77 JP Extended — личный пиксельный шрифт. Свой шрифт выберите из памяти телефона: .ttf, .otf или .ttc." to
            "Ndot 77 JP Extended is a personal pixel font. Choose your own font from phone storage: .ttf, .otf or .ttc.",
        "Выбрать шрифт" to "Choose font",
        "Читаем…" to "Reading…",
        "Сначала выберите файл шрифта" to "Choose a font file first",
        "Принтеры" to "Printers",
        "несколько принтеров на одном телефоне" to "several printers on one phone",
        "Профили принтеров" to "Printer profiles",
        "сохранено" to "saved",
        "Добавить принтер" to "Add printer",
        "Новый принтер" to "New printer",
        "Название" to "Name",
        "Демо-режим" to "Demo mode",
        "AMS в демо" to "AMS in demo",
        "Сделать активным" to "Make active",
        "Сохранить" to "Save",
        "Активный принтер" to "Active printer",
        "онлайн" to "online",
        "активен" to "active",
        "Удалить принтер?" to "Delete printer?",
        "Вход и проверка Cloudflare выполняются в браузере телефона. Приложение не хранит пароль и cookie MakerWorld." to
            "Login and Cloudflare verification happen in the phone browser. The app does not store your MakerWorld password or cookies.",
        "Войти / открыть" to "Sign in / open",
        "Сайт" to "Website",
        "Скачайте .3mf на странице модели и импортируйте через «Модели» → «Файл». Так Cloudflare и аккаунт работают без обхода." to
            "Download the .3mf on the model page and import it via “Models” → “File”. Cloudflare and your account work without bypassing anything.",
        "MakerWorld" to "MakerWorld",
        "Вход и Cloudflare — в браузере телефона" to "Sign-in and Cloudflare happen in the phone browser",
        "Войти" to "Sign in",
        "После входа скачайте .3mf на странице модели и вернитесь в «Модели» → «Файл». Пароль и cookie остаются в браузере, приложение их не видит." to
            "After signing in, download the .3mf on the model page and return to “Models” → “File”. Password and cookies stay in the browser; the app never sees them.",
        "Открыть в браузере" to "Open in browser",
        "MakerWorld открыт в браузере. Войдите и скачайте .3mf, затем «Файл»." to
            "MakerWorld opened in the browser. Sign in, download the .3mf, then use “File”.",
        "Не удалось открыть браузер" to "Could not open the browser",
        "модели и аккаунт Bambu Lab" to "models and a Bambu Lab account",
        "аккаунт Bambu Lab подключён" to "Bambu Lab account connected",
        "Аккаунт Bambu Lab" to "Bambu Lab account",
        "Один аккаунт для Bambu Cloud и MakerWorld" to "One account for Bambu Cloud and MakerWorld",
        "Пароль" to "Password",
        "Войти" to "Sign in",
        "Входим…" to "Signing in…",
        "Код" to "Code",
        "Код из письма" to "Code from email",
        "Подтвердить" to "Confirm",
        "Отправить код ещё раз" to "Resend code",
        "Другой аккаунт" to "Another account",
        "Введите код из приложения-аутентификатора" to "Enter the code from your authenticator app",
        "Войти через браузер" to "Sign in via browser",
        "Вход открыт в браузере. Для кабинета в приложении войдите по email ниже." to
            "Sign-in opened in the browser. To use the account in the app, sign in with email below.",
        "Пароль отправляется напрямую в Bambu Cloud по HTTPS и не сохраняется в приложении. Токен входа хранится зашифрованным. Если Bambu просит капчу, используйте вход в браузере." to
            "The password goes directly to Bambu Cloud over HTTPS and is not stored. The sign-in token is encrypted. If Bambu asks for a CAPTCHA, use browser sign-in.",
        "Токен входа хранится зашифрованным. Если Bambu просит капчу, используйте вход в браузере." to
            "The sign-in token is encrypted. If Bambu asks for a CAPTCHA, use browser sign-in.",
        "Найти" to "Search",
        "Ни подписок и ни рекламы. Управление принтером работает локально: IP, код доступа и файлы остаются между телефоном и принтером в домашней сети. Аккаунт Bambu Lab нужен только для дополнительного раздела MakerWorld и полностью опционален." to
            "No subscriptions or ads. Printer control works locally: IP, access code and files stay between your phone and printer on the home network. A Bambu Lab account is needed only for the optional MakerWorld section.",
        "Вкладка MakerWorld ищет модели, показывает профили печати и скачивает 3MF после входа в аккаунт Bambu Lab. Пароль не сохраняется, токен входа шифруется. Вход в браузере открывает официальный SSO Bambu Lab; cookie браузера Android не передаёт приложению, поэтому кабинет использует авторизацию Bambu Cloud." to
            "The MakerWorld tab searches models, shows print profiles and downloads 3MF after signing in to Bambu Lab. The password is not stored and the token is encrypted. Browser sign-in opens the official Bambu Lab SSO; Android does not share browser cookies with apps, so the account uses Bambu Cloud authorization.",
        "Printables отдаёт превью и ссылки. MakerWorld-модели ищите и скачивайте во вкладке MakerWorld — там работает аккаунт Bambu Lab и 3MF." to
            "Printables provides previews and links. Find and download MakerWorld models on the MakerWorld tab — the Bambu Lab account and 3MF downloads work there.",
        "Открыть кабинет" to "Open account",
        "Вход в браузере" to "Browser sign-in",
        "Личный кабинет, поиск и скачивание 3MF" to "Account, search and 3MF downloads",
        "Войдите в аккаунт Bambu Lab прямо в приложении: поиск, избранное и скачивание 3MF работают через официальный Bambu Cloud. Пароль не сохраняется, токен входа хранится зашифрованным." to
            "Sign in to Bambu Lab in the app: search, favorites and 3MF downloads use the official Bambu Cloud. The password is not stored and the token is encrypted.",
        "Если Bambu просит капчу, откройте вход в браузере. Для кабинета в приложении используйте вход по email — он идёт напрямую в Bambu Cloud без Cloudflare." to
            "If Bambu asks for a CAPTCHA, open browser sign-in. For the in-app account use email sign-in — it goes directly to Bambu Cloud without Cloudflare.",
        "Войдите по email Bambu Lab или через браузер на вкладке MakerWorld. Пароль не сохраняется, токен входа хранится зашифрованным." to
            "Sign in with a Bambu Lab email or via the browser on the MakerWorld tab. The password is not stored and the token is encrypted.",
        "Рабочий стол" to "Dashboard",
        "Скройте вкладки, которыми не пользуетесь — они перестанут занимать место внизу." to
            "Hide the tabs you do not use and they will stop taking space at the bottom.",
        "Вкладка «Модели»" to "“Models” tab",
        "Ссылки, импорт файлов и 3D-просмотр" to "Links, file import and 3D viewing",
        "Вкладка MakerWorld" to "MakerWorld tab",
        "Поиск моделей и аккаунт Bambu Lab" to "Model search and Bambu Lab account",
        "Печать" to "Print",
        "Быстрые действия" to "Quick actions",
        "Температуры" to "Temperatures",
        "AMS и пластик" to "AMS and filament",
        "Ошибки" to "Errors",
        "Файлы и управление" to "Files and controls",
        "Настроить экран" to "Customize screen",
        "Двигайте карточки стрелками и скрывайте то, что не нужно. Порядок сохранится." to
            "Move cards with the arrows and hide what you do not need. The order is saved.",
        "Выше" to "Move up",
        "Ниже" to "Move down",
        "Показать" to "Show",
        "Скрыть" to "Hide",
        "Сбросить" to "Reset",
        "Статистика вручную" to "Manual statistics",
        "Впишите общее время печати и число завершённых работ. Дальше приложение продолжит считать само." to
            "Enter total print time and finished jobs. The app will keep counting from there.",
        "Всего часов" to "Total hours",
        "Завершено печатей" to "Finished prints",
        "Введите неотрицательные числа" to "Enter non-negative numbers",
        "Вписать" to "Edit",
        "Строим 3D-пластину…" to "Building the 3D plate…",
        "Не удалось открыть 3D" to "Could not open 3D",
        "Один палец — вращать · два пальца — масштаб" to "One finger to rotate · two fingers to zoom",
        "Сбросить вид" to "Reset view",
        "Открыть 3D" to "Open 3D",
        "Треугольников" to "Triangles",
        "Найти модель на MakerWorld" to "Search MakerWorld",
        "Популярные" to "Popular",
        "Новые" to "New",
        "Лучшие" to "Top",
        "Очистить" to "Clear",
        "Показать ещё" to "Show more",
        "Поиск и карточки моделей доступны без входа. Для «Моих моделей» и скачивания 3MF войдите в аккаунт Bambu Lab." to
            "Search and model cards work without signing in. Sign in to Bambu Lab for My Models and 3MF downloads.",
        "Пока нет моделей" to "No models yet",
        "Ничего не нашлось" to "Nothing found",
        "Попробуйте другой запрос или сортировку." to "Try another query or sorting.",
        "Потяните обновить или измените запрос." to "Refresh or change the query.",
        "Обзор" to "Discover",
        "Мои модели" to "My models",
        "Избранное" to "Favorites",
        "Коллекции" to "Collections",
        "Пока нет опубликованных моделей" to "No published models yet",
        "В избранном пока пусто" to "Your favorites are empty",
        "Добавляйте модели на MakerWorld — они появятся здесь." to
            "Favorite models on MakerWorld and they will appear here.",
        "Коллекций пока нет" to "No collections yet",
        "Создавайте коллекции на MakerWorld — они появятся здесь." to
            "Create collections on MakerWorld and they will appear here.",
        "коллекция" to "collection",
        "Выйти" to "Sign out",
        "Обновить" to "Refresh",
        "Повторить" to "Retry",
        "Модель MakerWorld" to "MakerWorld model",
        "Модель не открылась" to "Could not open the model",
        "Профили печати" to "Print profiles",
        "Пластины" to "Plates",
        "У модели нет готовых профилей печати — можно скачать исходный 3MF." to
            "This model has no print profiles — you can download the source 3MF.",
        "Показать полностью" to "Show more",
        "Свернуть" to "Show less",
        "Для скачивания нужен вход" to "Sign in to download",
        "Скачивание 3MF с MakerWorld доступно после входа в аккаунт Bambu Lab." to
            "Downloading 3MF from MakerWorld requires a Bambu Lab account.",
        "Скачать 3MF" to "Download 3MF",
        "Файл сохраняется в «Загрузки/BambukHome». MakerWorld отдаёт исходный 3MF без G-code — для печати нарежьте его в Bambu Studio или OrcaSlicer." to
            "The file is saved to Downloads/BambukHome. MakerWorld provides the source 3MF without G-code — slice it in Bambu Studio or OrcaSlicer before printing.",
        "Открыть на MakerWorld" to "Open on MakerWorld",
        "Профиль загружен, но Bambu не вернул имя. Можно обновить." to
            "Profile loaded, but Bambu returned no name. You can refresh.",
        "Показать пароль" to "Show password",
        "Скрыть пароль" to "Hide password",
        "Назад" to "Back",
        "Корпус" to "Body",
        "Крышка" to "Lid",
        "Дно" to "Bottom",
        "Стенка" to "Wall",
        "Перегородка" to "Divider",
        "Полка" to "Shelf",
        "Скачать" to "Download",
        "Скачать таймлапс?" to "Download timelapse?",
        "Файл будет сохранён в «Загрузки/BambukHome»." to "The file will be saved to “Downloads/BambukHome”.",
        "Сохранено в Загрузки/BambukHome" to "Saved to Downloads/BambukHome",
        "Не удалось сохранить файл" to "Could not save the file",
        "Сохраняем…" to "Saving…",
        "Нажмите на деталь на пластине или отметьте её в списке." to
            "Tap a part on the plate or select it in the list.",
        "Печать выбранных деталей прекратится со следующего слоя. Действие необратимо." to
            "Printing of selected parts will stop from the next layer. This cannot be undone.",
        "Пропустить" to "Skip",
        "Читаем пластину и детали из 3MF…" to "Reading plate and parts from 3MF…",
        "На этой пластине нет деталей для пропуска" to "There are no parts to skip on this plate",
        "Печать выбранных деталей прекратится со следующего слоя." to "Printing of selected parts will stop from the next layer.",
        "Принтер сам нагревает сопло до температуры пластика, подаёт пруток и проверяет датчик. Следите за экраном принтера: иногда нужно подтвердить действие кнопкой." to
            "The printer heats the nozzle, feeds the filament and checks the sensor. Watch the printer screen: sometimes you need to confirm with a button.",
        "Во время печати заправка поставит её на паузу" to "Loading during a print will pause the print",
        "Есть катушки с остатком ≤15%. Проверьте перед длинной печатью." to
            "Some spools have ≤15% left. Check them before a long print.",
        "Камера · подключение…" to "Camera · connecting…",
        "нажмите, чтобы открыть" to "tap to open",
        "кадры идут напрямую с принтера" to "frames stream directly from the printer",
        "Камера P1S отдаёт кадры по TLS на порту 6000. В темноте включите свет камеры — иначе видно только контур." to
            "The P1S camera sends frames over TLS on port 6000. Turn on the light in the dark, otherwise you only see outlines.",
        "Если сайт закрыт Cloudflare или требует вход, BambukHome покажет карточку и откроет страницу в браузере. Скачанный 3MF добавьте кнопкой «Файл» — он сразу уйдёт на принтер." to
            "If the site is behind Cloudflare or login, BambukHome shows a card and opens the page in your browser. Add the downloaded 3MF via the File button — it goes straight to the printer.",
        "Printables отдаёт список файлов через API, но прямая загрузка закрыта Cloudflare. Нажмите «Открыть на сайте», скачайте .3mf и добавьте через «Файл»." to
            "Printables returns the file list via API, but direct download is blocked by Cloudflare. Tap Open on site, download the .3mf and add it via File.",
    )

    private val dictionary: Map<String, List<String>> = mapOf(
        // ---------------------------------------------------------- навигация
        "Печать" to row("Print", "Drucken", "Imprimer", "Imprimir", "Stampa", "Imprimir", "Drukuj", "Yazdır", "打印"),
        "Пластик" to row("Filament", "Filament", "Filament", "Filamento", "Filamento", "Filamento", "Filament", "Filament", "耗材"),
        "Файлы" to row("Files", "Dateien", "Fichiers", "Archivos", "File", "Arquivos", "Pliki", "Dosyalar", "文件"),
        "Модели" to row("Models", "Modelle", "Modèles", "Modelos", "Modelli", "Modelos", "Modele", "Modeller", "模型"),
        "Управление" to row("Control", "Steuerung", "Contrôle", "Control", "Controllo", "Controle", "Sterowanie", "Kontrol", "控制"),
        "Камера" to row("Camera", "Kamera", "Caméra", "Cámara", "Fotocamera", "Câmera", "Kamera", "Kamera", "摄像头"),
        "Настройки" to row("Settings", "Einstellungen", "Réglages", "Ajustes", "Impostazioni", "Configurações", "Ustawienia", "Ayarlar", "设置"),
        "О принтере" to row("About printer", "Über den Drucker", "À propos de l'imprimante", "Sobre la impresora", "Informazioni stampante", "Sobre a impressora", "O drukarce", "Yazıcı hakkında", "关于打印机"),

        // ---------------------------------------------------------- действия
        "Назад" to row("Back", "Zurück", "Retour", "Atrás", "Indietro", "Voltar", "Wstecz", "Geri", "返回"),
        "Отмена" to row("Cancel", "Abbrechen", "Annuler", "Cancelar", "Annulla", "Cancelar", "Anuluj", "İptal", "取消"),
        "Удалить" to row("Delete", "Löschen", "Supprimer", "Eliminar", "Elimina", "Excluir", "Usuń", "Sil", "删除"),
        "Сохранить" to row("Save", "Speichern", "Enregistrer", "Guardar", "Salva", "Salvar", "Zapisz", "Kaydet", "保存"),
        "Повторить" to row("Retry", "Wiederholen", "Réessayer", "Reintentar", "Riprova", "Tentar novamente", "Ponów", "Tekrar dene", "重试"),
        "Загрузить" to row("Upload", "Hochladen", "Téléverser", "Subir", "Carica", "Enviar", "Wgraj", "Yükle", "上传"),
        "Скачать" to row("Download", "Herunterladen", "Télécharger", "Descargar", "Scarica", "Baixar", "Pobierz", "İndir", "下载"),
        "Печатать" to row("Print", "Drucken", "Imprimer", "Imprimir", "Stampa", "Imprimir", "Drukuj", "Yazdır", "打印"),
        "Начать печать" to row("Start print", "Druck starten", "Lancer l'impression", "Iniciar impresión", "Avvia stampa", "Iniciar impressão", "Rozpocznij druk", "Yazdırmayı başlat", "开始打印"),
        "Готово" to row("Done", "Fertig", "Terminé", "Listo", "Fatto", "Concluído", "Gotowe", "Tamam", "完成"),
        "Пауза" to row("Pause", "Pause", "Pause", "Pausa", "Pausa", "Pausa", "Pauza", "Duraklat", "暂停"),
        "Подготовка" to row("Preparing", "Vorbereitung", "Préparation", "Preparando", "Preparazione", "Preparando", "Przygotowanie", "Hazırlanıyor", "准备中"),
        "Нагрев и калибровка" to row("Heating and calibration", "Heizen und Kalibrieren", "Chauffe et calibration", "Calentando y calibrando", "Riscaldamento e calibrazione", "Aquecimento e calibração", "Grzanie i kalibracja", "Isıtma ve kalibrasyon", "加热与校准"),
        "Продолжить" to row("Resume", "Fortsetzen", "Reprendre", "Reanudar", "Riprendi", "Retomar", "Wznów", "Devam et", "继续"),
        "Стоп" to row("Stop", "Stopp", "Arrêter", "Detener", "Arresta", "Parar", "Zatrzymaj", "Durdur", "停止"),
        "Смотреть" to row("Watch", "Ansehen", "Regarder", "Ver", "Guarda", "Ver", "Odtwórz", "İzle", "观看"),
        "Закрыть" to row("Close", "Schließen", "Fermer", "Cerrar", "Chiudi", "Fechar", "Zamknij", "Kapat", "关闭"),

        // ---------------------------------------------------------- статусы
        "ДЕМО" to row("DEMO", "DEMO", "DÉMO", "DEMO", "DEMO", "DEMO", "DEMO", "DEMO", "演示"),
        "ПЕЧАТЬ" to row("PRINTING", "DRUCKT", "IMPRESSION", "IMPRIMIENDO", "STAMPA", "IMPRIMINDO", "DRUK", "YAZDIRIYOR", "打印中"),
        "ПАУЗА" to row("PAUSED", "PAUSIERT", "EN PAUSE", "EN PAUSA", "IN PAUSA", "EM PAUSA", "PAUZA", "DURAKLATILDI", "已暂停"),
        "ПОДГОТОВКА" to row("PREPARING", "VORBEREITUNG", "PRÉPARATION", "PREPARANDO", "PREPARAZIONE", "PREPARANDO", "PRZYGOTOWANIE", "HAZIRLANIYOR", "准备中"),
        "ГОТОВО" to row("FINISHED", "FERTIG", "TERMINÉ", "TERMINADO", "COMPLETATO", "CONCLUÍDO", "GOTOWE", "TAMAMLANDI", "已完成"),
        "ОШИБКА" to row("ERROR", "FEHLER", "ERREUR", "ERROR", "ERRORE", "ERRO", "BŁĄD", "HATA", "错误"),
        "СВОБОДЕН" to row("IDLE", "BEREIT", "LIBRE", "LIBRE", "LIBERA", "LIVRE", "WOLNA", "BOŞTA", "空闲"),
        "НЕТ СВЯЗИ" to row("OFFLINE", "OFFLINE", "HORS LIGNE", "SIN CONEXIÓN", "OFFLINE", "OFFLINE", "BRAK POŁĄCZENIA", "ÇEVRİMDIŞI", "离线"),
        "ЛОКАЛЬНО" to row("LOCAL", "LOKAL", "LOCAL", "LOCAL", "LOCALE", "LOCAL", "LOKALNIE", "YEREL", "本地"),
        "ПОДКЛЮЧЕНИЕ" to row("CONNECTION", "VERBINDUNG", "CONNEXION", "CONEXIÓN", "CONNESSIONE", "CONEXÃO", "POŁĄCZENIE", "BAĞLANTI", "连接"),
        "ПЕРЕПОДКЛЮЧЕНИЕ" to row("RECONNECTING", "VERBINDE NEU", "RECONNEXION", "RECONECTANDO", "RICONNESSIONE", "RECONECTANDO", "POŁĄCZENIE", "YENİDEN BAĞLANIYOR", "重新连接"),
        "НЕ НАСТРОЕНО" to row("NOT SET UP", "NICHT EINGERICHTET", "NON CONFIGURÉ", "SIN CONFIGURAR", "NON CONFIGURATO", "NÃO CONFIGURADO", "NIE SKONFIGUROWANO", "AYARLANMADI", "未设置"),
        "ДЕМО · ПЕЧАТЬ" to row("DEMO · PRINTING", "DEMO · DRUCKT", "DÉMO · IMPRESSION", "DEMO · IMPRIMIENDO", "DEMO · STAMPA", "DEMO · IMPRIMINDO", "DEMO · DRUK", "DEMO · YAZDIRIYOR", "演示 · 打印中"),
        "напечатано" to row("printed", "gedruckt", "imprimé", "impreso", "stampato", "impresso", "wydrukowano", "yazdırıldı", "已打印"),

        // ---------------------------------------------------------- обзор
        "демонстрация · без принтера" to row("demo · no printer", "Demo · ohne Drucker", "démo · sans imprimante", "demo · sin impresora", "demo · senza stampante", "demo · sem impressora", "demo · bez drukarki", "demo · yazıcısız", "演示 · 无打印机"),
        "Осталось" to row("Remaining", "Restzeit", "Restant", "Restante", "Rimanente", "Restante", "Pozostało", "Kalan", "剩余"),
        "Слой" to row("Layer", "Schicht", "Couche", "Capa", "Strato", "Camada", "Warstwa", "Katman", "层"),
        "Настроить" to row("Adjust", "Anpassen", "Régler", "Ajustar", "Regola", "Ajustar", "Dostosuj", "Ayarla", "调整"),
        "Выбрать файл" to row("Choose file", "Datei wählen", "Choisir un fichier", "Elegir archivo", "Scegli file", "Escolher arquivo", "Wybierz plik", "Dosya seç", "选择文件"),
        "Свет вкл" to row("Light on", "Licht an", "Lumière on", "Luz on", "Luce on", "Luz on", "Światło wł.", "Işık açık", "灯光开"),
        "Свет" to row("Light", "Licht", "Lumière", "Luz", "Luce", "Luz", "Światło", "Işık", "灯光"),
        "Стандарт" to row("Standard", "Standard", "Standard", "Estándar", "Standard", "Padrão", "Standard", "Standart", "标准"),
        "Тихий" to row("Silent", "Leise", "Silencieux", "Silencioso", "Silenzioso", "Silencioso", "Cichy", "Sessiz", "静音"),
        "Спорт" to row("Sport", "Sport", "Sport", "Deportivo", "Sport", "Esporte", "Sport", "Spor", "运动"),
        "Безум" to row("Ludicrous", "Wahnsinn", "Extrême", "Extremo", "Estremo", "Extremo", "Szalony", "Çılgın", "极速"),
        "СОПЛО" to row("NOZZLE", "DÜSE", "BUSE", "BOQUILLA", "UGELLO", "BICO", "DYSZA", "NOZZLE", "喷嘴"),
        "СТОЛ" to row("BED", "BETT", "PLATEAU", "CAMA", "PIANO", "MESA", "STÓŁ", "TABLA", "热床"),
        "КАМЕРА" to row("CHAMBER", "KAMMER", "CHAMBRE", "CÁMARA", "CAMERA", "CÂMARA", "KOMORA", "HAZNE", "腔体"),
        "цель" to row("target", "Ziel", "cible", "objetivo", "obiettivo", "alvo", "cel", "hedef", "目标"),
        "датчик" to row("sensor", "Sensor", "capteur", "sensor", "sensore", "sensor", "czujnik", "sensör", "传感器"),
        "внешняя катушка" to row("external spool", "externe Spule", "bobine externe", "bobina externa", "bobina esterna", "carretel externo", "szpula zewnętrzna", "harici makara", "外置料卷"),
        "АКТИВНА" to row("ACTIVE", "AKTIV", "ACTIVE", "ACTIVA", "ATTIVA", "ATIVA", "AKTYWNA", "AKTİF", "已启用"),

        // ---------------------------------------------------------- управление
        "Температуры" to row("Temperatures", "Temperaturen", "Températures", "Temperaturas", "Temperature", "Temperaturas", "Temperatury", "Sıcaklıklar", "温度"),
        "Сопло" to row("Nozzle", "Düse", "Buse", "Boquilla", "Ugello", "Bico", "Dysza", "Nozzle", "喷嘴"),
        "Стол" to row("Bed", "Bett", "Plateau", "Cama", "Piano", "Mesa", "Stół", "Tabla", "热床"),
        "сейчас" to row("now", "jetzt", "actuel", "ahora", "ora", "agora", "teraz", "şimdi", "当前"),
        "Вентиляторы" to row("Fans", "Lüfter", "Ventilateurs", "Ventiladores", "Ventole", "Ventoinhas", "Wentylatory", "Fanlar", "风扇"),
        "Обдув модели" to row("Part cooling", "Bauteilkühlung", "Refroidissement pièce", "Ventilador de pieza", "Raffreddamento pezzo", "Ventilador da peça", "Chłodzenie modelu", "Parça soğutma", "模型风扇"),
        "Доп. вентилятор" to row("Aux fan", "Zusatzlüfter", "Ventilateur aux.", "Ventilador aux.", "Ventola ausiliaria", "Ventilador aux.", "Wentylator dodatkowy", "Ek fan", "辅助风扇"),
        "Вентилятор камеры" to row("Chamber fan", "Kammerlüfter", "Ventilateur chambre", "Ventilador cámara", "Ventola camera", "Ventilador câmara", "Wentylator komory", "Hazne fanı", "腔体风扇"),
        "Скорость" to row("Speed", "Geschwindigkeit", "Vitesse", "Velocidad", "Velocità", "Velocidade", "Prędkość", "Hız", "速度"),
        "Перемещение" to row("Movement", "Bewegung", "Déplacement", "Movimiento", "Movimento", "Movimento", "Ruch", "Hareket", "移动"),
        "Шаг" to row("Step", "Schritt", "Pas", "Paso", "Passo", "Passo", "Krok", "Adım", "步长"),
        "Домой" to row("Home", "Home", "Origine", "Inicio", "Home", "Início", "Początek", "Ana konum", "回原点"),
        "Пропуск" to row("Skip", "Überspringen", "Ignorer", "Omitir", "Salta", "Ignorar", "Pomiń", "Atla", "跳过"),
        "Пропуск деталей" to row("Skip objects", "Objekte überspringen", "Ignorer des objets", "Omitir objetos", "Salta oggetti", "Ignorar objetos", "Pomiń obiekty", "Nesneleri atla", "跳过对象"),
        "G-code" to row("G-code", "G-code", "G-code", "G-code", "G-code", "G-code", "G-code", "G-code", "G-code"),
        "Отправить" to row("Send", "Senden", "Envoyer", "Enviar", "Invia", "Enviar", "Wyślij", "Gönder", "发送"),
        "во время печати недоступно" to row("unavailable while printing", "während des Drucks gesperrt", "indisponible pendant l'impression", "no disponible imprimiendo", "non disponibile durante la stampa", "indisponível durante a impressão", "niedostępne podczas druku", "yazdırırken kullanılamaz", "打印时不可用"),

        // ---------------------------------------------------------- AMS
        "Катушек" to row("Spools", "Spulen", "Bobines", "Bobinas", "Bobine", "Carretéis", "Szpule", "Makaralar", "料卷"),
        "Средний остаток" to row("Avg. remaining", "Ø Rest", "Reste moyen", "Resto medio", "Resto medio", "Resto médio", "Średni zapas", "Ortalama kalan", "平均剩余"),
        "Мало" to row("Low", "Gering", "Bas", "Bajo", "Basso", "Baixo", "Mało", "Az", "不足"),
        "Внешняя катушка" to row("External spool", "Externe Spule", "Bobine externe", "Bobina externa", "Bobina esterna", "Carretel externo", "Szpula zewnętrzna", "Harici makara", "外置料卷"),
        "Катушка сбоку" to row("Side spool", "Seitliche Spule", "Bobine latérale", "Bobina lateral", "Bobina laterale", "Carretel lateral", "Szpula z boku", "Yan makara", "侧置料卷"),
        "Заправить" to row("Load", "Laden", "Charger", "Cargar", "Carica", "Carregar", "Załaduj", "Yükle", "进料"),
        "Выгрузить" to row("Unload", "Entladen", "Décharger", "Descargar", "Scarica", "Descarregar", "Wyładuj", "Boşalt", "退料"),
        "ЗАПРАВЛЕНА" to row("LOADED", "GELADEN", "CHARGÉE", "CARGADA", "CARICATA", "CARREGADO", "ZAŁADOWANA", "YÜKLÜ", "已装载"),
        "Как это работает" to row("How it works", "So funktioniert's", "Comment ça marche", "Cómo funciona", "Come funziona", "Como funciona", "Jak to działa", "Nasıl çalışır", "工作原理"),
        "Влажность в норме" to row("Humidity is normal", "Luftfeuchtigkeit normal", "Humidité normale", "Humedad normal", "Umidità nella norma", "Umidade normal", "Wilgotność w normie", "Nem normal", "湿度正常"),
        "Влажность высокая — просушите катушки" to row("High humidity — dry the spools", "Hohe Luftfeuchtigkeit – Spulen trocknen", "Humidité élevée — séchez les bobines", "Humedad alta — seca las bobinas", "Umidità alta — asciuga le bobine", "Umidade alta — seque os carretéis", "Wysoka wilgotność — osusz szpule", "Nem yüksek — makaraları kurutun", "湿度高——请干燥料卷"),
        "пусто" to row("empty", "leer", "vide", "vacío", "vuoto", "vazio", "puste", "boş", "空"),
        "Не заряжена" to row("Not loaded", "Nicht geladen", "Non chargée", "Sin cargar", "Non caricata", "Não carregado", "Nie załadowano", "Yüklü değil", "未装载"),
        "Бренд" to row("Brand", "Marke", "Marque", "Marca", "Marca", "Marca", "Marka", "Marka", "品牌"),
        "Линия" to row("Line", "Serie", "Série", "Línea", "Linea", "Linha", "Linia", "Seri", "系列"),
        "Остаток" to row("Remaining", "Rest", "Reste", "Resto", "Resto", "Resto", "Pozostało", "Kalan", "剩余"),
        "Температура сопла" to row("Nozzle temperature", "Düsentemperatur", "Température buse", "Temperatura de boquilla", "Temperatura ugello", "Temperatura do bico", "Temperatura dyszy", "Nozzle sıcaklığı", "喷嘴温度"),
        "Рекомендуемая заправка" to row("Recommended load temp", "Empfohlene Ladetemperatur", "Température de chargement", "Temperatura de carga", "Temperatura di carico", "Temperatura de carregamento", "Temperatura ładowania", "Önerilen yükleme", "建议进料温度"),
        "Уже заправлена" to row("Already loaded", "Bereits geladen", "Déjà chargée", "Ya cargada", "Già caricata", "Já carregado", "Już załadowana", "Zaten yüklü", "已装载"),
        "Смена прутка" to row("Filament change", "Filamentwechsel", "Changement de filament", "Cambio de filamento", "Cambio filamento", "Troca de filamento", "Zmiana filamentu", "Filament değişimi", "换料"),

        // ---------------------------------------------------------- файлы
        "Карта принтера" to row("Printer storage", "Druckerspeicher", "Carte imprimante", "Tarjeta de impresora", "Scheda stampante", "Cartão da impressora", "Karta drukarki", "Yazıcı kartı", "打印机存储卡"),
        "папка" to row("folder", "Ordner", "dossier", "carpeta", "cartella", "pasta", "folder", "klasör", "文件夹"),
        "SD-карта недоступна" to row("SD card unavailable", "SD-Karte nicht verfügbar", "Carte SD indisponible", "Tarjeta SD no disponible", "Scheda SD non disponibile", "Cartão SD indisponível", "Karta SD niedostępna", "SD kart kullanılamıyor", "SD 卡不可用"),
        "Папка пуста" to row("Folder is empty", "Ordner ist leer", "Dossier vide", "Carpeta vacía", "Cartella vuota", "Pasta vazia", "Folder jest pusty", "Klasör boş", "文件夹为空"),
        "Смотреть таймлапс" to row("Watch timelapse", "Timelapse ansehen", "Voir le timelapse", "Ver timelapse", "Guarda timelapse", "Ver timelapse", "Odtwórz timelapse", "Timelapse izle", "观看延时摄影"),
        "Удалить файл?" to row("Delete file?", "Datei löschen?", "Supprimer le fichier ?", "¿Eliminar archivo?", "Eliminare file?", "Excluir arquivo?", "Usunąć plik?", "Dosya silinsin mi?", "删除文件？"),

        // ---------------------------------------------------------- модели
        "ссылка или файл с телефона" to row("link or file from phone", "Link oder Datei vom Handy", "lien ou fichier du téléphone", "enlace o archivo del teléfono", "link o file dal telefono", "link ou arquivo do telefone", "link lub plik z telefonu", "bağlantı veya telefondan dosya", "链接或手机文件"),
        "ВСТАВЬТЕ ССЫЛКУ" to row("PASTE A LINK", "LINK EINFÜGEN", "COLLEZ UN LIEN", "PEGA UN ENLACE", "INCOLLA UN LINK", "COLE UM LINK", "WSTAW LINK", "BAĞLANTI EKLE", "粘贴链接"),
        "Вставить" to row("Paste", "Einfügen", "Coller", "Pegar", "Incolla", "Colar", "Wklej", "Yapıştır", "粘贴"),
        "Разобрать ссылку" to row("Resolve link", "Link prüfen", "Analyser le lien", "Analizar enlace", "Analizza link", "Analisar link", "Sprawdź link", "Bağlantıyı çöz", "解析链接"),
        "Файл" to row("File", "Datei", "Fichier", "Archivo", "File", "Arquivo", "Plik", "Dosya", "文件"),
        "Выбранная модель" to row("Selected model", "Ausgewähltes Modell", "Modèle sélectionné", "Modelo seleccionado", "Modello selezionato", "Modelo selecionado", "Wybrany model", "Seçili model", "已选模型"),
        "Недавние файлы" to row("Recent files", "Letzte Dateien", "Fichiers récents", "Archivos recientes", "File recenti", "Arquivos recentes", "Ostatnie pliki", "Son dosyalar", "最近文件"),
        "Отправить и печатать" to row("Send and print", "Senden und drucken", "Envoyer et imprimer", "Enviar e imprimir", "Invia e stampa", "Enviar e imprimir", "Wyślij i drukuj", "Gönder ve yazdır", "发送并打印"),
        "Открыть в браузере" to row("Open in browser", "Im Browser öffnen", "Ouvrir dans le navigateur", "Abrir en navegador", "Apri nel browser", "Abrir no navegador", "Otwórz w przeglądarce", "Tarayıcıda aç", "在浏览器中打开"),
        "ЧТО ПОДДЕРЖИВАЕТСЯ" to row("SUPPORTED SITES", "UNTERSTÜTZT", "SITES PRIS EN CHARGE", "SITIOS COMPATIBLES", "SITI SUPPORTATI", "SITES COMPATÍVEIS", "OBSŁUGIWANE STRONY", "DESTEKLENEN SİTELER", "支持的网站"),
        "автор" to row("author", "Autor", "auteur", "autor", "autore", "autor", "autor", "yazar", "作者"),

        // ---------------------------------------------------------- профили
        "Профиль печати" to row("Print profile", "Druckprofil", "Profil d'impression", "Perfil de impresión", "Profilo di stampa", "Perfil de impressão", "Profil druku", "Baskı profili", "打印配置"),
        "Черновик" to row("Draft", "Entwurf", "Brouillon", "Borrador", "Bozza", "Rascunho", "Szkic", "Taslak", "草稿"),
        "Качество" to row("Quality", "Qualität", "Qualité", "Calidad", "Qualità", "Qualidade", "Jakość", "Kalite", "质量"),
        "Ночной" to row("Night", "Nacht", "Nuit", "Noche", "Notte", "Noite", "Nocny", "Gece", "夜间"),
        "Печать из AMS" to row("Print from AMS", "Aus AMS drucken", "Imprimer depuis l'AMS", "Imprimir desde AMS", "Stampa da AMS", "Imprimir do AMS", "Drukuj z AMS", "AMS'ten yazdır", "从 AMS 打印"),
        "Сопоставление цветов" to row("Color mapping", "Farbzuordnung", "Correspondance des couleurs", "Asignación de colores", "Mappatura colori", "Mapeamento de cores", "Mapowanie kolorów", "Renk eşleme", "颜色映射"),
        "Катушка для печати" to row("Spool to print", "Spule zum Drucken", "Bobine à imprimer", "Bobina para imprimir", "Bobina da stampare", "Carretel para imprimir", "Szpula do druku", "Yazdırılacak makara", "打印料卷"),

        "Виртуальный принтер для знакомства" to row("Virtual printer for exploring", "Virtueller Drucker zum Kennenlernen", "Imprimante virtuelle pour découvrir", "Impresora virtual para explorar", "Stampante virtuale per esplorare", "Impressora virtual para explorar", "Wirtualna drukarka do testów", "Keşif için sanal yazıcı", "用于体验的虚拟打印机"),
        "у принтера обычно 990" to row("usually 990 on the printer", "normalerweise 990 am Drucker", "généralement 990 sur l'imprimante", "normalmente 990 en la impresora", "di solito 990 sulla stampante", "geralmente 990 na impressora", "zwykle 990 w drukarce", "genellikle yazıcıda 990", "打印机通常为 990"),
        "серийник, прошивка, часы печати" to row("serial, firmware, print hours", "Seriennummer, Firmware, Druckstunden", "n° de série, firmware, heures", "serie, firmware, horas", "seriale, firmware, ore", "série, firmware, horas", "numer, firmware, godziny", "seri, yazılım, saatler", "序列号、固件、打印时长"),
        "Чистый чёрный фон в тёмной теме" to row("Pure black background in dark theme", "Reiner schwarzer Hintergrund im Dunkelmodus", "Fond noir pur en thème sombre", "Fondo negro puro en tema oscuro", "Sfondo nero puro in tema scuro", "Fundo preto puro no tema escuro", "Czarne tło w trybie ciemnym", "Koyu temada saf siyah", "深色主题下的纯黑背景"),
        "Живой индикатор прогресса" to row("Animated progress indicator", "Animierter Fortschritt", "Indicateur animé", "Indicador animado", "Indicatore animato", "Indicador animado", "Animowany wskaźnik", "Canlı ilerleme göstergesi", "动态进度指示"),
        "Пока открыто приложение" to row("While the app is open", "Solange die App offen ist", "Tant que l'app est ouverte", "Mientras la app está abierta", "Finché l'app è aperta", "Enquanto o app está aberto", "Gdy aplikacja jest otwarta", "Uygulama açıkken", "应用打开时"),
        "Требуется разрешение системы" to row("Requires system permission", "Systemberechtigung nötig", "Autorisation système requise", "Requiere permiso del sistema", "Richiede autorizzazione", "Requer permissão do sistema", "Wymaga uprawnień", "Sistem izni gerekir", "需要系统权限"),
        "Тихо, без звука: проценты, слой и остаток" to row("Silent: percent, layer, remaining", "Lautlos: Prozent, Schicht, Restzeit", "Silencieux : pourcentage, couche, reste", "Silencioso: porcentaje, capa, resto", "Silenzioso: percentuale, strato, resto", "Silencioso: %, camada, restante", "Cicho: procent, warstwa, czas", "Sessiz: yüzde, katman, kalan", "静音：百分比、层、剩余"),
        "Включить подсветку камеры, если она выключена" to row("Turn on camera light if off", "Kamerlicht einschalten, wenn aus", "Allumer la lumière si éteinte", "Encender la luz si está apagada", "Accendi la luce se spenta", "Ligar a luz se estiver desligada", "Włącz światło, jeśli wyłączone", "Kapalıysa ışığı aç", "若关闭则打开摄像头灯"),
        "Снимать видео печати" to row("Record print video", "Druckvideo aufnehmen", "Enregistrer la vidéo", "Grabar vídeo", "Registra video", "Gravar vídeo", "Nagraj wideo", "Baskı videosu kaydet", "录制打印视频"),
        "Перед каждым запуском" to row("Before every print", "Vor jedem Druck", "Avant chaque impression", "Antes de cada impresión", "Prima di ogni stampa", "Antes de cada impressão", "Przed każdym drukiem", "Her baskıdan önce", "每次打印前"),
        "Точнее стенки, дольше старт" to row("Better walls, longer start", "Genauere Wände, längerer Start", "Parois plus précises, départ plus long", "Paredes más precisas, inicio más largo", "Pareti più precise, avvio più lungo", "Paredes melhores, início mais longo", "Dokładniejsze ściany, dłuższy start", "Daha iyi duvarlar, uzun başlangıç", "更精细的墙，启动更久"),
        "Меньше артефактов на углах" to row("Fewer corner artifacts", "Weniger Eckenartefakte", "Moins d'artefacts aux angles", "Menos artefactos en esquinas", "Meno artefatti agli angoli", "Menos artefatos nos cantos", "Mniej artefaktów na rogach", "Köşelerde daha az hata", "减少转角瑕疵"),
        "Автоподача выбранных катушек" to row("Auto-feed selected spools", "Automatische Zufuhr gewählter Spulen", "Alimentation auto des bobines", "Alimentación automática", "Alimentazione automatica", "Alimentação automática das bobinas", "Automatyczne podawanie", "Seçili makaraları otomatik besle", "自动送入所选料卷"),
        "Меньше «звона» на углах" to row("Less ringing on corners", "Weniger Klingeln an Ecken", "Moins de résonance aux angles", "Menos vibración en esquinas", "Meno ringing agli angoli", "Menos ressonância nos cantos", "Mniej dzwonienia na rogach", "Köşelerde daha az titreşim", "减少转角振纹"),
        "Принтер выключен" to row("Printer is offline", "Drucker ist aus", "Imprimante hors ligne", "Impresora apagada", "Stampante spenta", "Impressora desligada", "Drukarka wyłączona", "Yazıcı kapalı", "打印机已关闭"),
        "Проверьте питание и Wi-Fi" to row("Check power and Wi-Fi", "Strom und WLAN prüfen", "Vérifiez l'alimentation et le Wi-Fi", "Comprueba corriente y Wi-Fi", "Controlla alimentazione e Wi-Fi", "Verifique energia e Wi-Fi", "Sprawdź zasilanie i Wi-Fi", "Gücü ve Wi-Fi'yi kontrol edin", "检查电源和 Wi-Fi"),
        "Принтер свободен" to row("Printer is idle", "Drucker ist bereit", "Imprimante libre", "Impresora libre", "Stampante libera", "Impressora livre", "Drukarka wolna", "Yazıcı boşta", "打印机空闲"),
        "Можно забирать со стола" to row("Ready to remove", "Kann entnommen werden", "Prêt à retirer", "Listo para retirar", "Pronto da rimuovere", "Pronto para retirar", "Gotowe do zdjęcia", "Tablodan alınabilir", "可以取下了"),
        "Отправьте 3MF из «Файлов» или «Моделей»" to row("Send a 3MF from Files or Models", "3MF aus Dateien oder Modellen senden", "Envoyez un 3MF depuis Fichiers ou Modèles", "Envía un 3MF desde Archivos o Modelos", "Invia un 3MF da File o Modelli", "Envie um 3MF de Arquivos ou Modelos", "Wyślij 3MF z Plików lub Modeli", "Dosyalar veya Modellerden 3MF gönderin", "从文件或模型发送 3MF"),
        // ---------------------------------------------------------- настройки
        "всё локально" to row("all local", "alles lokal", "tout en local", "todo local", "tutto locale", "tudo local", "wszystko lokalnie", "tamamen yerel", "完全本地"),
        "Принтер" to row("Printer", "Drucker", "Imprimante", "Impresora", "Stampante", "Impressora", "Drukarka", "Yazıcı", "打印机"),
        "Изменить" to row("Change", "Ändern", "Modifier", "Cambiar", "Modifica", "Alterar", "Zmień", "Değiştir", "更改"),
        "Переподключиться" to row("Reconnect", "Neu verbinden", "Reconnecter", "Reconectar", "Riconnetti", "Reconectar", "Połącz ponownie", "Yeniden bağlan", "重新连接"),
        "Порт FTPS" to row("FTPS port", "FTPS-Port", "Port FTPS", "Puerto FTPS", "Porta FTPS", "Porta FTPS", "Port FTPS", "FTPS portu", "FTPS 端口"),
        "Демо-режим" to row("Demo mode", "Demo-Modus", "Mode démo", "Modo demo", "Modalità demo", "Modo demo", "Tryb demo", "Demo modu", "演示模式"),
        "Внешний вид" to row("Appearance", "Aussehen", "Apparence", "Apariencia", "Aspetto", "Aparência", "Wygląd", "Görünüm", "外观"),
        "Тема" to row("Theme", "Design", "Thème", "Tema", "Tema", "Tema", "Motyw", "Tema", "主题"),
        "Системная" to row("System", "System", "Système", "Sistema", "Sistema", "Sistema", "Systemowy", "Sistem", "跟随系统"),
        "Светлая" to row("Light", "Hell", "Clair", "Claro", "Chiaro", "Claro", "Jasny", "Açık", "浅色"),
        "Тёмная" to row("Dark", "Dunkel", "Sombre", "Oscuro", "Scuro", "Escuro", "Ciemny", "Koyu", "深色"),
        "Цвет приложения" to row("App color", "App-Farbe", "Couleur de l'app", "Color de la app", "Colore app", "Cor do app", "Kolor aplikacji", "Uygulama rengi", "应用颜色"),
        "Система" to row("System", "System", "Système", "Sistema", "Sistema", "Sistema", "System", "Sistem", "系统"),
        "Фирменный" to row("Brand", "Marke", "Marque", "Marca", "Marchio", "Marca", "Markowy", "Marka", "品牌"),
        "Свой" to row("Custom", "Eigene", "Personnalisé", "Personalizado", "Personalizzato", "Personalizado", "Własny", "Özel", "自定义"),
        "AMOLED-чёрный" to row("AMOLED black", "AMOLED-Schwarz", "Noir AMOLED", "Negro AMOLED", "Nero AMOLED", "Preto AMOLED", "Czerń AMOLED", "AMOLED siyah", "AMOLED 纯黑"),
        "Масштаб интерфейса" to row("Interface scale", "Oberflächengröße", "Échelle de l'interface", "Escala de interfaz", "Scala interfaccia", "Escala da interface", "Skala interfejsu", "Arayüz ölçeği", "界面缩放"),
        "Форма волны" to row("Wave shape", "Wellenform", "Forme de l'onde", "Forma de onda", "Forma onda", "Forma da onda", "Kształt fali", "Dalga biçimi", "波浪样式"),
        "Тонкая" to row("Thin", "Dünn", "Fine", "Fina", "Sottile", "Fina", "Cienka", "İnce", "细"),
        "Обычная" to row("Normal", "Normal", "Normale", "Normal", "Normale", "Normal", "Normalna", "Normal", "标准"),
        "Толстая" to row("Thick", "Dick", "Épaisse", "Gruesa", "Spessa", "Grossa", "Gruba", "Kalın", "粗"),
        "Прямая" to row("Straight", "Gerade", "Droite", "Recta", "Dritta", "Reta", "Prosta", "Düz", "平直"),
        "Волнистая" to row("Wavy", "Wellig", "Ondulée", "Ondulada", "Ondulata", "Ondulada", "Falista", "Dalgalı", "波浪"),
        "Сильная" to row("Strong", "Stark", "Forte", "Fuerte", "Forte", "Forte", "Mocna", "Güçlü", "强烈"),
        "Волновая анимация" to row("Wave animation", "Wellenanimation", "Animation d'onde", "Animación de onda", "Animazione onda", "Animação de onda", "Animacja fali", "Dalga animasyonu", "波浪动画"),
        "Не гасить экран" to row("Keep screen on", "Bildschirm an", "Écran allumé", "Mantener pantalla", "Schermo acceso", "Manter tela ligada", "Ekran włączony", "Ekranı açık tut", "保持屏幕常亮"),
        "Шрифт" to row("Font", "Schrift", "Police", "Fuente", "Carattere", "Fonte", "Czcionka", "Yazı tipi", "字体"),
        "Выбрать шрифт" to row("Choose font", "Schrift wählen", "Choisir une police", "Elegir fuente", "Scegli carattere", "Escolher fonte", "Wybierz czcionkę", "Yazı tipi seç", "选择字体"),
        "Читаем…" to row("Reading…", "Lesen…", "Lecture…", "Leyendo…", "Lettura…", "Lendo…", "Czytanie…", "Okunuyor…", "读取中…"),
        "Ndot 77 JP Extended — личный пиксельный шрифт. Свой шрифт выберите из памяти телефона: .ttf, .otf или .ttc." to row(
            "Ndot 77 JP Extended is a personal pixel font. Choose your own font from phone storage: .ttf, .otf or .ttc.",
            "Ndot 77 JP Extended ist eine persönliche Pixel-Schrift. Eigene Schrift aus dem Telefonspeicher wählen: .ttf, .otf oder .ttc.",
            "Ndot 77 JP Extended est une police pixel personnelle. Choisissez votre police dans le stockage du téléphone : .ttf, .otf ou .ttc.",
            "Ndot 77 JP Extended es una fuente pixel personal. Elige tu fuente del almacenamiento del teléfono: .ttf, .otf o .ttc.",
            "Ndot 77 JP Extended è un font pixel personale. Scegli il tuo font dalla memoria del telefono: .ttf, .otf o .ttc.",
            "Ndot 77 JP Extended é uma fonte pixel pessoal. Escolha sua fonte no armazenamento do telefone: .ttf, .otf ou .ttc.",
            "Ndot 77 JP Extended to osobista czcionka pikselowa. Wybierz własną czcionkę z pamięci telefonu: .ttf, .otf lub .ttc.",
            "Ndot 77 JP Extended kişisel bir piksel yazı tipidir. Kendi yazı tipini telefon belleğinden seç: .ttf, .otf veya .ttc.",
            "Ndot 77 JP Extended 是个人像素字体。从手机存储中选择字体：.ttf、.otf 或 .ttc。"
        ),
        "Сначала выберите файл шрифта" to row("Choose a font file first", "Wähle zuerst eine Schriftdatei", "Choisissez d'abord un fichier de police", "Elige primero un archivo de fuente", "Scegli prima un file di font", "Escolha primeiro um arquivo de fonte", "Najpierw wybierz plik czcionki", "Önce bir yazı tipi dosyası seç", "请先选择字体文件"),
        "Системный" to row("System", "System", "Système", "Sistema", "Sistema", "Sistema", "Systemowy", "Sistem", "系统"),
        "Печать по умолчанию" to row("Default print", "Standarddruck", "Impression par défaut", "Impresión predeterminada", "Stampa predefinita", "Impressão padrão", "Domyślny druk", "Varsayılan baskı", "默认打印"),
        "Таймлапс" to row("Timelapse", "Timelapse", "Timelapse", "Timelapse", "Timelapse", "Timelapse", "Timelapse", "Timelapse", "延时摄影"),
        "Выравнивание стола" to row("Bed leveling", "Bettnivellierung", "Nivellement du plateau", "Nivelación de cama", "Livellamento piano", "Nivelamento da mesa", "Poziomowanie stołu", "Tabla seviyeleme", "热床调平"),
        "Калибровка потока" to row("Flow calibration", "Flusskalibrierung", "Calibration du débit", "Calibración de flujo", "Calibrazione flusso", "Calibração de fluxo", "Kalibracja przepływu", "Akış kalibrasyonu", "流量校准"),
        "Калибровка вибраций" to row("Vibration calibration", "Vibrationskalibrierung", "Calibration des vibrations", "Calibración de vibraciones", "Calibrazione vibrazioni", "Calibração de vibração", "Kalibracja drgań", "Titreşim kalibrasyonu", "振动校准"),
        "Папка для загрузки" to row("Upload folder", "Upload-Ordner", "Dossier d'envoi", "Carpeta de subida", "Cartella di caricamento", "Pasta de envio", "Folder wysyłania", "Yükleme klasörü", "上传文件夹"),
        "Уведомления" to row("Notifications", "Benachrichtigungen", "Notifications", "Notificaciones", "Notifiche", "Notificações", "Powiadomienia", "Bildirimler", "通知"),
        "Показывать события" to row("Show events", "Ereignisse anzeigen", "Afficher les événements", "Mostrar eventos", "Mostra eventi", "Mostrar eventos", "Pokaż zdarzenia", "Olayları göster", "显示事件"),
        "Прогресс в шторке" to row("Progress in shade", "Fortschritt in der Leiste", "Progression dans la barre", "Progreso en la barra", "Avanzamento nella barra", "Progresso na barra", "Postęp w pasku", "Bildirimde ilerleme", "通知栏进度"),
        "Печать завершена" to row("Print finished", "Druck fertig", "Impression terminée", "Impresión terminada", "Stampa completata", "Impressão concluída", "Druk zakończony", "Baskı bitti", "打印完成"),
        "Ошибки и HMS" to row("Errors and HMS", "Fehler und HMS", "Erreurs et HMS", "Errores y HMS", "Errori e HMS", "Erros e HMS", "Błędy i HMS", "Hatalar ve HMS", "错误与 HMS"),
        "Свет при открытии" to row("Light on open", "Licht beim Öffnen", "Lumière à l'ouverture", "Luz al abrir", "Luce all'apertura", "Luz ao abrir", "Światło przy otwarciu", "Açınca ışık", "打开时开灯"),
        "Токен Thingiverse" to row("Thingiverse token", "Thingiverse-Token", "Jeton Thingiverse", "Token de Thingiverse", "Token Thingiverse", "Token do Thingiverse", "Token Thingiverse", "Thingiverse tokenı", "Thingiverse 令牌"),
        "О приложении" to row("About", "Über die App", "À propos", "Acerca de", "Informazioni", "Sobre", "O aplikacji", "Hakkında", "关于"),
        "Версия" to row("Version", "Version", "Version", "Versión", "Versione", "Versão", "Wersja", "Sürüm", "版本"),
        "Пакет" to row("Package", "Paket", "Paquet", "Paquete", "Pacchetto", "Pacote", "Pakiet", "Paket", "包名"),
        "Связь" to row("Connection", "Verbindung", "Connexion", "Conexión", "Connessione", "Conexão", "Połączenie", "Bağlantı", "连接"),
        "Полностью бесплатно" to row("Completely free", "Völlig kostenlos", "Entièrement gratuit", "Totalmente gratis", "Completamente gratis", "Totalmente grátis", "Całkowicie bezpłatnie", "Tamamen ücretsiz", "完全免费"),
        "Связаться с автором" to row("Contact the author", "Autor kontaktieren", "Contacter l'auteur", "Contactar al autor", "Contatta l'autore", "Contatar o autor", "Kontakt z autorem", "Yazarla iletişim", "联系作者"),
        "Сбросить подключение" to row("Reset connection", "Verbindung löschen", "Réinitialiser la connexion", "Restablecer conexión", "Reimposta connessione", "Redefinir conexão", "Resetuj połączenie", "Bağlantıyı sıfırla", "重置连接"),
        "Язык приложения" to row("App language", "App-Sprache", "Langue de l'app", "Idioma de la app", "Lingua app", "Idioma do app", "Język aplikacji", "Uygulama dili", "应用语言"),

        // ---------------------------------------------------------- камера
        "Демо-поток" to row("Demo stream", "Demo-Stream", "Flux démo", "Flujo demo", "Stream demo", "Fluxo demo", "Strumień demo", "Demo akışı", "演示画面"),
        "Снимок" to row("Snapshot", "Foto", "Photo", "Foto", "Scatto", "Foto", "Zdjęcie", "Fotoğraf", "截图"),
        "Весь экран" to row("Fullscreen", "Vollbild", "Plein écran", "Pantalla completa", "Schermo intero", "Tela cheia", "Pełny ekran", "Tam ekran", "全屏"),
        "LIVE" to row("LIVE", "LIVE", "LIVE", "LIVE", "LIVE", "LIVE", "LIVE", "CANLI", "实时"),
    )
}

val LocalLang = staticCompositionLocalOf { "ru" }
