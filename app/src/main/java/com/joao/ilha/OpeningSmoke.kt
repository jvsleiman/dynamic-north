package com.joao.ilha

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.floor
import kotlin.math.roundToInt

/** Duração da abertura do app (toque para pular). */
const val OPENING_MS = 2_800L

private const val COLS = 96
private val Bayer = intArrayOf(0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5)

private fun hash(x: Int, y: Int): Float {
    var h = x * 374761393 + y * 668265263
    h = (h xor (h ushr 13)) * 1274126177
    return ((h xor (h ushr 16)) and 0xFFFF) / 65535f
}

private fun smooth(t: Float) = t * t * (3f - 2f * t)

private fun noise(x: Float, y: Float): Float {
    val xi = floor(x).toInt(); val yi = floor(y).toInt()
    val fx = smooth(x - xi); val fy = smooth(y - yi)
    val a = hash(xi, yi); val b = hash(xi + 1, yi); val c = hash(xi, yi + 1); val d = hash(xi + 1, yi + 1)
    return (a + (b - a) * fx) + ((c + (d - c) * fx) - (a + (b - a) * fx)) * fy
}

private fun fbm(x: Float, y: Float): Float = noise(x, y) * 0.55f + noise(x * 2.1f, y * 2.1f) * 0.3f + noise(x * 4.3f, y * 4.3f) * 0.15f

/**
 * Fumaça subindo em 1 bit: cada célula da grade fica [fg] ou [bg], com pontilhado ordenado 4×4.
 * [level] de 0 a 1 controla quanto da fumaça aparece (para entrar e sair em pixels).
 */
internal fun smoke1Bit(px: IntArray, cols: Int, rows: Int, t: Float, level: Float, fg: Int, bg: Int, density: Float = 1f) {
    val rise = t * 1.35f
    for (y in 0 until rows) {
        val v = y / rows.toFloat()
        // Mais densa embaixo, rarefeita em cima; uma coluna central que ondula.
        val base = 0.15f + 0.85f * v * v
        for (x in 0 until cols) {
            val u = x / cols.toFloat()
            val sx = u * 3.2f; val sy = v * 6.5f * density + rise
            val warp = fbm(sx * 0.7f + t * 0.25f, sy * 0.5f) * 2.2f
            var d = fbm(sx + warp, sy + warp * 0.6f)
            val center = 1f - kotlin.math.abs(u - 0.5f - 0.12f * kotlin.math.sin(v * 5f + t * 1.4f)) * 2.2f
            d = (d * 1.25f - 0.45f) * base + center.coerceAtLeast(0f) * 0.22f * (1f - v)
            val th = (Bayer[(y and 3) * 4 + (x and 3)] + 0.5f) / 16f
            px[y * cols + x] = if (d * level > th) fg else bg
        }
    }
}

/**
 * Abertura do app: fumaça subindo, desenhada em pixels de 1 bit (só preto e branco, com
 * pontilhado ordenado), e "NOTCH" acendendo no meio. No fim a imagem se desfaz em pixels.
 */
