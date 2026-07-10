package com.jonathan.wifitoggle

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var listView: ListView
    private lateinit var btnLaunch: Button
    private lateinit var tvSelected: TextView
    private lateinit var tvInfo: TextView

    private var selectedPackage: String? = null
    private var apps: List<ApplicationInfo> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences("wifi_saltamonte", Context.MODE_PRIVATE)
        listView = findViewById(R.id.listApps)
        btnLaunch = findViewById(R.id.btnLaunch)
        tvSelected = findViewById(R.id.tvSelected)
        tvInfo = findViewById(R.id.tvInfo)

        selectedPackage = prefs.getString("pkg", null)
        refreshSelectedLabel()
        loadApps()

        // Toque corto = seleccionar
        listView.setOnItemClickListener { _, _, position, _ ->
            val app = apps[position]
            selectedPackage = app.packageName
            prefs.edit().putString("pkg", selectedPackage).apply()
            refreshSelectedLabel()
            Toast.makeText(this, "Seleccionado. Presiona LANZAR.", Toast.LENGTH_SHORT).show()
        }

        // Toque largo = seleccionar Y lanzar de inmediato
        listView.setOnItemLongClickListener { _, _, position, _ ->
            val app = apps[position]
            selectedPackage = app.packageName
            prefs.edit().putString("pkg", selectedPackage).apply()
            refreshSelectedLabel()
            launchApp()
            true
        }

        btnLaunch.setOnClickListener { launchApp() }
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
            val name = packageManager.getApplicationLabel(info).toString()
            tvSelected.text = "App: $name"
            btnLaunch.isEnabled = true
        } catch (e: PackageManager.NameNotFoundException) {
            tvSelected.text = "App no encontrada"
            btnLaunch.isEnabled = false
        }
    }

    private fun launchApp() {
        val pkg = selectedPackage ?: return
        val log = StringBuilder()

        val intent = resolveIntent(pkg, log)

        if (intent == null) {
            // Muestra debug para saber qué pasó
            try {
                val acts = packageManager.getPackageInfo(pkg, PackageManager.GET_ACTIVITIES)
                    ?.activities?.map { it.name } ?: emptyList()
                log.append("\nActividades en el package:\n${acts.joinToString("\n")}")
            } catch (e: Exception) {
                log.append("\nError leyendo actividades: ${e.message}")
            }
            AlertDialog.Builder(this)
                .setTitle("No se pudo abrir la app")
                .setMessage("Package: $pkg\n\n$log")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        WifiToggleService.start(this)
        try {
            startActivity(intent)
            tvInfo.text = "Lanzado. Mira la notificación para el conteo WiFi."
        } catch (e: Exception) {
            AlertDialog.Builder(this)
                .setTitle("Error al lanzar")
                .setMessage("Intent: ${intent.component}\nError: ${e.message}")
                .setPositiveButton("OK", null)
                .show()
        }
    }

    private fun resolveIntent(pkg: String, log: StringBuilder): Intent? {
        // 1. Standard launcher intent
        val standard = packageManager.getLaunchIntentForPackage(pkg)
        log.append("1. getLaunchIntentForPackage: ${if (standard != null) "OK" else "null"}\n")
        if (standard != null) {
            standard.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return standard
        }

        // 2. Leanback (Android TV)
        val leanback = packageManager.getLeanbackLaunchIntentForPackage(pkg)
        log.append("2. getLeanbackLaunchIntent: ${if (leanback != null) "OK" else "null"}\n")
        if (leanback != null) {
            leanback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return leanback
        }

        // 3. MAIN + LAUNCHER category
        val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
            setPackage(pkg)
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val launcherResults = packageManager.queryIntentActivities(launcherIntent, 0)
        log.append("3. MAIN+LAUNCHER: ${launcherResults.size} resultados\n")
        if (launcherResults.isNotEmpty()) {
            val act = launcherResults[0].activityInfo
            return Intent().apply {
                setClassName(act.packageName, act.name)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }

        // 4. MAIN + LEANBACK_LAUNCHER category
        val leanbackIntent = Intent(Intent.ACTION_MAIN).apply {
            setPackage(pkg)
            addCategory("android.intent.category.LEANBACK_LAUNCHER")
        }
        val leanbackResults = packageManager.queryIntentActivities(leanbackIntent, 0)
        log.append("4. MAIN+LEANBACK_LAUNCHER: ${leanbackResults.size} resultados\n")
        if (leanbackResults.isNotEmpty()) {
            val act = leanbackResults[0].activityInfo
            return Intent().apply {
                setClassName(act.packageName, act.name)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }

        // 5. Cualquier actividad MAIN sin categoría
        val anyMain = Intent(Intent.ACTION_MAIN).setPackage(pkg)
        val anyResults = packageManager.queryIntentActivities(anyMain, 0)
        log.append("5. MAIN (sin categoría): ${anyResults.size} resultados\n")
        if (anyResults.isNotEmpty()) {
            val act = anyResults[0].activityInfo
            return Intent().apply {
                setClassName(act.packageName, act.name)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }

        return null
    }
}

class AppAdapter(
    context: Context,
    private val apps: List<ApplicationInfo>
) : ArrayAdapter<ApplicationInfo>(context, 0, apps) {

    private val pm = context.packageManager

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView
            ?: LayoutInflater.from(context).inflate(R.layout.item_app, parent, false)
        val app = apps[position]
        view.findViewById<ImageView>(R.id.imgIcon).setImageDrawable(pm.getApplicationIcon(app))
        view.findViewById<TextView>(R.id.tvName).text = pm.getApplicationLabel(app)
        return view
    }
}
