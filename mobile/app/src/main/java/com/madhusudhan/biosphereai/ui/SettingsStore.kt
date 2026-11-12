package com.madhusudhan.biosphereai.ui

import android.content.Context
import androidx.core.content.edit

/** Keeps [AppSettings] across app restarts. */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun load(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            personaId = prefs.getString("persona", d.personaId) ?: d.personaId,
            speed = enumOr(prefs.getString("speed", null), d.speed),
            lowAlert = prefs.getFloat("low", d.lowAlert.toFloat()).toDouble(),
            highAlert = prefs.getFloat("high", d.highAlert.toFloat()).toDouble(),
            autoMeals = prefs.getBoolean("routine", d.autoMeals),
            safetyFilter = prefs.getBoolean("safety", d.safetyFilter),
            themeMode = enumOr(prefs.getString("theme", null), d.themeMode),
            pureBlack = prefs.getBoolean("pure_black", d.pureBlack),
            cloudForecast = prefs.getBoolean("cloud_forecast", d.cloudForecast),
            notifications = prefs.getBoolean("notifications", d.notifications),
            simulateServerFailure = false,
        )
    }

    fun save(s: AppSettings) = prefs.edit {
        putString("persona", s.personaId)
        putString("speed", s.speed.name)
        putFloat("low", s.lowAlert.toFloat())
        putFloat("high", s.highAlert.toFloat())
        putBoolean("routine", s.autoMeals)
        putBoolean("safety", s.safetyFilter)
        putString("theme", s.themeMode.name)
        putBoolean("pure_black", s.pureBlack)
        putBoolean("cloud_forecast", s.cloudForecast)
        putBoolean("notifications", s.notifications)
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        enumValues<E>().firstOrNull { it.name == name } ?: default
}
