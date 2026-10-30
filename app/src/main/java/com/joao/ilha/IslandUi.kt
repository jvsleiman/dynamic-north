package com.joao.ilha

import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.os.BatteryManager
import android.os.Build
import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Headset
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.palette.graphics.Palette
import kotlinx.coroutines.delay
import kotlin.math.abs

private val Green = Color(0xFF34C759)
private val Red = Color(0xFFFF3B30)

/** Cores da ilha (fundo e texto), escolhidas nos ajustes ou tiradas da capa/ícone. */
private data class IslandColors(val bg: Color, val text: Color, val dim: Color)

private val LocalIslandColors = compositionLocalOf { IslandColors(Color.Black, Color.White, Color(0xFFB4B4BC)) }

private enum class Mode(val large: Boolean) {
    Idle(false), Asleep(false), Media(false), Call(false), Charging(false), Bluetooth(false), SmallNotif(false),
    Notification(true), Test(true), IncomingCall(true),
    ExpandedCall(true), ExpandedMedia(true), ExpandedList(true), ExpandedBattery(true),
}

@Composable
fun IslandUi(state: IslandUiState, actions: IslandActions) {
    val s by Prefs.state.collectAsState()
    val popup by state.popup.collectAsState()
    val expanded by state.expanded.collectAsState()
    val asleep by state.asleep.collectAsState()
    val media by IslandBus.media.collectAsState()
    val notifs by IslandBus.notifications.collectAsState()
    val call by IslandBus.call.collectAsState()
    val screenW by state.screenWidthDp.collectAsState()

    val mode = when {
        call?.incoming == true -> Mode.IncomingCall
        expanded && call != null -> Mode.ExpandedCall
        expanded && media != null -> Mode.ExpandedMedia
        expanded && notifs.isNotEmpty() -> Mode.ExpandedList
        expanded -> Mode.ExpandedBattery
        popup is Popup.Notification -> if (s.notifExpanded) Mode.Notification else Mode.SmallNotif
        popup is Popup.Charging -> Mode.Charging
        popup is Popup.Bluetooth -> Mode.Bluetooth
        popup is Popup.Test -> Mode.Test
        call != null && s.showOngoingCall -> Mode.Call
        media != null -> Mode.Media
        asleep -> Mode.Asleep
        else -> Mode.Idle
    }

    // Cor automática: tirada da capa da música ou do ícone da notificação.
    val sourceBmp = when (mode) {
        Mode.Media, Mode.ExpandedMedia -> media?.art
        Mode.Notification, Mode.SmallNotif -> (popup as? Popup.Notification)?.item?.largeIcon
        else -> null
    }
    val autoBg = remember(sourceBmp, s.autoColor) { if (s.autoColor) sourceBmp?.let { darkColorOf(it) } else null }
    val colors = IslandColors(
        bg = autoBg ?: Color(s.bgColor),
        text = Color(s.textColor),
        dim = Color(s.textColor).copy(alpha = 0.62f),
    )

    val wide = minOf(screenW - 16, s.largeMaxWidth).dp
    val baseW = s.width.dp
    val baseH = s.height.dp
    val grow = if (s.growWhenActive) baseH * 2 + 12.dp else 0.dp
    val smallR = baseH * (s.smallRadius / 100f)
    val (tw, th) = when (mode) {
        Mode.Idle -> baseW to baseH
        Mode.Asleep -> (if (s.keepMiniWhenHidden) baseH else 1.dp) to baseH
        Mode.Media, Mode.Call -> baseW + grow to baseH
        Mode.Charging -> baseW + grow + 24.dp to baseH
        Mode.Bluetooth -> baseW + grow + 48.dp to baseH
        Mode.SmallNotif -> minOf(wide, baseW + grow + 120.dp) to baseH
        Mode.Notification, Mode.Test -> wide to 78.dp
        Mode.IncomingCall -> wide to 84.dp
        Mode.ExpandedCall -> wide to 132.dp
        Mode.ExpandedMedia -> wide to if (s.seekButtons) 206.dp else 196.dp
        Mode.ExpandedList -> wide to (76 + 58 * notifs.size.coerceAtMost(4)).dp
        Mode.ExpandedBattery -> wide to 112.dp
    }
    val tr = if (mode.large) s.largeRadius.dp else smallR
    val springy = spring<Dp>(dampingRatio = s.motion.damping, stiffness = s.motion.stiffness)
    val w by animateDpAsState(tw, springy, label = "w")
    val h by animateDpAsState(th, springy, label = "h")
    val r by animateDpAsState(tr, springy, label = "r")

    // Segunda ilha (bolinha) com a outra atividade quando há duas ao mesmo tempo.
    val secondArt = s.dualIsland && mode == Mode.Call && media != null
    val secondNotif = if (s.dualIsland && !secondArt && (mode == Mode.Media || mode == Mode.Call)) notifs.firstOrNull() else null
    val hasSecond = secondArt || secondNotif != null
    val bubble = baseH
    val bubbleContent: @Composable () -> Unit = {
        if (secondArt) Art(media?.art?.asImageBitmap(), baseH - 8.dp, CircleShape)
        else secondNotif?.let { NotifPicture(it, baseH - 8.dp) }
    }

    CompositionLocalProvider(LocalIslandColors provides colors) {
        Row(verticalAlignment = Alignment.Top) {
            // Espaço igual do outro lado para a ilha principal continuar centralizada.
            if (hasSecond && s.dualOnRight) Spacer(Modifier.width(bubble + 6.dp))
            if (hasSecond && !s.dualOnRight) {
                Bubble(bubble, colors.bg, bubbleContent)
                Spacer(Modifier.width(6.dp))
            }
            Island(
                w, h, r, mode, s,
                modifier = Modifier
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { actions.onTap() }, onLongPress = { actions.onHold() })
                    }
                    .pointerInput(Unit) {
                        var drag = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { drag = 0f },
                            onDragEnd = { if (abs(drag) > 60.dp.toPx()) actions.onSwipe() },
                            onHorizontalDrag = { _, d -> drag += d },
                        )
                    },
            ) {
                AnimatedContent(
                    targetState = mode,
                    transitionSpec = { fadeIn(tween(220, 90)) togetherWith fadeOut(tween(90)) },
                    label = "content",
                ) { m ->
                    when (m) {
                        Mode.Idle -> IdleContent(if (s.showCount) notifs.size else 0)
                        Mode.Asleep -> Box(Modifier.fillMaxSize())
                        Mode.Media -> media?.let { CompactMedia(it, baseH) }
                        Mode.Call -> call?.let { CompactCall(it, Color(s.callWaveColor)) }
                        Mode.Charging -> (popup as? Popup.Charging)?.let { CompactCharging(it.info, baseH) }
                        Mode.Bluetooth -> (popup as? Popup.Bluetooth)?.let { CompactBluetooth(it.info, baseH) }
                        Mode.SmallNotif -> (popup as? Popup.Notification)?.let { SmallNotification(it.item, baseH) }
                        Mode.Notification -> (popup as? Popup.Notification)?.let { NotificationBanner(it.item) }
                        Mode.Test -> TestBanner()
                        Mode.IncomingCall -> call?.let { IncomingCall(it) }
                        Mode.ExpandedCall -> call?.let { ExpandedCall(it, actions) }
                        Mode.ExpandedMedia -> media?.let { ExpandedMedia(it, s) }
                        Mode.ExpandedList -> NotificationList(notifs, actions)
                        Mode.ExpandedBattery -> BatteryCard()
                    }
                }
            }
            if (hasSecond && s.dualOnRight) {
                Spacer(Modifier.width(6.dp))
                Bubble(bubble, colors.bg, bubbleContent)
            }
            if (hasSecond && !s.dualOnRight) Spacer(Modifier.width(bubble + 6.dp))
        }
    }
}

