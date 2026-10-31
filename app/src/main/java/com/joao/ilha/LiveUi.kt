package com.joao.ilha

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

internal val TimerColor = Color(0xFFFF9F0A)
internal val NavColor = Color(0xFF0A84FF)
internal val CamColor = Color(0xFF34C759)
internal val MicColor = Color(0xFFFF9F0A)
internal val RecColor = Color(0xFFFF3B30)

private val timeLike = Regex("\\d{1,2}:\\d{2}(:\\d{2})?")

private fun LiveInfo.vector(): ImageVector = when (kind) {
    LiveKind.Timer, LiveKind.Stopwatch -> Icons.Rounded.Timer
    LiveKind.Alarm -> Icons.Rounded.Alarm
    LiveKind.Navigation -> Icons.Rounded.Navigation
    LiveKind.Recording -> RecordIcon
}

private fun LiveInfo.tint(): Color = when (kind) {
    LiveKind.Navigation -> NavColor
    LiveKind.Recording -> RecColor
    else -> TimerColor
}

/** Tempo que aparece na ilha: conta sozinho quando o app manda a base do cronômetro. */
@Composable
private fun liveTime(l: LiveInfo): String {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(l.chronoBase) {
        while (l.chronoBase > 0) {
            now = System.currentTimeMillis()
            delay(500)
        }
    }
    if (l.chronoBase > 0) {
        val ms = if (l.countDown) (l.chronoBase - now).coerceAtLeast(0) else (now - l.chronoBase).coerceAtLeast(0)
        val t = ms / 1000
        return if (t >= 3600) "%d:%02d:%02d".format(t / 3600, t / 60 % 60, t % 60) else "%d:%02d".format(t / 60, t % 60)
    }
    return timeLike.find(l.text)?.value ?: timeLike.find(l.title)?.value ?: l.title.ifBlank { l.text }
}

@Composable
private fun LiveIcon(l: LiveInfo, size: Dp) {
    val bmp = l.icon
    if (l.kind == LiveKind.Navigation && bmp != null) {
        // O Maps manda a seta da próxima curva como ícone grande.
        Image(bmp.asImageBitmap(), null, contentScale = ContentScale.Fit, modifier = Modifier.size(size))
    } else {
        Icon(l.vector(), null, tint = l.tint(), modifier = Modifier.size(size))
    }
}

@Composable
internal fun CompactLiveIcon(l: LiveInfo, size: Dp) = LiveIcon(l, size)

// ---------- ilha pequena ----------

@Composable
internal fun CompactLive(l: LiveInfo, h: Dp) {
    val col = LocalIslandColors.current
    val ic = LocalIconScale.current
    Row(
        Modifier.fillMaxSize().padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        LiveIcon(l, (h - 14.dp) * ic)
        val right = if (l.kind == LiveKind.Navigation) l.title else liveTime(l)
        Text(
            right, color = if (l.kind == LiveKind.Navigation) col.text else l.tint(),
            fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

// ---------- ilha aberta ----------

@Composable
internal fun ExpandedLive(l: LiveInfo) {
    val col = LocalIslandColors.current
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically) {
            if (l.kind == LiveKind.Navigation) {
                Box(Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)).background(NavColor.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                    LiveIcon(l, 40.dp)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(l.title, color = col.text, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(l.text, color = col.dim, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            } else {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(l.vector(), null, tint = l.tint(), modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            when (l.kind) {
                                LiveKind.Timer -> "Timer"
                                LiveKind.Recording -> "Gravando"
                                else -> "Cronômetro"
                            },
                            color = l.tint(), fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        )
                    }
                    val sub = listOf(l.title, l.text).firstOrNull { it.isNotBlank() && !timeLike.matches(it.trim()) }
                    if (sub != null) Text(sub, color = col.dim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(liveTime(l), color = l.tint(), fontSize = 42.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
        LiveActions(l)
    }
}

/** Alarme tocando: aparece grande, com os botões do próprio app (soneca, parar). */
@Composable
internal fun AlarmRinging(l: LiveInfo) {
    val col = LocalIslandColors.current
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 10.dp)) {
        Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(TimerColor.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Alarm, null, tint = TimerColor, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(l.title.ifBlank { "Alarme" }, color = col.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (l.text.isNotBlank()) Text(l.text, color = col.dim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        LiveActions(l)
    }
}

@Composable
private fun LiveActions(l: LiveInfo) {
    val col = LocalIslandColors.current
    if (l.actions.isEmpty()) return
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        l.actions.forEachIndexed { i, a ->
            // O último botão (parar, sair) fica destacado.
            val strong = i == l.actions.lastIndex && l.actions.size > 1
            Box(
                Modifier
                    .weight(1f)
                    .height(34.dp)
                    .clip(RoundedCornerShape(17.dp))
                    .background(if (strong) l.tint().copy(alpha = 0.85f) else col.text.copy(alpha = 0.14f))
                    .pointerInput(a) { detectTapGestures { runCatching { a.intent.send() } } },
                contentAlignment = Alignment.Center,
            ) {
                Text(a.title, color = if (strong) Color.White else col.text, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

// ---------- indicador de câmera e microfone ----------

@Composable
internal fun PrivacyDots() {
    val cam by IslandBus.cameraInUse.collectAsState()
    val mic by IslandBus.micInUse.collectAsState()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        if (cam) Box(Modifier.size(7.dp).clip(CircleShape).background(CamColor))
        if (mic) Box(Modifier.size(7.dp).clip(CircleShape).background(MicColor))
    }
}
