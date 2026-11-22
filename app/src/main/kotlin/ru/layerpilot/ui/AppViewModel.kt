package ru.layerpilot.ui

import android.app.Application
import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import ru.layerpilot.LayerPilotApp
import ru.layerpilot.MainActivity
import ru.layerpilot.R
import ru.layerpilot.data.camera.CameraClient
import ru.layerpilot.data.files.Downloads
import ru.layerpilot.data.files.FtpException
import ru.layerpilot.data.files.PrinterFtpClient
import ru.layerpilot.data.files.RemoteFile
import ru.layerpilot.data.model.PrintPhase
import ru.layerpilot.data.model.PrinterModel
import ru.layerpilot.data.model.PrinterSnapshot
import ru.layerpilot.data.model.Spool
import ru.layerpilot.data.model.ThemeMode
import ru.layerpilot.data.models.FilamentSpec
import ru.layerpilot.data.models.SkipPlate
import ru.layerpilot.data.models.SkipPart
import ru.layerpilot.data.models.PlatePoint
import ru.layerpilot.data.models.PlateInspector
import ru.layerpilot.data.models.ModelLink
import ru.layerpilot.data.models.ModelResolver
import ru.layerpilot.data.models.RemoteModelFile
import ru.layerpilot.data.models.ThreeMfInfo
import ru.layerpilot.data.models.ThreeMfInspector
import ru.layerpilot.data.printer.ConnectionStatus
import ru.layerpilot.data.printer.FanKind
import ru.layerpilot.data.printer.PrintOptions
import ru.layerpilot.data.printer.PrinterConfig
import ru.layerpilot.data.printer.PrinterRepository
import ru.layerpilot.data.settings.AppSettings
import ru.layerpilot.data.settings.PrinterProfile
import ru.layerpilot.data.settings.deleteProfile
import ru.layerpilot.data.settings.upsertProfile
import ru.layerpilot.data.settings.activatedProfile
import ru.layerpilot.data.settings.SettingsRepository
import ru.layerpilot.data.video.DemoTimelapse
import ru.layerpilot.notifications.PrintMonitorService
import ru.layerpilot.notifications.PrintNotifications
import ru.layerpilot.notifications.PrintState
import java.io.File

data class TransferState(
    val title: String,
    val progress: Float = 0f,
    val indeterminate: Boolean = false,
)

data class LocalModel(
    val file: File,
    val name: String,
    val size: Long,
    val info: ThreeMfInfo?,
) {
    val printable: Boolean
        get() = name.endsWith(".3mf", true) || name.endsWith(".gcode", true)
}

