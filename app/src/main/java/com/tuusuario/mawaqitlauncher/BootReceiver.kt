package com.tuusuario.mawaqitlauncher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper

class BootReceiver : BroadcastReceiver() {

    private val MAWAQIT_PACKAGE = "com.kanout.mawaqit"
    private val MAWAQIT_URL = "https://mawaqit.net/es/m/msjd-lhd-bhm-mrsy-alhama-de-murcia-30840-spain"
    private val MAX_RETRIES = 4
    private val RETRY_DELAY_MS = 2000L

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        // goAsync() mantiene vivo el receiver mientras reintentamos
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        val handler = Handler(Looper.getMainLooper())

        var attempts = 0
        lateinit var attempt: () -> Unit
        attempt = {
            attempts++
            val launched = tryLaunch(appContext)
            if (launched) {
                LaunchLog.record(appContext, "arranque")
                pendingResult.finish()
            } else if (attempts < MAX_RETRIES) {
                // Mawaqit puede no estar listo justo tras el arranque: reintentamos
                handler.postDelayed(attempt, RETRY_DELAY_MS)
            } else {
                pendingResult.finish()
            }
        }
        attempt()
    }

    private fun tryLaunch(context: Context): Boolean {
        return try {
            val viewIntent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(MAWAQIT_URL))
            viewIntent.setPackage(MAWAQIT_PACKAGE)
            viewIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val resolved = context.packageManager.resolveActivity(viewIntent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
            if (resolved != null) {
                context.startActivity(viewIntent)
                true
            } else {
                val launchIntent = context.packageManager.getLaunchIntentForPackage(MAWAQIT_PACKAGE) ?: return false
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                true
            }
        } catch (e: Exception) {
            false
        }
    }
}
