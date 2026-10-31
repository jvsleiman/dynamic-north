package com.joao.ilha

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        enableEdgeToEdge()
        setContent {
            val s by Prefs.state.collectAsState()
            IlhaTheme(s.appTheme) {
                val stack = remember { mutableStateListOf(Screen.Home) }
                val go: (Screen) -> Unit = { stack.add(it) }
                val back: () -> Unit = { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
                BackHandler(enabled = stack.size > 1) { back() }
                when (stack.last()) {
                    Screen.Home -> HomeScreen(go)
                    Screen.Display -> DisplayScreen(back)
                    Screen.Customize -> CustomizeScreen(back)
                    Screen.Idle -> IdleScreen(back)
                    Screen.Notifications -> NotificationsScreen(back, go)
                    Screen.Calls -> CallsScreen(back)
                    Screen.Music -> MusicScreen(back, go)
                    Screen.Charging -> ChargingScreen(back)
                    Screen.Live -> LiveScreen(back)
                    Screen.Controls -> ControlsScreen(back, go)
                    Screen.ControlsApps -> AppPickerScreen(
                        "Atalhos de apps", "Aparecem nos controles rápidos da ilha (até 6).",
                        s.controlsApps, back,
                    ) { v -> Prefs.update { it.copy(controlsApps = v.take(6).toSet()) } }
                    Screen.Bluetooth -> BluetoothScreen(back)
                    Screen.Border -> BorderScreen(back)
                    Screen.Gestures -> GesturesScreen(back)
                    Screen.Other -> OtherScreen(back)
                    Screen.Update -> UpdateScreen(back)
                    Screen.Help -> HelpScreen(back)
                    Screen.HiddenApps -> AppPickerScreen(
                        "Ocultar nos apps", "A ilha some enquanto esses apps estiverem abertos (menos durante chamadas).",
                        s.hiddenApps, back,
                    ) { v -> Prefs.update { it.copy(hiddenApps = v) } }
                    Screen.BlockedNotifApps -> AppPickerScreen(
                        "Apps bloqueados", "As notificações desses apps não aparecem na ilha.",
                        s.blockedNotifApps, back,
                    ) { v -> Prefs.update { it.copy(blockedNotifApps = v) } }
                    Screen.BlockedMusicApps -> AppPickerScreen(
                        "Apps de música bloqueados", "O player desses apps não aparece na ilha.",
                        s.blockedMusicApps, back,
                    ) { v -> Prefs.update { it.copy(blockedMusicApps = v) } }
                }
            }
        }
    }
}
