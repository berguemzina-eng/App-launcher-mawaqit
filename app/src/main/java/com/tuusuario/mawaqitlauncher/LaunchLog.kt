package com.tuusuario.mawaqitlauncher

import android.content.Context

/**
 * Pequeño registro compartido: guarda la fecha/hora de la última vez
 * que esta app lanzó Mawaqit, sea desde el arranque, el vigilante o
 * la propia app. Usado por Ajustes para mostrar "Última apertura".
 */
object LaunchLog {
    private const val PREFS = "mawaqit_launcher_prefs"
    private const val KEY_LAST_LAUNCH = "last_launch_time"
    private const val KEY_LAST_SOURCE = "last_launch_source"

    fun record(context: Context, source: String = "desconocido") {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit()
            .putLong(KEY_LAST_LAUNCH, System.currentTimeMillis())
            .putString(KEY_LAST_SOURCE, source)
            .apply()
    }

    fun lastLaunchTime(context: Context): Long {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getLong(KEY_LAST_LAUNCH, 0L)
    }

    fun lastLaunchSource(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LAST_SOURCE, "-") ?: "-"
    }
}
