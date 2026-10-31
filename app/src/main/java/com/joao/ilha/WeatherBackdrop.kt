package com.joao.ilha

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.sin

private enum class Sky { Clear, PartlyCloudy, Cloudy, Fog, Drizzle, Rain, Snow, Storm }

private fun skyOf(code: Int): Sky = when (code) {
    0, 1 -> Sky.Clear
    2 -> Sky.PartlyCloudy
    3 -> Sky.Cloudy
    45, 48 -> Sky.Fog
    in 51..57 -> Sky.Drizzle
    in 61..67, in 80..82 -> Sky.Rain
    in 71..77, 85, 86 -> Sky.Snow
    in 95..99 -> Sky.Storm
    else -> Sky.Cloudy
}

/** Pseudoaleatório fixo por índice (0..1): cada gota/estrela/nuvem tem seu lugar. */
private fun rnd(i: Int, salt: Int = 0): Float {
    val x = sin((i * 127.1f + salt * 311.7f)) * 43758.547f
    return x - kotlin.math.floor(x)
}

/**
 * Fundo animado do clima de agora: sol brilhando, estrelas, nuvens passando, chuva, neve, neblina ou raios.
 * Anima a ~30 quadros por segundo e só com a tela ligada (a ilha aberta é o único lugar que mostra).
 */
@Composable
internal fun WeatherBackdrop(code: Int, day: Boolean, modifier: Modifier) {
    var t by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = System.currentTimeMillis()
        while (true) {
            IslandBus.awaitScreenOn()
            t = (System.currentTimeMillis() - start) / 1000f
            delay(33)
        }
    }
    val sky = skyOf(code)
    Canvas(modifier) { drawSky(sky, day, t) }
}

private fun DrawScope.drawSky(sky: Sky, day: Boolean, t: Float) {
    val w = size.width; val h = size.height
    val colors = when (sky) {
        Sky.Clear -> if (day) listOf(Color(0xFF2F80D8), Color(0xFF6DB3F2)) else listOf(Color(0xFF0A1330), Color(0xFF1E2C55))
        Sky.PartlyCloudy -> if (day) listOf(Color(0xFF3B7FC4), Color(0xFF8DB6DD)) else listOf(Color(0xFF111A33), Color(0xFF2B3753))
        Sky.Cloudy -> if (day) listOf(Color(0xFF5D6D82), Color(0xFF8E9CAF)) else listOf(Color(0xFF1C2230), Color(0xFF353D4E))
        Sky.Fog -> if (day) listOf(Color(0xFF7D8793), Color(0xFFAAB2BB)) else listOf(Color(0xFF2A2F37), Color(0xFF474D57))
        Sky.Drizzle, Sky.Rain -> if (day) listOf(Color(0xFF34495E), Color(0xFF5D7286)) else listOf(Color(0xFF141C27), Color(0xFF2C3846))
        Sky.Snow -> if (day) listOf(Color(0xFF6B7D95), Color(0xFFA9B7C9)) else listOf(Color(0xFF1F2738), Color(0xFF3F4A60))
        Sky.Storm -> listOf(Color(0xFF1B1F2B), Color(0xFF3A3F52))
    }
    drawRect(Brush.verticalGradient(colors))

    when (sky) {
        Sky.Clear -> if (day) sun(t, 1f) else stars(t, moon = true)
        Sky.PartlyCloudy -> { if (day) sun(t, 0.8f) else stars(t, moon = true); clouds(t, 3, 0.55f) }
        Sky.Cloudy -> clouds(t, 5, 0.42f)
        Sky.Fog -> fog(t)
        Sky.Drizzle -> { clouds(t, 3, 0.25f); rain(t, 26, 0.5f) }
        Sky.Rain -> { clouds(t, 3, 0.25f); rain(t, 55, 1f) }
        Sky.Snow -> { clouds(t, 2, 0.25f); snow(t) }
        Sky.Storm -> {
            clouds(t, 4, 0.3f); rain(t, 70, 1.2f)
            // Clarão de vez em quando (dois piscos rápidos a cada ~5 s)
            val p = t % 5.3f
            val flash = when { p < 0.08f -> 0.45f; p in 0.16f..0.24f -> 0.3f; else -> 0f }
            if (flash > 0f) drawRect(Color.White.copy(alpha = flash))
        }
    }
    // Leve escurecida embaixo para o texto das horas continuar legível
    drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.22f)), startY = h * 0.45f, endY = h))
}

