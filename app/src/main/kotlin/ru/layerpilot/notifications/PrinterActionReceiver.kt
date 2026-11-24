package ru.layerpilot.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import ru.layerpilot.data.model.PrinterModel
import ru.layerpilot.data.printer.MqttPrinterClient
import ru.layerpilot.data.printer.PrinterConfig

/** Кнопки «Пауза / Продолжить / Стоп» прямо из уведомления. */
class PrinterActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.getStringExtra(EXTRA_ACTION) ?: return
        val host = intent.getStringExtra(EXTRA_HOST).orEmpty()
        val code = intent.getStringExtra(EXTRA_CODE).orEmpty()
        val serial = intent.getStringExtra(EXTRA_SERIAL).orEmpty()
        if (host.isBlank() || serial.isBlank()) return
        val model = PrinterModel.fromName(intent.getStringExtra(EXTRA_MODEL))
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val client = MqttPrinterClient(
                PrinterConfig(host, code, serial, model, "Bambu"),
                onJson = {},
                onStatus = {},
            )
            try {
                client.connect()
                val payload = when (action) {
                    "pause" -> JSONObject().put("print", JSONObject().put("command", "pause"))
                    "resume" -> JSONObject().put("print", JSONObject().put("command", "resume"))
                    "stop" -> JSONObject().put("print", JSONObject().put("command", "stop"))
                    else -> null
                }
                if (payload != null) {
                    repeat(16) {
                        if (client.publish(payload)) return@repeat
                        delay(250)
                    }
                }
            } finally {
                runCatching { client.disconnect() }
                pending.finish()
            }
        }
    }

    companion object {
        const val EXTRA_ACTION = "action"
        const val EXTRA_HOST = "host"
        const val EXTRA_CODE = "code"
        const val EXTRA_SERIAL = "serial"
        const val EXTRA_MODEL = "model"
    }
}
