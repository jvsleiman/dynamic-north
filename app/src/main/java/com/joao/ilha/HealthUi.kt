package com.joao.ilha

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

internal val StepColor = Color(0xFF30D158)
internal val WaterColor = Color(0xFF64D2FF)
internal val EyeColor = Color(0xFFFFD60A)
internal val BreathColor = Color(0xFF66D4CF)
internal val SleepColor = Color(0xFFBF5AF2)
private val Late = Color(0xFFFF9F0A)

// ---------- ilha parada ----------

@Composable
internal fun HealthIdleLeft(style: IdleStyle, size: Dp) {
    val s by Prefs.state.collectAsState()
    val steps by Health.steps.collectAsState()
    when (style) {
        IdleStyle.Health -> Ring(steps / s.stepGoal.toFloat(), StepColor, size + 4.dp, 2.5.dp) {
            Icon(Icons.AutoMirrored.Rounded.DirectionsWalk, null, tint = StepColor, modifier = Modifier.size(size - 6.dp))
        }
        IdleStyle.Steps -> Icon(Icons.AutoMirrored.Rounded.DirectionsWalk, null, tint = StepColor, modifier = Modifier.size(size))
        IdleStyle.Water -> Icon(Icons.Rounded.WaterDrop, null, tint = WaterColor, modifier = Modifier.size(size))
        IdleStyle.ScreenBreak -> Icon(Icons.Rounded.Visibility, null, tint = EyeColor, modifier = Modifier.size(size))
        IdleStyle.Breathing -> Icon(Icons.Rounded.Air, null, tint = BreathColor, modifier = Modifier.size(size))
        IdleStyle.Bedtime -> Icon(Icons.Rounded.Bedtime, null, tint = SleepColor, modifier = Modifier.size(size))
        else -> {}
    }
}

@Composable
internal fun HealthIdleRight(style: IdleStyle, now: Long, h: Dp) {
    val col = LocalIslandColors.current
    val s by Prefs.state.collectAsState()
    val steps by Health.steps.collectAsState()
    val water by Health.water.collectAsState()
    val since by Health.usingSince.collectAsState()
    val small: @Composable (String, Color) -> Unit = { text, color -> Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) }
    when (style) {
        IdleStyle.Health -> Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.WaterDrop, null, tint = WaterColor, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(2.dp))
            small("$water", col.text)
        }
        IdleStyle.Steps -> small(shortSteps(steps), col.text)
        IdleStyle.Water -> small("$water/${s.waterGoal}", col.text)
        IdleStyle.ScreenBreak -> {
            val m = remember(now, since) { Health.minutesUsing(now) }
            small(formatMinutes(m), if (m >= s.breakMinutes) Late else col.text)
        }
        IdleStyle.Breathing -> BreathDot(h * 0.42f)
        IdleStyle.Bedtime -> small(remember(now, s.bedtime) { formatMinutes(Health.minutesToBed(s.bedtime)) }, col.text)
        else -> {}
    }
}

private fun shortSteps(n: Int) = if (n >= 10_000) "${n / 1000} mil" else formatSteps(n)

/** Bolinha que cresce e encolhe no ritmo de uma respiração calma. */
@Composable
private fun BreathDot(max: Dp) {
    val tr = rememberInfiniteTransition(label = "breath")
    val f by tr.animateFloat(0.4f, 1f, infiniteRepeatable(tween(4000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "f")
    Box(Modifier.size(max), contentAlignment = Alignment.Center) {
        Box(Modifier.size(max * f).alpha(0.4f + 0.6f * f).clip(CircleShape).background(BreathColor))
    }
}

// ---------- aviso ----------

@Composable
internal fun ReminderBanner(kind: ReminderKind) {
    val col = LocalIslandColors.current
    val s by Prefs.state.collectAsState()
    val (icon, tint) = when (kind) {
        ReminderKind.Break -> Icons.Rounded.Visibility to EyeColor
        ReminderKind.Water -> Icons.Rounded.WaterDrop to WaterColor
        ReminderKind.Bedtime -> Icons.Rounded.Bedtime to SleepColor
        ReminderKind.BatteryLow -> Icons.Rounded.BatteryAlert to Color(0xFFFF3B30)
        ReminderKind.BatteryFull -> Icons.Rounded.BatteryFull to Color(0xFF34C759)
    }
    val (title, sub) = when (kind) {
        ReminderKind.Break -> "Hora de uma pausa" to "Olhe para algo longe por 20 segundos. Toque quando terminar."
        ReminderKind.Water -> "Beba um copo de água" to "Toque para registrar +1 copo (${Health.water.value}/${s.waterGoal})"
        ReminderKind.Bedtime -> "Faltam 30 min para dormir" to "Que tal ir largando o celular?"
        ReminderKind.BatteryLow -> "Bateria fraca" to "Restam ${s.lowBatteryLevel}% ou menos. Ligue o carregador ou ative a economia de bateria."
        ReminderKind.BatteryFull -> "Bateria cheia" to "100%. Já pode tirar o carregador."
    }
    Row(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(tint.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, color = col.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(sub, color = col.dim, fontSize = 12.sp, maxLines = 2)
        }
    }
}

// ---------- ilha aberta ----------

/** Painel de saúde: passos, água, tempo de tela e hora de dormir. Água e tela são tocáveis. */
@Composable
internal fun HealthCard(s: Settings) {
    val col = LocalIslandColors.current
    val steps by Health.steps.collectAsState()
    val water by Health.water.collectAsState()
    val hasSensor by Health.stepsAvailable.collectAsState()
    val since by Health.usingSince.collectAsState()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(10_000)
        }
    }
    val using = remember(now, since) { Health.minutesUsing(now) }
    val toBed = remember(now, s.bedtime) { Health.minutesToBed(s.bedtime) }
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 10.dp)) {
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            HealthTile(
                Icons.AutoMirrored.Rounded.DirectionsWalk, StepColor, steps / s.stepGoal.toFloat(),
                if (hasSensor) formatSteps(steps) else "—", if (hasSensor) "de ${formatSteps(s.stepGoal)}" else "sem permissão",
            )
            HealthTile(
                Icons.Rounded.WaterDrop, WaterColor, water / s.waterGoal.toFloat(), "$water/${s.waterGoal}", "toque +1",
                onTap = { Health.addWater(1) }, onHold = { Health.addWater(-1) },
            )
            HealthTile(
                Icons.Rounded.Visibility, if (using >= s.breakMinutes) Late else EyeColor, using / s.breakMinutes.toFloat(),
                formatMinutes(using), "toque: pausei", onTap = { Health.breakTaken() },
            )
            HealthTile(Icons.Rounded.Bedtime, SleepColor, 1f - toBed / (16f * 60), formatMinutes(toBed), "para dormir")
        }
        Text(
            "Segure a água para tirar um copo",
            color = col.dim, fontSize = 10.sp, modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
}

