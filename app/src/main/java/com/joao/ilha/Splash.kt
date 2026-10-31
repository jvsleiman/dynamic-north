package com.joao.ilha

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/** Quanto tempo a ilha fica aberta mostrando a animação de atualização. */
const val INTRO_MS = 6_200L

/** A animação aparece uma vez na ilha depois de cada atualização (ou instalação) do app. */
object Splash {
    private const val KEY = "abertura_vista"

    fun shouldShow(ctx: Context): Boolean =
        ctx.getSharedPreferences("ilha", Context.MODE_PRIVATE).getInt(KEY, -1) != BuildConfig.VERSION_CODE

    fun markSeen(ctx: Context) {
        ctx.getSharedPreferences("ilha", Context.MODE_PRIVATE).edit().putInt(KEY, BuildConfig.VERSION_CODE).apply()
    }
}

private val Dot = Color(0xFFF2F2F2)
private val Pine = Color(0xFF5E8C61)
private val Hill = Color(0xFF8A8A8A)
private val Moon = Color(0xFFF5E6B8)

// Velocidades em pontos por segundo. O chão anda na mesma velocidade que as patas empurram.
private const val GROUND_SPEED = 26f
private const val HILL_SPEED = 7f
private const val STRIDES_PER_SEC = 2.4f

/**
 * Animação da ilha aberta: paisagem noturna em pontinhos (estrelas, lua, montanhas, pinheiros) passando
 * enquanto um cavalo galopa no ritmo do chão. No fim o cavalo sai pela direita e aparece "Notch".
 */
@Composable
fun IntroScene() {
    var t by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameMillis { it }
        while (t < INTRO_MS / 1000f) withFrameMillis { t = (it - start) / 1000f }
    }
    val titleA = ((t - 4.3f) / 0.5f).coerceIn(0f, 1f)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().alpha((t / 0.35f).coerceIn(0f, 1f) * (1f - 0.7f * titleA))) { drawScene(t) }
        Text("Notch", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.alpha(titleA))
    }
}

private fun DrawScope.drawScene(t: Float) {
    val step = 3.5.dp.toPx()
    val r = step * 0.34f
    val cols = (size.width / step).toInt()
    val rows = (size.height / step).toInt()
    if (cols < 10 || rows < 12) return
    fun dot(cx: Int, cy: Int, c: Color, a: Float = 1f) {
        if (cx in 0 until cols && cy in 0 until rows) drawCircle(c.copy(alpha = c.alpha * a), r, Offset((cx + 0.5f) * step, (cy + 0.5f) * step))
    }
    fun dotF(x: Float, y: Float, c: Color) = dot(x.roundToInt(), y.roundToInt(), c)
    fun line(x0: Float, y0: Float, x1: Float, y1: Float, c: Color) {
        val n = (max(abs(x1 - x0), abs(y1 - y0)) * 2f).toInt().coerceAtLeast(1)
        for (i in 0..n) { val f = i / n.toFloat(); dotF(x0 + (x1 - x0) * f, y0 + (y1 - y0) * f, c) }
    }

    // Estrelas piscando
    for (i in 0 until 30) {
        val sx = (i * 37 + 11) % cols
        val sy = (i * 17 + 3) % (rows / 2)
        dot(sx, sy, Dot, 0.3f + 0.7f * abs(sin(t * 1.7f + i)))
    }
    // Lua minguante no canto
    val mx = cols - 9f; val my = 5f
    for (y in 1..9) for (x in cols - 14 until cols - 3) {
        val dx = x - mx; val dy = y - my
        val sx = x - (mx + 1.4f); val sy = y - (my - 0.9f)
        if (dx * dx + dy * dy <= 10f && sx * sx + sy * sy > 7f) dot(x, y, Moon)
    }
    val ground = rows - 4
    // Montanhas ao fundo (devagar)
    val shift = t * HILL_SPEED
    for (x in 0 until cols) {
        val wx = x + shift
        val y = ground - 4 - (4f * (sin(wx * 0.11f) + 1f) + 3f * (sin(wx * 0.037f + 1.3f) + 1f)).toInt()
        dot(x, y, Hill, 0.75f)
    }
    // Chão e pinheiros (na velocidade do galope)
    val near = t * GROUND_SPEED
    for (x in 0 until cols) {
        dot(x, ground, Hill, 0.55f)
        if (((x + near).toInt() % 7) == 0) dot(x, ground + 2, Hill, 0.35f)
    }
    var tx = -(near % 23f).toInt()
    var k = (near / 23f).toInt()
    while (tx < cols + 6) {
        val hgt = 4 + (k * 7 % 4)
        for (lvl in 0 until hgt) {
            val half = (hgt - lvl) / 2
            for (dx in -half..half) dot(tx + dx, ground - 1 - lvl, Pine)
        }
        tx += 15 + (k * 5 % 11)
        k++
    }

    // Cavalo: entra pela esquerda, galopa no meio e sai pela direita no fim.
    val enter = (t / 1.1f).coerceIn(0f, 1f)
    val easeIn = 1f - (1f - enter) * (1f - enter)
    var cx = -14f + (cols * 0.42f + 14f) * easeIn
    if (t > 3.6f) cx += (t - 3.6f) * (t - 3.6f) * 55f
    val phase = t * STRIDES_PER_SEC * 2f * PI.toFloat()
    val lift = abs(sin(phase)) * 1.1f
    val hipY = ground - 7f - lift
    val cy = hipY - 2f
    drawHorse(cx, cy, hipY, phase, t, ::dotF, ::line)
}

