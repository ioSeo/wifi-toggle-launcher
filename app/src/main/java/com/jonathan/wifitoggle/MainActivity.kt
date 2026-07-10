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
        val launchIntent = packageManager.getLaunchIntentForPackage(pkg)
        if (launchIntent == null) {
            Toast.makeText(this, "No se puede abrir esta app", Toast.LENGTH_SHORT).show()
            return
        }

        // Inicia el servicio que hace el salto WiFi
        WifiToggleService.start(this)

        // Abre la app de streaming
        startActivity(launchIntent)

        tvInfo.text = "Servicio activo — mira la notificación para el conteo"
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
