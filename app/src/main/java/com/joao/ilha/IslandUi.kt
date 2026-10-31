package com.joao.ilha

import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Build
import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BatteryStd
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.palette.graphics.Palette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign
import kotlin.math.roundToInt

private val Green = Color(0xFF34C759)
private val ListHeader = 46.dp
private val ListRow = 56.dp
private val Red = Color(0xFFFF3B30)

/** Cores da ilha (fundo e texto), escolhidas nos ajustes ou tiradas da capa/ícone. */
internal data class IslandColors(val bg: Color, val text: Color, val dim: Color)

/** Escala dos ícones e fotos escolhida em Personalizar. */
internal val LocalIconScale = compositionLocalOf { 1f }

internal val LocalIslandColors = compositionLocalOf { IslandColors(Color.Black, Color.White, Color(0xFFB4B4BC)) }

private enum class Mode(val large: Boolean) {
    Idle(false), Asleep(false), Media(false), Call(false), Charging(false), Bluetooth(false), SmallNotif(false),
    Notification(true), Test(true), IncomingCall(true),
    ExpandedCall(true), ExpandedMedia(true), ExpandedList(true), ExpandedBattery(true), ExpandedClock(true), ExpandedSystem(true),
    Reminder(true), ExpandedHealth(true), ExpandedBreathe(true),
    Live(false), AlarmRinging(true), ExpandedLive(true),
    Volume(false), ExpandedControls(true),
}

