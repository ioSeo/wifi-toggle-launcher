package com.jonathan.wifitoggle

import android.app.*
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.*
import androidx.core.app.NotificationCompat

class WifiToggleService : Service() {

    companion object {
        const val CHANNEL_ID = "wifi_toggle_channel"
        const val NOTIF_ID = 42
        const val DELAY_BEFORE_OFF = 15
        const val WIFI_OFF_DURATION = 40L

        fun start(context: Context) {
            val intent = Intent(context, WifiToggleService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var wifiManager: WifiManager
    private lateinit var notifManager: NotificationManager

    override fun onCreate() {
        super.onCreate()
        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        notifManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildNotif("Iniciando... Android ${Build.VERSION.SDK_INT}"))
        startCountdown(DELAY_BEFORE_OFF)
        return START_NOT_STICKY
    }

    private fun startCountdown(remaining: Int) {
        if (remaining <= 0) {
            disableWifi()
            return
        }
        updateNotif("Salto en $remaining s... (Android ${Build.VERSION.SDK_INT})")
        handler.postDelayed({ startCountdown(remaining - 1) }, 1000)
    }

    private fun disableWifi() {
        updateNotif("Desactivando WiFi...")
        Thread {
            val ok = tryDisable()
            handler.post {
                if (!ok) {
                    updateNotif("FALLO: no se pudo desactivar WiFi\nAndroid ${Build.VERSION.SDK_INT} - sin root?")
                    handler.postDelayed({ stopSelf() }, 5000)
                    return@post
                }
                updateNotif("WiFi OFF — esquivando actualización...")
                handler.postDelayed({
                    Thread {
                        tryEnable()
                        handler.post {
                            updateNotif("WiFi reactivado ✓ — listo!")
                            handler.postDelayed({ stopSelf() }, 3000)
                        }
                    }.start()
                }, WIFI_OFF_DURATION * 1000)
            }
        }.start()
    }

    private fun tryDisable(): Boolean {
        // Método 1: API estándar (Android 9-)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            @Suppress("DEPRECATION")
            wifiManager.setWifiEnabled(false)
            Thread.sleep(1000)
            if (!wifiManager.isWifiEnabled) return true
        }

        // Método 2: svc sin root (funciona en muchos TV box)
        try {
            Runtime.getRuntime().exec(arrayOf("svc", "wifi", "disable")).waitFor()
            Thread.sleep(1500)
            if (!wifiManager.isWifiEnabled) return true
        } catch (_: Exception) {}

        // Método 3: su root clásico
        try {
            Runtime.getRuntime().exec(arrayOf("su", "-c", "svc wifi disable")).waitFor()
            Thread.sleep(1500)
            if (!wifiManager.isWifiEnabled) return true
        } catch (_: Exception) {}

        // Método 4: su root alternativo
        try {
            val p = Runtime.getRuntime().exec("su")
            p.outputStream.write("svc wifi disable\nexit\n".toByteArray())
            p.outputStream.flush()
            p.waitFor()
            Thread.sleep(1500)
            if (!wifiManager.isWifiEnabled) return true
        } catch (_: Exception) {}

        return false
    }

    private fun tryEnable() {
        // Mismos métodos para reactivar
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            @Suppress("DEPRECATION")
            wifiManager.setWifiEnabled(true)
            return
        }
        try { Runtime.getRuntime().exec(arrayOf("svc", "wifi", "enable")).waitFor() } catch (_: Exception) {}
        try { Runtime.getRuntime().exec(arrayOf("su", "-c", "svc wifi enable")).waitFor() } catch (_: Exception) {}
        try {
            val p = Runtime.getRuntime().exec("su")
            p.outputStream.write("svc wifi enable\nexit\n".toByteArray())
            p.outputStream.flush()
            p.waitFor()
        } catch (_: Exception) {}
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "WiFi Saltamonte", NotificationManager.IMPORTANCE_LOW)
            notifManager.createNotificationChannel(ch)
        }
    }

    private fun buildNotif(text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("WiFi Saltamonte")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()

    private fun updateNotif(text: String) =
        notifManager.notify(NOTIF_ID, buildNotif(text))

    override fun onBind(intent: Intent?) = null

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }
}
