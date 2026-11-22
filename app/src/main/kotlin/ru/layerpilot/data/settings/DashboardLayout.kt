package ru.layerpilot.data.settings

/** Блоки главного экрана, которые пользователь может двигать и скрывать. */
enum class DashboardSection(val id: String, val title: String) {
    HERO("hero", "Печать"),
    QUICK("quick", "Быстрые действия"),
    TEMPS("temps", "Температуры"),
    AMS("ams", "AMS и пластик"),
    ERRORS("errors", "Ошибки"),
    CAMERA("camera", "Камера"),
    ACTIONS("actions", "Файлы и управление"),
    ;

    companion object {
        val defaultOrder = listOf(HERO, QUICK, TEMPS, AMS, ERRORS, CAMERA, ACTIONS)
        fun fromId(id: String): DashboardSection? = entries.firstOrNull { it.id == id }
    }
}

fun AppSettings.dashboardSections(): List<DashboardSection> {
    val saved = dashboardOrder.split(',')
        .mapNotNull { DashboardSection.fromId(it.trim()) }
    val unique = saved.distinct()
    return unique + DashboardSection.defaultOrder.filterNot { it in unique }
}

fun AppSettings.dashboardHiddenSections(): Set<String> =
    dashboardHidden.split(',').map { it.trim() }.filter { it.isNotBlank() }.toSet()

fun encodeDashboardOrder(order: List<DashboardSection>): String =
    order.joinToString(",") { it.id }

fun encodeDashboardHidden(hidden: Set<String>): String =
    hidden.sorted().joinToString(",")
