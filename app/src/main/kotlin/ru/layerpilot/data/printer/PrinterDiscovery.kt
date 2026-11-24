package ru.layerpilot.data.printer

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class FoundPrinter(val name: String, val host: String, val port: Int)

/** Поиск принтеров Bambu через mDNS в домашней сети. */
class PrinterDiscovery(private val context: Context) {
    private val _found = MutableStateFlow<List<FoundPrinter>>(emptyList())
    val found: StateFlow<List<FoundPrinter>> = _found.asStateFlow()
    private var manager: NsdManager? = null
    private var listener: NsdManager.DiscoveryListener? = null

    fun start() {
        stop()
        _found.value = emptyList()
        val nsd = context.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return
        manager = nsd
        val discovery = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String?) = Unit
            override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) = stop()
            override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) = stop()
            override fun onDiscoveryStopped(serviceType: String?) = Unit
            override fun onServiceLost(serviceInfo: NsdServiceInfo?) = Unit
            override fun onServiceFound(serviceInfo: NsdServiceInfo?) {
                serviceInfo ?: return
                runCatching {
                    nsd.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) = Unit
                        override fun onServiceResolved(info: NsdServiceInfo?) {
                            info ?: return
                            val host = info.host?.hostAddress ?: return
                            _found.value = (_found.value + FoundPrinter(info.serviceName ?: host, host, info.port))
                                .distinctBy { it.host }
                        }
                    })
                }.onFailure { stop() }
            }
        }
        listener = discovery
        runCatching { nsd.discoverServices("_bambu._tcp.", NsdManager.PROTOCOL_DNS_SD, discovery) }
    }

    fun stop() {
        val nsd = manager
        val discovery = listener
        if (nsd != null && discovery != null) runCatching { nsd.stopServiceDiscovery(discovery) }
        listener = null
        manager = null
    }
}
