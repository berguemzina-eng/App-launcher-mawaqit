package com.tuusuario.mawaqitlauncher

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.app.Activity
import android.app.UiModeManager
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {

    private val MAWAQIT_PACKAGE = "com.kanout.mawaqit"
    private val REQUEST_HOME_ROLE = 1001
    private val REQUEST_BATTERY = 1002
    private val REQUEST_WRITE_SETTINGS = 1003
    private val PREFS = "mawaqit_launcher_prefs"
    private val KEY_SPLASH_MS = "splash_duration_ms"
    private val KEY_SKIP_BATTERY = "skip_battery_step"
    private val KEY_SKIP_SCREEN = "skip_screen_step"
    private val DEFAULT_SPLASH_MS = 5000L

    private val GREEN = Color.parseColor("#0F6B3D")
    private val GOLD = Color.parseColor("#F2C744")

    private val handler = Handler(Looper.getMainLooper())
    private var pendingRunnable: Runnable? = null
    private var inSettings = false
    private var previewMode = false

    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        checkNextStepOrLaunch()
    }

    override fun onResume() {
        super.onResume()
        if (!inSettings) checkNextStepOrLaunch()
    }

    override fun onPause() {
        super.onPause()
        pendingRunnable?.let { handler.removeCallbacks(it) }
    }

    private fun checkNextStepOrLaunch() {
        val homeReady = if (isTelevision()) isAccessibilityServiceEnabled() else isDefaultHome()

        when {
            !homeReady -> showStepScreen(Step.HOME)
            !isIgnoringBatteryOptimizations() && !prefs.getBoolean(KEY_SKIP_BATTERY, false) -> showStepScreen(Step.BATTERY)
            !Settings.System.canWrite(this) && !prefs.getBoolean(KEY_SKIP_SCREEN, false) -> showStepScreen(Step.SCREEN)
            else -> showSplash(thenLaunch = true)
        }
    }

    private enum class Step { HOME, BATTERY, SCREEN }

    private fun isTelevision(): Boolean {
        val uiModeManager = getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
        return uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
    }

    private fun isDefaultHome(): Boolean {
        val intent = Intent(Intent.ACTION_MAIN)
        intent.addCategory(Intent.CATEGORY_HOME)
        val resolveInfo = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return resolveInfo?.activityInfo?.packageName == packageName
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.contains("$packageName/$packageName.TVHomeWatcherService")
    }

    private fun isIgnoringBatteryOptimizations(pkg: String = packageName): Boolean {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(pkg)
    }

    private fun applyKeepScreenOnSetting(enabled: Boolean = true) {
        try {
            val value = if (enabled) Int.MAX_VALUE else 60000
            Settings.System.putInt(contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, value)
        } catch (e: Exception) {
            // Si el fabricante bloquea este ajuste, lo omitimos
        }
    }

    private fun isConnectedToInternet(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun getSplashDuration(): Long = prefs.getLong(KEY_SPLASH_MS, DEFAULT_SPLASH_MS)

    private fun setSplashDuration(ms: Long) {
        prefs.edit().putLong(KEY_SPLASH_MS, ms).apply()
    }

    /**
     * Si el sistema nos lanzó como pantalla de Inicio (categoría HOME), esta
     * app NUNCA debe cerrarse con finish(): Android necesita siempre tener
     * un "Inicio" vivo al que volver. Cerrarla provoca pantalla en negro
     * al salir de todas las apps. Solo cerramos cuando NO somos el Inicio.
     */
    private fun isActingAsHome(): Boolean {
        return intent?.hasCategory(Intent.CATEGORY_HOME) == true
    }

    private fun finishUnlessHome() {
        if (!isActingAsHome()) {
            finish()
        }
    }

    /**
     * Intenta abrir cada Intent de la lista en orden hasta que uno funcione.
     * Muchos firmwares de Xiaomi TV no implementan todas las pantallas
     * estándar de Android, así que probamos varias alternativas antes
     * de mostrar un aviso al usuario en vez de dejar que el sistema
     * muestre el feo mensaje "Ninguna aplicación puede procesar esta acción".
     */
    private fun safeStartActivity(vararg intents: Intent): Boolean {
        for (intent in intents) {
            try {
                val resolved = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
                if (resolved != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                    return true
                }
            } catch (e: Exception) {
                // probamos el siguiente
            }
        }
        return false
    }

    private fun tryOpenAccessibilitySettings(manualHint: String) {
        val opened = safeStartActivity(
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
            packageManager.getLaunchIntentForPackage("com.android.tv.settings") ?: Intent(),
            packageManager.getLaunchIntentForPackage("com.android.settings") ?: Intent(),
            Intent(Settings.ACTION_SETTINGS)
        )
        if (!opened) {
            Toast.makeText(this, manualHint, Toast.LENGTH_LONG).show()
        }
    }

    // ---------- SPLASH ----------

    private fun showSplash(thenLaunch: Boolean) {
        previewMode = !thenLaunch

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.gravity = Gravity.CENTER
        root.setBackgroundColor(GREEN)

        val logo = ImageView(this)
        logo.setImageResource(R.drawable.ic_launcher_foreground)
        val size = (resources.displayMetrics.density * 160).toInt()
        val params = LinearLayout.LayoutParams(size, size)
        logo.layoutParams = params
        logo.alpha = 0f
        logo.scaleX = 0.7f
        logo.scaleY = 0.7f
        root.addView(logo)

        val title = TextView(this)
        title.text = getString(R.string.app_name)
        title.setTextColor(GOLD)
        title.textSize = 22f
        title.gravity = Gravity.CENTER
        title.alpha = 0f
        title.setPadding(0, 40, 0, 0)
        root.addView(title)

        val actionButton = Button(this)
        actionButton.text = if (previewMode) "Volver a Ajustes" else "Ajustes"
        actionButton.setBackgroundColor(GREEN)
        actionButton.setTextColor(GOLD)
        actionButton.alpha = 0f
        actionButton.setOnClickListener {
            pendingRunnable?.let { r -> handler.removeCallbacks(r) }
            showSettingsScreen()
        }
        val btnParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        )
        btnParams.topMargin = 60
        actionButton.layoutParams = btnParams
        root.addView(actionButton)

        setContentView(root)

        val fadeLogo = ObjectAnimator.ofFloat(logo, "alpha", 0f, 1f)
        val scaleXLogo = ObjectAnimator.ofFloat(logo, "scaleX", 0.7f, 1f)
        val scaleYLogo = ObjectAnimator.ofFloat(logo, "scaleY", 0.7f, 1f)
        val fadeTitle = ObjectAnimator.ofFloat(title, "alpha", 0f, 1f)
        val fadeButton = ObjectAnimator.ofFloat(actionButton, "alpha", 0f, 1f)

        val growIn = AnimatorSet()
        growIn.playTogether(fadeLogo, scaleXLogo, scaleYLogo)
        growIn.duration = 700
        growIn.interpolator = DecelerateInterpolator()

        val full = AnimatorSet()
        full.playSequentially(growIn, fadeTitle, fadeButton)
        full.start()

        val runnable = Runnable {
            if (thenLaunch) {
                applyKeepScreenOnSetting()
                launchMawaqit()
                finishUnlessHome()
            } else {
                showSettingsScreen()
            }
        }
        pendingRunnable = runnable
        handler.postDelayed(runnable, getSplashDuration())
    }

    // ---------- PASOS INICIALES ----------

    private fun showStepScreen(step: Step) {
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.gravity = Gravity.CENTER
        layout.setPadding(80, 80, 80, 80)
        layout.setBackgroundColor(GREEN)

        val text = TextView(this)
        val button = Button(this)
        text.setTextColor(GOLD)
        button.setBackgroundColor(GOLD)
        button.setTextColor(GREEN)
        text.textSize = 20f
        text.setPadding(0, 0, 0, 60)

        when (step) {
            Step.HOME -> {
                if (isTelevision()) {
                    text.text = "Paso 1 de 3\n\nActiva el servicio de accesibilidad para que\n" +
                            "Mawaqit se abra automáticamente.\n\n" +
                            "Si el botón no abre nada: ve manualmente a\n" +
                            "Ajustes → Accesibilidad (o Ajustes → Apps →\n" +
                            "Accesibilidad) y activa \"Mawaqit Launcher\"."
                    button.text = "Abrir Accesibilidad"
                    button.setOnClickListener {
                        tryOpenAccessibilitySettings(
                            "No se encontró la pantalla de Accesibilidad. Ve a Ajustes → Accesibilidad manualmente y activa \"Mawaqit Launcher\"."
                        )
                    }
                } else {
                    text.text = "Paso 1 de 3\n\nConfigura esta app como pantalla de inicio."
                    button.text = "Configurar como inicio"
                    button.setOnClickListener { requestHomeRole() }
                }
            }
            Step.BATTERY -> {
                text.text = "Paso 2 de 3\n\nDesactiva la optimización de batería para\n" +
                        "que el sistema nunca cierre esta app en segundo plano."
                button.text = "Desactivar optimización"
                button.setOnClickListener { requestBatteryExemption() }
            }
            Step.SCREEN -> {
                text.text = "Paso 3 de 3\n\nPermite que esta app controle el apagado\n" +
                        "de pantalla, para que el horario de Mawaqit\nquede siempre visible."
                button.text = "Permitir"
                button.setOnClickListener { requestWriteSettings() }
            }
        }

        layout.addView(text)
        layout.addView(button)

        if (step == Step.BATTERY || step == Step.SCREEN) {
            val skip = Button(this)
            skip.text = "Saltar este paso"
            skip.setBackgroundColor(Color.parseColor("#146B44"))
            skip.setTextColor(GOLD)
            val skipParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            skipParams.topMargin = 30
            skip.layoutParams = skipParams
            skip.setOnClickListener {
                val key = if (step == Step.BATTERY) KEY_SKIP_BATTERY else KEY_SKIP_SCREEN
                prefs.edit().putBoolean(key, true).apply()
                checkNextStepOrLaunch()
            }
            layout.addView(skip)
        }

        setContentView(layout)
    }

    // ---------- PANTALLA DE AJUSTES ----------

    private fun showSettingsScreen() {
        inSettings = true
        previewMode = false
        val scroll = ScrollView(this)
        scroll.setBackgroundColor(GREEN)

        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(70, 70, 70, 70)

        fun sectionTitle(t: String): TextView {
            val tv = TextView(this)
            tv.text = t
            tv.setTextColor(GOLD)
            tv.textSize = 22f
            tv.setPadding(0, 40, 0, 20)
            return tv
        }

        fun statusLine(label: String, ok: Boolean): TextView {
            val tv = TextView(this)
            tv.text = (if (ok) "✓ " else "✗ ") + label
            tv.setTextColor(if (ok) GOLD else Color.parseColor("#E08080"))
            tv.textSize = 16f
            return tv
        }

        fun actionButton(labelText: String, action: () -> Unit): Button {
            val b = Button(this)
            b.text = labelText
            b.setBackgroundColor(GOLD)
            b.setTextColor(GREEN)
            val p = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            p.topMargin = 20
            b.layoutParams = p
            b.setOnClickListener { action() }
            return b
        }

        val header = TextView(this)
        header.text = "Ajustes — Mawaqit Launcher"
        header.setTextColor(GOLD)
        header.textSize = 26f
        layout.addView(header)

        // Estado
        layout.addView(sectionTitle("Estado actual"))
        val homeReady = if (isTelevision()) isAccessibilityServiceEnabled() else isDefaultHome()
        layout.addView(statusLine(if (isTelevision()) "Accesibilidad activada" else "App configurada como inicio", homeReady))
        layout.addView(statusLine("Sin restricciones de batería", isIgnoringBatteryOptimizations()))
        layout.addView(statusLine("Control de apagado de pantalla concedido", Settings.System.canWrite(this)))
        layout.addView(statusLine("Conectado a internet", isConnectedToInternet()))
        layout.addView(statusLine("Mawaqit sin restricciones de batería", isIgnoringBatteryOptimizations(MAWAQIT_PACKAGE)))

        val lastLaunch = LaunchLog.lastLaunchTime(this)
        val lastLaunchText = TextView(this)
        lastLaunchText.setTextColor(GOLD)
        lastLaunchText.textSize = 16f
        lastLaunchText.setPadding(0, 10, 0, 0)
        lastLaunchText.text = if (lastLaunch > 0L) {
            val formatted = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date(lastLaunch))
            "Última apertura de Mawaqit: $formatted (${LaunchLog.lastLaunchSource(this)})"
        } else {
            "Última apertura de Mawaqit: aún sin registrar"
        }
        layout.addView(lastLaunchText)

        // Reconfigurar permisos
        layout.addView(sectionTitle("Reconfigurar permisos"))
        layout.addView(actionButton(if (isTelevision()) "Abrir Accesibilidad" else "Configurar inicio") {
            if (isTelevision()) {
                tryOpenAccessibilitySettings(
                    "No se encontró la pantalla de Accesibilidad. Ve a Ajustes → Accesibilidad manualmente."
                )
            } else {
                requestHomeRole()
            }
        })
        layout.addView(actionButton("Ajustar optimización de batería") { requestBatteryExemption() })
        layout.addView(actionButton("Excluir también a Mawaqit de batería") {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            intent.data = Uri.parse("package:$MAWAQIT_PACKAGE")
            val opened = safeStartActivity(intent, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS), Intent(Settings.ACTION_SETTINGS))
            if (!opened) Toast.makeText(this, "Ve a Ajustes → Batería → Mawaqit → Sin restricciones.", Toast.LENGTH_LONG).show()
        })
        layout.addView(actionButton("Ajustar permiso de pantalla") { requestWriteSettings() })
        layout.addView(actionButton("Repasar los 3 pasos desde el inicio") {
            prefs.edit().putBoolean(KEY_SKIP_BATTERY, false).putBoolean(KEY_SKIP_SCREEN, false).apply()
            inSettings = false
            checkNextStepOrLaunch()
        })

        // Pantalla de bienvenida
        layout.addView(sectionTitle("Pantalla de bienvenida (splash)"))
        val currentSeconds = getSplashDuration() / 1000
        val splashInfo = TextView(this)
        splashInfo.text = "Duración actual: ${currentSeconds}s"
        splashInfo.setTextColor(GOLD)
        splashInfo.textSize = 16f
        layout.addView(splashInfo)

        val durationRow = LinearLayout(this)
        durationRow.orientation = LinearLayout.HORIZONTAL
        durationRow.setPadding(0, 20, 0, 0)
        listOf(0L to "Instantáneo", 3000L to "3s", 5000L to "5s", 10000L to "10s").forEach { (ms, label) ->
            val b = Button(this)
            b.text = label
            b.setBackgroundColor(if (getSplashDuration() == ms) GOLD else Color.parseColor("#146B44"))
            b.setTextColor(if (getSplashDuration() == ms) GREEN else GOLD)
            b.setOnClickListener {
                setSplashDuration(ms)
                showSettingsScreen()
            }
            durationRow.addView(b)
        }
        layout.addView(durationRow)

        layout.addView(actionButton("Vista previa del splash") {
            inSettings = false
            showSplash(thenLaunch = false)
        })

        // Pantalla siempre encendida
        layout.addView(sectionTitle("Pantalla"))
        layout.addView(actionButton("Mantener pantalla siempre encendida") { applyKeepScreenOnSetting(true) })
        layout.addView(actionButton("Permitir que la pantalla se apague normalmente") { applyKeepScreenOnSetting(false) })

        // Accesos directos del sistema
        layout.addView(sectionTitle("Accesos directos"))
        layout.addView(actionButton("Ajustes de red / Wi-Fi") {
            val opened = safeStartActivity(
                Intent(Settings.ACTION_WIFI_SETTINGS),
                Intent(Settings.ACTION_WIRELESS_SETTINGS),
                Intent(Settings.ACTION_SETTINGS)
            )
            if (!opened) Toast.makeText(this, "No se encontró Ajustes de red en este dispositivo.", Toast.LENGTH_LONG).show()
        })
        layout.addView(actionButton("Ajustes de fecha y hora") {
            val opened = safeStartActivity(Intent(Settings.ACTION_DATE_SETTINGS), Intent(Settings.ACTION_SETTINGS))
            if (!opened) Toast.makeText(this, "No se encontró Ajustes de fecha y hora en este dispositivo.", Toast.LENGTH_LONG).show()
        })
        layout.addView(actionButton("Ajustes de protector de pantalla") {
            val opened = safeStartActivity(Intent(Settings.ACTION_DREAM_SETTINGS), Intent(Settings.ACTION_DISPLAY_SETTINGS), Intent(Settings.ACTION_SETTINGS))
            if (!opened) Toast.makeText(this, "No se encontró Ajustes de protector de pantalla en este dispositivo.", Toast.LENGTH_LONG).show()
        })
        layout.addView(actionButton("Permisos de Mawaqit") {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = Uri.parse("package:$MAWAQIT_PACKAGE")
            val opened = safeStartActivity(intent, Intent(Settings.ACTION_SETTINGS))
            if (!opened) Toast.makeText(this, "No se encontró la pantalla de detalles de la app.", Toast.LENGTH_LONG).show()
        })
        layout.addView(actionButton("Permisos de esta app (Mawaqit Launcher)") {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = Uri.parse("package:$packageName")
            val opened = safeStartActivity(intent, Intent(Settings.ACTION_SETTINGS))
            if (!opened) Toast.makeText(this, "No se encontró la pantalla de detalles de la app.", Toast.LENGTH_LONG).show()
        })
        layout.addView(actionButton("Abrir Mawaqit ahora") {
            launchMawaqit()
            finishUnlessHome()
        })

        val about = TextView(this)
        about.text = "\nMawaqit Launcher · v1.1\nAbre Mawaqit automáticamente al encender el dispositivo."
        about.setTextColor(GOLD)
        about.textSize = 14f
        about.setPadding(0, 60, 0, 0)
        layout.addView(about)

        scroll.addView(layout)
        setContentView(scroll)
    }

    // ---------- Permisos del sistema ----------

    private fun requestHomeRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
                if (roleManager.isRoleHeld(RoleManager.ROLE_HOME)) {
                    inSettings = false
                    checkNextStepOrLaunch()
                    return
                }
                startActivityForResult(roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME), REQUEST_HOME_ROLE)
                return
            }
        }
        try {
            startActivityForResult(Intent(Settings.ACTION_HOME_SETTINGS), REQUEST_HOME_ROLE)
        } catch (e: Exception) {
            inSettings = false
            checkNextStepOrLaunch()
        }
    }

    private fun requestBatteryExemption() {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
        intent.data = Uri.parse("package:$packageName")
        val opened = safeStartActivity(intent, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS), Intent(Settings.ACTION_SETTINGS))
        if (!opened) {
            Toast.makeText(this, "Ve a Ajustes → Batería → Mawaqit Launcher → Sin restricciones.", Toast.LENGTH_LONG).show()
            inSettings = false
            checkNextStepOrLaunch()
        }
    }

    private fun requestWriteSettings() {
        val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
        intent.data = Uri.parse("package:$packageName")
        val opened = safeStartActivity(intent, Intent(Settings.ACTION_DISPLAY_SETTINGS), Intent(Settings.ACTION_SETTINGS))
        if (!opened) {
            Toast.makeText(this, "Ve a Ajustes → Aplicaciones → Mawaqit Launcher → Permisos y actívalo ahí.", Toast.LENGTH_LONG).show()
            inSettings = false
            checkNextStepOrLaunch()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (inSettings) {
            showSettingsScreen()
        } else {
            checkNextStepOrLaunch()
        }
    }

    private fun launchMawaqit() {
        val launchIntent = packageManager.getLaunchIntentForPackage(MAWAQIT_PACKAGE)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(launchIntent)
            LaunchLog.record(this, "app")
        }
    }
}
