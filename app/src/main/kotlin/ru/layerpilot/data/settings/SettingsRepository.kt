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
    val thingiverseToken: String = "",
    val printersJson: String = "",
    val activePrinterId: String = "",
    val showModelsTab: Boolean = true,
    val showMakerWorldTab: Boolean = true,
    val dashboardOrder: String = "",
    val dashboardHidden: String = "",
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
                    prefs[Keys.THINGIVERSE] = next.thingiverseToken.trim()
                    prefs[Keys.PRINTERS] = next.printersJson
                    prefs[Keys.ACTIVE_PRINTER] = next.activePrinterId
                    prefs[Keys.SHOW_MODELS] = next.showModelsTab
                    prefs[Keys.SHOW_MAKERWORLD] = next.showMakerWorldTab
                    prefs[Keys.DASHBOARD_ORDER] = next.dashboardOrder
                    prefs[Keys.DASHBOARD_HIDDEN] = next.dashboardHidden
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
        thingiverseToken = this[Keys.THINGIVERSE] ?: "",
        printersJson = this[Keys.PRINTERS] ?: "",
        activePrinterId = this[Keys.ACTIVE_PRINTER] ?: "",
        showModelsTab = this[Keys.SHOW_MODELS] ?: true,
        showMakerWorldTab = this[Keys.SHOW_MAKERWORLD] ?: true,
        dashboardOrder = this[Keys.DASHBOARD_ORDER] ?: "",
        dashboardHidden = this[Keys.DASHBOARD_HIDDEN] ?: "",
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
        val THINGIVERSE = stringPreferencesKey("thingiverse_token")
        val PRINTERS = stringPreferencesKey("printer_profiles")
        val ACTIVE_PRINTER = stringPreferencesKey("active_printer")
        val SHOW_MODELS = booleanPreferencesKey("show_models_tab")
        val SHOW_MAKERWORLD = booleanPreferencesKey("show_makerworld_tab")
        val DASHBOARD_ORDER = stringPreferencesKey("dashboard_order")
        val DASHBOARD_HIDDEN = stringPreferencesKey("dashboard_hidden")
    }
}
