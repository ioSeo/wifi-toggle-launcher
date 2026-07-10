package com.jonathan.wifitoggle

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.*
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    companion object {
        const val DELAY_BEFORE_OFF_MS = 15_000L
        const val WIFI_OFF_DURATION_MS = 40_000L
    }

    private lateinit var prefs: SharedPreferences
    private lateinit var wifiManager: WifiManager
    private lateinit var listView: ListView
    private lateinit var btnLaunch: Button
    private lateinit var tvSelected: TextView
    private lateinit var tvInfo: TextView
    private val handler = Handler(Looper.getMainLooper())

    private var selectedPackage: String? = null
    private var apps: List<ApplicationInfo> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences("wifi_saltamonte", Context.MODE_PRIVATE)
        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        listView = findViewById(R.id.listApps)
        btnLaunch = findViewById(R.id.btnLaunch)
        tvSelected = findViewById(R.id.tvSelected)
        tvInfo = findViewById(R.id.tvInfo)

        selectedPackage = prefs.getString("pkg", null)
        refreshSelectedLabel()
        loadApps()

        listView.setOnItemClickListener { _, _, position, _ ->
            selectAndLaunch(position)
        }
        listView.setOnItemLongClickListener { _, _, position, _ ->
            selectAndLaunch(position); true
        }
        btnLaunch.setOnClickListener { launchApp() }
    }

    private fun selectAndLaunch(position: Int) {
        val app = apps[position]
        selectedPackage = app.packageName
        prefs.edit().putString("pkg", selectedPackage).apply()
        refreshSelectedLabel()
        launchApp()
    }

    private fun loadApps() {
        val pm = packageManager
        apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 }
            .filter { it.packageName != packageName }
            .sortedBy { pm.getApplicationLabel(it).toString().lowercase() }
        listView.adapter = AppAdapter(this, apps)
        tvInfo.text = "${apps.size} apps encontradas"
    }

    private fun refreshSelectedLabel() {
        if (selectedPackage == null) {
            tvSelected.text = "Ninguna app seleccionada"
            btnLaunch.isEnabled = false
            return
        }
        try {
            val info = packageManager.getApplicationInfo(selectedPackage!!, 0)
            tvSelected.text = "App: ${packageManager.getApplicationLabel(info)}"
            btnLaunch.isEnabled = true
        } catch (_: PackageManager.NameNotFoundException) {
            tvSelected.text = "App no encontrada"
            btnLaunch.isEnabled = false
        }
    }

    private fun launchApp() {
        val pkg = selectedPackage ?: return
        val log = StringBuilder()
        val intent = resolveIntent(pkg, log)
        if (intent == null) {
            try {
                val acts = packageManager.getPackageInfo(pkg, PackageManager.GET_ACTIVITIES)
                    ?.activities?.map { it.name } ?: emptyList()
                log.append("\nActividades:\n${acts.joinToString("\n")}")
            } catch (_: Exception) {}
            AlertDialog.Builder(this)
                .setTitle("No se pudo abrir")
                .setMessage("$log")
                .setPositiveButton("OK", null).show()
            return
        }

        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            return
        }

        tvInfo.text = "Abierta. WiFi se apaga en ${DELAY_BEFORE_OFF_MS/1000}s..."
        Toast.makeText(this, "WiFi se apaga en ${DELAY_BEFORE_OFF_MS/1000}s", Toast.LENGTH_LONG).show()

        // Hilo background — sobrevive aunque la Activity quede en segundo plano
        Thread {
            Thread.sleep(DELAY_BEFORE_OFF_MS)

            val disabled = tryDisableWifi()
            handler.post {
                if (disabled) {
                    Toast.makeText(this, "WiFi OFF — saltando actualización...", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "FALLO: no se pudo desactivar WiFi (Android ${Build.VERSION.SDK_INT})", Toast.LENGTH_LONG).show()
                }
            }

            if (disabled) {
                Thread.sleep(WIFI_OFF_DURATION_MS)
                tryEnableWifi()
                handler.post {
                    Toast.makeText(this, "WiFi reactivado ✓", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun tryDisableWifi(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            @Suppress("DEPRECATION")
            wifiManager.setWifiEnabled(false)
            Thread.sleep(1000)
            if (!wifiManager.isWifiEnabled) return true
        }
        try {
            Runtime.getRuntime().exec(arrayOf("svc", "wifi", "disable")).waitFor()
            Thread.sleep(1500)
            if (!wifiManager.isWifiEnabled) return true
        } catch (_: Exception) {}
        try {
            Runtime.getRuntime().exec(arrayOf("su", "-c", "svc wifi disable")).waitFor()
            Thread.sleep(1500)
            if (!wifiManager.isWifiEnabled) return true
        } catch (_: Exception) {}
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

    private fun tryEnableWifi() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            @Suppress("DEPRECATION")
            wifiManager.setWifiEnabled(true); return
        }
        try { Runtime.getRuntime().exec(arrayOf("svc", "wifi", "enable")).waitFor() } catch (_: Exception) {}
        try { Runtime.getRuntime().exec(arrayOf("su", "-c", "svc wifi enable")).waitFor() } catch (_: Exception) {}
    }

    private fun resolveIntent(pkg: String, log: StringBuilder): Intent? {
        packageManager.getLaunchIntentForPackage(pkg)?.let {
            log.append("1.standard: OK\n")
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); return it
        }
        packageManager.getLeanbackLaunchIntentForPackage(pkg)?.let {
            log.append("2.leanback: OK\n")
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); return it
        }
        listOf(Intent.CATEGORY_LAUNCHER, "android.intent.category.LEANBACK_LAUNCHER").forEachIndexed { i, cat ->
            val qi = Intent(Intent.ACTION_MAIN).apply { setPackage(pkg); addCategory(cat) }
            val r = packageManager.queryIntentActivities(qi, 0)
            log.append("${i+3}.$cat: ${r.size}\n")
            if (r.isNotEmpty()) return Intent().apply {
                setClassName(r[0].activityInfo.packageName, r[0].activityInfo.name)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        val any = packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).setPackage(pkg), 0)
        log.append("5.anyMAIN: ${any.size}\n")
        if (any.isNotEmpty()) return Intent().apply {
            setClassName(any[0].activityInfo.packageName, any[0].activityInfo.name)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return null
    }
}

class AppAdapter(context: Context, private val apps: List<ApplicationInfo>) :
    ArrayAdapter<ApplicationInfo>(context, 0, apps) {
    private val pm = context.packageManager
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_app, parent, false)
        val app = apps[position]
        view.findViewById<ImageView>(R.id.imgIcon).setImageDrawable(pm.getApplicationIcon(app))
        view.findViewById<TextView>(R.id.tvName).text = pm.getApplicationLabel(app)
        return view
    }
}