@Composable
fun OpeningSmoke(onDone: () -> Unit) {
    val done by rememberUpdatedState(onDone)
    var t by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            t = (now - start) / 1e9f
            if (t * 1000f >= OPENING_MS) { done(); break }
        }
    }
    val holder = remember { arrayOfNulls<Bitmap>(1) }
    val pixels = remember { arrayOf(IntArray(0)) }
    Canvas(
        Modifier.fillMaxSize().background(Color.Black).pointerInput(Unit) { detectTapGestures { done() } },
    ) {
        val cols = COLS
        val rows = (cols * size.height / size.width).roundToInt().coerceAtLeast(1)
        val bmp = holder[0]?.takeIf { it.width == cols && it.height == rows }
            ?: Bitmap.createBitmap(cols, rows, Bitmap.Config.ARGB_8888).also { holder[0] = it; pixels[0] = IntArray(cols * rows) }
        val px = pixels[0]
        val total = OPENING_MS / 1000f
        // Entra devagar, segura e no fim se desfaz.
        val fadeIn = (t / 0.5f).coerceIn(0f, 1f)
        val fadeOut = ((total - t) / 0.55f).coerceIn(0f, 1f)
        val level = fadeIn * fadeOut
        val white = 0xFFFFFFFF.toInt(); val black = 0xFF000000.toInt()
        smoke1Bit(px, cols, rows, t, level, white, black)
        // "NOTCH": cada pixel da letra vale 2×2 da grade, branco com contorno preto.
        val scale = 2
        val glyphW = 5 * scale; val gap = scale
        val text = "NOTCH"
        val tw = text.length * glyphW + (text.length - 1) * gap
        val ox = (cols - tw) / 2; val oy = (rows - 7 * scale) / 2
        val titleIn = ((t - 0.55f) / 0.6f).coerceIn(0f, 1f) * fadeOut
        if (titleIn > 0f) {
            val on = BooleanArray(cols * rows)
            text.forEachIndexed { i, ch ->
                val g = Glyphs[ch] ?: return@forEachIndexed
                for (gy in 0 until 7) for (gx in 0 until 5) if (g[gy][gx] == '1') {
                    for (dy in 0 until scale) for (dx in 0 until scale) {
                        val x = ox + i * (glyphW + gap) + gx * scale + dx; val y = oy + gy * scale + dy
                        if (x in 0 until cols && y in 0 until rows) on[y * cols + x] = true
                    }
                }
            }
            for (y in 0 until rows) for (x in 0 until cols) {
                val th = (Bayer[(y and 3) * 4 + (x and 3)] + 0.5f) / 16f
                if (titleIn <= th) continue
                val i = y * cols + x
                if (on[i]) px[i] = white
                else if ((x > 0 && on[i - 1]) || (x < cols - 1 && on[i + 1]) || (y > 0 && on[i - cols]) || (y < rows - 1 && on[i + cols])) px[i] = black
            }
        }
        bmp.setPixels(px, 0, cols, 0, 0, cols, rows)
        drawImage(
            bmp.asImageBitmap(), dstOffset = IntOffset.Zero,
            dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()), filterQuality = FilterQuality.None,
        )
    }
}

/**
 * Cartão do topo do Início: a mesma fumaça em 1 bit (pixels de 3 dp, sem cinza nem cor) passando
 * atrás do contorno do celular, com a ilha no tamanho e no arredondamento escolhidos em Exibição.
 * Anima a ~20 quadros por segundo e só enquanto a tela do app está visível.
 */
@Composable
fun HomeHero(s: Settings) {
    val p = LocalPalette.current
    val dark = p.text == Color.White
    val fg = if (dark) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
    val bg = if (dark) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
    var t by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            t = (now - start) / 1e9f
            kotlinx.coroutines.delay(50)
        }
    }
    val holder = remember { arrayOfNulls<Bitmap>(1) }
    val pixels = remember { arrayOf(IntArray(0)) }
    val line = if (dark) Color.White else Color.Black
    androidx.compose.foundation.layout.Box(
        Modifier.fillMaxWidth().padding(bottom = 22.dp).height(212.dp).clip(RoundedCornerShape(16.dp)).background(Color(bg)),
        contentAlignment = Alignment.TopCenter,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val cell = 3.dp.toPx()
            val cols = (size.width / cell).toInt().coerceAtLeast(1)
            val rows = (size.height / cell).toInt().coerceAtLeast(1)
            val bmp = holder[0]?.takeIf { it.width == cols && it.height == rows }
                ?: Bitmap.createBitmap(cols, rows, Bitmap.Config.ARGB_8888).also { holder[0] = it; pixels[0] = IntArray(cols * rows) }
            val px = pixels[0]
            smoke1Bit(px, cols, rows, t * 0.6f, 1f, fg, bg, density = 0.5f)
            bmp.setPixels(px, 0, cols, 0, 0, cols, rows)
            drawImage(
                bmp.asImageBitmap(), dstOffset = IntOffset.Zero,
                dstSize = IntSize((cols * cell).roundToInt(), (rows * cell).roundToInt()), filterQuality = FilterQuality.None,
            )
        }
        // Celular só em contorno, com a ilha preta por cima (a fumaça passa por trás).
        androidx.compose.foundation.layout.Box(
            Modifier.padding(top = 26.dp).width(220.dp).height(186.dp)
                .border(2.dp, line, RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp))
                .padding(top = 12.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            val scale = 220f / 400f
            val h = (s.height * scale * 1.6f).dp
            androidx.compose.foundation.layout.Box(
                Modifier
                    .width((s.width * scale * 1.6f).dp).height(h)
                    .clip(RoundedCornerShape(h * (s.smallRadius / 100f)))
                    .background(Color.Black)
                    .border(1.dp, if (dark) Color.White.copy(alpha = 0.6f) else Color.Transparent, RoundedCornerShape(h * (s.smallRadius / 100f))),
            )
        }
    }
}
