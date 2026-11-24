package ru.layerpilot.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ru.layerpilot.data.model.PrinterModel
import ru.layerpilot.data.model.ThemeMode

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class AppSettings(
    val host: String = "",
    val accessCode: String = "",
    val serial: String = "",
    val model: PrinterModel = PrinterModel.P1S,
    val printerName: String = "Bambu P1S",
    val demoMode: Boolean = false,
    val demoAms: Boolean = true,
    val dynamicColor: Boolean = false,
    val amoled: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** BRAND — фирменный зелёный, CUSTOM — свой оттенок, SYSTEM — обои Android. */
    val accentMode: String = "BRAND",
    val accentHue: Int = 130,
    val uiScale: Float = 1f,
    val waveThickness: Float = 1f,
    val waveAmplitude: Float = 1f,
    /** SYSTEM, NDOT или CUSTOM (загруженный .ttf). */
    val fontMode: String = "SYSTEM",
    val customFontPath: String = "",
    val customFontName: String = "",
    val liveNotification: Boolean = true,
    val dashboardLive: Boolean = true,
    val language: String = "",
    val printProfile: String = "standard",
    val waveAnimation: Boolean = true,
    val notifications: Boolean = true,
    val notifyFinish: Boolean = true,
    val notifyPause: Boolean = true,
    val notifyError: Boolean = true,
    val cameraAutoLight: Boolean = false,
    val keepScreenOn: Boolean = true,
    val timelapseDefault: Boolean = true,
    val bedLevelingDefault: Boolean = true,
    val flowCaliDefault: Boolean = false,
    val vibrationCaliDefault: Boolean = false,
    val uploadFolder: String = "/cache",
    val ftpPort: Int = 990,
    val printersJson: String = "",
    val activePrinterId: String = "",
    val dashboardOrder: String = "",
    val dashboardHidden: String = "",
    // ---- Clawd, локальный питомец. По умолчанию выключен.
    val clawdEnabled: Boolean = false,
    val clawdName: String = "Clawd",
    val clawdOwnerName: String = "",
    val clawdSize: Int = 1,
    val clawdColor: Int = 0,
    val clawdWander: Boolean = true,
    val clawdPhrases: Boolean = true,
    val clawdSleepMin: Int = 3,
    val clawdEat: Boolean = true,
    val clawdPoop: Boolean = true,
    val clawdShake: Boolean = true,
    val clawdHaptics: Boolean = true,
    val clawdBrain: Boolean = true,
    val clawdAbout: String = "",
    val clawdDance: Boolean = true,
    val clawdReduceMotion: Boolean = false,
    val clawdQuiet: Boolean = true,
    val energyPricePerKwh: Float = 0f,
    val biometricLock: Boolean = false,
    val hideCameraInRecents: Boolean = true,
    val spaghettiDetection: Boolean = false,
    val showFarmQuickAction: Boolean = false,
) {
    val isConfigured: Boolean
        get() = demoMode || (host.isNotBlank() && accessCode.isNotBlank() && serial.isNotBlank())
}

