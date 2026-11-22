package ru.layerpilot.data.printer

import org.eclipse.paho.client.mqttv3.IMqttActionListener
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.IMqttToken
import org.eclipse.paho.client.mqttv3.MqttAsyncClient
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttException
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import org.json.JSONObject
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.UUID
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Локальный MQTT-канал принтера. Принтер использует самоподписанный сертификат,
 * поэтому соединение шифруется, но сертификат не проверяется по цепочке — как в
 * Bambu Studio и в боте: доверяем только конкретному IP в домашней сети.
 */
class MqttPrinterClient(
    private val config: PrinterConfig,
    private val onJson: (JSONObject) -> Unit,
    private val onStatus: (ConnectionStatus) -> Unit,
) {
    private val topicReport = "device/${config.serial}/report"
    private val topicRequest = "device/${config.serial}/request"
    private var client: MqttAsyncClient? = null
    private var sequence = 0L
    private val lock = Any()

    @Volatile
    private var closed = false

    fun connect() {
        closed = false
        val uri = "ssl://${config.host}:8883"
        try {
            val mqtt = MqttAsyncClient(uri, "layerpilot-${UUID.randomUUID().toString().take(8)}", MemoryPersistence())
            client = mqtt
            mqtt.setCallback(object : MqttCallbackExtended {
                override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                    if (closed) return
                    runCatching { mqtt.subscribe(topicReport, 1) }
                    requestPushAll()
                    onStatus(ConnectionStatus.Connected())
                }

                override fun connectionLost(cause: Throwable?) {
                    if (closed) return
                    onStatus(ConnectionStatus.Reconnecting(cause?.message ?: "связь потеряна"))
                }

                override fun messageArrived(topic: String?, message: MqttMessage?) {
                    val text = message?.payload?.toString(Charsets.UTF_8) ?: return
                    runCatching { JSONObject(text) }.onSuccess(onJson)
                }

                override fun deliveryComplete(token: IMqttDeliveryToken?) = Unit
            })
            val options = MqttConnectOptions().apply {
                isAutomaticReconnect = true
                isCleanSession = true
                userName = "bblp"
                password = config.accessCode.toCharArray()
                connectionTimeout = 10
                keepAliveInterval = 30
                socketFactory = trustAllContext().socketFactory
                runCatching { isHttpsHostnameVerificationEnabled = false }
            }
            onStatus(ConnectionStatus.Connecting)
            mqtt.connect(options, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) = Unit
                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    if (!closed) {
                        onStatus(ConnectionStatus.Error(describe(exception)))
                    }
                }
            })
        } catch (e: Exception) {
            onStatus(ConnectionStatus.Error(describe(e)))
        }
    }

    fun disconnect() {
        closed = true
        val mqtt = client ?: return
        client = null
        runCatching { mqtt.disconnectForcibly(200, 200, false) }
        runCatching { mqtt.close() }
    }

    fun publish(payload: JSONObject): Boolean {
        val mqtt = client ?: return false
        if (!mqtt.isConnected) return false
        return try {
            val seq = synchronized(lock) { (++sequence).toString() }
            val section = payload.keys().asSequence().firstOrNull() ?: return false
            payload.optJSONObject(section)?.put("sequence_id", seq)
            val message = MqttMessage(payload.toString().toByteArray(Charsets.UTF_8)).apply { qos = 0 }
            mqtt.publish(topicRequest, message)
            true
        } catch (_: MqttException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    fun requestPushAll() {
        publish(JSONObject().put("pushing", JSONObject()
            .put("command", "pushall")
            .put("version", 1)
            .put("push_target", 1)))
    }

    private fun describe(t: Throwable?): String = when {
        t == null -> "не удалось подключиться"
        t.message?.contains("NOT_AUTHORIZED", true) == true -> "неверный код доступа"
        t.message?.contains("timeout", true) == true -> "принтер не отвечает"
        t.message?.contains("Connection refused", true) == true -> "порт 8883 закрыт"
        else -> t.message ?: t.javaClass.simpleName
    }

    companion object {
        fun trustAllContext(): SSLContext {
            val manager = object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            }
            val ctx = SSLContext.getInstance("TLS")
            ctx.init(null, arrayOf<TrustManager>(manager), SecureRandom())
            return ctx
        }
    }
}