@Composable
private fun Island(
    w: Dp, h: Dp, r: Dp, mode: Mode, s: Settings,
    modifier: Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = LocalIslandColors.current
    val shape = RoundedCornerShape(r)
    val showBorder = if (mode.large) s.borderLarge else s.borderSmall
    val animated = s.borderAnim != BorderAnim.Off && !mode.large && mode != Mode.Asleep
    Box(
        modifier
            .padding(2.dp)
            .width(w)
            .height(h)
            .clip(shape)
            .then(if (animated) Modifier.animatedBorder(s) else Modifier)
            .padding(if (animated) s.borderAnimWidth.dp else 0.dp)
            .clip(shape)
            .background(colors.bg)
            .then(if (showBorder && !animated) Modifier.border(1.5.dp, Color(s.borderColor), shape) else Modifier),
        content = content,
    )
}

/** Borda colorida girando em volta da ilha pequena. */
@Composable
private fun Modifier.animatedBorder(s: Settings): Modifier {
    val t = rememberInfiniteTransition(label = "border")
    val period = (7 - s.borderAnimSpeed.coerceIn(1, 6)) * 700
    val angle by t.animateFloat(0f, 360f, infiniteRepeatable(tween(period, easing = LinearEasing), RepeatMode.Restart), label = "angle")
    val brush = if (s.borderAnim == BorderAnim.Rainbow) {
        Brush.sweepGradient(listOf(Color(0xFFFF375F), Color(0xFFFF9F0A), Color(0xFFFFD60A), Color(0xFF30D158), Color(0xFF64D2FF), Color(0xFFBF5AF2), Color(0xFFFF375F)))
    } else {
        val c = Color(s.borderAnimColor)
        Brush.sweepGradient(listOf(c.copy(alpha = 0f), c, c.copy(alpha = 0f), c.copy(alpha = 0f)))
    }
    return drawBehind {
        rotate(angle) { drawCircle(brush, radius = size.maxDimension, center = Offset(size.width / 2, size.height / 2)) }
    }
}

