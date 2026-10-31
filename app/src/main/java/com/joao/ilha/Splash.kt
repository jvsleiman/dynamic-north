package com.joao.ilha

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sin

/** A abertura aparece na primeira vez depois de cada atualização (ou instalação). */
object Splash {
    private const val KEY = "abertura_vista"

    /** Pedido para ver a abertura de novo (tela Testar). */
    val replay = kotlinx.coroutines.flow.MutableStateFlow(false)

    fun shouldShow(ctx: Context): Boolean =
        ctx.getSharedPreferences("ilha", Context.MODE_PRIVATE).getInt(KEY, -1) != BuildConfig.VERSION_CODE

    fun markSeen(ctx: Context) {
        ctx.getSharedPreferences("ilha", Context.MODE_PRIVATE).edit().putInt(KEY, BuildConfig.VERSION_CODE).apply()
    }
}

private val Dot = Color(0xFFF2F2F2)
private val Pine = Color(0xFF5E8C61)
private val Hill = Color(0xFF8A8A8A)

/**
 * Abertura: a ilha cresce mostrando uma paisagem noturna em pontinhos (estrelas, montanhas e
 * pinheiros passando) e o nome NOTCH chega correndo pela direita. Depois ela encolhe para a pílula
 * e o nome aparece embaixo. Toque para pular.
 */
@Composable
fun SplashScreen(onDone: () -> Unit) {
    val w = remember { Animatable(120f) }
    val h = remember { Animatable(36f) }
    val scene = remember { Animatable(0f) }
    val title = remember { Animatable(0f) }
    val out = remember { Animatable(1f) }
    var t by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameMillis { it }
        launch { while (true) withFrameMillis { t = (it - start) / 1000f } }
        delay(250)
        launch { w.animateTo(330f, spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow)) }
        h.animateTo(150f, spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow))
        scene.animateTo(1f, tween(300))
        delay(3_000)
        scene.animateTo(0f, tween(220))
        launch { w.animateTo(124f, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium)) }
        h.animateTo(36f, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium))
        title.animateTo(1f, tween(450))
        delay(900)
        out.animateTo(0f, tween(350))
        onDone()
    }
    Box(
        Modifier.fillMaxSize().alpha(out.value).background(MaterialTheme.colorScheme.background)
            .pointerInput(Unit) { detectTapGestures { onDone() } },
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(Modifier.statusBarsPadding().padding(top = 160.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(w.value.coerceAtLeast(1f).dp, h.value.coerceAtLeast(1f).dp)
                    .clip(RoundedCornerShape((h.value / 2f).coerceIn(18f, 44f).dp))
                    .background(Color.Black),
            ) {
                Canvas(Modifier.fillMaxSize().alpha(scene.value)) { drawScene(t) }
            }
            Spacer(Modifier.height(22.dp))
            Text(
                "Notch", color = MaterialTheme.colorScheme.onBackground, fontSize = 34.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.alpha(title.value).padding(top = ((1f - title.value) * 12f).dp),
            )
        }
    }
}

/** Letras 5×7 em pontos. */
private val Glyphs = mapOf(
    'N' to listOf("10001", "11001", "10101", "10011", "10001", "10001", "10001"),
    'O' to listOf("01110", "10001", "10001", "10001", "10001", "10001", "01110"),
    'T' to listOf("11111", "00100", "00100", "00100", "00100", "00100", "00100"),
    'C' to listOf("01111", "10000", "10000", "10000", "10000", "10000", "01111"),
    'H' to listOf("10001", "10001", "10001", "11111", "10001", "10001", "10001"),
)

private fun DrawScope.drawScene(t: Float) {
    val step = 4.dp.toPx()
    val r = step * 0.34f
    val cols = (size.width / step).toInt()
    val rows = (size.height / step).toInt()
    fun dot(cx: Int, cy: Int, c: Color, a: Float = 1f) {
        if (cx in 0 until cols && cy in 0 until rows) drawCircle(c.copy(alpha = c.alpha * a), r, Offset((cx + 0.5f) * step, (cy + 0.5f) * step))
    }
    // Estrelas piscando (posições fixas)
    for (i in 0 until 26) {
        val sx = (i * 37 + 11) % cols
        val sy = (i * 17 + 3) % (rows / 2)
        val tw = 0.35f + 0.65f * abs(sin(t * 1.7f + i))
        dot(sx, sy, Dot, tw)
    }
    // Montanhas passando devagar (linha de pontos)
    val shift = t * 9f
    for (x in 0 until cols) {
        val wx = x + shift
        val y = rows - 7 - (4.5f * (sin(wx * 0.11f) + 1f) + 3f * (sin(wx * 0.037f + 1.3f) + 1f)).toInt()
        dot(x, y, Hill, 0.85f)
    }
    // Chão e pinheiros passando mais rápido
    val near = t * 22f
    for (x in 0 until cols) dot(x, rows - 3, Hill, 0.5f)
    var tx = -((near % 23f)).toInt()
    var k = (near / 23f).toInt()
    while (tx < cols + 6) {
        val hgt = 4 + (k * 7 % 4)
        for (lvl in 0 until hgt) {
            val half = (hgt - lvl) / 2
            for (dx in -half..half) dot(tx + dx, rows - 4 - lvl, Pine)
        }
        tx += 15 + (k * 5 % 11)
        k++
    }
    // NOTCH chegando pela direita e parando no meio
    val text = "NOTCH"
    val textW = text.length * 6 - 1
    val target = (cols - textW) / 2
    val p = ((t - 0.6f) / 1.2f).coerceIn(0f, 1f)
    val ease = 1f - (1f - p) * (1f - p) * (1f - p)
    val left = (cols + (target - cols) * ease).toInt()
    val top = rows / 2 - 6
    text.forEachIndexed { i, ch ->
        val g = Glyphs[ch] ?: return@forEachIndexed
        g.forEachIndexed { gy, line ->
            line.forEachIndexed { gx, bit -> if (bit == '1') dot(left + i * 6 + gx, top + gy, Dot) }
        }
    }
}
