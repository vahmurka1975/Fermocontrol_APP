package ru.fermocontrol.data

import android.content.Context

class SettingsStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("settings", Context.MODE_PRIVATE)

    var paletteId: String
        get() = prefs.getString("palette", null) ?: "graphite"
        set(v) = prefs.edit().putString("palette", v).apply()

    var cloud: String
        get() = prefs.getString("cloud", null) ?: "Yandex"
        set(v) = prefs.edit().putString("cloud", v).apply()

    var interval: Int
        get() = prefs.getInt("interval", 3)
        set(v) = prefs.edit().putInt("interval", v).apply()

    var pace: Double
        get() = prefs.getString("pace", null)?.toDoubleOrNull() ?: 1.5
        set(v) = prefs.edit().putString("pace", v.toString()).apply()
}