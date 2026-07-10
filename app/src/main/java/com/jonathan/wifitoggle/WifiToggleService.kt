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
        // 20 segundos para que aparezca el popup de actualización
        const val DELAY_BEFORE_OFF = 20
        // 10 segundos sin WiFi para que el popup falle y se cierre
        const val WIFI_OFF_DURATION = 10L

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
        startForeground(NOTIF_ID, buildNotif("Preparando salto... $DELAY_BEFORE_OFF s"))
        startCountdown(DELAY_BEFORE_OFF)
        return START_NOT_STICKY
    }

    private fun startCountdown(remaining: Int) {
        if (remaining <= 0) {
            disableWifi()
            return
        }
        updateNotif("Salto en $remaining s...")
        handler.postDelayed({ startCountdown(remaining - 1) }, 1000)
    }

    @Suppress("DEPRECATION")
    private fun disableWifi() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            updateNotif("Android 10+: no se puede desactivar WiFi sin root")
            handler.postDelayed({ stopSelf() }, 4000)
            return
        }

        updateNotif("WiFi OFF — esquivando actualización...")
        wifiManager.setWifiEnabled(false)

        handler.postDelayed({
            updateNotif("Reactivando WiFi...")
            wifiManager.setWifiEnabled(true)
            handler.postDelayed({
                updateNotif("Listo — actualizacion esquivada ✓")
                handler.postDelayed({ stopSelf() }, 3000)
            }, 2000)
        }, WIFI_OFF_DURATION * 1000)
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