@Composable
fun IslandUi(state: IslandUiState, actions: IslandActions) {
    val s by Prefs.state.collectAsState()
    val popup by state.popup.collectAsState()
    val expanded by state.expanded.collectAsState()
    val kind by state.expandKind.collectAsState()
    val asleep by state.asleep.collectAsState()
    val media by IslandBus.media.collectAsState()
    val notifs by IslandBus.notifications.collectAsState()
    val call by IslandBus.call.collectAsState()
    val live by IslandBus.live.collectAsState()
    val cam by IslandBus.cameraInUse.collectAsState()
    val mic by IslandBus.micInUse.collectAsState()
    val privacy = s.privacyDot && (cam || mic)
    val screenW by state.screenWidthDp.collectAsState()
    val offsetX by state.offsetXPx.collectAsState()
    val focus by state.focus.collectAsState()
    val thread by state.thread.collectAsState()

    // Atividades ao mesmo tempo: a principal fica na ilha e a outra na bolinha.
    // Tocar na bolinha troca as duas (focus).
    val acts = buildList {
        if (call != null && s.showOngoingCall) add(Act.Call)
        if (live != null) add(Act.Live)
        if (media != null) add(Act.Media)
    }
    val main = focus?.takeIf { it in acts } ?: acts.firstOrNull()
    val second = if (s.dualIsland) acts.firstOrNull { it != main } else null

    val mode = when {
        call?.incoming == true -> Mode.IncomingCall
        live?.kind == LiveKind.Alarm -> Mode.AlarmRinging
        expanded && kind == ExpandKind.Activity && main == Act.Call -> Mode.ExpandedCall
        expanded && kind == ExpandKind.Activity && main == Act.Live -> Mode.ExpandedLive
        expanded && kind == ExpandKind.Activity && main == Act.Media -> Mode.ExpandedMedia
        expanded && call != null && kind == ExpandKind.Activity -> Mode.ExpandedCall
        expanded && kind == ExpandKind.Controls -> Mode.ExpandedControls
        expanded && kind == ExpandKind.Battery -> Mode.ExpandedBattery
        expanded && kind == ExpandKind.Clock -> Mode.ExpandedClock
        expanded && kind == ExpandKind.System -> Mode.ExpandedSystem
        expanded && kind == ExpandKind.Health -> Mode.ExpandedHealth
        expanded && kind == ExpandKind.Breathe -> Mode.ExpandedBreathe
        expanded && notifs.isNotEmpty() -> Mode.ExpandedList
        expanded && media != null -> Mode.ExpandedMedia
        expanded -> Mode.ExpandedBattery
        popup is Popup.Notification -> if (s.notifExpanded) Mode.Notification else Mode.SmallNotif
        popup is Popup.Charging -> Mode.Charging
        popup is Popup.Bluetooth -> Mode.Bluetooth
        popup is Popup.Test -> Mode.Test
        popup is Popup.Reminder -> Mode.Reminder
        popup is Popup.Volume -> Mode.Volume
        main == Act.Call -> Mode.Call
        main == Act.Live -> Mode.Live
        main == Act.Media -> Mode.Media
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

    val wide = if (s.fullWidth) (screenW - 2 * s.sideMargin).dp else minOf(screenW - 16, s.largeMaxWidth).dp
    val idleHasContent = privacy || when (s.idleStyle) {
        IdleStyle.Simple -> false
        IdleStyle.AppIcons, IdleStyle.Count -> notifs.isNotEmpty()
        else -> true
    }
    val baseW = s.width.dp
    val baseH = s.height.dp
    val grow = if (s.growWhenActive) baseH * 2 + 12.dp else 0.dp
    val smallR = baseH * (s.smallRadius / 100f)
    val k = s.layoutScale / 100f
    val t = s.textScale / 100f
    val popupItem = (popup as? Popup.Notification)?.item
    val actionsH = if (s.showNotifActions && popupItem?.actions?.isNotEmpty() == true) 46.dp else 0.dp
    val rows = notifs.size.coerceIn(1, s.listRows)
    fun sizeFor(m: Mode): Pair<Dp, Dp> = when (m) {
        Mode.Idle -> (if (idleHasContent) baseW + baseH * 2 + 12.dp else baseW) to baseH
        Mode.Asleep -> (if (s.keepMiniWhenHidden) baseH else 1.dp) to baseH
        Mode.Media, Mode.Call -> baseW + grow to baseH
        Mode.Charging -> baseW + grow + 24.dp to baseH
        Mode.Bluetooth -> baseW + grow + 48.dp to baseH
        Mode.SmallNotif -> minOf(wide, baseW + grow + 120.dp) to baseH
        Mode.Notification -> wide to 78.dp * k * t + actionsH +
            (if (thread) ThreadRow * t * (popupItem?.messages?.size ?: 0).coerceIn(1, 6) + 8.dp else 0.dp)
        Mode.Test -> wide to 78.dp * k * t
        Mode.IncomingCall -> wide to 84.dp * k
        Mode.ExpandedCall -> wide to 132.dp * k
        Mode.ExpandedMedia -> wide to (196.dp + (if (s.volumeInPlayer) 34.dp else 0.dp)) * k * t
        Mode.ExpandedList -> wide to (ListHeader + ListRow * rows) * t + 14.dp
        Mode.ExpandedBattery -> wide to 112.dp * k
        Mode.ExpandedClock -> wide to 132.dp * k * t
        Mode.ExpandedSystem -> wide to 150.dp * k * t
        Mode.Reminder -> wide to 78.dp * k * t
        Mode.Volume -> baseW + grow + 90.dp to baseH
        Mode.ExpandedControls -> wide to controlsHeight(s.controlsApps.isNotEmpty()) * k
        Mode.Live -> baseW + grow + (if (live?.kind == LiveKind.Navigation) 64.dp else 28.dp) to baseH
        Mode.AlarmRinging -> wide to (if (live?.actions.isNullOrEmpty()) 70.dp else 104.dp) * k * t
        Mode.ExpandedLive -> wide to (if (live?.actions.isNullOrEmpty()) 92.dp else 132.dp) * k * t
        Mode.ExpandedHealth -> wide to 158.dp * k * t
        Mode.ExpandedBreathe -> wide to 168.dp * k
    }
    val (tw, th) = sizeFor(mode)
    val tr = if (mode.large) s.largeRadius.dp else smallR
    val springy = spring<Dp>(dampingRatio = s.motion.damping, stiffness = s.motion.stiffness)
    // A mola passa um pouco do valor final; nada aqui pode ficar negativo (o Compose fecha o app).
    val wAnim by animateDpAsState(tw, springy, label = "w")
    val hAnim by animateDpAsState(th, springy, label = "h")
    val rAnim by animateDpAsState(tr, springy, label = "r")
    val w = wAnim.coerceAtLeast(1.dp)
    val h = hAnim.coerceAtLeast(1.dp)
    val r = rAnim.coerceAtLeast(0.dp)
    // Aberta, a ilha desce um pouco para não ficar colada na câmera.
    val gapAnim by animateDpAsState(if (mode.large) s.largeTopGap.dp else 0.dp, springy, label = "gap")
    val gap = gapAnim.coerceAtLeast(0.dp)

    // Segunda ilha (bolinha): a outra atividade, ou a última notificação por alguns segundos.
    val compactMain = mode == Mode.Call || mode == Mode.Live || mode == Mode.Media
    val newestNotif = notifs.firstOrNull()
    var notifBubbleKey by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(newestNotif?.key, newestNotif?.postedAt) {
        notifBubbleKey = newestNotif?.key
        if (newestNotif != null) {
            delay(8_000)
            notifBubbleKey = null
        }
    }
    val bubbleAct = if (compactMain) second else null
    val secondNotif = if (s.dualIsland && compactMain && bubbleAct == null) newestNotif?.takeIf { it.key == notifBubbleKey } else null
    val hasSecond = bubbleAct != null || secondNotif != null
    val bubble = baseH
    val bubbleContent: @Composable () -> Unit = {
        when (bubbleAct) {
            Act.Media -> Art(media?.art?.asImageBitmap(), baseH - 8.dp, CircleShape)
            Act.Call -> Icon(Icons.Rounded.Call, null, tint = Green, modifier = Modifier.size(baseH * 0.5f))
            Act.Live -> live?.let { CompactLiveIcon(it, baseH * 0.55f) }
            null -> secondNotif?.let { NotifPicture(it, baseH - 8.dp) }
        }
    }
    val onBubble = { actions.onBubble(bubbleAct) }

    // Arrastar para o lado: a notificação acompanha o dedo e fecha ao soltar longe ou rápido.
    val swipeable = mode == Mode.Notification || mode == Mode.SmallNotif
    val drag = remember { Animatable(0f) }
    val dragScope = rememberCoroutineScope()
    // Ilha mudou de modo (fechou ou chegou outra coisa): volta para o lugar na hora, sem deslizar de volta.
    LaunchedEffect(mode) { drag.snapTo(0f) }

    val density = LocalDensity.current
    // Área de toque = ilha + bolinhas. A janela desenhada é fixa, então isso não aparece na tela.
    val touchW = tw + 4.dp + if (hasSecond) (bubble + 6.dp) * 2 else 0.dp
    val touchH = th + 4.dp + if (mode.large) s.largeTopGap.dp else 0.dp
    val targetW = with(density) { touchW.roundToPx() }
    val targetH = with(density) { touchH.roundToPx() }
    LaunchedEffect(targetW, targetH) { actions.onSize(targetW, targetH) }
    val islandDensity = remember(density.density, s.textScale) { Density(density.density, s.textScale / 100f) }
    CompositionLocalProvider(LocalIslandColors provides colors, LocalDensity provides islandDensity, LocalIconScale provides s.iconScale / 100f) {
        // A janela desenhada ocupa a largura toda; a ilha fica no centro (ou alinhada à câmera).
        Box(Modifier.fillMaxWidth().offset { IntOffset(offsetX, 0) }, contentAlignment = Alignment.TopCenter) {
        Row(Modifier.padding(top = gap), verticalAlignment = Alignment.Top) {
            // Espaço igual do outro lado para a ilha principal continuar centralizada.
            if (hasSecond && s.dualOnRight) Spacer(Modifier.width(bubble + 6.dp))
            if (hasSecond && !s.dualOnRight) {
                Bubble(bubble, colors.bg, onBubble, bubbleContent)
                Spacer(Modifier.width(6.dp))
            }
            Island(
                w, h, r, mode, s,
                modifier = Modifier
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { actions.onTap() }, onLongPress = { actions.onHold() })
                    }
                    // Fica por fora do deslocamento, para o arraste não brigar com o próprio movimento.
                    .pointerInput(swipeable) {
                        val tracker = VelocityTracker()
                        var dx = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { tracker.resetTracking(); dx = 0f },
                            onDragEnd = {
                                val v = tracker.calculateVelocity().x
                                val far = abs(dx) > (if (swipeable) 48.dp else 60.dp).toPx()
                                val fling = abs(v) > 900f && (dx == 0f || sign(v) == sign(dx))
                                val dir = if (dx != 0f) dx else v
                                dragScope.launch {
                                    if (!(far || fling)) drag.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = 600f))
                                    else if (swipeable) {
                                        // Sai pela lateral e só então fecha.
                                        drag.animateTo(sign(dir) * size.width * 1.2f, tween(150))
                                        actions.onSwipe(dir)
                                        delay(700)
                                        drag.snapTo(0f)
                                    } else {
                                        actions.onSwipe(dir)
                                        drag.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = 600f))
                                    }
                                }
                            },
                            onDragCancel = { dragScope.launch { drag.animateTo(0f) } },
                            onHorizontalDrag = { ch, d ->
                                ch.consume()
                                tracker.addPosition(ch.uptimeMillis, ch.position)
                                dx += d
                                dragScope.launch { drag.snapTo(dx) }
                            },
                        )
                    }
                    .graphicsLayer {
                        // Notificação segue o dedo e vai sumindo; o resto só cede um pouco.
                        val x = drag.value
                        translationX = if (swipeable) x else x * 0.25f
                        if (swipeable) alpha = (1f - abs(x) / (size.width * 0.9f)).coerceIn(0f, 1f)
                    },
            ) {
                AnimatedContent(
                    targetState = mode,
                    // Sem animação de tamanho própria: quem anima o tamanho é a ilha (senão as duas brigam).
                    // Ao fechar, o conteúdo grande some devagar enquanto a ilha encolhe (sem quadro preto vazio).
                    transitionSpec = {
                        val closing = initialState.large && !targetState.large
                        val spec = if (closing) fadeIn(tween(160, 140)) togetherWith fadeOut(tween(220))
                        else fadeIn(tween(200, 70)) togetherWith fadeOut(tween(80))
                        spec.using(null)
                    },
                    modifier = Modifier.fillMaxSize(),
                    label = "content",
                ) { m ->
                    // O conteúdo já nasce no tamanho final e a ilha vai revelando (nada fica espremido).
                    // Se a mola passar do tamanho final, o conteúdo acompanha (sem faixa preta na borda).
                    val (cw, ch) = sizeFor(m)
                    Box(Modifier.requiredSize(if (m == mode) maxOf(cw, w) else cw, if (m == mode) maxOf(ch, h) else ch)) {
                    when (m) {
                        Mode.Idle -> IdleContent(s.idleStyle, notifs, baseH, privacy)
                        Mode.Asleep -> Box(Modifier.fillMaxSize())
                        Mode.Media -> media?.let { CompactMedia(it, baseH) }
                        Mode.Call -> call?.let { CompactCall(it, Color(s.callWaveColor)) }
                        Mode.Charging -> (popup as? Popup.Charging)?.let { CompactCharging(it.info, baseH) }
                        Mode.Bluetooth -> (popup as? Popup.Bluetooth)?.let { CompactBluetooth(it.info, baseH) }
                        Mode.SmallNotif -> (popup as? Popup.Notification)?.let { SmallNotification(it.item, baseH) }
                        Mode.Notification -> (popup as? Popup.Notification)?.let { NotificationBanner(it.item, s.showNotifActions, thread, actions) }
                        Mode.Test -> TestBanner()
                        Mode.IncomingCall -> call?.let { IncomingCall(it) }
                        Mode.ExpandedCall -> call?.let { ExpandedCall(it, actions) }
                        Mode.ExpandedMedia -> media?.let { ExpandedMedia(it, s, actions) }
                        Mode.ExpandedList -> NotificationList(notifs, actions)
                        Mode.ExpandedBattery -> BatteryCard()
                        Mode.ExpandedClock -> ClockCard()
                        Mode.ExpandedSystem -> SystemCard()
                        Mode.Reminder -> (popup as? Popup.Reminder)?.let { ReminderBanner(it.kind) }
                        Mode.ExpandedHealth -> HealthCard(s)
                        Mode.ExpandedBreathe -> BreatheCard()
                        Mode.Live -> live?.let { CompactLive(it, baseH) }
                        Mode.Volume -> (popup as? Popup.Volume)?.let { VolumePopup(it.level, it.max, it.ring, baseH) }
                        Mode.ExpandedControls -> ControlsCard(s, actions)
                        Mode.AlarmRinging -> live?.let { AlarmRinging(it) }
                        Mode.ExpandedLive -> live?.let { ExpandedLive(it) }
                    }
                    }
                }
            }
            if (hasSecond && s.dualOnRight) {
                Spacer(Modifier.width(6.dp))
                Bubble(bubble, colors.bg, onBubble, bubbleContent)
            }
            if (hasSecond && !s.dualOnRight) Spacer(Modifier.width(bubble + 6.dp))
        }
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
private fun Bubble(size: Dp, bg: Color, onTap: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier.padding(top = 2.dp).size(size).clip(CircleShape).background(bg)
            .pointerInput(onTap) { detectTapGestures { onTap() } },
        contentAlignment = Alignment.Center,
    ) { content() }
}