private fun DrawScope.sun(t: Float, strength: Float) {
    val c = Offset(size.width * 0.86f, size.height * 0.12f)
    val pulse = 1f + 0.08f * sin(t * 1.3f)
    val r = size.height * 0.95f * pulse
    drawCircle(Brush.radialGradient(listOf(Color(0xFFFFF3C4).copy(alpha = 0.75f * strength), Color(0xFFFFD36B).copy(alpha = 0.25f * strength), Color.Transparent), c, r), r, c)
    drawCircle(Color(0xFFFFF6D8).copy(alpha = 0.9f * strength), size.height * 0.16f, c)
}

private fun DrawScope.stars(t: Float, moon: Boolean) {
    val r = 1.3f * density
    for (i in 0 until 34) {
        val x = rnd(i, 1) * size.width; val y = rnd(i, 2) * size.height * 0.8f
        val a = 0.25f + 0.75f * abs(sin(t * (0.8f + rnd(i, 3) * 1.6f) + i))
        drawCircle(Color.White.copy(alpha = a), r * (0.7f + rnd(i, 4) * 0.7f), Offset(x, y))
    }
    if (moon) {
        val c = Offset(size.width * 0.86f, size.height * 0.24f)
        val mr = size.height * 0.15f
        drawCircle(Brush.radialGradient(listOf(Color(0xFFF5E6B8).copy(alpha = 0.3f), Color.Transparent), c, mr * 3f), mr * 3f, c)
        drawCircle(Color(0xFFF5E6B8), mr, c)
    }
}

/** Nuvens: grupos de círculos translúcidos andando devagar para a direita e voltando pela esquerda. */
private fun DrawScope.clouds(t: Float, n: Int, alpha: Float) {
    val w = size.width; val h = size.height
    for (i in 0 until n) {
        val speed = 6f + rnd(i, 5) * 10f
        val span = w + 160f * density
        val x = ((rnd(i, 6) * span + t * speed * density) % span) - 80f * density
        val y = h * (0.12f + rnd(i, 7) * 0.45f)
        val s = (26f + rnd(i, 8) * 22f) * density
        val c = Color.White.copy(alpha = alpha * (0.6f + rnd(i, 9) * 0.4f))
        drawCircle(c, s * 0.55f, Offset(x - s * 0.55f, y + s * 0.12f))
        drawCircle(c, s * 0.75f, Offset(x, y - s * 0.1f))
        drawCircle(c, s * 0.5f, Offset(x + s * 0.65f, y + s * 0.15f))
    }
}

private fun DrawScope.rain(t: Float, n: Int, strength: Float) {
    val w = size.width; val h = size.height
    val len = (10f + 6f * strength) * density
    val dx = len * 0.28f
    for (i in 0 until n) {
        val speed = (1.1f + rnd(i, 10) * 0.6f) * strength.coerceAtLeast(0.6f)
        val p = (rnd(i, 11) + t * speed) % 1f
        val x = rnd(i, 12) * (w + 40f * density) - p * 30f * density
        val y = p * (h + len) - len
        drawLine(Color(0xFFD6E6F5).copy(alpha = 0.35f + 0.3f * rnd(i, 13)), Offset(x, y), Offset(x - dx, y + len), 1.4f * density, StrokeCap.Round)
    }
}

private fun DrawScope.snow(t: Float) {
    val w = size.width; val h = size.height
    for (i in 0 until 45) {
        val speed = 0.12f + rnd(i, 14) * 0.15f
        val p = (rnd(i, 15) + t * speed) % 1f
        val sway = sin(t * (0.8f + rnd(i, 16)) + i) * 10f * density
        val x = rnd(i, 17) * w + sway
        val y = p * (h + 8f * density) - 4f * density
        drawCircle(Color.White.copy(alpha = 0.55f + 0.4f * rnd(i, 18)), (1.4f + rnd(i, 19) * 1.8f) * density, Offset(x, y))
    }
}

private fun DrawScope.fog(t: Float) {
    val w = size.width; val h = size.height
    for (i in 0 until 5) {
        val y = h * (0.15f + i * 0.18f)
        val bandH = h * 0.14f
        val x = ((t * (8f + i * 4f) * density + rnd(i, 20) * w) % (w * 2f)) - w
        drawRoundRect(
            Color.White.copy(alpha = 0.10f + 0.06f * (i % 2)),
            topLeft = Offset(x, y), size = androidx.compose.ui.geometry.Size(w * 1.2f, bandH),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(bandH / 2f),
        )
    }
}
