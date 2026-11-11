package com.joao.ilha

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings

/**
 * Avisos flutuantes do próprio Android (os que descem do topo). O Notch só consegue desligá-los
 * depois de receber, pelo computador (ADB), a permissão de alterar configurações protegidas.
 */
object SystemPopups {
    private const val KEY = "heads_up_notifications_enabled"
    const val GRANT_COMMAND = "adb shell pm grant com.joao.ilha android.permission.WRITE_SECURE_SETTINGS"

    fun allowed(ctx: Context) =
        ctx.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    fun hidden(ctx: Context) = Settings.Global.getInt(ctx.contentResolver, KEY, 1) == 0

    /** Esconde ou devolve os avisos do Android; false se a permissão não foi liberada. */
    fun set(ctx: Context, hide: Boolean): Boolean =
        allowed(ctx) && runCatching { Settings.Global.putInt(ctx.contentResolver, KEY, if (hide) 0 else 1) }.getOrDefault(false)

    /**
     * Mantém o Android de acordo com o ajuste: avisos escondidos só enquanto a ilha está ligada
     * e mostrando notificações. Se a ilha desligar, os avisos do Android voltam sozinhos.
     */
    fun sync(ctx: Context, islandRunning: Boolean) {
        val s = Prefs.state.value
        if (!s.hideSystemPopups && !hidden(ctx)) return
        set(ctx, s.hideSystemPopups && islandRunning && s.enabled && s.notifEnabled)
    }
}
