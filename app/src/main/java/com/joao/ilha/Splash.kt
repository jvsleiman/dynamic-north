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
 * enquanto um cavalo galopa no ritmo do chão. No fim o cavalo sai pela direita e "NOTCH" acende em pontinhos.
 */
@Composable
fun IntroScene() {
    var t by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameMillis { it }
        while (t < INTRO_MS / 1000f) withFrameMillis { t = (it - start) / 1000f }
    }
    val bg = LocalIslandColors.current.bg
    val titleA = ((t - 4.2f) / 0.6f).coerceIn(0f, 1f)
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize().alpha((t / 0.35f).coerceIn(0f, 1f) * (1f - 0.75f * titleA))) { drawScene(t, bg) }
        if (t > 4.2f) Canvas(Modifier.fillMaxSize()) { drawTitle(t - 4.2f) }
    }
}

private fun DrawScope.grid(): Triple<Float, Int, Int> {
    val step = 3.5.dp.toPx()
    return Triple(step, (size.width / step).toInt(), (size.height / step).toInt())
}

/** Letras 5×7 em pontos. */
private val Glyphs = mapOf(
    'N' to listOf("10001", "11001", "10101", "10011", "10001", "10001", "10001"),
    'O' to listOf("01110", "10001", "10001", "10001", "10001", "10001", "01110"),
    'T' to listOf("11111", "00100", "00100", "00100", "00100", "00100", "00100"),
    'C' to listOf("01111", "10000", "10000", "10000", "10000", "10000", "01111"),
    'H' to listOf("10001", "10001", "10001", "11111", "10001", "10001", "10001"),
)

/** "NOTCH" acendendo ponto a ponto, no mesmo estilo da cena. */
private fun DrawScope.drawTitle(time: Float) {
    val (step, cols, rows) = grid()
    val text = "NOTCH"
    val sc = if (cols >= 64 && rows >= 20) 2 else 1
    val w = (text.length * 6 - 1) * sc
    val left = (cols - w) / 2
    val top = (rows - 7 * sc) / 2
    val r = step * 0.36f
    text.forEachIndexed { i, ch ->
        val g = Glyphs[ch] ?: return@forEachIndexed
        g.forEachIndexed { gy, line ->
            line.forEachIndexed { gx, bit ->
                if (bit != '1') return@forEachIndexed
                for (sy in 0 until sc) for (sx in 0 until sc) {
                    val x = left + (i * 6 + gx) * sc + sx
                    val y = top + gy * sc + sy
                    // Cada ponto acende num instante, da esquerda para a direita, com um brilho rápido.
                    val h = ((x * 73 + y * 151) % 97) / 97f
                    val on = time - (x - left) / w.toFloat() * 0.45f - h * 0.2f
                    if (on <= 0f) continue
                    val flash = if (on < 0.12f) 1.6f - on * 5f else 1f
                    val a = (on / 0.12f).coerceIn(0f, 1f)
                    drawCircle(Dot.copy(alpha = a), r * flash.coerceAtLeast(1f), Offset((x + 0.5f) * step, (y + 0.5f) * step))
                }
            }
        }
    }
}

