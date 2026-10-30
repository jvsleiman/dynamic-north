package com.joao.ilha

import android.graphics.drawable.Icon
import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BatteryStd
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private val Accent = Color(0xFF7CF5B0)
private val Dim = Color(0xFFB4B4BC)

private enum class Mode { Idle, Media, Notification, Charging, Test, ExpandedMedia, ExpandedList }

@Composable
fun IslandUi(state: IslandUiState, onTap: () -> Unit, onLongPress: () -> Unit) {
    val s by Prefs.state.collectAsState()
    val popup by state.popup.collectAsState()
    val expanded by state.expanded.collectAsState()
    val media by IslandBus.media.collectAsState()
    val notifs by IslandBus.notifications.collectAsState()
    val screenW by state.screenWidthDp.collectAsState()

    val mode = when {
        expanded && media != null && s.showMedia -> Mode.ExpandedMedia
        expanded && notifs.isNotEmpty() -> Mode.ExpandedList
        popup is Popup.Notification -> Mode.Notification
        popup is Popup.Charging -> Mode.Charging
        popup is Popup.Test -> Mode.Test
        media != null && s.showMedia -> Mode.Media
        else -> Mode.Idle
    }

    val wide = (screenW - 24).coerceAtMost(400).dp
    val baseW = s.width.dp
    val baseH = s.height.dp
    val (tw, th, tr) = when (mode) {
        Mode.Idle -> Triple(baseW, baseH, baseH / 2)
        Mode.Media -> Triple(baseW + 2 * baseH + 16.dp, baseH, baseH / 2)
        Mode.Charging -> Triple(baseW + 2 * baseH + 40.dp, baseH, baseH / 2)
        Mode.Notification, Mode.Test -> Triple(wide, 76.dp, 38.dp)
        Mode.ExpandedMedia -> Triple(wide, 196.dp, 40.dp)
        Mode.ExpandedList -> Triple(wide, 260.dp, 32.dp)
    }
    val springy = spring<Dp>(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow)
    val w by animateDpAsState(tw, springy, label = "w")
    val h by animateDpAsState(th, springy, label = "h")
    val r by animateDpAsState(tr, springy, label = "r")

    Box(
        Modifier
            .padding(2.dp)
            .width(w)
            .height(h)
            .clip(RoundedCornerShape(r))
            .background(Color.Black)
            .pointerInput(Unit) { detectTapGestures(onTap = { onTap() }, onLongPress = { onLongPress() }) },
    ) {
        AnimatedContent(
            targetState = mode,
            transitionSpec = { fadeIn(tween(220, 90)) togetherWith fadeOut(tween(90)) },
            label = "content",
        ) { m ->
            when (m) {
                Mode.Idle -> Box(Modifier.fillMaxSize())
                Mode.Media -> media?.let { CompactMedia(it, baseH) }
                Mode.Charging -> (popup as? Popup.Charging)?.let { CompactCharging(it.info, baseH) }
                Mode.Notification -> (popup as? Popup.Notification)?.let { NotificationBanner(it.item) }
                Mode.Test -> TestBanner()
                Mode.ExpandedMedia -> media?.let { ExpandedMedia(it) }
                Mode.ExpandedList -> NotificationList(notifs)
            }
        }
    }
}

@Composable
private fun CompactMedia(m: MediaInfo, h: Dp) {
    Row(
        Modifier.fillMaxSize().padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Art(m.art?.asImageBitmap(), h - 10.dp, CircleShape)
        Bars(m.playing, Modifier.size(h - 12.dp))
    }
}

@Composable
private fun CompactCharging(c: ChargingInfo, h: Dp) {
    Row(
        Modifier.fillMaxSize().padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(if (c.charging) "Carregando" else "Desconectado", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${c.level}%", color = if (c.charging) Accent else Dim, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Icon(
                if (c.charging) Icons.Rounded.BatteryChargingFull else Icons.Rounded.BatteryStd,
                null,
                tint = if (c.charging) Accent else Dim,
                modifier = Modifier.size(h - 14.dp),
            )
        }
    }
}

