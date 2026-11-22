package ru.layerpilot.data.printer

import ru.layerpilot.data.model.PrinterModel

data class PrinterConfig(
    val host: String,
    val accessCode: String,
    val serial: String,
    val model: PrinterModel,
    val name: String,
) {
    val displayName: String get() = name.ifBlank { model.label }
}

sealed interface ConnectionStatus {
    data object Idle : ConnectionStatus
    data object Connecting : ConnectionStatus
    data class Connected(val demo: Boolean = false) : ConnectionStatus
    data class Reconnecting(val reason: String = "") : ConnectionStatus
    data class Error(val message: String) : ConnectionStatus

    val isOnline: Boolean get() = this is Connected
}
