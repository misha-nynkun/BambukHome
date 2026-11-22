package ru.layerpilot.data.settings

import org.json.JSONArray
import org.json.JSONObject
import ru.layerpilot.data.model.PrinterModel
import java.util.UUID

/** Один сохранённый принтер. Активный принтер дублируется в полях AppSettings. */
data class PrinterProfile(
    val id: String,
    val name: String,
    val host: String,
    val accessCode: String,
    val serial: String,
    val model: PrinterModel,
    val demo: Boolean,
    val demoAms: Boolean,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("host", host)
        .put("code", accessCode)
        .put("serial", serial)
        .put("model", model.name)
        .put("demo", demo)
        .put("demo_ams", demoAms)

    companion object {
        fun fromJson(obj: JSONObject): PrinterProfile = PrinterProfile(
            id = obj.optString("id").ifBlank { UUID.randomUUID().toString() },
            name = obj.optString("name").ifBlank { "Bambu" },
            host = obj.optString("host"),
            accessCode = obj.optString("code"),
            serial = obj.optString("serial"),
            model = PrinterModel.fromName(obj.optString("model")),
            demo = obj.optBoolean("demo"),
            demoAms = obj.optBoolean("demo_ams", true),
        )

        fun newId(): String = UUID.randomUUID().toString()
    }

    val isConfigured: Boolean
        get() = demo || (host.isNotBlank() && accessCode.isNotBlank() && serial.isNotBlank())
}

fun decodePrinterProfiles(raw: String): List<PrinterProfile> = runCatching {
    if (raw.isBlank()) return emptyList()
    val array = JSONArray(raw)
    (0 until array.length()).mapNotNull { index ->
        array.optJSONObject(index)?.let { PrinterProfile.fromJson(it) }
    }
}.getOrDefault(emptyList())

fun encodePrinterProfiles(profiles: List<PrinterProfile>): String {
    val array = JSONArray()
    profiles.forEach { array.put(it.toJson()) }
    return array.toString()
}

fun AppSettings.profileList(): List<PrinterProfile> {
    val saved = decodePrinterProfiles(printersJson)
    if (saved.isNotEmpty()) return saved
    if (host.isBlank() && !demoMode) return emptyList()
    return listOf(currentAsProfile("default"))
}

fun AppSettings.currentProfileId(): String =
    activePrinterId.ifBlank { profileList().firstOrNull()?.id.orEmpty() }

fun AppSettings.currentProfile(): PrinterProfile? =
    profileList().firstOrNull { it.id == currentProfileId() } ?: profileList().firstOrNull()

fun AppSettings.currentAsProfile(id: String = currentProfileId().ifBlank { "default" }): PrinterProfile =
    PrinterProfile(
        id = id,
        name = printerName.ifBlank { model.label },
        host = host,
        accessCode = accessCode,
        serial = serial,
        model = model,
        demo = demoMode,
        demoAms = demoAms,
    )

/** Синхронизирует поля активного принтера со списком профилей. */
fun AppSettings.syncActiveProfile(): AppSettings {
    val base = profileList()
    if (base.isEmpty()) {
        return if (host.isBlank() && !demoMode) this else {
            val profile = currentAsProfile()
            copy(
                printersJson = encodePrinterProfiles(listOf(profile)),
                activePrinterId = profile.id,
            )
        }
    }
    val id = activePrinterId.ifBlank { base.first().id }
    val current = currentAsProfile(id)
    val list = base.toMutableList()
    val index = list.indexOfFirst { it.id == id }
    if (index >= 0) list[index] = current else list.add(current)
    return copy(
        activePrinterId = id,
        printersJson = encodePrinterProfiles(list),
    )
}

fun AppSettings.activatedProfile(id: String): AppSettings {
    val list = profileList()
    val profile = list.firstOrNull { it.id == id } ?: return this
    return copy(
        printersJson = encodePrinterProfiles(list),
        activePrinterId = profile.id,
        host = profile.host,
        accessCode = profile.accessCode,
        serial = profile.serial,
        model = profile.model,
        printerName = profile.name,
        demoMode = profile.demo,
        demoAms = profile.demoAms,
    )
}

fun AppSettings.upsertProfile(profile: PrinterProfile): AppSettings {
    val list = profileList().toMutableList()
    val index = list.indexOfFirst { it.id == profile.id }
    if (index >= 0) list[index] = profile else list.add(profile)
    val next = copy(printersJson = encodePrinterProfiles(list))
    return if (currentProfileId() == profile.id ||
        (activePrinterId.isBlank() && host.isBlank() && !demoMode)
    ) {
        next.activatedProfile(profile.id)
    } else {
        next
    }
}

fun AppSettings.deleteProfile(id: String): AppSettings {
    val list = profileList().filterNot { it.id == id }
    if (id != currentProfileId()) {
        return copy(printersJson = encodePrinterProfiles(list))
    }
    val next = list.firstOrNull()
    return if (next != null) {
        copy(
            printersJson = encodePrinterProfiles(list),
            activePrinterId = next.id,
            host = next.host,
            accessCode = next.accessCode,
            serial = next.serial,
            model = next.model,
            printerName = next.name,
            demoMode = next.demo,
            demoAms = next.demoAms,
        )
    } else {
        copy(
            printersJson = "",
            activePrinterId = "",
            host = "",
            accessCode = "",
            serial = "",
            demoMode = false,
            demoAms = true,
        )
    }
}
