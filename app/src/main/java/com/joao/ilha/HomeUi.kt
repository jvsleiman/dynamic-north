package com.joao.ilha

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
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.WaterDrop
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val BR = Locale("pt", "BR")
private val CalColor = Color(0xFFFF453A)

/** Altura da ilha principal conforme o que ela mostra. */
internal fun homeHeight(s: Settings, weather: WeatherInfo?, agenda: AgendaInfo?): Dp {
    var h = 92.dp + 44.dp
    if (s.homeWeather && weather != null && weather.hours.isNotEmpty()) h += 70.dp
    if (s.homeHealth) h += 58.dp
    if (s.homeAgenda && agenda != null) h += 56.dp + (if (agenda.next != null) 26.dp else 0.dp)
    return h
}

/**
 * Ilha principal: o que aparece ao tocar na ilha parada.
 * Hora e clima no topo, depois previsão, saúde e agenda; notificações e controles ficam nos botões de baixo.
 */
@Composable
internal fun HomeCard(s: Settings, notifCount: Int, actions: IslandActions) {
    val col = LocalIslandColors.current
    val weather by Weather.now.collectAsState()
    val agenda by Agenda.now.collectAsState()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            IslandBus.awaitScreenOn()
            now = System.currentTimeMillis()
            delay(60_000 - System.currentTimeMillis() % 60_000 + 20)
        }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 12.dp)) {
        // Hora, data e clima agora
        Row(Modifier.fillMaxWidth().height(68.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(SimpleDateFormat("HH:mm", BR).format(Date(now)), color = col.text, fontSize = 40.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(
                    SimpleDateFormat("EEEE, d 'de' MMMM", BR).format(Date(now)).replaceFirstChar { it.uppercase() },
                    color = col.dim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            val w = weather
            if (s.homeWeather && w != null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(weatherIcon(w.code, w.day), null, tint = col.text, modifier = Modifier.size(30.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("${w.temp}°", color = col.text, fontSize = 26.sp, fontWeight = FontWeight.Medium)
                    }
                    Text(weatherLabel(w.code), color = col.dim, fontSize = 11.sp, maxLines = 1)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        // Próximas horas
        weather?.takeIf { s.homeWeather && it.hours.isNotEmpty() }?.let { w ->
            Row(Modifier.fillMaxWidth().height(58.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                w.hours.forEach { h ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
                        Text("%02d:00".format(h.hour), color = col.dim, fontSize = 11.sp)
                        Icon(weatherIcon(h.code, h.day), null, tint = col.text, modifier = Modifier.size(22.dp))
                        Text("${h.temp}°", color = col.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        // Saúde
        if (s.homeHealth) {
            HomeHealth(s, now)
            Spacer(Modifier.height(12.dp))
        }
        // Agenda: semana com o dia de hoje marcado e o próximo compromisso
        agenda?.takeIf { s.homeAgenda }?.let { a ->
            WeekStrip(a, now)
            a.next?.let { e ->
                Row(Modifier.fillMaxWidth().height(22.dp).padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(width = 3.dp, height = 14.dp).clip(CircleShape).background(CalColor))
                    Spacer(Modifier.width(8.dp))
                    Text(e.title, color = col.text, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(eventWhen(e, now), color = col.dim, fontSize = 12.sp, maxLines = 1)
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HomeButton(Icons.Rounded.Notifications, if (notifCount > 0) "Notificações ($notifCount)" else "Notificações", Modifier.weight(1f)) {
                IslandBus.expand?.invoke(ExpandKind.Notifications)
            }
            HomeButton(Icons.Rounded.Tune, "Controles", Modifier.weight(1f)) { IslandBus.expand?.invoke(ExpandKind.Controls) }
        }
    }
}

@Composable
private fun HomeHealth(s: Settings, now: Long) {
    val col = LocalIslandColors.current
    val steps by Health.steps.collectAsState()
    val water by Health.water.collectAsState()
    val since by Health.usingSince.collectAsState()
    val using = remember(now, since) { Health.minutesUsing(now) }
    Row(Modifier.fillMaxWidth().height(46.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        HealthChip(Icons.AutoMirrored.Rounded.DirectionsWalk, StepColor, steps / s.stepGoal.toFloat(), formatSteps(steps), "passos", Modifier.weight(1f))
        HealthChip(Icons.Rounded.WaterDrop, WaterColor, water / s.waterGoal.toFloat(), "$water/${s.waterGoal}", "copos", Modifier.weight(1f)) { Health.addWater(1) }
        HealthChip(Icons.Rounded.Visibility, EyeColor, using / s.breakMinutes.toFloat(), formatMinutes(using), "de tela", Modifier.weight(1f)) { Health.breakTaken() }
    }
}

@Composable
private fun HealthChip(icon: ImageVector, tint: Color, progress: Float, value: String, label: String, modifier: Modifier, onTap: (() -> Unit)? = null) {
    val col = LocalIslandColors.current
    Row(
        modifier.then(if (onTap != null) Modifier.pointerInput(onTap) { detectTapGestures { onTap() } } else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Ring(progress, tint, 36.dp, 3.dp) { Icon(icon, null, tint = tint, modifier = Modifier.size(17.dp)) }
        Spacer(Modifier.width(7.dp))
        Column {
            Text(value, color = col.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(label, color = col.dim, fontSize = 10.sp, maxLines = 1)
        }
    }
}

@Composable
private fun WeekStrip(a: AgendaInfo, now: Long) {
    val col = LocalIslandColors.current
    val today = Calendar.getInstance().apply { timeInMillis = now }
    val start = (today.clone() as Calendar).apply { set(Calendar.DAY_OF_WEEK, firstDayOfWeek) }
    Column(Modifier.fillMaxWidth().height(52.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.CalendarMonth, null, tint = CalColor, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(4.dp))
            Text(SimpleDateFormat("MMMM", BR).format(Date(now)).replaceFirstChar { it.uppercase() }, color = col.text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            for (i in 0 until 7) {
                val d = (start.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, i) }
                val isToday = d.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(SimpleDateFormat("EEEEE", BR).format(d.time).uppercase(), color = col.dim, fontSize = 10.sp)
                    Box(
                        Modifier.size(22.dp).clip(CircleShape).background(if (isToday) col.text else Color.Transparent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(d.get(Calendar.DAY_OF_MONTH).toString(), color = if (isToday) Color.Black else col.text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Box(Modifier.size(4.dp).clip(CircleShape).background(if (d.get(Calendar.DAY_OF_YEAR) in a.daysWithEvents) CalColor else Color.Transparent))
                }
            }
        }
    }
}

/** "agora", "em 25 min", "15:00", "amanhã 09:30", "qua 14:00". */
internal fun eventWhen(e: AgendaEvent, now: Long): String {
    if (e.begin <= now) return "agora"
    val mins = (e.begin - now) / 60_000
    if (mins < 60) return "em $mins min"
    val c = Calendar.getInstance().apply { timeInMillis = e.begin }
    val t = Calendar.getInstance().apply { timeInMillis = now }
    val time = if (e.allDay) "dia todo" else SimpleDateFormat("HH:mm", BR).format(Date(e.begin))
    return when (c.get(Calendar.DAY_OF_YEAR) - t.get(Calendar.DAY_OF_YEAR)) {
        0 -> time
        1 -> "amanhã $time"
        else -> SimpleDateFormat("EEE", BR).format(Date(e.begin)).replace(".", "") + " $time"
    }
}

@Composable
private fun HomeButton(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    val col = LocalIslandColors.current
    Row(
        modifier.height(36.dp).clip(RoundedCornerShape(18.dp)).background(col.text.copy(alpha = 0.12f))
            .pointerInput(label) { detectTapGestures { onClick() } },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = col.text, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = col.text, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

/** Ilha parada no estilo Clima ou Agenda: ícone na esquerda. */
@Composable
internal fun WeatherAgendaIdleLeft(style: IdleStyle, size: Dp) {
    val col = LocalIslandColors.current
    val w by Weather.now.collectAsState()
    when (style) {
        IdleStyle.Weather -> w?.let { Icon(weatherIcon(it.code, it.day), null, tint = col.text, modifier = Modifier.size(size)) }
        IdleStyle.Agenda -> Icon(Icons.Rounded.CalendarMonth, null, tint = CalColor, modifier = Modifier.size(size))
        else -> {}
    }
}

@Composable
internal fun WeatherAgendaIdleRight(style: IdleStyle, now: Long) {
    val col = LocalIslandColors.current
    val w by Weather.now.collectAsState()
    val a by Agenda.now.collectAsState()
    val text = when (style) {
        IdleStyle.Weather -> w?.let { "${it.temp}°" } ?: "—"
        IdleStyle.Agenda -> a?.next?.let { eventWhen(it, now) } ?: "—"
        else -> ""
    }
    Text(text, color = col.text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
}
