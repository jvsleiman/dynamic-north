package com.joao.ilha

import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.DoNotDisturbOff
import android.app.NotificationManager
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings as Sys
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.FiberManualRecord
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private val OnColor = Color(0xFF0A84FF)

/** Altura do painel de controles (sem escala), usada para dimensionar a ilha. */
internal fun controlsHeight(hasApps: Boolean) = if (hasApps) 228.dp else 182.dp

/** Painel de controles rápidos: botões do sistema, brilho, volume e atalhos de apps. */
@Composable
internal fun ControlsCard(s: Settings, actions: IslandActions) {
    val ctx = LocalContext.current
    val col = LocalIslandColors.current
    var tick by remember { mutableIntStateOf(0) }
    // Estado dos botões muda fora do app (painel do sistema): relê a cada segundo.
    LaunchedEffect(Unit) {
        while (true) {
            IslandBus.awaitScreenOn()
            delay(1000)
            tick++
        }
    }
    val torch = rememberTorch(ctx)
    val am = remember { ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val nm = remember { ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager }
    val wifiOn = remember(tick) { runCatching { (ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager).isWifiEnabled }.getOrDefault(false) }
    val btOn = remember(tick) { runCatching { (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter?.isEnabled == true }.getOrDefault(false) }
    val dndOn = remember(tick) { nm.currentInterruptionFilter > NotificationManager.INTERRUPTION_FILTER_ALL }
    val ringer = remember(tick) { am.ringerMode }
    val rotation = remember(tick) { Sys.System.getInt(ctx.contentResolver, Sys.System.ACCELEROMETER_ROTATION, 0) == 1 }

    fun open(i: Intent) {
        runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        actions.onCollapse()
    }
    fun askWriteSettings() = open(Intent(Sys.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${ctx.packageName}")))
    fun askDnd() = open(Intent(Sys.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Toggle(Icons.Rounded.FlashlightOn, "Lanterna", torch.on) { torch.toggle() }
            Toggle(Icons.Rounded.Wifi, "Wi‑Fi", wifiOn) {
                if (Build.VERSION.SDK_INT >= 29) open(Intent(Sys.Panel.ACTION_WIFI))
                else {
                    toggleWifiLegacy(ctx)
                    tick++
                }
            }
            Toggle(Icons.Rounded.Bluetooth, "Bluetooth", btOn) { open(Intent(Sys.ACTION_BLUETOOTH_SETTINGS)) }
            Toggle(Icons.Rounded.DoNotDisturbOn, "Não perturbe", dndOn) {
                if (!nm.isNotificationPolicyAccessGranted) askDnd()
                else {
                    nm.setInterruptionFilter(if (dndOn) NotificationManager.INTERRUPTION_FILTER_ALL else NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                    tick++
                }
            }
            val (ringIcon, ringLabel) = when (ringer) {
                AudioManager.RINGER_MODE_VIBRATE -> Icons.Rounded.Vibration to "Vibrar"
                AudioManager.RINGER_MODE_SILENT -> Icons.Rounded.NotificationsOff to "Silencioso"
                else -> Icons.Rounded.NotificationsActive to "Som"
            }
            Toggle(ringIcon, ringLabel, ringer != AudioManager.RINGER_MODE_NORMAL) {
                // Som → Vibrar → Silencioso (precisa do acesso ao Não perturbe) → Som
                val next = when (ringer) {
                    AudioManager.RINGER_MODE_NORMAL -> AudioManager.RINGER_MODE_VIBRATE
                    AudioManager.RINGER_MODE_VIBRATE -> if (nm.isNotificationPolicyAccessGranted) AudioManager.RINGER_MODE_SILENT else AudioManager.RINGER_MODE_NORMAL
                    else -> AudioManager.RINGER_MODE_NORMAL
                }
                runCatching { am.ringerMode = next }
                tick++
            }
            Toggle(Icons.Rounded.ScreenRotation, "Girar", rotation) {
                if (!Sys.System.canWrite(ctx)) askWriteSettings()
                else {
                    Sys.System.putInt(ctx.contentResolver, Sys.System.ACCELEROMETER_ROTATION, if (rotation) 0 else 1)
                    tick++
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        BrightnessBar(onNeedPermission = { askWriteSettings() })
        Spacer(Modifier.height(8.dp))
        MediaVolumeBar()
        if (s.controlsApps.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                s.controlsApps.take(6).forEach { pkg -> AppShortcut(pkg) { actions.onOpen(pkg) } }
            }
        } else {
            Spacer(Modifier.height(8.dp))
            Text(
                "Escolha atalhos de apps em Controles rápidos, no app Notch",
                color = col.dim, fontSize = 10.sp, modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

@Suppress("DEPRECATION")
private fun toggleWifiLegacy(ctx: Context) {
    runCatching {
        val wm = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        wm.isWifiEnabled = !wm.isWifiEnabled
    }
}

@Composable
private fun Toggle(icon: ImageVector, label: String, on: Boolean, onTap: () -> Unit) {
    val col = LocalIslandColors.current
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(48.dp)) {
        Box(
            Modifier.size(44.dp).clip(CircleShape)
                .background(if (on) OnColor else col.text.copy(alpha = 0.14f))
                .pointerInput(onTap) { detectTapGestures { onTap() } },
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = if (on) Color.White else col.text, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(label, color = col.dim, fontSize = 9.sp, maxLines = 1)
    }
}

/** Barra grossa que se arrasta, como no painel do sistema. */
@Composable
private fun LevelBar(icon: ImageVector, value: Float, onChange: (Float) -> Unit) {
    val col = LocalIslandColors.current
    Box(
        Modifier.fillMaxWidth().height(34.dp).clip(RoundedCornerShape(17.dp)).background(col.text.copy(alpha = 0.14f))
            .pointerInput(Unit) { detectTapGestures { o -> onChange(o.x / size.width) } }
            .pointerInput(Unit) { detectHorizontalDragGestures { ch, _ -> ch.consume(); onChange(ch.position.x / size.width) } },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(value.coerceIn(0.06f, 1f)).clip(RoundedCornerShape(17.dp)).background(col.text.copy(alpha = 0.85f)))
        Icon(icon, null, tint = col.bg, modifier = Modifier.padding(start = 10.dp).size(18.dp))
    }
}

@Composable
private fun BrightnessBar(onNeedPermission: () -> Unit) {
    val ctx = LocalContext.current
    val cr = ctx.contentResolver
    var v by remember { mutableFloatStateOf(Sys.System.getInt(cr, Sys.System.SCREEN_BRIGHTNESS, 128) / 255f) }
    LevelBar(Icons.Rounded.BrightnessMedium, v) { f ->
        if (!Sys.System.canWrite(ctx)) {
            onNeedPermission()
            return@LevelBar
        }
        v = f.coerceIn(0f, 1f)
        runCatching {
            Sys.System.putInt(cr, Sys.System.SCREEN_BRIGHTNESS_MODE, Sys.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
            Sys.System.putInt(cr, Sys.System.SCREEN_BRIGHTNESS, (v * 255).roundToInt().coerceIn(1, 255))
        }
    }
}

@Composable
private fun MediaVolumeBar() {
    val ctx = LocalContext.current
    val am = remember { ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val max = remember { am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var vol by remember { mutableIntStateOf(am.getStreamVolume(AudioManager.STREAM_MUSIC)) }
    LevelBar(if (vol == 0) Icons.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp, vol.toFloat() / max) { f ->
        val n = (f.coerceIn(0f, 1f) * max).roundToInt()
        if (n != vol) {
            vol = n
            runCatching { am.setStreamVolume(AudioManager.STREAM_MUSIC, n, 0) }
        }
    }
}

@Composable
private fun AppShortcut(pkg: String, onTap: () -> Unit) {
    val ctx = LocalContext.current
    val icon = remember(pkg) { runCatching { ctx.packageManager.getApplicationIcon(pkg).toBitmapSafe()?.asImageBitmap() }.getOrNull() }
    Box(Modifier.size(40.dp).clip(RoundedCornerShape(11.dp)).pointerInput(pkg) { detectTapGestures { onTap() } }) {
        if (icon != null) Image(icon, null, Modifier.fillMaxSize())
    }
}

private class TorchState(val on: Boolean, val toggle: () -> Unit)

/** Lanterna pela câmera traseira, acompanhando mudanças feitas pelo sistema. */
@Composable
private fun rememberTorch(ctx: Context): TorchState {
    val cm = remember { ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager }
    val id = remember {
        runCatching { cm.cameraIdList.firstOrNull { cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true } }.getOrNull()
    }
    var on by remember { mutableStateOf(false) }
    DisposableEffect(id) {
        val cb = object : CameraManager.TorchCallback() {
            override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                if (cameraId == id) on = enabled
            }
        }
        runCatching { cm.registerTorchCallback(cb, Handler(Looper.getMainLooper())) }
        onDispose { runCatching { cm.unregisterTorchCallback(cb) } }
    }
    return TorchState(on) { id?.let { runCatching { cm.setTorchMode(it, !on) } } }
}

// ---------- aviso de volume ----------

@Composable
internal fun VolumePopup(level: Int, max: Int, ring: Boolean, h: Dp) {
    val col = LocalIslandColors.current
    val f = level.toFloat() / max.coerceAtLeast(1)
    Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            when {
                level == 0 -> Icons.Rounded.VolumeOff
                ring -> Icons.Rounded.NotificationsActive
                else -> Icons.AutoMirrored.Rounded.VolumeUp
            },
            null, tint = col.text, modifier = Modifier.size(h * 0.5f),
        )
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f).height(6.dp).clip(CircleShape).background(col.text.copy(alpha = 0.2f))) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(f.coerceIn(0f, 1f)).background(col.text))
        }
        Spacer(Modifier.width(10.dp))
        Text("${(f * 100).roundToInt()}", color = col.text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Aviso curto do sistema: ícone na esquerda, texto na direita, como no iPhone. */
@Composable
internal fun StatusPopup(kind: StatusKind, h: Dp) {
    val col = LocalIslandColors.current
    val (icon, label, tint) = when (kind) {
        StatusKind.Ring -> Triple(Icons.Rounded.NotificationsActive, "Som", col.text)
        StatusKind.Vibrate -> Triple(Icons.Rounded.Vibration, "Vibrar", col.text)
        StatusKind.Silent -> Triple(Icons.Rounded.NotificationsOff, "Silencioso", Color(0xFFFF453A))
        StatusKind.DndOn -> Triple(Icons.Rounded.DoNotDisturbOn, "Não perturbe", Color(0xFF5E5CE6))
        StatusKind.DndOff -> Triple(Icons.Rounded.DoNotDisturbOff, "Não perturbe desligado", col.dim)
        StatusKind.Unlocked -> Triple(Icons.Rounded.LockOpen, "", col.text)
    }
    if (kind == StatusKind.Unlocked) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(icon, null, tint = tint, modifier = Modifier.size(h * 0.46f)) }
        return
    }
    Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (kind == StatusKind.Silent) {
            // Silencioso: sininho branco numa pílula vermelha.
            Box(Modifier.height(h * 0.62f).width(h * 0.95f).clip(CircleShape).background(tint), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = Color.White, modifier = Modifier.size(h * 0.42f))
            }
        } else Icon(icon, null, tint = tint, modifier = Modifier.size(h * 0.5f))
        Spacer(Modifier.weight(1f))
        if (label.isNotEmpty()) Text(label, color = tint, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Ícone vermelho para gravação de tela/voz (usado no modo ao vivo). */
internal val RecordIcon = Icons.Rounded.FiberManualRecord