// ---------- modos pequenos ----------

/** Ilha parada: o meio fica livre para a câmera; o conteúdo vai nas pontas. */
@Composable
private fun IdleContent(style: IdleStyle, notifs: List<NotifItem>, h: Dp, privacy: Boolean) {
    val col = LocalIslandColors.current
    val ic = LocalIconScale.current
    val ctx = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var battery by remember { mutableIntStateOf(batteryNow(ctx)) }
    var netSpeed by remember { mutableLongStateOf(0L) }
    LaunchedEffect(style) {
        val live = style != IdleStyle.Simple && style != IdleStyle.AppIcons && style != IdleStyle.Count && style != IdleStyle.Pulse
        while (live) {
            now = System.currentTimeMillis()
            battery = batteryNow(ctx)
            if (style == IdleStyle.NetSpeed) netSpeed = NetMeter.sample()
            // Acorda na virada do minuto (a hora troca junto com a do sistema).
            delay(if (style == IdleStyle.NetSpeed) 1_000 else 60_000 - System.currentTimeMillis() % 60_000 + 20)
        }
    }
    val apps = remember(notifs) { notifs.distinctBy { it.pkg } }
    val time = remember(now) { java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(now)) }
    val date = remember(now) {
        java.text.SimpleDateFormat("EEE d", java.util.Locale("pt", "BR")).format(java.util.Date(now)).replace(".", "")
    }
    val iconSize = (h - 14.dp) * ic
    val timeText = @Composable { Text(time, color = col.text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
    Row(
        Modifier.fillMaxSize().padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // Ponta esquerda
        Box {
            when (style) {
                IdleStyle.AppIcons -> apps.getOrNull(0)?.let { AppIconBadge(it, iconSize) }
                IdleStyle.Count -> if (notifs.isNotEmpty()) Icon(Icons.Rounded.Notifications, null, tint = col.dim, modifier = Modifier.size((h - 18.dp) * ic))
                IdleStyle.Clock, IdleStyle.ClockBattery -> timeText()
                IdleStyle.Date -> Text(date, color = col.text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                IdleStyle.NetSpeed -> Icon(Icons.Rounded.SwapVert, null, tint = Color(0xFF64D2FF), modifier = Modifier.size((h - 18.dp) * ic))
                IdleStyle.Memory -> Icon(Icons.Rounded.Memory, null, tint = Color(0xFFBF5AF2), modifier = Modifier.size((h - 18.dp) * ic))
                IdleStyle.Storage -> Icon(Icons.Rounded.Storage, null, tint = Color(0xFFFF9F0A), modifier = Modifier.size((h - 18.dp) * ic))
                IdleStyle.Alarm -> Icon(Icons.Rounded.Alarm, null, tint = Color(0xFFFF9F0A), modifier = Modifier.size((h - 18.dp) * ic))
                IdleStyle.Greeting -> Text(greetingEmoji(now), fontSize = 12.sp)
                IdleStyle.Health, IdleStyle.Steps, IdleStyle.Water, IdleStyle.ScreenBreak, IdleStyle.Breathing, IdleStyle.Bedtime ->
                    HealthIdleLeft(style, (h - 18.dp) * ic)
                else -> {}
            }
        }
        // Ponta direita (com o pontinho de câmera/microfone na frente, se algum app estiver usando)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (privacy) {
                PrivacyDots()
                if (style != IdleStyle.Simple) Spacer(Modifier.width(4.dp))
            }
            when (style) {
                IdleStyle.AppIcons -> when {
                    apps.size > 2 -> Text("+${apps.size - 1}", color = col.dim, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    apps.size == 2 -> AppIconBadge(apps[1], iconSize)
                }
                IdleStyle.Count -> if (notifs.isNotEmpty()) Text(notifs.size.toString(), color = col.text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                IdleStyle.Battery, IdleStyle.ClockBattery -> BatteryText(battery, h)
                IdleStyle.NetSpeed -> Text(formatSpeed(netSpeed), color = col.text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                IdleStyle.Memory -> Text("${remember(now) { SystemInfo.freeRamPercent(ctx) }}%", color = col.text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                IdleStyle.Storage -> Text("${remember(now) { SystemInfo.freeStorageGb() }} GB", color = col.text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                IdleStyle.Alarm -> Text(remember(now) { SystemInfo.nextAlarm(ctx) ?: "—" }, color = col.text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                IdleStyle.Greeting -> Text(greeting(now), color = col.text, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                IdleStyle.Pulse -> PulseDot()
                IdleStyle.Health, IdleStyle.Steps, IdleStyle.Water, IdleStyle.ScreenBreak, IdleStyle.Breathing, IdleStyle.Bedtime ->
                    HealthIdleRight(style, now, h)
                else -> {}
            }
        }
    }
}

/** Pontinho que acende e apaga devagar, para a ilha parada não parecer "morta". */
@Composable
private fun PulseDot() {
    val tr = rememberInfiniteTransition(label = "pulse")
    val a by tr.animateFloat(0.25f, 1f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "a")
    val sz by tr.animateFloat(6f, 10f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "s")
    Box(Modifier.size(sz.dp).alpha(a).clip(CircleShape).background(Green))
}

private fun greeting(now: Long): String = when (java.util.Calendar.getInstance().apply { timeInMillis = now }.get(java.util.Calendar.HOUR_OF_DAY)) {
    in 5..11 -> "Bom dia"
    in 12..17 -> "Boa tarde"
    else -> "Boa noite"
}

private fun greetingEmoji(now: Long): String = when (java.util.Calendar.getInstance().apply { timeInMillis = now }.get(java.util.Calendar.HOUR_OF_DAY)) {
    in 5..11 -> "☀️"
    in 12..17 -> "🌤️"
    else -> "🌙"
}

private fun formatSpeed(bps: Long): String = when {
    bps >= 1_000_000 -> "%.1f MB/s".format(bps / 1_000_000f)
    bps >= 1_000 -> "${bps / 1_000} KB/s"
    else -> "$bps B/s"
}

/** Ilha aberta a partir de Internet/Memória/Armazenamento. */
@Composable
private fun SystemCard() {
    val col = LocalIslandColors.current
    val ctx = LocalContext.current
    var speed by remember { mutableLongStateOf(0L) }
    var tick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            speed = NetMeter.sample()
            tick++
            delay(1000)
        }
    }
    val ram = remember(tick / 5) { SystemInfo.freeRamPercent(ctx) }
    val storage = remember(tick / 30) { SystemInfo.freeStorageGb() }
    val battery = remember(tick / 30) { batteryNow(ctx) }
    Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        Stat(Icons.Rounded.SwapVert, Color(0xFF64D2FF), formatSpeed(speed), "Internet")
        Stat(Icons.Rounded.Memory, Color(0xFFBF5AF2), "$ram%", "RAM livre")
        Stat(Icons.Rounded.Storage, Color(0xFFFF9F0A), "$storage GB", "Livre")
        Stat(Icons.Rounded.BatteryStd, Green, "$battery%", "Bateria")
    }
}

@Composable
private fun Stat(icon: ImageVector, tint: Color, value: String, label: String) {
    val col = LocalIslandColors.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(tint.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(value, color = col.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Text(label, color = col.dim, fontSize = 11.sp, maxLines = 1)
    }
}

@Composable
private fun BatteryText(level: Int, h: Dp) {
    val col = LocalIslandColors.current
    val c = if (level <= 15) Red else col.text
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("$level%", color = c, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(3.dp))
        BatteryGlyph(level, if (level <= 15) Red else Green, Modifier, h * 0.8f)
    }
}

/** Ícone do app (o ícone colorido do launcher, não o da notificação). */
@Composable
private fun AppIconBadge(n: NotifItem, size: Dp) {
    val ctx = LocalContext.current
    val img = remember(n.pkg) {
        runCatching { ctx.packageManager.getApplicationIcon(n.pkg).toBitmapSafe()?.asImageBitmap() }.getOrNull()
    }
    if (img != null) Image(img, null, modifier = Modifier.size(size).clip(CircleShape))
    else NotifPicture(n, size)
}

private fun batteryNow(ctx: android.content.Context): Int =
    (ctx.getSystemService(android.content.Context.BATTERY_SERVICE) as BatteryManager).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

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

private val ThreadRow = 38.dp

@Composable
private fun NotificationBanner(n: NotifItem, showActions: Boolean, thread: Boolean, actions: IslandActions) {
    val col = LocalIslandColors.current
    val ic = LocalIconScale.current
    // A setinha aparece quando a conversa tem mais do que a mensagem que já está na tela.
    val hasMore = n.messages.size > 1
    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp)) {
        Row(
            Modifier.fillMaxWidth().then(if (thread) Modifier.height(50.dp) else Modifier.weight(1f)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BadgedPicture(n, 46.dp * ic)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(n.title, color = col.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (thread) Text("${n.messages.size} mensagens · ${n.appName}", color = col.dim, fontSize = 12.sp, maxLines = 1)
                else if (n.text.isNotBlank()) Text(n.text, color = col.dim, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (hasMore) {
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(col.text.copy(alpha = 0.14f))
                        .pointerInput(thread) { detectTapGestures { actions.onThread(!thread) } },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(if (thread) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, "Ver mensagens", tint = col.text, modifier = Modifier.size(22.dp))
                }
            }
        }
        if (thread) {
            val shown = n.messages.takeLast(6)
            val group = shown.map { it.sender }.filter { it.isNotBlank() }.distinct().size > 1
            Column(Modifier.fillMaxWidth().weight(1f).padding(top = 4.dp)) {
                shown.forEach { m ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .height(ThreadRow)
                            .pointerInput(n.key) { detectTapGestures { runCatching { n.contentIntent?.send() }; actions.onThread(false) } },
                        verticalArrangement = Arrangement.Center,
                    ) {
                        if (group && m.sender.isNotBlank()) Text(m.sender, color = col.dim, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(m.text, color = col.text, fontSize = 13.sp, maxLines = if (group) 1 else 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        if (showActions && n.actions.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                n.actions.forEach { a ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(34.dp)
                            .clip(RoundedCornerShape(17.dp))
                            .background(col.text.copy(alpha = 0.14f))
                            .pointerInput(a) { detectTapGestures { actions.onAction(n, a) } },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(a.title, color = col.text, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

/** Foto de quem mandou, com o ícone do app bem pequeno no canto de baixo. */
@Composable
private fun BadgedPicture(n: NotifItem, size: Dp) {
    Box(Modifier.size(size)) {
        NotifPicture(n, size)
        if (n.largeIcon != null) {
            val badge = size * 0.38f
            val bg = if (n.color != 0) Color(n.color).copy(alpha = 1f) else Color(0xFF2A2A30)
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 2.dp, y = 2.dp)
                    .size(badge)
                    .clip(CircleShape)
                    .background(LocalIslandColors.current.bg)
                    .padding(1.5.dp)
                    .clip(CircleShape)
                    .background(bg),
                contentAlignment = Alignment.Center,
            ) { SmallIcon(n.smallIcon, badge * 0.62f) }
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
private fun ExpandedMedia(m: MediaInfo, s: Settings, actions: IslandActions) {
    val col = LocalIslandColors.current
    val controls = m.controller.transportControls
    Box(Modifier.fillMaxSize()) {
        val art = remember(m.art) { m.art?.asImageBitmap() }
        if (s.blurArt && art != null) {
            Image(
                art, null, contentScale = ContentScale.Crop,
                // Ampliada e com borda "solta": o desfoque não escurece as beiradas (era a borda preta).
                modifier = Modifier.fillMaxSize()
                    .graphicsLayer { scaleX = 1.3f; scaleY = 1.3f }
                    .alpha(if (Build.VERSION.SDK_INT >= 31) 0.55f else 0.25f)
                    .then(if (Build.VERSION.SDK_INT >= 31) Modifier.blur(28.dp, BlurredEdgeTreatment.Unbounded) else Modifier),
            )
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))
        }
        Column(Modifier.fillMaxSize().padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.pointerInput(m.pkg) { detectTapGestures { actions.onOpen(m.pkg) } }) {
                    Art(art, 58.dp * LocalIconScale.current, RoundedCornerShape(14.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(m.title, color = col.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(m.artist.ifBlank { m.appName }, color = col.dim, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Bars(m.playing, Green, Modifier.size(22.dp))
            }
            Spacer(Modifier.height(14.dp))
            Progress(m)
            if (s.volumeInPlayer) {
                Spacer(Modifier.height(8.dp))
                VolumeBar()
            }
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
    // Enquanto arrasta, mostra a posição do dedo; ao soltar, pula para ela.
    var dragFrac by remember { mutableStateOf<Float?>(null) }
    val frac = dragFrac ?: if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(fmt(if (dragFrac != null) (frac * dur).toLong() else pos), color = col.dim, fontSize = 11.sp)
        Box(
            Modifier
                .weight(1f)
                .padding(horizontal = 10.dp)
                .height(22.dp)
                .pointerInput(dur) {
                    detectTapGestures { o -> if (dur > 0) m.controller.transportControls.seekTo((o.x / size.width * dur).toLong()) }
                }
                .pointerInput(dur) {
                    detectHorizontalDragGestures(
                        onDragStart = { o -> dragFrac = (o.x / size.width).coerceIn(0f, 1f) },
                        onDragEnd = {
                            dragFrac?.let { f -> if (dur > 0) m.controller.transportControls.seekTo((f * dur).toLong()) }
                            dragFrac = null
                        },
                        onDragCancel = { dragFrac = null },
                        onHorizontalDrag = { ch, _ -> ch.consume(); dragFrac = (ch.position.x / size.width).coerceIn(0f, 1f) },
                    )
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            val barH = if (dragFrac != null) 8.dp else 4.dp
            Box(Modifier.fillMaxWidth().height(barH).clip(CircleShape).background(col.text.copy(alpha = 0.2f))) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(frac).background(col.text))
            }
        }
        Text(if (dur > 0) fmt(dur) else "--:--", color = col.dim, fontSize = 11.sp)
    }
}

/** Volume da música, arrastando ou tocando na barra. */
@Composable
private fun VolumeBar() {
    val col = LocalIslandColors.current
    val ctx = LocalContext.current
    val am = remember { ctx.getSystemService(android.content.Context.AUDIO_SERVICE) as AudioManager }
    val max = remember { am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var vol by remember { mutableIntStateOf(am.getStreamVolume(AudioManager.STREAM_MUSIC)) }
    fun set(f: Float) {
        val v = (f.coerceIn(0f, 1f) * max).roundToInt()
        if (v != vol) {
            vol = v
            runCatching { am.setStreamVolume(AudioManager.STREAM_MUSIC, v, 0) }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.AutoMirrored.Rounded.VolumeDown, null, tint = col.dim, modifier = Modifier.size(18.dp))
        Box(
            Modifier
                .weight(1f)
                .padding(horizontal = 10.dp)
                .height(22.dp)
                .pointerInput(Unit) { detectTapGestures { o -> set(o.x / size.width) } }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { ch, _ -> ch.consume(); set(ch.position.x / size.width) }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(col.text.copy(alpha = 0.2f))) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(vol.toFloat() / max).background(col.text))
            }
        }
        Icon(Icons.AutoMirrored.Rounded.VolumeUp, null, tint = col.dim, modifier = Modifier.size(18.dp))
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
            // Troca o número logo depois da virada de cada segundo da chamada.
            delay(1000 - Math.floorMod(now - startedAt, 1000L) + 5)
        }
    }
    Text(fmt(now - startedAt), color = color, fontSize = size.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun NotificationList(list: List<NotifItem>, actions: IslandActions) {
    val col = LocalIslandColors.current
    val ic = LocalIconScale.current
    Column(Modifier.fillMaxSize().padding(bottom = 8.dp)) {
        Row(Modifier.fillMaxWidth().height(ListHeader * LocalDensity.current.fontScale).padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Notificações", color = col.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(
                "Limpar tudo", color = Color(0xFF0A84FF), fontSize = 13.sp,
                modifier = Modifier.pointerInput(Unit) { detectTapGestures { IslandBus.dismiss?.invoke(null); actions.onCollapse() } },
            )
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(list.take(50), key = { it.key }) { n -> SwipeRow(n, ic, actions) }
        }
    }
}

/** Linha da lista: toque abre, deslizar para o lado apaga. */
@Composable
private fun SwipeRow(n: NotifItem, ic: Float, actions: IslandActions) {
    val col = LocalIslandColors.current
    var dx by remember(n.key) { mutableFloatStateOf(0f) }
    val shown by animateFloatAsState(dx, label = "dx")
    Row(
        Modifier
            .fillMaxWidth()
            .height(ListRow * LocalDensity.current.fontScale)
            .offset { IntOffset(shown.toInt(), 0) }
            .alpha((1f - abs(shown) / 600f).coerceIn(0.2f, 1f))
            .pointerInput(n.key) { detectTapGestures { runCatching { n.contentIntent?.send() }; actions.onCollapse() } }
            .pointerInput(n.key) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (abs(dx) > 90.dp.toPx() && n.clearable) {
                            dx = if (dx > 0) 1200f else -1200f
                            actions.onDismiss(n.key)
                        } else dx = 0f
                    },
                    onDragCancel = { dx = 0f },
                    onHorizontalDrag = { ch, d -> ch.consume(); dx += d },
                )
            }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NotifPicture(n, 38.dp * ic)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(n.title, color = col.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(n.text.ifBlank { n.appName }, color = col.dim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Ilha aberta a partir do estilo Hora/Data: relógio grande, data, próximo alarme e bateria. */
@Composable
private fun ClockCard() {
    val col = LocalIslandColors.current
    val ctx = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val br = java.util.Locale("pt", "BR")
    val time = java.text.SimpleDateFormat("HH:mm", br).format(java.util.Date(now))
    val date = java.text.SimpleDateFormat("EEEE, d 'de' MMMM", br).format(java.util.Date(now)).replaceFirstChar { it.uppercase() }
    val alarm = remember {
        (ctx.getSystemService(android.content.Context.ALARM_SERVICE) as android.app.AlarmManager).nextAlarmClock?.triggerTime
    }
    val battery = batteryNow(ctx)
    Row(Modifier.fillMaxSize().padding(horizontal = 22.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(time, color = col.text, fontSize = 44.sp, fontWeight = FontWeight.Bold)
            Text(date, color = col.dim, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment = Alignment.End) {
            if (alarm != null) {
                Text("Alarme", color = col.dim, fontSize = 11.sp)
                Text(java.text.SimpleDateFormat("EEE HH:mm", br).format(java.util.Date(alarm)).replace(".", ""), color = col.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
            }
            BatteryText(battery, 30.dp)
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

/** Barrinhas de áudio: seguem o som de verdade quando o Visualizer está ligado; senão, animação. */
@Composable
private fun Bars(playing: Boolean, color: Color, modifier: Modifier) {
    val real by AudioLevels.active.collectAsState()
    val levels by AudioLevels.levels.collectAsState()
    var tick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(playing, real) {
        while (playing && !real) {
            tick++
            delay(140)
        }
    }
    Row(modifier, horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        listOf(0.45f, 0.9f, 0.6f, 0.8f).forEachIndexed { i, base ->
            val f = when {
                !playing -> base * 0.4f
                real -> 0.15f + levels.getOrElse(i) { 0f } * 0.85f
                else -> 0.3f + ((tick * (i + 2) * 37 + i * 53) % 70) / 100f
            }
            val bh by animateDpAsState((f.coerceIn(0.12f, 1f) * 18).dp, tween(if (real) 70 else 130), label = "bar$i")
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
