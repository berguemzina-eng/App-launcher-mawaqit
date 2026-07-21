package com.tuusuario.mawaqitlauncher

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

/**
 * Vigilante permanente: esta pantalla solo debe mostrar Mawaqit.
 * Si aparece cualquier otra app en primer plano (el launcher de Xiaomi,
 * un diálogo de error, o Mawaqit cerrándose por cualquier motivo),
 * este servicio lo detecta y vuelve a abrir Mawaqit al instante.
 */
class TVHomeWatcherService : AccessibilityService() {

    private val MAWAQIT_PACKAGE = "com.kanout.mawaqit"
    private val MAWAQIT_URL = "https://mawaqit.net/es/m/msjd-lhd-bhm-mrsy-alhama-de-murcia-30840-spain"
    private var lastRelaunchTime = 0L
    private val MIN_INTERVAL_MS = 1500L // evita relanzamientos en cadena

    override fun onServiceConnected() {
        super.onServiceConnected()
        launchMawaqit()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return

        // Si el que está en pantalla ya es Mawaqit o nuestra propia app, no hacer nada
        if (pkg == packageName || pkg == MAWAQIT_PACKAGE) return

        val now = System.currentTimeMillis()
        if (now - lastRelaunchTime < MIN_INTERVAL_MS) return
        lastRelaunchTime = now

        launchMawaqit()
    }

    private fun launchMawaqit() {
        try {
            val viewIntent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(MAWAQIT_URL))
            viewIntent.setPackage(MAWAQIT_PACKAGE)
            viewIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val resolved = packageManager.resolveActivity(viewIntent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
            if (resolved != null) {
                startActivity(viewIntent)
                LaunchLog.record(this, "vigilante")
                return
            }
            val launchIntent = packageManager.getLaunchIntentForPackage(MAWAQIT_PACKAGE)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(launchIntent)
                LaunchLog.record(this, "vigilante")
            }
        } catch (e: Exception) {
            // Si falla, lo intentamos de nuevo en el próximo evento
        }
    }

    override fun onInterrupt() {}
}
