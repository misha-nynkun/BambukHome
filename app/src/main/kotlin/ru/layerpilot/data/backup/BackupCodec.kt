package ru.layerpilot.data.backup

import android.util.Base64
import org.json.JSONObject
import ru.layerpilot.data.model.PrinterModel
import ru.layerpilot.data.model.ThemeMode
import ru.layerpilot.data.settings.AppSettings
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Зашифрованная резервная копия профилей и настроек.
 * Формат: BAMBUK1:<base64(salt|iv|ciphertext)>, AES-256-GCM, PBKDF2.
 */
object BackupCodec {
    private const val PREFIX = "BAMBUK1:"
    private const val ITERATIONS = 120_000
    private const val KEY_BITS = 256

    fun export(settings: AppSettings, password: String): String {
        val json = toJson(settings).toString()
        return PREFIX + Base64.encodeToString(encrypt(json, password), Base64.NO_WRAP)
    }

    fun import(text: String, password: String): AppSettings? = runCatching {
        val clean = text.trim()
        if (!clean.startsWith(PREFIX)) return null
        val raw = Base64.decode(clean.removePrefix(PREFIX), Base64.NO_WRAP)
        val json = decrypt(raw, password)
        fromJson(JSONObject(json))
    }.getOrNull()

    private fun encrypt(plain: String, password: String): ByteArray {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(password, salt), GCMParameterSpec(128, iv))
        val body = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return salt + iv + body
    }

    private fun decrypt(raw: ByteArray, password: String): String {
        require(raw.size > 28) { "short" }
        val salt = raw.copyOfRange(0, 16)
        val iv = raw.copyOfRange(16, 28)
        val body = raw.copyOfRange(28, raw.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(password, salt), GCMParameterSpec(128, iv))
        return cipher.doFinal(body).toString(Charsets.UTF_8)
    }

    private fun key(password: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_BITS)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(spec).encoded
        return SecretKeySpec(bytes, "AES")
    }

    private fun toJson(s: AppSettings): JSONObject = JSONObject().apply {
        put("host", s.host); put("accessCode", s.accessCode); put("serial", s.serial)
        put("model", s.model.name); put("printerName", s.printerName)
        put("demoMode", s.demoMode); put("demoAms", s.demoAms)
        put("dynamicColor", s.dynamicColor); put("amoled", s.amoled)
        put("themeMode", s.themeMode.name); put("accentMode", s.accentMode)
        put("accentHue", s.accentHue); put("uiScale", s.uiScale)
        put("waveThickness", s.waveThickness); put("waveAmplitude", s.waveAmplitude)
        put("fontMode", s.fontMode); put("customFontPath", s.customFontPath)
        put("customFontName", s.customFontName); put("language", s.language)
        put("liveNotification", s.liveNotification); put("dashboardLive", s.dashboardLive)
        put("printProfile", s.printProfile); put("waveAnimation", s.waveAnimation)
        put("notifications", s.notifications); put("notifyFinish", s.notifyFinish)
        put("notifyPause", s.notifyPause); put("notifyError", s.notifyError)
        put("cameraAutoLight", s.cameraAutoLight); put("keepScreenOn", s.keepScreenOn)
        put("timelapseDefault", s.timelapseDefault); put("bedLevelingDefault", s.bedLevelingDefault)
        put("flowCaliDefault", s.flowCaliDefault); put("vibrationCaliDefault", s.vibrationCaliDefault)
        put("uploadFolder", s.uploadFolder); put("ftpPort", s.ftpPort)
        put("printersJson", s.printersJson); put("activePrinterId", s.activePrinterId)
        put("dashboardOrder", s.dashboardOrder); put("dashboardHidden", s.dashboardHidden)
        put("energyPricePerKwh", s.energyPricePerKwh.toDouble())
        put("biometricLock", s.biometricLock); put("hideCameraInRecents", s.hideCameraInRecents)
        put("spaghettiDetection", s.spaghettiDetection)
        put("showFarmQuickAction", s.showFarmQuickAction)
        put("clawdEnabled", s.clawdEnabled); put("clawdName", s.clawdName)
        put("clawdOwnerName", s.clawdOwnerName); put("clawdSize", s.clawdSize)
        put("clawdColor", s.clawdColor)
        put("clawdWander", s.clawdWander); put("clawdPhrases", s.clawdPhrases)
        put("clawdSleepMin", s.clawdSleepMin); put("clawdEat", s.clawdEat)
        put("clawdPoop", s.clawdPoop); put("clawdShake", s.clawdShake)
        put("clawdHaptics", s.clawdHaptics); put("clawdBrain", s.clawdBrain)
        put("clawdAbout", s.clawdAbout); put("clawdDance", s.clawdDance)
        put("clawdReduceMotion", s.clawdReduceMotion); put("clawdQuiet", s.clawdQuiet)
    }

    private fun fromJson(o: JSONObject): AppSettings = AppSettings(
        host = o.optString("host", ""), accessCode = o.optString("accessCode", ""),
        serial = o.optString("serial", ""), model = PrinterModel.fromName(o.optString("model")),
        printerName = o.optString("printerName", "Bambu P1S"),
        demoMode = o.optBoolean("demoMode", false), demoAms = o.optBoolean("demoAms", true),
        dynamicColor = o.optBoolean("dynamicColor", false), amoled = o.optBoolean("amoled", true),
        themeMode = runCatching { ThemeMode.valueOf(o.optString("themeMode")) }.getOrDefault(ThemeMode.SYSTEM),
        accentMode = o.optString("accentMode", "BRAND"), accentHue = o.optInt("accentHue", 130),
        uiScale = o.optDouble("uiScale", 1.0).toFloat(),
        waveThickness = o.optDouble("waveThickness", 1.0).toFloat(),
        waveAmplitude = o.optDouble("waveAmplitude", 1.0).toFloat(),
        fontMode = o.optString("fontMode", "SYSTEM"), customFontPath = o.optString("customFontPath", ""),
        customFontName = o.optString("customFontName", ""), language = o.optString("language", ""),
        liveNotification = o.optBoolean("liveNotification", true),
        dashboardLive = o.optBoolean("dashboardLive", true),
        printProfile = o.optString("printProfile", "standard"),
        waveAnimation = o.optBoolean("waveAnimation", true),
        notifications = o.optBoolean("notifications", true),
        notifyFinish = o.optBoolean("notifyFinish", true),
        notifyPause = o.optBoolean("notifyPause", true),
        notifyError = o.optBoolean("notifyError", true),
        cameraAutoLight = o.optBoolean("cameraAutoLight", false),
        keepScreenOn = o.optBoolean("keepScreenOn", true),
        timelapseDefault = o.optBoolean("timelapseDefault", true),
        bedLevelingDefault = o.optBoolean("bedLevelingDefault", true),
        flowCaliDefault = o.optBoolean("flowCaliDefault", false),
        vibrationCaliDefault = o.optBoolean("vibrationCaliDefault", false),
        uploadFolder = o.optString("uploadFolder", "/cache"), ftpPort = o.optInt("ftpPort", 990),
        printersJson = o.optString("printersJson", ""),
        activePrinterId = o.optString("activePrinterId", ""),
        dashboardOrder = o.optString("dashboardOrder", ""),
        dashboardHidden = o.optString("dashboardHidden", ""),
        energyPricePerKwh = o.optDouble("energyPricePerKwh", 0.0).toFloat(),
        biometricLock = o.optBoolean("biometricLock", false),
        hideCameraInRecents = o.optBoolean("hideCameraInRecents", true),
        spaghettiDetection = o.optBoolean("spaghettiDetection", false),
        showFarmQuickAction = o.optBoolean("showFarmQuickAction", false),
        clawdEnabled = o.optBoolean("clawdEnabled", false),
        clawdName = o.optString("clawdName", "Clawd"),
        clawdOwnerName = o.optString("clawdOwnerName", ""),
        clawdSize = o.optInt("clawdSize", 1), clawdColor = o.optInt("clawdColor", 0),
        clawdWander = o.optBoolean("clawdWander", true),
        clawdPhrases = o.optBoolean("clawdPhrases", true),
        clawdSleepMin = o.optInt("clawdSleepMin", 3),
        clawdEat = o.optBoolean("clawdEat", true), clawdPoop = o.optBoolean("clawdPoop", true),
        clawdShake = o.optBoolean("clawdShake", true), clawdHaptics = o.optBoolean("clawdHaptics", true),
        clawdBrain = o.optBoolean("clawdBrain", true), clawdAbout = o.optString("clawdAbout", ""),
        clawdDance = o.optBoolean("clawdDance", true),
        clawdReduceMotion = o.optBoolean("clawdReduceMotion", false),
        clawdQuiet = o.optBoolean("clawdQuiet", true),
    )
}
