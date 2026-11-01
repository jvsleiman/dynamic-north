package com.joao.ilha

import androidx.compose.foundation.Image
import android.graphics.drawable.Icon
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Snooze
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.withFrameMillis
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
    LiveKind.Progress -> Icons.Rounded.Download
    LiveKind.Trip -> Icons.Rounded.DirectionsCar
    LiveKind.Workout -> Icons.AutoMirrored.Rounded.DirectionsRun
    LiveKind.Delivery -> Icons.Rounded.DeliveryDining
}

private fun LiveInfo.tint(): Color = when (kind) {
    LiveKind.Navigation -> NavColor
    LiveKind.Recording -> RecColor
    LiveKind.Progress -> CamColor
    LiveKind.Trip, LiveKind.Delivery -> if (color != 0) Color(color).copy(alpha = 1f) else NavColor
    LiveKind.Workout -> if (color != 0) Color(color).copy(alpha = 1f) else Color(0xFFFC4C02)
    else -> TimerColor
}

/** Tempo que aparece na ilha: conta sozinho quando o app manda a base do cronômetro. */
@Composable
private fun liveTime(l: LiveInfo): String {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(l.chronoBase) {
        // Acorda logo depois de cada virada de segundo, para o número trocar junto com o relógio.
        while (l.chronoBase > 0) {
            IslandBus.awaitScreenOn()
            now = System.currentTimeMillis()
            val untilNext = if (l.countDown) Math.floorMod(l.chronoBase - now, 1000L) else 1000 - Math.floorMod(now - l.chronoBase, 1000L)
            delay(untilNext.coerceAtLeast(1) + 5)
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
        when (l.kind) {
            // Gravação: ponto vermelho e tempo na esquerda, microfone na direita.
            LiveKind.Recording -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(RecColor))
                    Spacer(Modifier.width(6.dp))
                    Text(liveTime(l), color = col.text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
                Icon(Icons.Rounded.Mic, null, tint = col.text, modifier = Modifier.size((h - 14.dp) * ic))
            }
            LiveKind.Delivery -> CompactOrder(l, h)
            // Download: ícone do app e anel de progresso.
            LiveKind.Progress -> {
                AppGlyph(l, (h - 12.dp) * ic)
                ProgressRing(l.progress, CamColor, h - 12.dp, 2.5.dp)
            }
            LiveKind.Workout -> {
                Icon(l.vector(), null, tint = l.tint(), modifier = Modifier.size((h - 12.dp) * ic))
                Text(workoutMetrics(l).firstOrNull() ?: liveTime(l), color = l.tint(), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
            LiveKind.Trip -> {
                AppGlyph(l, (h - 12.dp) * ic)
                Text(shortTrip(l), color = col.text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            else -> {
                LiveIcon(l, (h - 14.dp) * ic)
                val right = if (l.kind == LiveKind.Navigation) l.title else liveTime(l)
                Text(
                    right, color = if (l.kind == LiveKind.Navigation) col.text else l.tint(),
                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** "Chega em 3 min" → "3 min"; senão o título inteiro. */
private fun shortTrip(l: LiveInfo): String =
    Regex("\\d+\\s*min").find("${l.title} ${l.text}")?.value ?: l.title.ifBlank { l.text }

/** Ícone do app (o pequeno, da barra de status) num quadradinho com a cor do app. */
@Composable
private fun AppGlyph(l: LiveInfo, size: Dp) {
    val ctx = LocalContext.current
    val bmp = remember(l.smallIcon) {
        l.smallIcon?.let { i -> runCatching { i.setTint(android.graphics.Color.WHITE); i.loadDrawable(ctx)?.toBitmapSafe()?.asImageBitmap() }.getOrNull() }
    }
    Box(Modifier.size(size).clip(RoundedCornerShape(size * 0.28f)).background(l.tint()), contentAlignment = Alignment.Center) {
        if (bmp != null) Image(bmp, null, modifier = Modifier.size(size * 0.62f))
        else Icon(l.vector(), null, tint = Color.White, modifier = Modifier.size(size * 0.62f))
    }
}

@Composable
internal fun ProgressRing(progress: Float, color: Color, size: Dp, stroke: Dp, done: Boolean = false) {
    // Sem porcentagem (preparando/instalando): um arco girando.
    val indeterminate = progress < 0f && !done
    // O sistema atualiza a barra mais ou menos a cada segundo: o anel anda contínuo até o novo valor.
    // Se o valor voltar (outro arquivo), pula direto em vez de girar para trás.
    val target = progress.coerceIn(0f, 1f)
    val anim = remember { androidx.compose.animation.core.Animatable(target) }
    LaunchedEffect(target) {
        if (target < anim.value - 0.02f) anim.snapTo(target) else anim.animateTo(target, tween(900, easing = androidx.compose.animation.core.LinearEasing))
    }
    var spin by remember { mutableFloatStateOf(0f) }
    if (indeterminate) LaunchedEffect(Unit) {
        val start = withFrameMillis { it }
        while (true) withFrameMillis { spin = ((it - start) % 1000L) / 1000f * 360f }
    }
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = stroke.toPx()
            drawCircle(color.copy(alpha = 0.25f), radius = (this.size.minDimension - w) / 2, style = Stroke(w))
            val start = if (indeterminate) spin - 90f else -90f
            val sweep = if (done) 360f else if (indeterminate) 90f else 360f * anim.value
            drawArc(color, start, sweep, false, topLeft = Offset(w / 2, w / 2), size = Size(this.size.width - w, this.size.height - w), style = Stroke(w, cap = StrokeCap.Round))
        }
        if (done) Icon(Icons.Rounded.Check, null, tint = color, modifier = Modifier.size(size * 0.6f))
    }
}

// ---------- ilha aberta ----------

@Composable
internal fun ExpandedLive(l: LiveInfo) {
    when (l.kind) {
        LiveKind.Navigation -> ExpandedNav(l)
        LiveKind.Progress -> ExpandedProgress(l)
        LiveKind.Trip -> ExpandedTrip(l)
        LiveKind.Delivery -> ExpandedOrder(l)
        LiveKind.Workout -> ExpandedWorkout(l)
        else -> ExpandedTimer(l)
    }
}

/** "2,35 km · 15:20 · 6'32\"/km" → partes; o tempo do cronômetro vem primeiro quando existe. */
@Composable
private fun workoutMetrics(l: LiveInfo): List<String> {
    val parts = "${l.text}".split(Regex("\\s*[·•|\\n]\\s*|,\\s+(?=\\d)")).map { it.trim() }.filter { it.isNotBlank() }
    return (if (l.chronoBase > 0) listOf(liveTime(l)) else emptyList()) + parts
}

/** Treino: logo do app, métricas em colunas e os botões do app (pausar em destaque). */
@Composable
private fun ExpandedWorkout(l: LiveInfo) {
    val col = LocalIslandColors.current
    val metrics = workoutMetrics(l).take(3)
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppGlyph(l, 26.dp)
            Spacer(Modifier.width(8.dp))
            Text(l.title.ifBlank { l.appName }, color = col.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            if (metrics.isEmpty()) Text(liveTime(l), color = l.tint(), fontSize = 28.sp, fontWeight = FontWeight.Bold)
            metrics.forEachIndexed { i, m ->
                Text(m, color = if (i == 0) l.tint() else col.text, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.weight(1f))
            }
        }
        if (l.actions.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                l.actions.forEach { a ->
                    val mapped = actionIcon(a.title)
                    val strong = mapped?.second == true
                    Box(
                        Modifier.height(40.dp).clip(RoundedCornerShape(20.dp)).background(if (strong) l.tint() else col.text.copy(alpha = 0.16f))
                            .pointerInput(a) { detectTapGestures { runCatching { a.intent.send() } } }
                            .padding(horizontal = if (mapped != null && !strong) 10.dp else 18.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (mapped != null) Icon(mapped.first, null, tint = if (strong) Color.White else col.text, modifier = Modifier.size(20.dp))
                            if (mapped == null || strong) {
                                if (mapped != null) Spacer(Modifier.width(6.dp))
                                Text(a.title, color = if (strong) Color.White else col.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpandedNav(l: LiveInfo) {
    val col = LocalIslandColors.current
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)).background(NavColor.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                LiveIcon(l, 40.dp)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(l.title, color = col.text, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(l.text, color = col.dim, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        LiveActions(l)
    }
}

/** Timer, cronômetro e gravação: botões redondos na esquerda e o tempo grande na direita. */
@Composable
private fun ExpandedTimer(l: LiveInfo) {
    val col = LocalIslandColors.current
    val icons = l.actions.map { actionIcon(it.title) }
    val round = l.actions.isNotEmpty() && icons.all { it != null }
    val label = when (l.kind) {
        LiveKind.Timer -> "Timer"
        LiveKind.Recording -> "Gravando"
        else -> "Cronômetro"
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 12.dp)) {
        if (l.kind == LiveKind.Recording) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(RecColor))
                Spacer(Modifier.width(6.dp))
                Text(liveTime(l), color = col.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically) {
            if (round) {
                l.actions.forEachIndexed { i, a ->
                    val (icon, strong) = icons[i]!!
                    val bg = if (strong) l.tint().copy(alpha = 0.3f) else col.text.copy(alpha = 0.16f)
                    Box(
                        Modifier.size(46.dp).clip(CircleShape).background(bg)
                            .pointerInput(a) { detectTapGestures { runCatching { a.intent.send() } } },
                        contentAlignment = Alignment.Center,
                    ) { Icon(icon, a.title, tint = if (strong) l.tint() else col.text, modifier = Modifier.size(24.dp)) }
                    Spacer(Modifier.width(10.dp))
                }
                Spacer(Modifier.weight(1f))
            } else {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(l.vector(), null, tint = l.tint(), modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(label, color = l.tint(), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                    val sub = listOf(l.title, l.text).firstOrNull { it.isNotBlank() && !timeLike.matches(it.trim()) }
                    if (sub != null) Text(sub, color = col.dim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (l.kind == LiveKind.Recording) {
                Text(l.title.ifBlank { label }, color = col.text, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            } else {
                Row(verticalAlignment = Alignment.Bottom) {
                    if (round) Text(label, color = l.tint(), fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(end = 6.dp, bottom = 8.dp))
                    Text(liveTime(l), color = l.tint(), fontSize = 40.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                }
            }
        }
        if (!round) LiveActions(l)
    }
}

@Composable
private fun ExpandedProgress(l: LiveInfo) {
    val col = LocalIslandColors.current
    Row(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        AppGlyph(l, 44.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(l.appName.ifBlank { "Baixando" }, color = col.dim, fontSize = 12.sp, maxLines = 1)
            Text(l.title.ifBlank { l.text }, color = col.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            // Texto do próprio sistema ("45 MB de 100 MB", "Instalando…").
            if (l.title.isNotBlank() && l.text.isNotBlank()) Text(l.text, color = col.dim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Box(contentAlignment = Alignment.Center) {
            ProgressRing(l.progress, CamColor, 46.dp, 3.5.dp)
            if (l.progress >= 0f) Text("${(l.progress.coerceIn(0f, 1f) * 100).toInt()}%", color = col.text, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Corrida ou entrega: logo do app, título grande e a foto (motorista, carro, pedido) na direita. */
@Composable
private fun ExpandedTrip(l: LiveInfo) {
    val col = LocalIslandColors.current
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                AppGlyph(l, 30.dp)
                Spacer(Modifier.height(8.dp))
                Text(l.title.ifBlank { l.appName }, color = col.text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (l.text.isNotBlank()) Text(l.text, color = col.dim, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            l.icon?.let { bmp ->
                Spacer(Modifier.width(12.dp))
                Image(bmp.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.size(84.dp).clip(RoundedCornerShape(18.dp)))
            }
        }
        LiveActions(l)
    }
}

/** Ícone para um botão de notificação pelo nome (pausar, parar, mudo…); true = destaque. */
internal fun actionIcon(title: String): Pair<ImageVector, Boolean>? {
    val t = title.lowercase()
    fun has(vararg w: String) = w.any { it in t }
    return when {
        has("desligar", "encerrar", "hang up", "end call", "sair da chamada", "leave") -> Icons.Rounded.CallEnd to true
        has("ativar som", "unmute", "desmutar") -> Icons.Rounded.MicOff to false
        has("mudo", "mute", "silenciar", "microfone", "mic") -> Icons.Rounded.Mic to false
        has("alto-falante", "alto falante", "viva-voz", "speaker") -> Icons.AutoMirrored.Rounded.VolumeUp to false
        has("vídeo", "video", "câmera", "camera") -> Icons.Rounded.Videocam to false
        has("retomar", "continuar", "resume", "iniciar", "start") -> Icons.Rounded.PlayArrow to true
        has("pausar", "pause") -> Icons.Rounded.Pause to true
        has("soneca", "snooze", "adiar") -> Icons.Rounded.Snooze to false
        has("volta", "lap") -> Icons.Rounded.Flag to false
        has("+1", "adicionar 1", "add 1") -> Icons.Rounded.Add to false
        has("redefinir", "zerar", "reset", "reiniciar") -> Icons.Rounded.Replay to false
        has("parar", "stop", "cancelar", "cancel", "excluir", "dispensar", "dismiss", "fechar", "close") -> Icons.Rounded.Close to false
        else -> null
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
