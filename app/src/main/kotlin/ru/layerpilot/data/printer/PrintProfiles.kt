package ru.layerpilot.data.printer

/**
 * Печатные профили — то, что можно поменять уже после слайсинга:
 * скорость, калибровки и таймлапс. Толщина слоя и стенки задаются в слайсере,
 * поэтому здесь честно нет «магического» изменения качества геометрии.
 */
data class PrintProfile(
    val id: String,
    val name: String,
    val subtitle: String,
    val speedLevel: Int,
    val bedLeveling: Boolean,
    val flowCali: Boolean,
    val vibrationCali: Boolean,
    val timelapse: Boolean,
)

object PrintProfiles {
    val all = listOf(
        PrintProfile(
            id = "draft",
            name = "Черновик",
            subtitle = "Спорт · минимум подготовки",
            speedLevel = 3,
            bedLeveling = true,
            flowCali = false,
            vibrationCali = false,
            timelapse = true,
        ),
        PrintProfile(
            id = "standard",
            name = "Стандарт",
            subtitle = "Баланс скорости и качества",
            speedLevel = 2,
            bedLeveling = true,
            flowCali = false,
            vibrationCali = false,
            timelapse = true,
        ),
        PrintProfile(
            id = "quality",
            name = "Качество",
            subtitle = "Тихий · калибровки включены",
            speedLevel = 1,
            bedLeveling = true,
            flowCali = true,
            vibrationCali = true,
            timelapse = true,
        ),
        PrintProfile(
            id = "silent",
            name = "Ночной",
            subtitle = "Самый тихий · без таймлапса",
            speedLevel = 1,
            bedLeveling = true,
            flowCali = false,
            vibrationCali = false,
            timelapse = false,
        ),
    )

    fun byId(id: String?): PrintProfile = all.firstOrNull { it.id == id } ?: all[1]
}
