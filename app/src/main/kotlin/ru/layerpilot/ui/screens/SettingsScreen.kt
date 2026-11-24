@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package ru.layerpilot.ui.screens

import ru.layerpilot.ui.components.ExpressiveSpinner
import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.QrCode
import androidx.compose.material.icons.rounded.Refresh
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.layerpilot.BuildConfig
import ru.layerpilot.R
import ru.layerpilot.data.identity.AppIdentity
import ru.layerpilot.data.identity.CustomIconStore
import ru.layerpilot.data.model.ThemeMode
import ru.layerpilot.ui.AppViewModel
import ru.layerpilot.ui.components.ConnectionBadge
import ru.layerpilot.ui.components.ScreenHeader
import ru.layerpilot.ui.components.TonalCard
import ru.layerpilot.ui.components.BackupSection
import ru.layerpilot.data.settings.profileList
import ru.layerpilot.clawd.ClawdDaily
import ru.layerpilot.clawd.ClawdPalettes
import ru.layerpilot.clawd.ClawdSizes
import ru.layerpilot.clawd.ClawdSizeOrder
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.clip

@Composable
fun SettingsScreen(
    vm: AppViewModel,
    onBack: () -> Unit,
    onEditConnection: () -> Unit,
    onPrinterInfo: () -> Unit = {},
    onPrinters: () -> Unit = {},
    onMaintenance: () -> Unit = {},
    onHistory: () -> Unit = {},
    onFarm: () -> Unit = {},
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val connection by vm.connection.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val official = remember { AppIdentity.isOfficial(ctx) }
    var showDisconnect by remember { mutableStateOf(false) }
    val fontLoading by vm.fontLoading.collectAsStateWithLifecycle()
    val fontError by vm.fontError.collectAsStateWithLifecycle()
    var category by remember { mutableStateOf<SettingsCategory?>(null) }
    var customIconVersion by remember { mutableStateOf(0) }
    val printerCount = remember(settings.printersJson, settings.host, settings.activePrinterId) {
        settings.profileList().size
    }
    BackHandler(enabled = category != null) { category = null }

    if (category == null) {
        SettingsHome(
            settings = settings,
            connection = connection,
            onBack = onBack,
            onOpen = { category = it },
            onPrinters = onPrinters,
        )
        return
    }

    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.updateSettings { it.copy(notifications = granted) }
        if (!granted) vm.message("Уведомления запрещены в системе")
    }
    val fontPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importFont(uri)
    }
    val customIconPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            if (CustomIconStore.save(ctx, uri) && CustomIconStore.pin(ctx, settings.printerName.ifBlank { "BambukHome" })) {
                vm.message("Ярлык с иконкой отправлен на рабочий стол")
            } else {
                vm.message("Не удалось закрепить ярлык. Проверьте настройки лаунчера.")
            }
        }
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
        ScreenHeader(
            title = category?.title ?: "Настройки",
            subtitle = category?.hint ?: "всё локально",
            onBack = { category = null },
        )
        Column(Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {

            if (category == SettingsCategory.PRINTER) {
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

                Spacer(Modifier.height(8.dp))
                SwitchRow(
                    "Биометрическая блокировка",
                    "Спрашивать отпечаток или лицо при запуске",
                    settings.biometricLock,
                    { v -> vm.updateSettings { it.copy(biometricLock = v) } },
                )
                if (printerCount >= 3) {
                    SwitchRow(
                        "Ферма на рабочем столе",
                        "Быстрая кнопка при 3+ принтерах",
                        settings.showFarmQuickAction,
                        { v -> vm.updateSettings { it.copy(showFarmQuickAction = v) } },
                    )
                }
                Spacer(Modifier.height(8.dp))
                BackupSection(vm)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = onMaintenance) {
                        Icon(Icons.Rounded.Build, null, Modifier.size(17.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Сервис")
                    }
                    TextButton(onClick = onHistory) { Text("История") }
                    TextButton(onClick = onFarm) { Text("Ферма") }
                }
            }

            if (category == SettingsCategory.APPEARANCE) {
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

            }

            if (category == SettingsCategory.PRINTING) {
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
                    title = "Умный свет в камере",
                    subtitle = "Включить свет при входе, если вы выключили его сами, и погасить при выходе",
                    checked = settings.cameraAutoLight,
                    onChange = { v -> vm.updateSettings { it.copy(cameraAutoLight = v) } },
                )
                SwitchRow(
                    title = "Скрывать камеру в недавних",
                    subtitle = "Запретить превью экрана камеры в списке приложений",
                    checked = settings.hideCameraInRecents,
                    onChange = { v -> vm.updateSettings { it.copy(hideCameraInRecents = v) } },
                )
                SwitchRow(
                    title = "Детектор соплей (эксперимент)",
                    subtitle = "Локальный анализ кадров. Может ошибаться при смене света",
                    checked = settings.spaghettiDetection,
                    onChange = { v -> vm.updateSettings { it.copy(spaghettiDetection = v) } },
                )
                Text(
                    "Поток идёт напрямую по TLS на порт 6000. Снимки сохраняются в галерею в папку BambukHome.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            }

            if (category == SettingsCategory.CLAWD) {
                SectionCard(icon = Icons.Rounded.Pets, title = "Кто такой Clawd") {
                    Text(
                        "Clawd — локальный питомец, который живёт в приложении. Он ходит, спит, " +
                            "радуется удачной печати, подсказывает про филамент и рассказывает факты " +
                            "о 3D-печати. Работает без ИИ, сети и аккаунтов. По умолчанию выключен, " +
                            "чтобы не мешать тем, кому он не нужен.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    SwitchRow(
                        "Включить Clawd",
                        "Питомец поверх приложения, внизу справа",
                        settings.clawdEnabled,
                        { v -> vm.updateSettings { it.copy(clawdEnabled = v) } },
                    )
                    if (settings.clawdEnabled) {
                        OutlinedTextField(
                            value = settings.clawdName,
                            onValueChange = { v -> vm.updateSettings { it.copy(clawdName = v.take(24)) } },
                            label = { Text("Имя питомца") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = settings.clawdOwnerName,
                            onValueChange = { v -> vm.updateSettings { it.copy(clawdOwnerName = v.take(30)) } },
                            label = { Text("Как вас зовут") },
                            placeholder = { Text("Имя для приветствия Clawd") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = settings.clawdAbout,
                            onValueChange = { v -> vm.updateSettings { it.copy(clawdAbout = v.take(200)) } },
                            label = { Text("Что Clawd должен знать о вас") },
                            placeholder = { Text("Например: люблю PETG и тишину") },
                            minLines = 2,
                            maxLines = 3,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("Засыпает", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(1 to "1 мин", 3 to "3 мин", 10 to "10 мин", 0 to "Никогда").forEach { (m, label) ->
                                FilterChip(
                                    selected = settings.clawdSleepMin == m,
                                    onClick = { vm.updateSettings { it.copy(clawdSleepMin = m) } },
                                    label = { Text(label) },
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("Размер", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ClawdSizeOrder.forEach { index ->
                                FilterChip(
                                    selected = settings.clawdSize == index,
                                    onClick = { vm.updateSettings { it.copy(clawdSize = index) } },
                                    label = { Text(ClawdSizes[index]) },
                                )
                            }
                        }
                        Text("Окрас", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ClawdPalettes.forEachIndexed { index, palette ->
                                FilterChip(
                                    selected = settings.clawdColor == index,
                                    onClick = { vm.updateSettings { it.copy(clawdColor = index) } },
                                    leadingIcon = {
                                        Surface(shape = CircleShape,
                                            color = palette.body,
                                            modifier = Modifier.size(14.dp)) {}
                                    },
                                    label = { Text(palette.name) },
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        SwitchRow("Гуляет сам", "Ходит и поглядывает", settings.clawdWander,
                            { v -> vm.updateSettings { it.copy(clawdWander = v) } })
                        SwitchRow("Разговаривает", "Факты по нажатию и советы", settings.clawdPhrases,
                            { v -> vm.updateSettings { it.copy(clawdPhrases = v) } })
                        SwitchRow("Ест пиксели", "Шалость, не трогает кнопки", settings.clawdEat,
                            { v -> vm.updateSettings { it.copy(clawdEat = v) } })
                        SwitchRow("Оставляет следы", "Пиксельная кучка, можно убрать", settings.clawdPoop,
                            { v -> vm.updateSettings { it.copy(clawdPoop = v) } })
                        SwitchRow("Реагирует на тряску", "Акселерометр", settings.clawdShake,
                            { v -> vm.updateSettings { it.copy(clawdShake = v) } })
                        SwitchRow("Танцует", "Радуется хорошим событиям", settings.clawdDance,
                            { v -> vm.updateSettings { it.copy(clawdDance = v) } })
                        SwitchRow("Вибрация", "Только приятные отклики, без звука", settings.clawdHaptics,
                            { v -> vm.updateSettings { it.copy(clawdHaptics = v) } })
                        SwitchRow("Умный мозг", "Вопросы про печать и влажность", settings.clawdBrain,
                            { v -> vm.updateSettings { it.copy(clawdBrain = v) } })
                        SwitchRow("Тихие часы", "Ночью только важное", settings.clawdQuiet,
                            { v -> vm.updateSettings { it.copy(clawdQuiet = v) } })
                        SwitchRow("Меньше движения", "Спокойный режим и экономия батареи", settings.clawdReduceMotion,
                            { v -> vm.updateSettings { it.copy(clawdReduceMotion = v) } })
                        Text(
                            "ИИ отключён. Все факты и советы хранятся в приложении и работают офлайн.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }
                val day = remember { ClawdDaily.forDate() }
                SectionCard(icon = Icons.Rounded.Info, title = "Дневник Clawd") {
                    Text("Сегодня ${day.dateText}, ${day.weekday} · ${day.season}",
                        style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(day.fact, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    Text("Совет дня: ${day.tip}", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(6.dp))
                    Text("Фокус года: ${day.yearTheme} — ${day.yearThemeHint}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Запас: ${ru.layerpilot.clawd.ClawdFactDeck.summary()} — без ИИ и без сети.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (category == SettingsCategory.ICONS) {
                SectionCard(icon = Icons.Rounded.Pets, title = "Своя иконка") {
                    Text(
                        "Android не позволяет менять иконку приложения на лету. Можно закрепить " +
                            "свою фотографию как отдельный ярлык на рабочем столе — он будет выглядеть " +
                            "как кастомная иконка принтера.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Выберите квадратное фото — приложение предложит закрепить ярлык с ним. " +
                            "Это безопасно: фото хранится только на телефоне.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = { customIconPicker.launch(arrayOf("image/*")) },
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Icon(Icons.Rounded.Apps, null, Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Выбрать фото и закрепить")
                    }
                    val customBitmap = remember(customIconVersion, category) { CustomIconStore.load(ctx) }
                    customBitmap?.let { bmp ->
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "Своя иконка",
                                modifier = Modifier.size(72.dp).clip(RoundedCornerShape(18.dp)),
                            )
                            Spacer(Modifier.width(12.dp))
                            TextButton(onClick = {
                                CustomIconStore.clear(ctx)
                                customIconVersion++
                                vm.message("Своя иконка удалена из приложения")
                            }) {
                                Text("Удалить иконку", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }

            if (category == SettingsCategory.ABOUT) {
            // ---------------------------------------------------- о приложении
            SectionCard(icon = Icons.Rounded.Info, title = "О приложении") {
                InfoRow("Версия", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                InfoRow("Пакет", "ru.layerpilot")
                InfoRow("Android", "8+ (API 26)")
                InfoRow("Сборка", if (official) "официальная" else "неофициальная")
                InfoRow("Связь", "MQTT 8883 · FTPS ${settings.ftpPort} · камера 6000")
                InfoRow("Ферма", "до 8 принтеров одновременно")
                InfoRow("Данные", "только на телефоне")

                if (!official) {
                    Spacer(Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            "Подпись не совпадает с оригиналом. Это неофициальная сборка. " +
                                "Оригинал и поддержка: t.me/BambukHome. Если вы купили приложение — вас обманули.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }

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
                                "и файлы остаются между телефоном и принтером в домашней сети. " +
                                "Облако, аккаунты и реклама не используются.",
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
                        Text("Что нового в ${BuildConfig.VERSION_NAME}", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        listOf(
                            "2.0.0 — вторая стабильная версия. Исправлен порядок размеров Clawd, убран нерабочий переключатель гравитации.",
                            "Стабильные стандартные индикаторы загрузки — без MD3-экспериментов.",
                            "Удалены разделы «Модели», MakerWorld и стили иконок Plumpy/Fluent.",
                            "В камере появилось управление столом и головой, а приближённую картинку можно двигать пальцем.",
                            "Работает скачивание видео из «Файлов» и очистка SD от всего, кроме .gcode.",
                            "Clawd по нажатию рассказывает новый факт и совет, не повторяясь.",
                            "Умный свет камеры, резервная копия настроек и автоудаление старых уведомлений.",
                        ).forEach { line ->
                            Row(Modifier.padding(vertical = 2.dp)) {
                                Text("•", color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(end = 7.dp))
                                Text(line, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                Text("Telegram-канал", style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Surface(
                    onClick = {
                        runCatching {
                            ctx.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(AppIdentity.TELEGRAM_CHANNEL))
                            )
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
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
                            Text("@BambukHome", fontWeight = FontWeight.Bold)
                            Text("Новости, релизы и обсуждения",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        Icon(Icons.Rounded.ChevronRight, null, Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer)
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
                    "${AppIdentity.COPYRIGHT} · ${AppIdentity.TELEGRAM_CHANNEL}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "BambukHome — независимый клиент. Не связан с Bambu Lab; названия моделей " +
                        "используются только для совместимости. Протокол неофициальный: после обновления " +
                        "прошивки часть команд может измениться.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "BambukHome Freeware License: личное использование бесплатно; " +
                        "продажа, ребрендинг и присвоение авторства запрещены.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = { showDisconnect = true }) {
                    Text("Сбросить подключение", color = MaterialTheme.colorScheme.error)
                }
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
private fun ChipRow(labels: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.forEachIndexed { index, label ->
            FilterChip(
                selected = selectedIndex == index,
                onClick = { onSelect(index) },
                label = { Text(label) },
            )
        }
    }
}

@Composable
private fun PackPreview(label: String, res: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.size(64.dp)) {
            Image(
                painter = painterResource(res),
                contentDescription = label,
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface),
                modifier = Modifier.padding(14.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
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