data class ModelUiState(
    val loading: Boolean = false,
    val resolving: Boolean = false,
    val link: ModelLink? = null,
    val error: String? = null,
    val downloadName: String? = null,
    val downloadProgress: Float = 0f,
    val local: LocalModel? = null,
    val history: List<LocalModel> = emptyList(),
)

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepo = SettingsRepository(application)
    private val statsRepo = ru.layerpilot.data.stats.StatsRepository(application)
    private var statsTicker: Job? = null
    private val printer = PrinterRepository()
    private val camera = CameraClient()
    private val resolver = ModelResolver(application)

    val settings: StateFlow<AppSettings> = settingsRepo.settings
    val settingsLoaded: StateFlow<Boolean> = settingsRepo.loaded
    val snapshot: StateFlow<PrinterSnapshot> = printer.snapshot
    val stats: StateFlow<ru.layerpilot.data.stats.PrintStats> = statsRepo.stats
    val connection: StateFlow<ConnectionStatus> = printer.connection

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val _transfer = MutableStateFlow<TransferState?>(null)
    val transfer: StateFlow<TransferState?> = _transfer.asStateFlow()

    private val _fontLoading = MutableStateFlow(false)
    val fontLoading: StateFlow<Boolean> = _fontLoading.asStateFlow()

    private val _fontError = MutableStateFlow<String?>(null)
    val fontError: StateFlow<String?> = _fontError.asStateFlow()

    private val _models = MutableStateFlow(ModelUiState())
    val models: StateFlow<ModelUiState> = _models.asStateFlow()

    private val _skipPlate = MutableStateFlow<SkipPlate?>(null)
    val skipPlate: StateFlow<SkipPlate?> = _skipPlate.asStateFlow()

    private val _skipLoading = MutableStateFlow(false)
    val skipLoading: StateFlow<Boolean> = _skipLoading.asStateFlow()

    private val _openModels = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val openModels: SharedFlow<Unit> = _openModels.asSharedFlow()

    private var lastConnectionKey = "none"
    private var liveServiceRunning = false
    private val app: Application get() = getApplication()

    init {
        viewModelScope.launch {
            settingsRepo.settings.collectLatest { s ->
                val key = connectionKey(s)
                if (key != lastConnectionKey) {
                    lastConnectionKey = key
                    if (!s.isConfigured) {
                        printer.disconnect()
                    } else {
                        printer.connect(
                            PrinterConfig(s.host, s.accessCode, s.serial, s.model, s.printerName),
                            demoMode = s.demoMode,
                            demoWithAms = s.demoAms,
                        )
                    }
                }
            }
        }
        viewModelScope.launch { observeEvents() }
        viewModelScope.launch { refreshHistory() }
    }

    private suspend fun observeEvents() {
        var previous: PrinterSnapshot? = null
        printer.snapshot.collect { now ->
            val before = previous
            previous = now
            if (before == null) return@collect

            if (now.phase == PrintPhase.PRINTING) {
                if (statsTicker == null) {
                    statsTicker = viewModelScope.launch {
                        while (true) {
                            delay(60_000)
                            statsRepo.addMinute()
                        }
                    }
                }
            } else {
                statsTicker?.cancel()
                statsTicker = null
            }
            if (now.phase == PrintPhase.FINISHED && before.phase != PrintPhase.FINISHED) {
                statsRepo.finish(now.jobName)
            }

            val s = settings.value
            val liveEnabled = s.notifications && s.liveNotification
            val liveActive = now.phase == PrintPhase.PRINTING ||
                now.phase == PrintPhase.PAUSED || now.phase == PrintPhase.PREPARE
            if (liveEnabled && liveActive && PrintNotifications.allowed(app)) {
                if (!liveServiceRunning) {
                    PrintMonitorService.start(app)
                    liveServiceRunning = true
                }
                PrintState.set(now, s.printerName)
            } else if (liveServiceRunning) {
                PrintState.clear()
                PrintMonitorService.stop(app)
                liveServiceRunning = false
            }

            if (!s.notifications) return@collect
            when {
                now.phase == PrintPhase.FINISHED && before.phase != PrintPhase.FINISHED && s.notifyFinish ->
                    notify("Печать завершена", "«${now.jobName.ifBlank { "Модель" }}» готова. Заберите со стола.")
                now.phase == PrintPhase.PAUSED && before.phase != PrintPhase.PAUSED && s.notifyPause &&
                    !liveEnabled ->
                    notify("Печать на паузе", now.stageLabel.ifBlank { now.jobName.ifBlank { "Принтер ждёт" } }, silent = true)
                now.errors.size > before.errors.size && s.notifyError ->
                    notify("Ошибка принтера", now.errors.last().text)
            }
        }
    }

    private fun notify(title: String, text: String, silent: Boolean = false) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(app, android.Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        val intent = Intent(app, MainActivity::class.java)
        val pending = PendingIntent.getActivity(app, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val channel = if (silent) LayerPilotApp.CHANNEL_PROGRESS else LayerPilotApp.CHANNEL_PRINTER
        val notification: Notification = Notification.Builder(app, channel)
            .setSmallIcon(R.drawable.ic_stat_layerpilot)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(app).notify((System.currentTimeMillis() % 10000).toInt(), notification) }
    }

    fun message(text: String) {
        _messages.tryEmit(text)
    }

    // ------------------------------------------------------------ настройки

    fun saveConnection(
        host: String,
        code: String,
        serial: String,
        model: PrinterModel,
        name: String,
        demo: Boolean = false,
    ) {
        settingsRepo.update {
            it.copy(
                host = host.trim().removePrefix("http://").removePrefix("https://").trimEnd('/'),
                accessCode = code.trim(),
                serial = serial.trim(),
                model = model,
                printerName = name.trim().ifBlank { model.label },
                demoMode = demo,
            )
        }
    }

    fun switchPrinter(id: String) {
        settingsRepo.update { it.activatedProfile(id) }
        message("Принтер переключён")
    }

    fun savePrinterProfile(profile: PrinterProfile, activate: Boolean = true) {
        settingsRepo.update { current ->
            val next = current.upsertProfile(profile)
            if (activate) next.activatedProfile(profile.id) else next
        }
    }

    fun deletePrinterProfile(id: String) {
        settingsRepo.update { it.deleteProfile(id) }
    }

    fun setDemo(enabled: Boolean) {
        settingsRepo.update { it.copy(demoMode = enabled) }
    }

    fun setDemoAms(hasAms: Boolean) {
        settingsRepo.update { it.copy(demoAms = hasAms) }
    }

    fun updateSettings(block: (AppSettings) -> AppSettings) = settingsRepo.update(block)

    fun setThemeMode(mode: ThemeMode) = settingsRepo.update { it.copy(themeMode = mode) }

    fun setAccentMode(mode: String) = settingsRepo.update { it.copy(accentMode = mode) }

    fun setAccentHue(hue: Int) = settingsRepo.update { it.copy(accentHue = hue.coerceIn(0, 360)) }

    fun setUiScale(scale: Float) = settingsRepo.update { it.copy(uiScale = scale.coerceIn(0.8f, 1.35f)) }

    fun setWaveThickness(value: Float) = settingsRepo.update { it.copy(waveThickness = value.coerceIn(0.6f, 1.6f)) }

    fun setWaveAmplitude(value: Float) = settingsRepo.update { it.copy(waveAmplitude = value.coerceIn(0f, 1.8f)) }

    fun setFontMode(mode: String) {
        if (mode == "CUSTOM" && settings.value.customFontPath.isBlank()) {
            message("Сначала выберите файл шрифта")
            return
        }
        settingsRepo.update { it.copy(fontMode = mode) }
    }

    fun clearFontError() {
        _fontError.value = null
    }

    /** Копирует выбранный в памяти телефона .ttf/.otf/.ttc во внутреннюю папку приложения. */
    fun importFont(uri: Uri) {
        viewModelScope.launch {
            _fontLoading.value = true
            _fontError.value = null
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val resolver = app.contentResolver
                    var display = "Свой шрифт"
                    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                        ?.use { cursor ->
                            if (cursor.moveToFirst()) {
                                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                                if (index >= 0) {
                                    cursor.getString(index)?.takeIf { it.isNotBlank() }?.let { display = it }
                                }
                            }
                        }
                    val dir = File(app.filesDir, "fonts").apply { mkdirs() }
                    val tmp = File(dir, "custom.tmp")
                    resolver.openInputStream(uri)?.use { input ->
                        tmp.outputStream().use { out ->
                            val buffer = ByteArray(64 * 1024)
                            var total = 0L
                            while (true) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                total += read
                                if (total > 30L * 1024 * 1024) {
                                    throw IllegalStateException("Шрифт больше 30 МБ")
                                }
                                out.write(buffer, 0, read)
                            }
                        }
                    } ?: throw IllegalStateException("Не удалось открыть файл шрифта")
                    if (tmp.length() < 1024) {
                        tmp.delete()
                        throw IllegalStateException("Файл слишком маленький — это не шрифт?")
                    }
                    val magic = tmp.inputStream().use { stream ->
                        val bytes = ByteArray(4)
                        stream.read(bytes)
                        bytes
                    }
                    val extension = when {
                        magic[0] == 0.toByte() && magic[1] == 1.toByte() -> "ttf"
                        String(magic, Charsets.US_ASCII) == "OTTO" -> "otf"
                        String(magic, Charsets.US_ASCII) == "true" -> "ttf"
                        String(magic, Charsets.US_ASCII) == "ttcf" -> "ttc"
                        else -> {
                            tmp.delete()
                            throw IllegalStateException("Это не TTF/OTF — выберите файл шрифта")
                        }
                    }
                    val target = File(dir, "custom.$extension")
                    dir.listFiles()?.forEach { file ->
                        if (file.name.startsWith("custom.") && file.name != "custom.tmp") file.delete()
                    }
                    if (target.exists()) target.delete()
                    if (!tmp.renameTo(target)) {
                        tmp.copyTo(target, overwrite = true)
                        tmp.delete()
                    }
                    target to display
                }
            }
            result.onSuccess { (file, name) ->
                settingsRepo.update {
                    it.copy(fontMode = "CUSTOM", customFontPath = file.absolutePath, customFontName = name)
                }
                message("Шрифт подключён: $name")
            }.onFailure { e ->
                _fontError.value = e.message ?: "Не удалось прочитать шрифт"
            }
            _fontLoading.value = false
        }
    }

    private fun connectionKey(s: AppSettings): String = listOf(
        s.activePrinterId, s.host, s.accessCode, s.serial, s.model, s.demoMode,
        if (s.demoMode) s.demoAms else true,
    ).joinToString("|")

    fun reconnect() {
        val s = settings.value
        if (!s.isConfigured) {
            message("Сначала заполните подключение")
            return
        }
        lastConnectionKey = connectionKey(s)
        printer.connect(
            PrinterConfig(s.host, s.accessCode, s.serial, s.model, s.printerName),
            s.demoMode,
            s.demoAms,
        )
    }

    // ------------------------------------------------------------ команды

    private fun command(ok: Boolean, success: String? = null) {
        if (ok) success?.let(::message) else message("Команда не ушла: нет связи с принтером")
    }

    fun requestRefresh() {
        printer.requestPushAll()
        message("Запросили свежие данные")
    }

    fun pause() = command(printer.pause(), "Пауза")
    fun resume() = command(printer.resume(), "Продолжаем")
    fun stop() = command(printer.stop(), "Печать остановлена")
    fun setLight(on: Boolean) = command(printer.setLight(on), if (on) "Свет включён" else "Свет выключен")
    fun setSpeed(level: Int) = command(printer.setSpeed(level), "Скорость: ${PrinterSnapshot.SPEED_NAMES[level]}")
    fun setNozzleTemp(temp: Int) = command(printer.setNozzleTemp(temp), "Сопло: $temp°")
    fun setBedTemp(temp: Int) = command(printer.setBedTemp(temp), "Стол: $temp°")
    fun setFan(kind: FanKind, percent: Int) = command(printer.setFan(kind, percent), "${kind.label}: $percent%")
    fun home() = command(printer.home(), "Парковка")
    fun move(axis: String, mm: Int) = command(printer.move(axis, mm), "$axis$mm мм")
    fun extrude(mm: Int) = command(printer.extrude(mm), if (mm >= 0) "Выдавлено $mm мм" else "Втянуто ${-mm} мм")
    fun clearError() = command(printer.clearPrintError(), "Ошибка сброшена")

    fun sendGcode(text: String) {
        val lines = text.lines()
            .map { it.substringBefore(';').trim() }
            .filter { it.isNotEmpty() }
        if (lines.isEmpty()) {
            message("Нет команд")
            return
        }
        command(printer.gcode(*lines.toTypedArray()), "G-code отправлен")
    }

    fun loadSpool(target: Int) {
        val state = snapshot.value
        val spool = when (target) {
            254 -> state.externalSpool
            else -> state.allTrays.firstOrNull { it.slot == target }
        }
        val temp = spool?.loadTemp ?: 220
        val current = state.nozzle.toInt().coerceAtLeast(temp)
        command(printer.loadSpool(target, current, temp), "Заправка запущена · $temp°")
    }

    fun unloadSpool() {
        val temp = snapshot.value.activeSpool?.loadTemp ?: 210
        val current = snapshot.value.nozzle.toInt().coerceAtLeast(temp)
        command(printer.unloadSpool(current), "Выгрузка запущена")
    }

    fun amsReply(param: String) {
        val text = when (param) {
            "resume" -> "Продолжаем заправку"
            "done" -> "Пластик пошёл"
            else -> "Мастер сброшен"
        }
        command(printer.amsControl(param), text)
    }

    fun setFanSafely(kind: FanKind, percent: Int) = setFan(kind, percent)

    // ------------------------------------------------------------ файлы

    private fun ftp(): PrinterFtpClient {
        val s = settings.value
        return PrinterFtpClient(s.host, s.accessCode, s.ftpPort)
    }

    suspend fun listFiles(path: String): Result<List<RemoteFile>> = withContext(Dispatchers.IO) {
        runCatching {
            if (settings.value.demoMode) demoFiles(path) else ftp().list(path)
        }
    }

    suspend fun deleteFile(file: RemoteFile): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (settings.value.demoMode) return@runCatching
            if (!ftp().delete(file.path)) throw FtpException("принтер не удалил файл")
        }
    }

    suspend fun downloadFile(file: RemoteFile, onProgress: (Float) -> Unit = {}): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                val target = File(app.cacheDir, "downloads/${file.name}")
                if (settings.value.demoMode) {
                    target.parentFile?.mkdirs()
                    target.writeText("demo")
                } else {
                    ftp().download(file.path, target) { sent, total ->
                        if (total > 0) onProgress((sent.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
                if (Downloads.save(app, target, file.name)) {
                    message("Сохранено в Загрузки/BambukHome")
                } else {
                    message("Файл скачан во внутренний кэш")
                }
                target
            }
        }

    fun setPrintProfile(id: String) = settingsRepo.update { it.copy(printProfile = id) }

    suspend fun prepareVideo(remote: RemoteFile, onProgress: (Float) -> Unit = {}): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (settings.value.demoMode) {
                    DemoTimelapse.ensure(app, remote.name)
                } else {
                    val target = File(app.cacheDir, "timelapse/${remote.name}")
                    val needsDownload = !target.exists() || (remote.size > 0 && target.length() != remote.size)
                    if (needsDownload) {
                        target.parentFile?.mkdirs()
                        ftp().download(remote.path, target) { sent, total ->
                            if (total > 0) onProgress((sent.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                    target
                }
            }
        }

    suspend fun printRemote(path: String, options: PrintOptions): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            settingsRepo.update { it.copy(printProfile = options.profileId) }
            val ok = printer.startPrint(path, options)
            if (!ok) throw IllegalStateException("команда не ушла")
        }
    }

    /**
     * Копирует выбранный документ в кэш приложения, проверяет формат,
     * отправляет на SD принтера и (по желанию) сразу запускает печать.
     */
    suspend fun uploadAndPrint(
        uri: Uri,
        options: PrintOptions,
        startPrint: Boolean,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val name = queryName(uri)
            if (!ModelResolver.isPrintableFile(name)) {
                throw IllegalArgumentException("Нужен готовый .3mf или .gcode. STL сначала нарежьте в слайсере.")
            }
            val local = copyToCache(uri, name)
            uploadLocal(local, startPrint, options).getOrThrow()
        }.onFailure { _transfer.value = null }
    }

    suspend fun uploadLocal(local: File, startPrint: Boolean, options: PrintOptions): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val folder = settings.value.uploadFolder
                val remote = if (settings.value.demoMode) {
                    simulateTransfer(local.name)
                    "/cache/${local.name}"
                } else {
                    _transfer.value = TransferState("Отправка на принтер", 0f)
                    runCatching { ftp().mkdir(folder) }
                    ftp().upload(folder, local.name, local.inputStream(), local.length()) { sent, total ->
                        val p = if (total > 0) sent.toFloat() / total else 0f
                        _transfer.value = TransferState("Отправка на принтер", p.coerceIn(0f, 1f), total <= 0)
                    }
                }
                if (startPrint) {
                    settingsRepo.update { it.copy(printProfile = options.profileId) }
                    _transfer.value = TransferState("Запуск печати", 1f)
                    if (!printer.startPrint(remote, options)) {
                        throw IllegalStateException("принтер не принял команду печати")
                    }
                }
                _transfer.value = null
                remote
            }.onFailure { _transfer.value = null }
        }

    suspend fun sendModelToPrinter(options: PrintOptions): Result<String> {
        val local = _models.value.local?.file ?: return Result.failure(IllegalStateException("Файл не выбран"))
        return uploadLocal(local, startPrint = true, options)
            .onSuccess { message("Печать запущена: ${local.name}") }
    }

    private suspend fun simulateTransfer(name: String): Unit {
        _transfer.value = TransferState("Отправка на принтер", 0f)
        repeat(12) { i ->
            delay(70)
            _transfer.value = TransferState("Отправка на принтер", (i + 1) / 12f)
        }
    }

    private fun copyToCache(uri: Uri, name: String): File {
        val dir = File(app.cacheDir, "imports").apply { mkdirs() }
        val target = File(dir, name.replace(Regex("[^\\p{L}\\p{N}._\\- ]+"), "_"))
        app.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { out -> input.copyTo(out, 128 * 1024) }
        } ?: throw IllegalStateException("Не удалось открыть файл")
        if (target.length() == 0L) throw IllegalStateException("Файл пустой")
        return target
    }

    private fun queryName(uri: Uri): String {
        var name = ""
        runCatching {
            app.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && cursor.moveToFirst()) name = cursor.getString(idx).orEmpty()
            }
        }
        return name.ifBlank { uri.lastPathSegment?.substringAfterLast('/') ?: "model.3mf" }
    }

    fun inspectLocal(file: File): ThreeMfInfo? =
        if (file.name.endsWith(".3mf", true)) ThreeMfInspector.inspect(file) else null

    fun selectLocal(file: File) {
        viewModelScope.launch(Dispatchers.IO) {
            val info = inspectLocal(file)
            _models.value = _models.value.copy(
                local = LocalModel(file, file.name, file.length(), info),
                error = null,
            )
        }
    }

    // ------------------------------------------------------------ модели из ссылок

    fun resolveLink(text: String) {
        if (text.isBlank()) {
            _models.value = _models.value.copy(error = "Вставьте ссылку на модель")
            return
        }
        viewModelScope.launch {
            _models.value = _models.value.copy(resolving = true, error = null, link = null, local = null)
            runCatching { resolver.resolve(text, settings.value.thingiverseToken) }
                .onSuccess { link ->
                    _models.value = _models.value.copy(resolving = false, link = link)
                    if (link.files.isEmpty()) {
                        message("Файлы не найдены — откройте страницу и скачайте 3MF вручную")
                    }
                }
                .onFailure { e ->
                    val fallback = resolver.fallbackLink(text, e.message)
                    _models.value = _models.value.copy(
                        resolving = false,
                        error = null,
                        link = fallback,
                    )
                    message("Сайт закрылся от автоматики — откройте его вручную")
                }
        }
    }

    fun downloadModel(file: RemoteModelFile) {
        viewModelScope.launch {
            _models.value = _models.value.copy(downloadName = file.name, downloadProgress = 0f, error = null)
            runCatching {
                resolver.download(file) { sent, total ->
                    val p = if (total > 0) sent.toFloat() / total else 0f
                    _models.value = _models.value.copy(downloadProgress = p.coerceIn(0f, 1f))
                }
            }.onSuccess { local ->
                val info = inspectLocal(local)
                _models.value = _models.value.copy(
                    downloadName = null,
                    local = LocalModel(local, local.name, local.length(), info),
                )
                refreshHistory()
                message("Скачано: ${local.name}")
            }.onFailure { e ->
                _models.value = _models.value.copy(downloadName = null, error = e.message ?: "Ошибка скачивания")
            }
        }
    }

    fun importShared(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val name = queryName(uri)
                val local = copyToCache(uri, name)
                val info = inspectLocal(local)
                LocalModel(local, local.name, local.length(), info)
            }.onSuccess { model ->
                _models.value = _models.value.copy(local = model, error = null)
                refreshHistory()
                _openModels.tryEmit(Unit)
            }.onFailure { message(it.message ?: "Не удалось открыть файл") }
        }
    }

    private suspend fun refreshHistory() {
        withContext(Dispatchers.IO) {
            val dirs = listOf(File(app.cacheDir, "models"), File(app.cacheDir, "imports"))
            val list = dirs.flatMap { dir ->
                dir.listFiles()?.filter { it.isFile && ModelResolver.isPrintableFile(it.name) }?.map {
                    LocalModel(it, it.name, it.length(), null)
                } ?: emptyList()
            }.sortedByDescending { it.file.lastModified() }.take(12)
            _models.value = _models.value.copy(history = list)
        }
    }

    // ------------------------------------------------------------ пропуск деталей

    suspend fun loadSkipForCurrentJob(): Result<SkipPlate> = withContext(Dispatchers.IO) {
        runCatching {
            _skipLoading.value = true
            if (settings.value.demoMode) {
                return@runCatching demoSkipPlate()
            }
            val stem = printer.snapshot.value.jobName.substringBeforeLast('.')
            if (stem.isBlank()) throw IllegalStateException("Сейчас нет активной печати")
            val client = ftp()
            val candidates = runCatching { client.list("/") }.getOrDefault(emptyList()) +
                runCatching { client.list("/cache") }.getOrDefault(emptyList())
            val file = candidates.firstOrNull { f ->
                !f.isDir && f.name.endsWith(".3mf", true) && f.name.contains(stem, true)
            } ?: candidates.firstOrNull { f ->
                !f.isDir && f.name.endsWith(".3mf", true)
            } ?: throw IllegalStateException("Не нашли файл печати на SD")

            _transfer.value = TransferState("Чтение модели с SD", 0f)
            val local = File(app.cacheDir, "skip/${file.name}")
            client.download(file.path, local) { sent, total ->
                if (total > 0) {
                    _transfer.value = TransferState("Чтение модели с SD", (sent.toFloat() / total).coerceIn(0f, 1f))
                }
            }
            _transfer.value = null
            val bed = PlateInspector.bedSizeFor(settings.value.model.name)
            val plateIndex = printer.snapshot.value.plate.coerceAtLeast(1)
            val plate = PlateInspector.inspect(local, plateIndex, bed.first, bed.second)
            if (plate.parts.isEmpty()) throw IllegalStateException("В 3MF нет списка объектов")
            plate
        }.onSuccess { plate ->
            _skipPlate.value = plate
            _skipLoading.value = false
            _transfer.value = null
        }.onFailure {
            _skipLoading.value = false
            _transfer.value = null
        }
    }

    private fun demoSkipPlate(): SkipPlate {
        val parts = listOf(
            SkipPart(11, "Корпус", listOf(PlatePoint(45f, 55f), PlatePoint(105f, 55f), PlatePoint(105f, 115f), PlatePoint(45f, 115f))),
            SkipPart(12, "Крышка", listOf(PlatePoint(120f, 60f), PlatePoint(170f, 60f), PlatePoint(170f, 110f), PlatePoint(120f, 110f))),
            SkipPart(13, "Дно", listOf(PlatePoint(55f, 135f), PlatePoint(110f, 135f), PlatePoint(110f, 190f), PlatePoint(55f, 190f))),
            SkipPart(14, "Стенка", listOf(PlatePoint(135f, 135f), PlatePoint(185f, 135f), PlatePoint(185f, 190f), PlatePoint(135f, 190f))),
            SkipPart(15, "Перегородка", listOf(PlatePoint(195f, 70f), PlatePoint(225f, 70f), PlatePoint(225f, 160f), PlatePoint(195f, 160f))),
            SkipPart(16, "Полка", listOf(PlatePoint(85f, 205f), PlatePoint(165f, 205f), PlatePoint(165f, 230f), PlatePoint(85f, 230f))),
        )
        return SkipPlate(parts, 256f, 256f)
    }

    fun requestSkipObjects(ids: List<Int>) {
        if (ids.isEmpty()) return
        command(printer.skipObjects(ids), "Пропуск: ${ids.size} дет.")
    }

    // ------------------------------------------------------------ камера

    fun cameraAvailable(): Boolean = settings.value.demoMode || settings.value.model.hasCamera

    suspend fun cameraSnapshot(): ByteArray? = withContext(Dispatchers.IO) {
        if (!cameraAvailable()) return@withContext null
        if (settings.value.demoMode) return@withContext demoCameraJpeg()
        runCatching {
            camera.snapshot(settings.value.host, settings.value.accessCode)
        }.getOrNull()
    }

    fun cameraFrames(): Flow<ByteArray> {
        if (settings.value.demoMode) {
            return flow {
                while (true) {
                    emit(demoCameraJpeg())
                    delay(800)
                }
            }.flowOn(Dispatchers.IO)
        }
        return camera.frames(settings.value.host, settings.value.accessCode)
    }

    fun saveSnapshot(jpeg: ByteArray) {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = CameraClient.saveToGallery(app, jpeg)
            withContext(Dispatchers.Main) {
                message(if (ok) "Снимок сохранён в галерею" else "Не удалось сохранить снимок")
            }
        }
    }

    fun toggleCameraLight() {
        val s = snapshot.value
        val on = s.lightOn ?: false
        setLight(!on)
    }

    fun setStats(totalMinutes: Int, printsFinished: Int) =
        statsRepo.updateManual(totalMinutes, printsFinished)

    fun resetStats() = statsRepo.reset()

    override fun onCleared() {
        super.onCleared()
        statsTicker?.cancel()
        PrintState.clear()
        PrintMonitorService.stop(app)
        printer.disconnect()
    }

    // ------------------------------------------------------------ демо-данные

    private fun demoFiles(path: String): List<RemoteFile> {
        val now = System.currentTimeMillis()
        return when (path.trimEnd('/')) {
            "", "/" -> listOf(
                RemoteFile("cache", "/cache", true, 0, now),
                RemoteFile("timelapse", "/timelapse", true, 0, now),
                RemoteFile("Ваза hex.3mf", "/Ваза hex.3mf", false, 18_400_000, now - 3_600_000),
                RemoteFile("Органайзер.3mf", "/Органайзер.3mf", false, 24_100_000, now - 86_400_000),
                RemoteFile("Подставка.3mf", "/Подставка.3mf", false, 9_800_000, now - 172_800_000),
                RemoteFile("PA_PLA_clean.gcode", "/PA_PLA_clean.gcode", false, 240_000, now - 259_200_000),
            )
            "/cache" -> listOf(
                RemoteFile("Кольцо.3mf", "/cache/Кольцо.3mf", false, 12_300_000, now - 240_000),
                RemoteFile("Клипса.3mf", "/cache/Клипса.3mf", false, 5_600_000, now - 400_000),
            )
            "/timelapse" -> listOf(
                RemoteFile("video_2026-11-21_13-42-10.avi", "/timelapse/video_2026-11-21_13-42-10.avi", false, 74_000_000, now - 40_000),
                RemoteFile("video_2026-11-20_19-05-44.avi", "/timelapse/video_2026-11-20_19-05-44.avi", false, 122_000_000, now - 86_000_000),
            )
            else -> emptyList()
        }
    }

    private fun demoCameraJpeg(): ByteArray {
        demoCameraCache?.let { return it }
        val w = 1280
        val h = 720
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        paint.shader = LinearGradient(0f, 0f, 0f, h.toFloat(),
            Color.rgb(8, 14, 10), Color.rgb(24, 40, 28), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        paint.shader = null

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 10f
        paint.color = Color.rgb(42, 62, 46)
        c.drawRect(70f, 40f, w - 70f, h - 40f, paint)
        paint.strokeWidth = 4f
        paint.color = Color.rgb(30, 46, 34)
        c.drawLine(120f, 80f, 120f, h - 80f, paint)
        c.drawLine(w - 120f, 80f, w - 120f, h - 80f, paint)

        val bed = Path().apply {
            moveTo(180f, 620f); lineTo(1100f, 620f); lineTo(880f, 430f); lineTo(400f, 430f); close()
        }
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(30, 48, 34)
        c.drawPath(bed, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3f
        paint.color = Color.rgb(62, 96, 68)
        c.drawPath(bed, paint)
        for (i in 1..5) {
            val t = i / 6f
            c.drawLine(400f + (880f - 400f) * t, 430f, 180f + (1100f - 180f) * t, 620f, paint)
        }

        paint.style = Paint.Style.FILL
        c.drawRect(300f, 250f, 980f, 264f, paint.apply { color = Color.rgb(48, 68, 52) })
        c.drawRoundRect(600f, 264f, 690f, 330f, 12f, 12f, paint.apply { color = Color.rgb(38, 54, 42) })
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = Color.rgb(140, 230, 150)
        c.drawLine(640f, 340f, 640f, 430f, paint)

        // ваза
        val vase = Path().apply {
            val cx = 640f
            fun p(y: Float, rx: Float) = Pair(cx, y)
            moveTo(590f, 600f)
            cubicTo(570f, 560f, 566f, 520f, 585f, 480f)
            cubicTo(600f, 450f, 598f, 420f, 605f, 398f)
            lineTo(675f, 398f)
            cubicTo(682f, 420f, 680f, 450f, 695f, 480f)
            cubicTo(714f, 520f, 710f, 560f, 690f, 600f)
            close()
        }
        paint.style = Paint.Style.FILL
        paint.shader = LinearGradient(560f, 0f, 720f, 0f,
            Color.rgb(150, 235, 160), Color.rgb(58, 128, 70), Shader.TileMode.CLAMP)
        c.drawPath(vase, paint)
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = Color.argb(90, 20, 50, 26)
        c.drawPath(vase, paint)
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(34, 74, 42)
        c.drawOval(585f, 388f, 695f, 408f, paint)

        paint.color = Color.argb(70, 140, 230, 150)
        c.drawOval(520f, 580f, 760f, 640f, paint)

        var out = java.io.ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 88, out)
        bitmap.recycle()
        return out.toByteArray().also { demoCameraCache = it }
    }

    private var demoCameraCache: ByteArray? = null
}