private fun drawHorse(
    cx: Float, cy: Float, hipY: Float, phase: Float, t: Float,
    dot: (Float, Float, Color) -> Unit,
    line: (Float, Float, Float, Float, Color) -> Unit,
) {
    val c = Dot
    // Patas (galope: as de trás e as da frente em pares defasados)
    fun leg(hx: Float, off: Float) {
        val p = phase + off
        val th = 0.5f * sin(p)
        val fold = max(0f, cos(p)) * 1.15f
        val kx = hx + 3.2f * sin(th); val ky = hipY + 3.2f * cos(th)
        val ph = th - fold
        val fx = kx + 3.2f * sin(ph); val fy = ky + 3.2f * cos(ph)
        line(hx, hipY, kx, ky, c)
        line(kx, ky, fx, fy, c)
    }
    leg(cx - 4.5f, 0.5f)
    leg(cx + 4.5f, 3.1f)
    // Corpo
    for (y in -3..3) for (x in -8..8) {
        val nx = x / 7f; val ny = y / 2.6f
        if (nx * nx + ny * ny <= 1f) dot(cx + x, cy + y, c)
    }
    leg(cx - 5.5f, 0f)
    leg(cx + 5.5f, 2.6f)
    // Pescoço e cabeça (balançam um pouco com o passo)
    val bob = sin(phase + 1f) * 0.6f
    line(cx + 4f, cy - 1f, cx + 8f, cy - 6f + bob, c)
    line(cx + 5f, cy - 1f, cx + 9f, cy - 6f + bob, c)
    line(cx + 6f, cy, cx + 9.5f, cy - 5f + bob, c)
    line(cx + 8f, cy - 7f + bob, cx + 12.5f, cy - 4.5f + bob, c)
    line(cx + 8.5f, cy - 6f + bob, cx + 12f, cy - 3.8f + bob, c)
    dot(cx + 8f, cy - 8.2f + bob, c)
    // Crina e rabo ao vento
    for (i in 0..4) dot(cx + 3.6f + i * 0.9f - 1f, cy - 2f - i * 1.2f + bob + sin(t * 14f + i) * 0.4f, c)
    for (i in 1..6) {
        val wave = sin(t * 13f - i * 0.8f) * 0.7f * i / 6f
        dot(cx - 7.5f - i, cy - 1.5f + i * 0.45f + wave, c)
    }
}
