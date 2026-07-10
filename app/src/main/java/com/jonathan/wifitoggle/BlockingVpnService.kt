package com.jonathan.wifitoggle

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor

class BlockingVpnService : VpnService() {

    companion object {
        const val BLOCK_DURATION_MS = 25_000L
    }

    private var vpnInterface: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        vpnInterface = Builder()
            .addAddress("10.111.111.1", 32)
            .addRoute("0.0.0.0", 0)       // captura todo el tráfico
            .addRoute("::", 0)             // IPv6 también
            .setSession("WiFi Saltamonte")
            .setBlocking(false)
            .establish()

        // Se desactiva solo después del tiempo configurado
        Thread {
            Thread.sleep(BLOCK_DURATION_MS)
            stopSelf()
        }.start()

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        vpnInterface?.close()
        vpnInterface = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = super.onBind(intent)
}
