@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package ru.layerpilot.ui.screens

import ru.layerpilot.ui.components.ExpressiveSpinner
import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.QrCode
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.ViewQuilt
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import ru.layerpilot.ui.locale.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.layerpilot.BuildConfig
import ru.layerpilot.data.model.ThemeMode
import ru.layerpilot.ui.AppViewModel
import ru.layerpilot.ui.components.ConnectionBadge
import ru.layerpilot.ui.components.ScreenHeader
import ru.layerpilot.ui.components.TonalCard

@Composable
fun SettingsScreen(
    vm: AppViewModel,
    onBack: () -> Unit,
    onEditConnection: () -> Unit,
    onPrinterInfo: () -> Unit = {},
    onPrinters: () -> Unit = {},
    onMakerWorld: () -> Unit = {},
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val connection by vm.connection.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    var showDisconnect by remember { mutableStateOf(false) }
    val fontLoading by vm.fontLoading.collectAsStateWithLifecycle()
    val fontError by vm.fontError.collectAsStateWithLifecycle()
    var token by remember(settings.thingiverseToken) { mutableStateOf(settings.thingiverseToken) }

    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.updateSettings { it.copy(notifications = granted) }
        if (!granted) vm.message("Уведомления запрещены в системе")
    }
    val fontPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importFont(uri)
    }
    val livePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.updateSettings { it.copy(liveNotification = granted) }
        if (!granted) vm.message("Уведомления запрещены в системе")
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding(),
    ) {
        ScreenHeader(title = "Настройки", subtitle = "всё локально", onBack = onBack)
        Column(Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {

            // ---------------------------------------------------- принтер
            SectionCard(icon = Icons.Rounded.Wifi, title = "Принтер") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            settings.printerName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            when {
                                settings.demoMode -> "демо-режим · без железа"
                                settings.host.isBlank() -> "адрес не задан"
                                else -> "${settings.host} · ${settings.serial}"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    ConnectionBadge(connection)
                }
                if (connection is ru.layerpilot.data.printer.ConnectionStatus.Connecting ||
                    connection is ru.layerpilot.data.printer.ConnectionStatus.Reconnecting
                ) {
                    Spacer(Modifier.height(10.dp))
                    ExpressiveSpinner(size = 24.dp)
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = onEditConnection, shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)) {
                        Text(if (settings.host.isBlank()) "Настроить" else "Изменить")
                    }
                    TextButton(onClick = { vm.reconnect() }, modifier = Modifier.height(40.dp)) {
                        Icon(Icons.Rounded.Refresh, null, Modifier.size(17.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("Переподключиться")
                    }
                }
                Spacer(Modifier.height(6.dp))
                Surface(
                    onClick = onPrinters,
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Print, null, Modifier.size(19.dp),
                            tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Профили принтеров", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "${ru.layerpilot.data.settings.decodePrinterProfiles(settings.printersJson).size.coerceAtLeast(if (settings.host.isNotBlank() || settings.demoMode) 1 else 0)} сохранено",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(Icons.Rounded.ChevronRight, null, Modifier.size(19.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = settings.ftpPort.toString(),
                    onValueChange = { text ->
                        val port = text.filter { it.isDigit() }.take(5).toIntOrNull()
                        if (port != null && port in 1..65535) {
                            vm.updateSettings { it.copy(ftpPort = port) }
                        }
                    },
                    label = { Text("Порт FTPS") },
                    supportingText = { Text("у принтера обычно 990") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                Surface(
                    onClick = onPrinterInfo,
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Info, null, Modifier.size(19.dp),
                            tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("О принтере", style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium)
                            Text("серийник, прошивка, часы печати",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Rounded.ChevronRight, null, Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(4.dp))
                SwitchRow(
                    title = "Демо-режим",
                    subtitle = "Виртуальный принтер для знакомства",
                    checked = settings.demoMode,
                    onChange = { vm.setDemo(it) },
                )
            }

            // ---------------------------------------------------- внешний вид
            SectionCard(icon = Icons.Rounded.Palette, title = "Внешний вид") {
                Text("Тема", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                ChipFlow {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = settings.themeMode == mode,
                            onClick = { vm.setThemeMode(mode) },
                            label = {
                                Text(
                                    when (mode) {
                                        ThemeMode.SYSTEM -> "Системная"
                                        ThemeMode.LIGHT -> "Светлая"
                                        ThemeMode.DARK -> "Тёмная"
                                    }
                                )
                            },
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))
                Text("Цвет приложения", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                ChipFlow {
                    listOf("SYSTEM" to "Система", "BRAND" to "Фирменный", "CUSTOM" to "Свой").forEach { (mode, label) ->
                        FilterChip(
                            selected = settings.accentMode == mode,
                            onClick = { vm.setAccentMode(mode) },
                            enabled = mode != "SYSTEM" || Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
                            label = { Text(label) },
                        )
                    }
                }
                if (settings.accentMode == "CUSTOM") {
                    Spacer(Modifier.height(10.dp))
                    HuePicker(
                        hue = settings.accentHue,
                        onHue = { vm.setAccentHue(it) },
                    )
                }

                Spacer(Modifier.height(6.dp))
                SwitchRow(
                    title = "AMOLED-чёрный",
                    subtitle = "Чистый чёрный фон в тёмной теме",
                    checked = settings.amoled,
                    onChange = { v -> vm.updateSettings { it.copy(amoled = v) } },
                )

                Spacer(Modifier.height(10.dp))
                Text("Масштаб интерфейса", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                ChipFlow {
                    listOf(0.85f to "85 %", 0.9f to "90 %", 0.95f to "95 %", 1f to "100 %", 1.15f to "115 %", 1.3f to "130 %").forEach { (value, label) ->
                        FilterChip(
                            selected = kotlin.math.abs(settings.uiScale - value) < 0.01f,
                            onClick = { vm.setUiScale(value) },
                            label = { Text(label) },
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))
                Text("Форма волны", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                ChipFlow {
                    listOf(0.65f to "Тонкая", 1f to "Обычная", 1.45f to "Толстая").forEach { (value, label) ->
                        FilterChip(
                            selected = kotlin.math.abs(settings.waveThickness - value) < 0.01f,
                            onClick = { vm.setWaveThickness(value) },
                            label = { Text(label) },
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                ChipFlow {
                    listOf(0.25f to "Прямая", 1f to "Волнистая", 1.7f to "Сильная").forEach { (value, label) ->
                        FilterChip(
                            selected = kotlin.math.abs(settings.waveAmplitude - value) < 0.01f,
                            onClick = { vm.setWaveAmplitude(value) },
                            label = { Text(label) },
                        )
                    }
                }
                WavePreview()

                Spacer(Modifier.height(4.dp))
                SwitchRow(
                    title = "Волновая анимация",
                    subtitle = "Живой индикатор прогресса",
                    checked = settings.waveAnimation,
                    onChange = { v -> vm.updateSettings { it.copy(waveAnimation = v) } },
                )
                SwitchRow(
                    title = "Не гасить экран",
                    subtitle = "Пока открыто приложение",
                    checked = settings.keepScreenOn,
                    onChange = { v -> vm.updateSettings { it.copy(keepScreenOn = v) } },
                )
            }

            // ---------------------------------------------------- вкладки
            SectionCard(icon = Icons.Rounded.ViewQuilt, title = "Рабочий стол") {
                Text(
                    "Скройте вкладки, которыми не пользуетесь — они перестанут занимать место внизу.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SwitchRow(
                    title = "Вкладка «Модели»",
                    subtitle = "Ссылки, импорт файлов и 3D-просмотр",
                    checked = settings.showModelsTab,
                    onChange = { v -> vm.updateSettings { it.copy(showModelsTab = v) } },
                )
                SwitchRow(
                    title = "Вкладка MakerWorld",
                    subtitle = "Поиск моделей и аккаунт Bambu Lab",
                    checked = settings.showMakerWorldTab,
                    onChange = { v -> vm.updateSettings { it.copy(showMakerWorldTab = v) } },
                )
            }

            // ---------------------------------------------------- шрифт
            SectionCard(icon = Icons.Rounded.TextFields, title = "Шрифт") {
                ChipFlow {
                    listOf("SYSTEM" to "Системный", "NDOT" to "Ndot 77", "CUSTOM" to "Свой").forEach { (mode, label) ->
                        FilterChip(
                            selected = settings.fontMode == mode,
                            onClick = { vm.setFontMode(mode) },
                            label = { Text(label) },
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "Ndot 77 JP Extended — личный пиксельный шрифт. Свой шрифт выберите " +
                        "из памяти телефона: .ttf, .otf или .ttc.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            vm.clearFontError()
                            fontPicker.launch(
                                arrayOf(
                                    "font/ttf", "font/otf", "font/ttc",
                                    "application/x-font-ttf", "application/x-font-opentype",
                                    "application/octet-stream", "*/*",
                                )
                            )
                        },
                        enabled = !fontLoading,
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        if (fontLoading) {
                            ExpressiveSpinner(Modifier.size(16.dp),
                                color = MaterialTheme.colorScheme.onPrimary)
                            Spacer(Modifier.width(8.dp))
                            Text("Читаем…")
                        } else {
                            Icon(Icons.Rounded.FolderOpen, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Выбрать шрифт")
                        }
                    }
                    if (settings.customFontName.isNotBlank()) {
                        Text(
                            settings.customFontName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                    }
                }
                fontError?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error)
                }
            }

            // ---------------------------------------------------- язык
            SectionCard(icon = Icons.Rounded.Language, title = "Язык приложения") {
                ChipFlow {
                    FilterChip(
                        selected = settings.language.isBlank(),
                        onClick = { vm.updateSettings { it.copy(language = "") } },
                        label = { Text("Системный") },
                    )
                    listOf(
                        "ru" to "Русский", "en" to "English", "de" to "Deutsch",
                        "fr" to "Français", "es" to "Español", "it" to "Italiano",
                        "pt" to "Português", "pl" to "Polski", "tr" to "Türkçe",
                        "zh" to "中文",
                    ).forEach { (code, name) ->
                        FilterChip(
                            selected = settings.language == code,
                            onClick = { vm.updateSettings { it.copy(language = code) } },
                            label = { Text(name) },
                        )
                    }
                }
                Text(
                    "Перевод применяется сразу. Технические подсказки и названия моделей могут остаться на языке оригинала.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            // ---------------------------------------------------- печать
            SectionCard(icon = Icons.Rounded.Print, title = "Печать по умолчанию") {
                SwitchRow(
                    title = "Таймлапс",
                    subtitle = "Снимать видео печати",
                    checked = settings.timelapseDefault,
                    onChange = { v -> vm.updateSettings { it.copy(timelapseDefault = v) } },
                )
                SwitchRow(
                    title = "Выравнивание стола",
                    subtitle = "Перед каждым запуском",
                    checked = settings.bedLevelingDefault,
                    onChange = { v -> vm.updateSettings { it.copy(bedLevelingDefault = v) } },
                )
                SwitchRow(
                    title = "Калибровка потока",
                    subtitle = "Точные стенки, дольше старт",
                    checked = settings.flowCaliDefault,
                    onChange = { v -> vm.updateSettings { it.copy(flowCaliDefault = v) } },
                )
                SwitchRow(
                    title = "Калибровка вибраций",
                    subtitle = "Меньше артефактов на углах",
                    checked = settings.vibrationCaliDefault,
                    onChange = { v -> vm.updateSettings { it.copy(vibrationCaliDefault = v) } },
                )
                Spacer(Modifier.height(8.dp))
                Text("Папка для загрузки", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("/cache", "/").forEach { folder ->
                        FilterChip(
                            selected = settings.uploadFolder == folder,
                            onClick = { vm.updateSettings { it.copy(uploadFolder = folder) } },
                            label = { Text(if (folder == "/") "корень SD" else "cache") },
                        )
                    }
                }
            }

            // ---------------------------------------------------- уведомления
            SectionCard(icon = Icons.Rounded.Notifications, title = "Уведомления") {
                SwitchRow(
                    title = "Показывать события",
                    subtitle = "Требуется разрешение системы",
                    checked = settings.notifications,
                    onChange = { enabled ->
                        if (enabled && Build.VERSION.SDK_INT >= 33) {
                            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            vm.updateSettings { it.copy(notifications = enabled) }
                        }
                    },
                )
                SwitchRow(
                    title = "Прогресс в шторке",
                    subtitle = "Тихо, без звука: проценты, слой и остаток",
                    checked = settings.liveNotification,
                    onChange = { enabled ->
                        if (enabled && Build.VERSION.SDK_INT >= 33 &&
                            androidx.core.content.ContextCompat.checkSelfPermission(
                                ctx, Manifest.permission.POST_NOTIFICATIONS,
                            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                        ) {
                            livePermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            vm.updateSettings { it.copy(liveNotification = enabled) }
                        }
                    },
                )
                SwitchRow("Печать завершена", null, settings.notifyFinish,
                    { v -> vm.updateSettings { it.copy(notifyFinish = v) } }, enabled = settings.notifications)
                SwitchRow("Пауза", null, settings.notifyPause,
                    { v -> vm.updateSettings { it.copy(notifyPause = v) } }, enabled = settings.notifications)
                SwitchRow("Ошибки и HMS", null, settings.notifyError,
                    { v -> vm.updateSettings { it.copy(notifyError = v) } }, enabled = settings.notifications)
            }

            // ---------------------------------------------------- камера
            SectionCard(icon = Icons.Rounded.CameraAlt, title = "Камера") {
                SwitchRow(
                    title = "Свет при открытии",
                    subtitle = "Включить подсветку камеры, если она выключена",
                    checked = settings.cameraAutoLight,
                    onChange = { v -> vm.updateSettings { it.copy(cameraAutoLight = v) } },
                )
                Text(
                    "Поток идёт напрямую по TLS на порт 6000. Снимки сохраняются в галерею в папку BambukHome.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (settings.showMakerWorldTab) {
                // ---------------------------------------------------- MakerWorld
                SectionCard(icon = Icons.Rounded.Public, title = "MakerWorld") {
                    Text(
                        "Войдите в аккаунт Bambu Lab прямо в приложении: поиск, избранное и скачивание 3MF " +
                            "работают через официальный Bambu Cloud. Пароль не сохраняется, токен входа " +
                            "хранится зашифрованным.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onMakerWorld,
                            shape = RoundedCornerShape(14.dp),
                        ) {
                            Icon(Icons.Rounded.Public, null, Modifier.size(17.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Открыть кабинет")
                        }
                        TextButton(onClick = {
                            val opened = runCatching {
                                ctx.startActivity(Intent(Intent.ACTION_VIEW,
                                    Uri.parse(MAKERWORLD_LOGIN_URL)))
                            }.isSuccess
                            if (!opened) vm.message("Не удалось открыть браузер")
                        }) { Text("Вход в браузере") }
                    }
                    Text(
                        "Если Bambu просит капчу, откройте вход в браузере. Для кабинета в приложении " +
                            "используйте вход по email — он идёт напрямую в Bambu Cloud без Cloudflare.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (settings.showModelsTab) {
                // ---------------------------------------------------- модели
                SectionCard(icon = Icons.Rounded.Link, title = "Модели") {
                    Text(
                        "Thingiverse отдаёт файлы через API. Токен бесплатный: Settings → Apps → API.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = token,
                        onValueChange = { token = it.trim() },
                        label = { Text("Токен Thingiverse") },
                        visualTransformation = PasswordVisualTransformation(),
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = { vm.updateSettings { it.copy(thingiverseToken = token) }; vm.message("Токен сохранён") },
                        ) { Text("Сохранить") }
                        if (settings.thingiverseToken.isNotBlank()) {
                            TextButton(onClick = {
                                token = ""
                                vm.updateSettings { it.copy(thingiverseToken = "") }
                            }) { Text("Убрать", color = MaterialTheme.colorScheme.error) }
                        }
                    }
                    Text(
                        "Printables отдаёт превью и ссылки. MakerWorld-модели ищите и скачивайте " +
                            "во вкладке MakerWorld — там работает аккаунт Bambu Lab и 3MF.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // ---------------------------------------------------- о приложении
            SectionCard(icon = Icons.Rounded.Info, title = "О приложении") {
                InfoRow("Версия", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                InfoRow("Пакет", "ru.layerpilot")
                InfoRow("Android", "11+ (API 30)")
                InfoRow("Связь", "MQTT 8883 · FTPS ${settings.ftpPort} · камера 6000")

                Spacer(Modifier.height(12.dp))
                Surface(shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Полностью бесплатно", fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Ни подписок и ни рекламы. Управление принтером работает локально: IP, код доступа " +
                                "и файлы остаются между телефоном и принтером в домашней сети. Аккаунт Bambu Lab " +
                                "нужен только для дополнительного раздела MakerWorld и полностью опционален.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text("MakerWorld", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Вкладка MakerWorld ищет модели, показывает профили печати и скачивает 3MF после " +
                                "входа в аккаунт Bambu Lab. Пароль не сохраняется, токен входа шифруется. " +
                                "Вход в браузере открывает официальный SSO Bambu Lab; cookie браузера Android " +
                                "не передаёт приложению, поэтому кабинет использует авторизацию Bambu Cloud.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))
                Text("Связаться с автором", style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Surface(
                    onClick = {
                        runCatching {
                            ctx.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/mikhail_qqq4"))
                            )
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.Send, null, Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onPrimary)
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("@mikhail_qqq4", fontWeight = FontWeight.Bold)
                            Text("Telegram · идеи, баги, вопросы",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Rounded.ChevronRight, null, Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Нашли баг или что-то не работает с вашей моделью принтера — напишите в Telegram. " +
                        "Приложите модель, версию прошивки и что именно пошло не так: так исправлю быстрее.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(12.dp))
                Text(
                    "BambukHome — независимый клиент. Не связан с Bambu Lab; названия моделей " +
                        "используются только для совместимости. Протокол неофициальный: после обновления " +
                        "прошивки часть команд может измениться.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = { showDisconnect = true }) {
                    Text("Сбросить подключение", color = MaterialTheme.colorScheme.error)
                }
            }

            Spacer(Modifier.height(30.dp))
        }
    }

    if (showDisconnect) {
        AlertDialog(
            onDismissRequest = { showDisconnect = false },
            title = { Text("Удалить данные принтера?") },
            text = { Text("IP, код доступа и серийный номер будут стёрты с телефона.") },
            confirmButton = {
                TextButton(onClick = {
                    showDisconnect = false
                    vm.saveConnection("", "", "", settings.model, settings.printerName, demo = false)
                    vm.setDemo(false)
                }) { Text("Стереть", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDisconnect = false }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun SectionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    content: @Composable () -> Unit,
) {
    TonalCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(34.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, null, Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            Spacer(Modifier.width(10.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(14.dp))
        content()
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 10.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipFlow(content: @Composable androidx.compose.foundation.layout.FlowRowScope.() -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        content = content,
    )
}

@Composable
private fun HuePicker(hue: Int, onHue: (Int) -> Unit) {
    val rainbow = listOf(
        Color(0xFFEF5350), Color(0xFFFFA726), Color(0xFFFFEE58), Color(0xFF66BB6A),
        Color(0xFF26C6DA), Color(0xFF42A5F5), Color(0xFF7E57C2), Color(0xFFEC407A),
        Color(0xFFEF5350),
    )
    val current = Color.hsl(hue.toFloat().coerceIn(0f, 360f), 0.6f, 0.5f)
    Slider(
        value = hue.toFloat(),
        onValueChange = { onHue(it.toInt()) },
        valueRange = 0f..360f,
        colors = SliderDefaults.colors(
            thumbColor = current,
            activeTrackColor = Color.Transparent,
            inactiveTrackColor = Color.Transparent,
        ),
        track = {
            Canvas(Modifier.fillMaxWidth().height(26.dp)) {
                drawRoundRect(
                    brush = Brush.horizontalGradient(rainbow),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2f),
                )
            }
        },
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        listOf(130, 95, 60, 35, 10, 340, 315, 285, 260, 225, 200, 170).forEach { h ->
            val color = Color.hsl(h.toFloat(), 0.6f, 0.5f)
            Surface(
                onClick = { onHue(h) },
                shape = androidx.compose.foundation.shape.CircleShape,
                color = color,
                border = androidx.compose.foundation.BorderStroke(
                    width = if (hue == h) 2.dp else 1.dp,
                    color = if (hue == h) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.outlineVariant,
                ),
                modifier = Modifier.size(22.dp),
            ) {}
        }
    }
}

@Composable
private fun WavePreview() {
    Spacer(Modifier.height(10.dp))
    ru.layerpilot.ui.components.WavyProgress(
        progress = 0.62f,
        modifier = Modifier.fillMaxWidth(),
        height = 14.dp,
    )
}

