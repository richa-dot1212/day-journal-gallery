package com.journalgallery.shared.sync

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import com.journalgallery.shared.domain.DeviceInfo
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.net.InetAddress

/** mDNS discovery via [NsdManager] for services of type [Protocol.MDNS_SERVICE_TYPE]. */
class AndroidDeviceDiscovery(context: Context) : DeviceDiscovery {

    private val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val serviceType = "${Protocol.MDNS_SERVICE_TYPE}."

    override fun discover(): Flow<List<DeviceInfo>> = callbackFlow {
        val found = LinkedHashMap<String, DeviceInfo>()

        fun emitSnapshot() { trySend(found.values.toList()) }

        val resolveListener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                val host = hostOf(serviceInfo) ?: return
                found[serviceInfo.serviceName] = DeviceInfo(serviceInfo.serviceName, host, serviceInfo.port)
                emitSnapshot()
            }
        }

        val discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) { close() }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                @Suppress("DEPRECATION")
                nsd.resolveService(serviceInfo, resolveListener)
            }
            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                found.remove(serviceInfo.serviceName)
                emitSnapshot()
            }
        }

        nsd.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        awaitClose { runCatching { nsd.stopServiceDiscovery(discoveryListener) } }
    }

    private fun hostOf(info: NsdServiceInfo): String? {
        if (Build.VERSION.SDK_INT >= 34) {
            val list = info.hostAddresses
            return list.firstOrNull()?.hostAddress
        }
        @Suppress("DEPRECATION")
        return (info.host as? InetAddress)?.hostAddress
    }
}
