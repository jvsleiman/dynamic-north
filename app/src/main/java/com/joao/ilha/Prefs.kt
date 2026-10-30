package com.joao.ilha

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Ajustes da ilha. Tamanhos e posições em dp. */
data class Settings(
    val enabled: Boolean = true,
    val width: Int = 110,
    val height: Int = 34,
    val offsetX: Int = 0,
    val offsetY: Int = 0,
    val alignToCamera: Boolean = true,
    val showNotifications: Boolean = true,
    val showMedia: Boolean = true,
    val showCharging: Boolean = true,
    val popupSeconds: Int = 4,
    val hideInLandscape: Boolean = true,
    val vibrate: Boolean = true,
    val blockedApps: Set<String> = emptySet(),
)

object Prefs {
    private lateinit var sp: SharedPreferences
    private val _state = MutableStateFlow(Settings())
    val state: StateFlow<Settings> get() = _state

    fun init(context: Context) {
        if (::sp.isInitialized) return
        sp = context.applicationContext.getSharedPreferences("ilha", Context.MODE_PRIVATE)
        _state.value = read()
    }

    private fun read(): Settings {
        val d = Settings()
        return Settings(
            enabled = sp.getBoolean("enabled", d.enabled),
            width = sp.getInt("width", d.width),
            height = sp.getInt("height", d.height),
            offsetX = sp.getInt("offsetX", d.offsetX),
            offsetY = sp.getInt("offsetY", d.offsetY),
            alignToCamera = sp.getBoolean("alignToCamera", d.alignToCamera),
            showNotifications = sp.getBoolean("showNotifications", d.showNotifications),
            showMedia = sp.getBoolean("showMedia", d.showMedia),
            showCharging = sp.getBoolean("showCharging", d.showCharging),
            popupSeconds = sp.getInt("popupSeconds", d.popupSeconds),
            hideInLandscape = sp.getBoolean("hideInLandscape", d.hideInLandscape),
            vibrate = sp.getBoolean("vibrate", d.vibrate),
            blockedApps = sp.getStringSet("blockedApps", d.blockedApps)?.toSet() ?: d.blockedApps,
        )
    }

    fun update(change: (Settings) -> Settings) {
        val s = change(_state.value)
        sp.edit()
            .putBoolean("enabled", s.enabled)
            .putInt("width", s.width)
            .putInt("height", s.height)
            .putInt("offsetX", s.offsetX)
            .putInt("offsetY", s.offsetY)
            .putBoolean("alignToCamera", s.alignToCamera)
            .putBoolean("showNotifications", s.showNotifications)
            .putBoolean("showMedia", s.showMedia)
            .putBoolean("showCharging", s.showCharging)
            .putInt("popupSeconds", s.popupSeconds)
            .putBoolean("hideInLandscape", s.hideInLandscape)
            .putBoolean("vibrate", s.vibrate)
            .putStringSet("blockedApps", s.blockedApps)
            .apply()
        _state.value = s
    }

    fun reset() = update { Settings() }
}