private fun DrawScope.drawScene(t: Float, bg: Color) {
    val (step, cols, rows) = grid()
    val r = step * 0.34f
    if (cols < 10 || rows < 12) return
    fun dot(cx: Int, cy: Int, c: Color, a: Float = 1f) {
        if (cx in 0 until cols && cy in 0 until rows) drawCircle(c.copy(alpha = c.alpha * a), r, Offset((cx + 0.5f) * step, (cy + 0.5f) * step))
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
    var cx = -16f + (cols * 0.42f + 16f) * easeIn
    if (t > 3.6f) cx += (t - 3.6f) * (t - 3.6f) * 55f
    val phase = t * STRIDES_PER_SEC * 2f * PI.toFloat()
    // Poeira levantada pelas patas, ficando para trás e sumindo.
    for (i in 0 until 12) {
        val age = (t * 2.6f + i * 0.383f) % 1f
        val px = cx - 3f - age * 14f + (i % 3) * 1.3f
        val py = ground - 1f - age * (1.5f + (i % 4) * 0.6f)
        dot(px.roundToInt(), py.roundToInt(), Hill, (1f - age) * 0.7f)
    }
    drawHorse(cx, ground.toFloat(), phase, t, step, r, cols, rows, bg)
}

/** Cavalo desenhado em pontos, com galope, corpo balançando, crina e rabo ao vento. */
private fun DrawScope.drawHorse(cx: Float, ground: Float, phase: Float, t: Float, step: Float, r: Float, cols: Int, rows: Int, bg: Color) {
    val lift = max(0f, sin(phase - 0.5f)) * 1.6f
    val cy = ground - 8.6f - lift
    val pitch = 0.07f * sin(phase + 0.4f)
    val cp = cos(pitch); val sp = sin(pitch)
    val drawn = HashSet<Long>()
    fun p(lx: Float, ly: Float, c: Color = Dot, a: Float = 1f) {
        val x = (cx + lx * cp - ly * sp).roundToInt()
        val y = (cy + lx * sp + ly * cp).roundToInt()
        if (x !in 0 until cols || y !in 0 until rows) return
        if (!drawn.add(x.toLong() shl 32 or (y.toLong() and 0xffffffffL)) && c == Dot) return
        drawCircle(c.copy(alpha = c.alpha * a), if (c == Dot) r else r * 1.2f, Offset((x + 0.5f) * step, (y + 0.5f) * step))
    }
    fun line(x0: Float, y0: Float, x1: Float, y1: Float) {
        val n = (max(abs(x1 - x0), abs(y1 - y0)) * 2f).toInt().coerceAtLeast(1)
        for (i in 0..n) { val f = i / n.toFloat(); p(x0 + (x1 - x0) * f, y0 + (y1 - y0) * f) }
    }
    fun thick(x0: Float, y0: Float, x1: Float, y1: Float, w0: Float, w1: Float) {
        val len = kotlin.math.hypot(x1 - x0, y1 - y0).coerceAtLeast(0.01f)
        val nx = -(y1 - y0) / len; val ny = (x1 - x0) / len
        val n = (len * 2f).toInt().coerceAtLeast(1)
        for (i in 0..n) {
            val f = i / n.toFloat()
            val w = w0 + (w1 - w0) * f
            var o = -w / 2f
            while (o <= w / 2f) { p(x0 + (x1 - x0) * f + nx * o, y0 + (y1 - y0) * f + ny * o); o += 0.5f }
        }
    }
    fun ellipse(ex: Float, ey: Float, rx: Float, ry: Float) {
        var y = -ry
        while (y <= ry) { var x = -rx; while (x <= rx) { if ((x / rx) * (x / rx) + (y / ry) * (y / ry) <= 1f) p(ex + x, ey + y); x += 0.5f }; y += 0.5f }
    }
    // Patas: dianteiras dobram o joelho para trás, traseiras têm o jarrete; pares defasados (galope).
    fun leg(hx: Float, off: Float, hind: Boolean) {
        val ph = phase + off
        val th = 0.52f * sin(ph)
        val fold = max(0f, cos(ph)) * 1.2f
        val a1 = if (hind) th + 0.3f else th
        val kx = hx + 3.3f * sin(a1); val ky = 1.8f + 3.3f * cos(a1)
        val a2 = if (hind) th - 0.35f - fold * 0.9f else th - fold
        val fx = kx + 3.3f * sin(a2); val fy = ky + 3.3f * cos(a2)
        line(hx, 1.8f, kx, ky)
        line(kx, ky, fx, fy)
        p(fx + 0.6f, fy) // casco
    }
    leg(-4.2f, 0.5f, true)
    leg(4.8f, 3.1f, false)
    // Corpo: barriga, peito e garupa
    ellipse(0f, 0f, 7f, 2.6f)
    ellipse(4.6f, 0.2f, 3.2f, 3f)
    ellipse(-5f, -0.3f, 2.8f, 2.8f)
    leg(-5.4f, 0f, true)
    leg(5.8f, 2.6f, false)
    // Pescoço e cabeça (sobem e descem com o galope)
    val bob = sin(phase + 1.1f) * 0.9f
    thick(5.2f, -1f, 8.6f, -6.6f + bob, 4f, 2.2f)
    thick(8.4f, -7f + bob, 13f, -4.3f + bob, 2.8f, 1.6f)
    p(8.1f, -9.1f + bob); p(8.9f, -9.4f + bob); p(8.5f, -8.3f + bob)
    p(10.1f, -6.6f + bob, bg) // olho
    // Narina soltando vapor de vez em quando
    val puff = (t * 1.2f) % 1f
    if (puff < 0.5f) for (i in 0..2) p(13.6f + puff * 4f + i * 0.7f, -4.3f + bob - puff * 2f - i * 0.4f, Hill, (0.5f - puff) * 1.4f)
    // Crina ao vento
    for (i in 0..6) {
        val bx = 4.6f + i * 0.62f; val by = -1.8f - i * 0.82f + bob * (i / 6f)
        val wave = sin(t * 15f + i * 0.9f) * 0.6f
        p(bx - 1f, by + wave * 0.3f); p(bx - 2f, by + 0.3f + wave)
    }
    // Rabo: três fios ondulando, mais erguido quando ele corre
    for (s in 0..2) for (j in 1..7) {
        val wave = sin(t * 12f - j * 0.75f + s) * 0.8f * j / 7f
        p(-7.6f - j * 0.95f, -1.6f + j * (0.25f + s * 0.12f) + wave)
    }
}
