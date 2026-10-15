package com.muvusoft.agentfarm.net

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.muvusoft.agentfarm.core.link.Discovery
import java.net.Inet4Address

/**
 * Looks for paired farms on the local network (mDNS, the contract's transport.discovery) and says where
 * each answered. Answers are resolved one at a time: before Android 14 a second resolve fails while one runs.
 */
class FarmFinder(context: Context, private val onFound: (farmId: String, address: String) -> Unit) {
    private val nsd: NsdManager? = context.getSystemService(NsdManager::class.java)
    private var listener: NsdManager.DiscoveryListener? = null
    private val queue = ArrayDeque<NsdServiceInfo>()
    private var resolving = false

    @Synchronized
    fun start() {
        val m = nsd ?: return
        if (listener != null) return
        val l = object : NsdManager.DiscoveryListener {
            override fun onServiceFound(info: NsdServiceInfo) = resolve(info)
            override fun onServiceLost(info: NsdServiceInfo) = Unit
            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) { synchronized(this@FarmFinder) { listener = null } }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
        }
        listener = l
        runCatching { m.discoverServices(Discovery.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, l) }.onFailure { listener = null }
    }

    @Synchronized
    fun stop() {
        val l = listener ?: return
        listener = null
        queue.clear()
        runCatching { nsd?.stopServiceDiscovery(l) }
    }

    private fun resolve(info: NsdServiceInfo) {
        synchronized(this) {
            if (listener == null) return
            queue.addLast(info)
        }
        next()
    }

    @Suppress("DEPRECATION")
    private fun next() {
        val m = nsd ?: return
        val info = synchronized(this) {
            if (resolving) return
            queue.removeFirstOrNull()?.also { resolving = true } ?: return
        }
        val done = {
            synchronized(this) { resolving = false }
            next()
        }
        val r = runCatching {
            m.resolveService(info, object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = done()
                override fun onServiceResolved(resolved: NsdServiceInfo) {
                    val ipv4 = (resolved.host as? Inet4Address)?.hostAddress
                    Discovery.found(resolved.attributes, ipv4, resolved.port)?.let { onFound(it.farm, it.address) }
                    done()
                }
            })
        }
        if (r.isFailure) done()
    }
}