class SettingsRepository(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings
    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded

    init {
        scope.launch {
            val stored = runCatching { context.dataStore.data.first() }.getOrNull()
            if (stored != null) {
                var value = stored.toSettings()
                if (stored[Keys.SMART_LIGHT_MIGRATED] != true) {
                    // В 1.1.6 «свет при открытии» стал умным: включаем один раз
                    // для существующих установок, но дальше пользователь может выключить.
                    value = value.copy(cameraAutoLight = true)
                    context.dataStore.edit { prefs ->
                        prefs[Keys.SMART_LIGHT_MIGRATED] = true
                        prefs[Keys.CAM_LIGHT] = true
                    }
                }
                if (value.printersJson.isBlank() &&
                    (value.host.isNotBlank() || value.demoMode)
                ) {
                    value = value.syncActiveProfile()
                    context.dataStore.edit { prefs ->
                        prefs[Keys.PRINTERS] = value.printersJson
                        prefs[Keys.ACTIVE_PRINTER] = value.activePrinterId
                    }
                }
                _settings.value = value
            }
            _loaded.value = true
        }
    }

    fun update(block: (AppSettings) -> AppSettings) {
        val current = _settings.value
        val next = block(current).syncActiveProfile()
        if (next == current) return
        _settings.value = next
        scope.launch {
            runCatching {
                context.dataStore.edit { prefs ->
                    prefs[Keys.HOST] = next.host.trim()
                    prefs[Keys.CODE] = next.accessCode.trim()
                    prefs[Keys.SERIAL] = next.serial.trim()
                    prefs[Keys.MODEL] = next.model.name
                    prefs[Keys.NAME] = next.printerName
                    prefs[Keys.DEMO] = next.demoMode
                    prefs[Keys.DEMO_AMS] = next.demoAms
                    prefs[Keys.DYNAMIC] = next.dynamicColor
                    prefs[Keys.AMOLED] = next.amoled
                    prefs[Keys.THEME] = next.themeMode.name
                    prefs[Keys.ACCENT_MODE] = next.accentMode
                    prefs[Keys.ACCENT_HUE] = next.accentHue
                    prefs[Keys.UI_SCALE] = next.uiScale
                    prefs[Keys.WAVE_THICKNESS] = next.waveThickness
                    prefs[Keys.WAVE_AMPLITUDE] = next.waveAmplitude
                    prefs[Keys.FONT_MODE] = next.fontMode
                    prefs[Keys.FONT_PATH] = next.customFontPath
                    prefs[Keys.FONT_NAME] = next.customFontName
                    prefs[Keys.LIVE_NOTIF] = next.liveNotification
                    prefs[Keys.DASH_LIVE] = next.dashboardLive
                    prefs[Keys.LANGUAGE] = next.language
                    prefs[Keys.PROFILE] = next.printProfile
                    prefs[Keys.WAVE] = next.waveAnimation
                    prefs[Keys.NOTIF] = next.notifications
                    prefs[Keys.NOTIF_FINISH] = next.notifyFinish
                    prefs[Keys.NOTIF_PAUSE] = next.notifyPause
                    prefs[Keys.NOTIF_ERROR] = next.notifyError
                    prefs[Keys.CAM_LIGHT] = next.cameraAutoLight
                    prefs[Keys.KEEP_ON] = next.keepScreenOn
                    prefs[Keys.TIMELAPSE] = next.timelapseDefault
                    prefs[Keys.BED_LEVEL] = next.bedLevelingDefault
                    prefs[Keys.FLOW] = next.flowCaliDefault
                    prefs[Keys.VIBRATION] = next.vibrationCaliDefault
                    prefs[Keys.UPLOAD] = next.uploadFolder
                    prefs[Keys.FTP_PORT] = next.ftpPort.toString()
                    prefs[Keys.PRINTERS] = next.printersJson
                    prefs[Keys.ACTIVE_PRINTER] = next.activePrinterId
                    prefs[Keys.DASHBOARD_ORDER] = next.dashboardOrder
                    prefs[Keys.DASHBOARD_HIDDEN] = next.dashboardHidden
                    prefs[Keys.CLAWD_ENABLED] = next.clawdEnabled
                    prefs[Keys.CLAWD_NAME] = next.clawdName
                    prefs[Keys.CLAWD_OWNER] = next.clawdOwnerName
                    prefs[Keys.CLAWD_SIZE] = next.clawdSize
                    prefs[Keys.CLAWD_COLOR] = next.clawdColor
                    prefs[Keys.CLAWD_WANDER] = next.clawdWander
                    prefs[Keys.CLAWD_PHRASES] = next.clawdPhrases
                    prefs[Keys.CLAWD_SLEEP] = next.clawdSleepMin
                    prefs[Keys.CLAWD_EAT] = next.clawdEat
                    prefs[Keys.CLAWD_POOP] = next.clawdPoop
                    prefs[Keys.CLAWD_SHAKE] = next.clawdShake
                    prefs[Keys.CLAWD_HAPTICS] = next.clawdHaptics
                    prefs[Keys.CLAWD_BRAIN] = next.clawdBrain
                    prefs[Keys.CLAWD_ABOUT] = next.clawdAbout
                    prefs[Keys.CLAWD_DANCE] = next.clawdDance
                    prefs[Keys.CLAWD_REDUCE] = next.clawdReduceMotion
                    prefs[Keys.CLAWD_QUIET] = next.clawdQuiet
                    prefs[Keys.ENERGY_PRICE] = next.energyPricePerKwh
                    prefs[Keys.BIOMETRIC] = next.biometricLock
                    prefs[Keys.CAMERA_SECURE] = next.hideCameraInRecents
                    prefs[Keys.SPAGHETTI] = next.spaghettiDetection
                    prefs[Keys.FARM_QUICK] = next.showFarmQuickAction
                }
            }
        }
    }

    private fun Preferences.toSettings(): AppSettings = AppSettings(
        host = this[Keys.HOST] ?: "",
        accessCode = this[Keys.CODE] ?: "",
        serial = this[Keys.SERIAL] ?: "",
        model = PrinterModel.fromName(this[Keys.MODEL]),
        printerName = this[Keys.NAME] ?: "Bambu P1S",
        demoMode = this[Keys.DEMO] ?: false,
        demoAms = this[Keys.DEMO_AMS] ?: true,
        dynamicColor = this[Keys.DYNAMIC] ?: false,
        amoled = this[Keys.AMOLED] ?: true,
        themeMode = runCatching { ThemeMode.valueOf(this[Keys.THEME] ?: "SYSTEM") }.getOrDefault(ThemeMode.SYSTEM),
        accentMode = this[Keys.ACCENT_MODE] ?: if (this[Keys.DYNAMIC] == true) "SYSTEM" else "BRAND",
        accentHue = this[Keys.ACCENT_HUE] ?: 130,
        uiScale = this[Keys.UI_SCALE] ?: 1f,
        waveThickness = this[Keys.WAVE_THICKNESS] ?: 1f,
        waveAmplitude = this[Keys.WAVE_AMPLITUDE] ?: 1f,
        fontMode = this[Keys.FONT_MODE] ?: "SYSTEM",
        customFontPath = this[Keys.FONT_PATH] ?: "",
        customFontName = this[Keys.FONT_NAME] ?: "",
        liveNotification = this[Keys.LIVE_NOTIF] ?: true,
        dashboardLive = this[Keys.DASH_LIVE] ?: true,
        language = this[Keys.LANGUAGE] ?: "",
        printProfile = this[Keys.PROFILE] ?: "standard",
        waveAnimation = this[Keys.WAVE] ?: true,
        notifications = this[Keys.NOTIF] ?: true,
        notifyFinish = this[Keys.NOTIF_FINISH] ?: true,
        notifyPause = this[Keys.NOTIF_PAUSE] ?: true,
        notifyError = this[Keys.NOTIF_ERROR] ?: true,
        cameraAutoLight = this[Keys.CAM_LIGHT] ?: false,
        keepScreenOn = this[Keys.KEEP_ON] ?: true,
        timelapseDefault = this[Keys.TIMELAPSE] ?: true,
        bedLevelingDefault = this[Keys.BED_LEVEL] ?: true,
        flowCaliDefault = this[Keys.FLOW] ?: false,
        vibrationCaliDefault = this[Keys.VIBRATION] ?: false,
        uploadFolder = this[Keys.UPLOAD] ?: "/cache",
        ftpPort = this[Keys.FTP_PORT]?.toIntOrNull() ?: 990,
        printersJson = this[Keys.PRINTERS] ?: "",
        activePrinterId = this[Keys.ACTIVE_PRINTER] ?: "",
        dashboardOrder = this[Keys.DASHBOARD_ORDER] ?: "",
        dashboardHidden = this[Keys.DASHBOARD_HIDDEN] ?: "",
        clawdEnabled = this[Keys.CLAWD_ENABLED] ?: false,
        clawdName = this[Keys.CLAWD_NAME] ?: "Clawd",
        clawdOwnerName = this[Keys.CLAWD_OWNER] ?: "",
        clawdSize = this[Keys.CLAWD_SIZE] ?: 1,
        clawdColor = this[Keys.CLAWD_COLOR] ?: 0,
        clawdWander = this[Keys.CLAWD_WANDER] ?: true,
        clawdPhrases = this[Keys.CLAWD_PHRASES] ?: true,
        clawdSleepMin = this[Keys.CLAWD_SLEEP] ?: 3,
        clawdEat = this[Keys.CLAWD_EAT] ?: true,
        clawdPoop = this[Keys.CLAWD_POOP] ?: true,
        clawdShake = this[Keys.CLAWD_SHAKE] ?: true,
        clawdHaptics = this[Keys.CLAWD_HAPTICS] ?: true,
        clawdBrain = this[Keys.CLAWD_BRAIN] ?: true,
        clawdAbout = this[Keys.CLAWD_ABOUT] ?: "",
        clawdDance = this[Keys.CLAWD_DANCE] ?: true,
        clawdReduceMotion = this[Keys.CLAWD_REDUCE] ?: false,
        clawdQuiet = this[Keys.CLAWD_QUIET] ?: true,
        energyPricePerKwh = this[Keys.ENERGY_PRICE] ?: 0f,
        biometricLock = this[Keys.BIOMETRIC] ?: false,
        hideCameraInRecents = this[Keys.CAMERA_SECURE] ?: true,
        spaghettiDetection = this[Keys.SPAGHETTI] ?: false,
        showFarmQuickAction = this[Keys.FARM_QUICK] ?: false,
    )

    private object Keys {
        val HOST = stringPreferencesKey("host")
        val CODE = stringPreferencesKey("access_code")
        val SERIAL = stringPreferencesKey("serial")
        val MODEL = stringPreferencesKey("model")
        val NAME = stringPreferencesKey("printer_name")
        val DEMO = booleanPreferencesKey("demo_mode")
        val DEMO_AMS = booleanPreferencesKey("demo_ams")
        val DYNAMIC = booleanPreferencesKey("dynamic_color")
        val AMOLED = booleanPreferencesKey("amoled")
        val THEME = stringPreferencesKey("theme_mode")
        val ACCENT_MODE = stringPreferencesKey("accent_mode")
        val ACCENT_HUE = intPreferencesKey("accent_hue")
        val UI_SCALE = floatPreferencesKey("ui_scale")
        val WAVE_THICKNESS = floatPreferencesKey("wave_thickness")
        val WAVE_AMPLITUDE = floatPreferencesKey("wave_amplitude")
        val FONT_MODE = stringPreferencesKey("font_mode")
        val FONT_PATH = stringPreferencesKey("font_path")
        val FONT_NAME = stringPreferencesKey("font_name")
        val LIVE_NOTIF = booleanPreferencesKey("live_notification")
        val DASH_LIVE = booleanPreferencesKey("dashboard_live")
        val LANGUAGE = stringPreferencesKey("language")
        val PROFILE = stringPreferencesKey("print_profile")
        val WAVE = booleanPreferencesKey("wave_animation")
        val NOTIF = booleanPreferencesKey("notifications")
        val NOTIF_FINISH = booleanPreferencesKey("notify_finish")
        val NOTIF_PAUSE = booleanPreferencesKey("notify_pause")
        val NOTIF_ERROR = booleanPreferencesKey("notify_error")
        val CAM_LIGHT = booleanPreferencesKey("camera_auto_light")
        val KEEP_ON = booleanPreferencesKey("keep_screen_on")
        val TIMELAPSE = booleanPreferencesKey("timelapse_default")
        val BED_LEVEL = booleanPreferencesKey("bed_leveling_default")
        val FLOW = booleanPreferencesKey("flow_cali_default")
        val VIBRATION = booleanPreferencesKey("vibration_cali_default")
        val UPLOAD = stringPreferencesKey("upload_folder")
        val FTP_PORT = stringPreferencesKey("ftp_port")
        val PRINTERS = stringPreferencesKey("printer_profiles")
        val ACTIVE_PRINTER = stringPreferencesKey("active_printer")
        val DASHBOARD_ORDER = stringPreferencesKey("dashboard_order")
        val DASHBOARD_HIDDEN = stringPreferencesKey("dashboard_hidden")
        val CLAWD_ENABLED = booleanPreferencesKey("clawd_enabled")
        val CLAWD_NAME = stringPreferencesKey("clawd_name")
        val CLAWD_OWNER = stringPreferencesKey("clawd_owner")
        val CLAWD_SIZE = intPreferencesKey("clawd_size")
        val CLAWD_COLOR = intPreferencesKey("clawd_color")
        val CLAWD_WANDER = booleanPreferencesKey("clawd_wander")
        val CLAWD_PHRASES = booleanPreferencesKey("clawd_phrases")
        val CLAWD_SLEEP = intPreferencesKey("clawd_sleep")
        val CLAWD_EAT = booleanPreferencesKey("clawd_eat")
        val CLAWD_POOP = booleanPreferencesKey("clawd_poop")
        val CLAWD_SHAKE = booleanPreferencesKey("clawd_shake")
        val CLAWD_HAPTICS = booleanPreferencesKey("clawd_haptics")
        val CLAWD_BRAIN = booleanPreferencesKey("clawd_brain")
        val CLAWD_ABOUT = stringPreferencesKey("clawd_about")
        val CLAWD_DANCE = booleanPreferencesKey("clawd_dance")
        val CLAWD_REDUCE = booleanPreferencesKey("clawd_reduce")
        val CLAWD_QUIET = booleanPreferencesKey("clawd_quiet")
        val ENERGY_PRICE = floatPreferencesKey("energy_price")
        val BIOMETRIC = booleanPreferencesKey("biometric_lock")
        val CAMERA_SECURE = booleanPreferencesKey("camera_secure")
        val SPAGHETTI = booleanPreferencesKey("spaghetti_detection")
        val FARM_QUICK = booleanPreferencesKey("farm_quick_action")
        val SMART_LIGHT_MIGRATED = booleanPreferencesKey("smart_light_migrated")
    }
}