@Composable
private fun HealthTile(icon: ImageVector, tint: Color, progress: Float, value: String, label: String, onTap: (() -> Unit)? = null, onHold: (() -> Unit)? = null) {
    val col = LocalIslandColors.current
    var pressed by remember { mutableStateOf(false) }
    val sc by animateFloatAsState(if (pressed) 0.9f else 1f, label = "press")
    val tap = if (onTap == null) Modifier else Modifier.pointerInput(onTap) {
        detectTapGestures(
            onPress = { pressed = true; tryAwaitRelease(); pressed = false },
            onTap = { onTap() },
            onLongPress = { onHold?.invoke() },
        )
    }
    Column(tap.graphicsLayer { scaleX = sc; scaleY = sc }, horizontalAlignment = Alignment.CenterHorizontally) {
        Ring(progress, tint, 50.dp, 4.dp) { Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp)) }
        Spacer(Modifier.height(5.dp))
        Text(value, color = col.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Text(label, color = col.dim, fontSize = 10.sp, maxLines = 1)
    }
}

/** Anel de progresso com algo no meio. */
@Composable
internal fun Ring(progress: Float, color: Color, size: Dp, stroke: Dp, content: @Composable () -> Unit) {
    val p by animateFloatAsState(progress.coerceIn(0f, 1f), tween(600), label = "ring")
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = stroke.toPx()
            val inset = w / 2
            val arcSize = androidx.compose.ui.geometry.Size(this.size.width - w, this.size.height - w)
            val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
            drawArc(color.copy(alpha = 0.22f), 0f, 360f, false, topLeft, arcSize, style = Stroke(w))
            drawArc(color, -90f, 360f * p, false, topLeft, arcSize, style = Stroke(w, cap = StrokeCap.Round))
        }
        content()
    }
}

/** Respiração guiada: 4 s puxando o ar, 4 s soltando, com a bolinha acompanhando. */
@Composable
internal fun BreatheCard() {
    val col = LocalIslandColors.current
    var inhale by remember { mutableStateOf(true) }
    var cycles by remember { mutableIntStateOf(0) }
    var left by remember { mutableIntStateOf(4) }
    LaunchedEffect(Unit) {
        while (true) {
            inhale = true
            for (i in 4 downTo 1) { left = i; delay(1000) }
            inhale = false
            for (i in 4 downTo 1) { left = i; delay(1000) }
            cycles++
        }
    }
    val f by animateFloatAsState(if (inhale) 1f else 0.45f, tween(4000, easing = FastOutSlowInEasing), label = "breath")
    Row(Modifier.fillMaxSize().padding(horizontal = 22.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(120.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(120.dp * f).clip(CircleShape).background(BreathColor.copy(alpha = 0.25f)))
            Box(Modifier.size(84.dp * f).clip(CircleShape).background(BreathColor.copy(alpha = 0.55f)))
            Text("$left", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(20.dp))
        Column(Modifier.weight(1f)) {
            Text(if (inhale) "Inspire" else "Expire", color = col.text, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text(if (inhale) "Puxe o ar devagar pelo nariz" else "Solte o ar devagar pela boca", color = col.dim, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            Text(if (cycles == 1) "1 respiração" else "$cycles respirações", color = BreathColor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
