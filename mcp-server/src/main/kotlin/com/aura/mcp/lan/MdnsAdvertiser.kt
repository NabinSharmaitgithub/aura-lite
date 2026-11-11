package com.aura.mcp.lan

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo

/** Advertises the paired server on the local network as `_mcp._tcp`. */
class MdnsAdvertiser(private val context: Context) {

    private val nsdManager: NsdManager? =
        context.getSystemService(Context.NSD_SERVICE) as? NsdManager
    private var registrationListener: NsdManager.RegistrationListener? = null

    fun advertise(serviceName: String, port: Int, onRegistered: (String) -> Unit = {}) {
        val manager = nsdManager ?: return
        val info = NsdServiceInfo().apply {
            this.serviceName = serviceName
            this.serviceType = "_mcp._tcp"
            this.port = port
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                onRegistered(serviceInfo.serviceName)
            }

            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) = Unit
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
        }
        registrationListener = listener
        runCatching { manager.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener) }
    }

    fun stop() {
        registrationListener?.let { runCatching { nsdManager?.unregisterService(it) } }
        registrationListener = null
    }
}