@Composable
private fun NotificationBanner(n: NotifItem) {
    Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        NotifPicture(n, 46.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(n.title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (n.text.isNotBlank()) {
                Text(n.text, color = Dim, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.width(8.dp))
        SmallIcon(n.smallIcon, 18.dp)
    }
}

@Composable
private fun TestBanner() {
    Row(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Notifications, null, tint = Accent, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text("Ilha funcionando", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text("Toque para abrir, segure para ir ao app", color = Dim, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ExpandedMedia(m: MediaInfo) {
    val controls = m.controller.transportControls
    Column(Modifier.fillMaxSize().padding(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Art(m.art?.asImageBitmap(), 58.dp, RoundedCornerShape(14.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(m.title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(m.artist.ifBlank { m.appName }, color = Dim, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Bars(m.playing, Modifier.size(22.dp))
        }
        Spacer(Modifier.height(14.dp))
        Progress(m)
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            Control(Icons.Rounded.SkipPrevious, 34.dp) { controls.skipToPrevious() }
            Control(if (m.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, 44.dp) {
                if (m.playing) controls.pause() else controls.play()
            }
            Control(Icons.Rounded.SkipNext, 34.dp) { controls.skipToNext() }
        }
    }
}

@Composable
private fun Progress(m: MediaInfo) {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(m.playing) {
        while (m.playing) {
            now = SystemClock.elapsedRealtime()
            delay(500)
        }
    }
    val pos = if (m.playing) m.positionMs + (now - m.positionAt) else m.positionMs
    val dur = m.durationMs
    val frac = if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(fmt(pos), color = Dim, fontSize = 11.sp)
        Box(
            Modifier.weight(1f).padding(horizontal = 10.dp).height(4.dp).clip(CircleShape).background(Color(0xFF3A3A40)),
        ) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(frac).background(Color.White))
        }
        Text(if (dur > 0) fmt(dur) else "--:--", color = Dim, fontSize = 11.sp)
    }
}

private fun fmt(ms: Long): String {
    val t = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(t / 60, t % 60)
}

@Composable
private fun NotificationList(list: List<NotifItem>) {
    LazyColumn(Modifier.fillMaxSize().padding(vertical = 10.dp)) {
        items(list.take(20), key = { it.key }) { n ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .pointerInput(n.key) { detectTapGestures { runCatching { n.contentIntent?.send() } } }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NotifPicture(n, 36.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(n.title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(n.text.ifBlank { n.appName }, color = Dim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun Control(icon: ImageVector, size: Dp, onClick: () -> Unit) {
    Icon(
        icon,
        null,
        tint = Color.White,
        modifier = Modifier.size(size).clip(CircleShape).pointerInput(icon) { detectTapGestures { onClick() } },
    )
}

@Composable
private fun Art(bmp: ImageBitmap?, size: Dp, shape: androidx.compose.ui.graphics.Shape) {
    if (bmp != null) {
        Image(bmp, null, contentScale = ContentScale.Crop, modifier = Modifier.size(size).clip(shape))
    } else {
        Box(Modifier.size(size).clip(shape).background(Color(0xFF2A2A30)), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.MusicNote, null, tint = Dim, modifier = Modifier.size(size * 0.55f))
        }
    }
}

@Composable
private fun NotifPicture(n: NotifItem, size: Dp) {
    val big = remember(n.key, n.largeIcon) { n.largeIcon?.asImageBitmap() }
    if (big != null) {
        Image(big, null, contentScale = ContentScale.Crop, modifier = Modifier.size(size).clip(CircleShape))
    } else {
        Box(Modifier.size(size).clip(CircleShape).background(Color(0xFF2A2A30)), contentAlignment = Alignment.Center) {
            SmallIcon(n.smallIcon, size * 0.5f)
        }
    }
}

@Composable
private fun SmallIcon(icon: Icon?, size: Dp) {
    val ctx = LocalContext.current
    val bmp = remember(icon) {
        icon?.let { i -> runCatching { i.setTint(android.graphics.Color.WHITE); i.loadDrawable(ctx)?.toBitmapSafe()?.asImageBitmap() }.getOrNull() }
    }
    if (bmp != null) Image(bmp, null, modifier = Modifier.size(size))
}

/** Barrinhas de áudio animadas (paradas quando a música está pausada). */
@Composable
private fun Bars(playing: Boolean, modifier: Modifier) {
    var tick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(playing) {
        while (playing) {
            tick++
            delay(140)
        }
    }
    Row(modifier, horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        val heights = listOf(0.45f, 0.9f, 0.6f, 0.8f)
        heights.forEachIndexed { i, base ->
            val f = if (playing) (0.3f + ((tick * (i + 2) * 37 + i * 53) % 70) / 100f) else base * 0.4f
            val bh by animateDpAsState((f.coerceIn(0.2f, 1f) * 18).dp, tween(130), label = "bar$i")
            Box(Modifier.width(3.dp).height(bh).clip(CircleShape).background(Accent))
        }
    }
}
