package com.jonathan.wifitoggle

import android.content.Context
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

        listView.setOnItemClickListener { _, _, position, _ ->
            val app = apps[position]
            selectedPackage = app.packageName
            prefs.edit().putString("pkg", selectedPackage).apply()
            refreshSelectedLabel()
            Toast.makeText(this, "Seleccionado: ${packageManager.getApplicationLabel(app)}", Toast.LENGTH_SHORT).show()
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
    }

    private fun refreshSelectedLabel() {
        if (selectedPackage == null) {
            tvSelected.text = "Ninguna app seleccionada — toca una de la lista"
            btnLaunch.isEnabled = false
            return
        }
        try {
            val info = packageManager.getApplicationInfo(selectedPackage!!, 0)
            val name = packageManager.getApplicationLabel(info).toString()
            tvSelected.text = "App: $name"
            btnLaunch.isEnabled = true
        } catch (e: PackageManager.NameNotFoundException) {
            tvSelected.text = "App no encontrada — selecciona otra"
            btnLaunch.isEnabled = false
        }
    }

    private fun launchApp() {
        val pkg = selectedPackage ?: return
        val intent = resolveIntent(pkg)
        if (intent == null) {
            Toast.makeText(this, "No se encontró cómo abrir $pkg", Toast.LENGTH_LONG).show()
            return
        }

        WifiToggleService.start(this)
        startActivity(intent)
        tvInfo.text = "Servicio activo — mira la notificación para el conteo"
    }

    private fun resolveIntent(pkg: String): android.content.Intent? {
        // 1. Intento estándar (móvil)
        packageManager.getLaunchIntentForPackage(pkg)?.let { return it }

        // 2. Intento Leanback (TV)
        packageManager.getLeanbackLaunchIntentForPackage(pkg)?.let { return it }

        // 3. Busca cualquier actividad MAIN del package
        val intent = android.content.Intent(android.content.Intent.ACTION_MAIN)
        intent.setPackage(pkg)
        val resolved = packageManager.queryIntentActivities(intent, 0)
        if (resolved.isNotEmpty()) {
            val act = resolved[0].activityInfo
            return android.content.Intent().apply {
                setClassName(act.packageName, act.name)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
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