@Composable
private fun Bubble(size: Dp, bg: Color, content: @Composable () -> Unit) {
    Box(
        Modifier.padding(top = 2.dp).size(size).clip(CircleShape).background(bg),
        contentAlignment = Alignment.Center,
    ) { content() }
}

// ---------- modos pequenos ----------

@Composable
private fun IdleContent(count: Int) {
    val c = LocalIslandColors.current
    Box(Modifier.fillMaxSize().padding(horizontal = 10.dp), contentAlignment = Alignment.CenterEnd) {
        if (count > 0) Text(count.toString(), color = c.dim, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun CompactMedia(m: MediaInfo, h: Dp) {
    Row(
        Modifier.fillMaxSize().padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Art(m.art?.asImageBitmap(), h - 10.dp, CircleShape, spinning = m.playing)
        Bars(m.playing, Green, Modifier.size(h - 12.dp))
    }
}

@Composable
private fun CompactCall(c: CallInfo, wave: Color) {
    Row(
        Modifier.fillMaxSize().padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Call, null, tint = Green, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Timer(c.startedAt, Green, 12)
        }
        Bars(true, wave, Modifier.size(18.dp))
    }
}

@Composable
private fun CompactCharging(c: ChargingInfo, h: Dp) {
    val col = LocalIslandColors.current
    val t = rememberInfiniteTransition(label = "charge")
    val pulse by t.animateFloat(0.45f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulse")
    Row(
        Modifier.fillMaxSize().padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(if (c.charging) "Carregando" else "Na bateria", color = col.text, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${c.level}%", color = if (c.charging) Green else col.dim, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(4.dp))
            BatteryGlyph(c.level, if (c.charging) Green else col.dim, Modifier.alpha(if (c.charging) pulse else 1f), h)
        }
    }
}

@Composable
private fun BatteryGlyph(level: Int, color: Color, modifier: Modifier, h: Dp) {
    Box(modifier.width(h * 0.85f).height(h * 0.42f).clip(RoundedCornerShape(4.dp)).border(1.5.dp, color.copy(alpha = 0.6f), RoundedCornerShape(4.dp)).padding(2.dp)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth((level / 100f).coerceIn(0.05f, 1f)).clip(RoundedCornerShape(2.dp)).background(color))
    }
}

@Composable
private fun CompactBluetooth(b: BluetoothInfo, h: Dp) {
    val col = LocalIslandColors.current
    Row(
        Modifier.fillMaxSize().padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(btIcon(b.kind), null, tint = if (b.connected) Color(0xFF0A84FF) else col.dim, modifier = Modifier.size(h - 14.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                if (b.connected) b.name else "Desconectado",
                color = col.text, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        if (b.battery != null) Text("${b.battery}%", color = Green, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun btIcon(k: BtKind): ImageVector = when (k) {
    BtKind.Earbuds -> Icons.Rounded.Headset
    BtKind.Headphones -> Icons.Rounded.Headphones
    BtKind.Watch -> Icons.Rounded.Watch
    BtKind.Speaker -> Icons.Rounded.Speaker
    BtKind.Other -> Icons.Rounded.Bluetooth
}

@Composable
private fun SmallNotification(n: NotifItem, h: Dp) {
    val col = LocalIslandColors.current
    Row(Modifier.fillMaxSize().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        NotifPicture(n, h - 10.dp)
        Spacer(Modifier.width(8.dp))
        Text(n.title, color = col.text, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(6.dp))
        SmallIcon(n.smallIcon, 14.dp)
        Spacer(Modifier.width(4.dp))
    }
}

// ---------- modos grandes ----------

@Composable
private fun NotificationBanner(n: NotifItem) {
    val col = LocalIslandColors.current
    Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        NotifPicture(n, 46.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(n.title, color = col.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (n.text.isNotBlank()) Text(n.text, color = col.dim, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            SmallIcon(n.smallIcon, 16.dp)
            Text(n.appName, color = col.dim, fontSize = 10.sp, maxLines = 1)
        }
    }
}

@Composable
private fun TestBanner() {
    val col = LocalIslandColors.current
    Row(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Notifications, null, tint = Green, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text("A ilha está funcionando", color = col.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text("Toque para expandir, segure para abrir o app", color = col.dim, fontSize = 12.sp)
        }
    }
}

@Composable
private fun IncomingCall(c: CallInfo) {
    val col = LocalIslandColors.current
    Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Avatar(c.photo, 50.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Chamada recebida", color = col.dim, fontSize = 12.sp)
            Text(c.name, color = col.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        RoundButton(Icons.Rounded.CallEnd, Red, 46.dp) { runCatching { c.decline?.send() } }
        Spacer(Modifier.width(12.dp))
        RoundButton(Icons.Rounded.Call, Green, 46.dp) { runCatching { (c.answer ?: c.contentIntent)?.send() } }
    }
}

@Composable
private fun ExpandedCall(c: CallInfo, actions: IslandActions) {
    val col = LocalIslandColors.current
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(c.photo, 48.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(c.name, color = col.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Timer(c.startedAt, Green, 13)
            }
            Bars(true, Color(Prefs.state.value.callWaveColor), Modifier.size(24.dp))
        }
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            RoundButton(Icons.Rounded.Call, Color(0xFF3A3A3C), 44.dp) { runCatching { c.contentIntent?.send() }; actions.onCollapse() }
            RoundButton(Icons.Rounded.CallEnd, Red, 44.dp) { runCatching { c.decline?.send() }; actions.onCollapse() }
        }
    }
}

@Composable
private fun ExpandedMedia(m: MediaInfo, s: Settings) {
    val col = LocalIslandColors.current
    val controls = m.controller.transportControls
    Box(Modifier.fillMaxSize()) {
        val art = remember(m.art) { m.art?.asImageBitmap() }
        if (s.blurArt && art != null) {
            Image(
                art, null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().alpha(if (Build.VERSION.SDK_INT >= 31) 0.55f else 0.25f)
                    .then(if (Build.VERSION.SDK_INT >= 31) Modifier.blur(28.dp) else Modifier),
            )
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))
        }
        Column(Modifier.fillMaxSize().padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Art(art, 58.dp, RoundedCornerShape(14.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(m.title, color = col.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(m.artist.ifBlank { m.appName }, color = col.dim, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Bars(m.playing, Green, Modifier.size(22.dp))
            }
            Spacer(Modifier.height(14.dp))
            Progress(m)
            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                if (s.seekButtons) Control(Icons.Rounded.Replay10, 28.dp) { controls.seekTo((livePosition(m) - 10_000).coerceAtLeast(0)) }
                Control(Icons.Rounded.SkipPrevious, 34.dp) { controls.skipToPrevious() }
                Control(if (m.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, 46.dp) {
                    if (m.playing) controls.pause() else controls.play()
                }
                Control(Icons.Rounded.SkipNext, 34.dp) { controls.skipToNext() }
                if (s.seekButtons) Control(Icons.Rounded.Forward10, 28.dp) { controls.seekTo(livePosition(m) + 10_000) }
            }
        }
    }
}

private fun livePosition(m: MediaInfo): Long =
    if (m.playing) m.positionMs + (SystemClock.elapsedRealtime() - m.positionAt) else m.positionMs

@Composable
private fun Progress(m: MediaInfo) {
    val col = LocalIslandColors.current
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
        Text(fmt(pos), color = col.dim, fontSize = 11.sp)
        Box(
            Modifier
                .weight(1f)
                .padding(horizontal = 10.dp)
                .height(16.dp)
                .pointerInput(dur) {
                    detectTapGestures { o -> if (dur > 0) m.controller.transportControls.seekTo((o.x / size.width * dur).toLong()) }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(col.text.copy(alpha = 0.2f))) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(frac).background(col.text))
            }
        }
        Text(if (dur > 0) fmt(dur) else "--:--", color = col.dim, fontSize = 11.sp)
    }
}

private fun fmt(ms: Long): String {
    val t = (ms / 1000).coerceAtLeast(0)
    return if (t >= 3600) "%d:%02d:%02d".format(t / 3600, t / 60 % 60, t % 60) else "%d:%02d".format(t / 60, t % 60)
}

@Composable
private fun Timer(startedAt: Long, color: Color, size: Int) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(startedAt) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    Text(fmt(now - startedAt), color = color, fontSize = size.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun NotificationList(list: List<NotifItem>, actions: IslandActions) {
    val col = LocalIslandColors.current
    Column(Modifier.fillMaxSize().padding(top = 14.dp, bottom = 8.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Notificações", color = col.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(
                "Limpar tudo", color = Color(0xFF0A84FF), fontSize = 13.sp,
                modifier = Modifier.pointerInput(Unit) { detectTapGestures { IslandBus.dismiss?.invoke(null); actions.onCollapse() } },
            )
        }
        Spacer(Modifier.height(6.dp))
        LazyColumn(Modifier.fillMaxSize()) {
            items(list.take(30), key = { it.key }) { n ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .pointerInput(n.key) { detectTapGestures { runCatching { n.contentIntent?.send() }; actions.onCollapse() } }
                        .padding(horizontal = 16.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    NotifPicture(n, 38.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(n.title, color = col.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(n.text.ifBlank { n.appName }, color = col.dim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun BatteryCard() {
    val col = LocalIslandColors.current
    val ctx = LocalContext.current
    val i = remember { ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) }
    val level = i?.let { it.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) * 100 / it.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1) } ?: 0
    val temp = (i?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f
    val st = i?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
    val charging = st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL
    Row(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(if (charging) Icons.Rounded.BatteryChargingFull else Icons.Rounded.BatteryStd, null, tint = if (charging) Green else col.dim, modifier = Modifier.size(44.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(if (charging) "Carregando" else "Bateria", color = col.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text("Temperatura %.1f °C".format(temp), color = col.dim, fontSize = 12.sp)
        }
        Text("$level%", color = if (charging) Green else col.text, fontSize = 30.sp, fontWeight = FontWeight.Bold)
    }
}

// ---------- peças ----------

@Composable
private fun RoundButton(icon: ImageVector, bg: Color, size: Dp, onClick: () -> Unit) {
    Box(
        Modifier.size(size).clip(CircleShape).background(bg).pointerInput(icon) { detectTapGestures { onClick() } },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(size * 0.5f)) }
}

@Composable
private fun Control(icon: ImageVector, size: Dp, onClick: () -> Unit) {
    Icon(
        icon, null, tint = LocalIslandColors.current.text,
        modifier = Modifier.size(size).clip(CircleShape).pointerInput(icon) { detectTapGestures { onClick() } },
    )
}

@Composable
private fun Art(bmp: ImageBitmap?, size: Dp, shape: Shape, spinning: Boolean = false) {
    val angle = if (spinning && shape == CircleShape) {
        val t = rememberInfiniteTransition(label = "spin")
        t.animateFloat(0f, 360f, infiniteRepeatable(tween(9000, easing = LinearEasing)), label = "a").value
    } else 0f
    if (bmp != null) {
        Image(bmp, null, contentScale = ContentScale.Crop, modifier = Modifier.size(size).rotate(angle).clip(shape))
    } else {
        Box(Modifier.size(size).clip(shape).background(Color(0xFF2A2A30)), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.MusicNote, null, tint = Color(0xFFB4B4BC), modifier = Modifier.size(size * 0.55f))
        }
    }
}

@Composable
private fun Avatar(bmp: Bitmap?, size: Dp) {
    val img = remember(bmp) { bmp?.asImageBitmap() }
    if (img != null) {
        Image(img, null, contentScale = ContentScale.Crop, modifier = Modifier.size(size).clip(CircleShape))
    } else {
        Box(Modifier.size(size).clip(CircleShape).background(Color(0xFF3A3A3C)), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Call, null, tint = Color.White, modifier = Modifier.size(size * 0.45f))
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

/** Barrinhas de áudio animadas (paradas quando pausado). */
@Composable
private fun Bars(playing: Boolean, color: Color, modifier: Modifier) {
    var tick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(playing) {
        while (playing) {
            tick++
            delay(140)
        }
    }
    Row(modifier, horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        listOf(0.45f, 0.9f, 0.6f, 0.8f).forEachIndexed { i, base ->
            val f = if (playing) (0.3f + ((tick * (i + 2) * 37 + i * 53) % 70) / 100f) else base * 0.4f
            val bh by animateDpAsState((f.coerceIn(0.2f, 1f) * 18).dp, tween(130), label = "bar$i")
            Box(Modifier.width(3.dp).height(bh).clip(CircleShape).background(color))
        }
    }
}

/** Cor escura dominante de uma imagem, para o fundo automático. */
private fun darkColorOf(b: Bitmap): Color? = runCatching {
    val p = Palette.from(b).maximumColorCount(12).generate()
    val sw = p.darkMutedSwatch ?: p.darkVibrantSwatch ?: p.dominantSwatch ?: return null
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(sw.rgb, hsv)
    hsv[2] = hsv[2].coerceAtMost(0.32f)
    Color(android.graphics.Color.HSVToColor(hsv))
}.getOrNull()
