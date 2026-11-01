package com.joao.ilha

import android.graphics.Bitmap
import android.service.notification.StatusBarNotification
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Pagamento por aproximação feito pela Google Carteira. */
data class WalletPay(val amount: String, val merchant: String, val last4: String, val art: Bitmap?, val color: Int)

object Wallet {
    const val PKG = "com.google.android.apps.walletnfcrel"
    const val SHOW_MS = 4_200L

    private val amountRe = Regex("""(R\$|US\$|\$|€|£)\s?\d[\d.,]*""")
    private val last4Re = Regex("""(?:•+|\*+|final|terminado em|ending in|termina em)\s*(\d{4})""", RegexOption.IGNORE_CASE)
    private val payWords = listOf("pagamento", "pago", "pagou", "paid", "payment", "compra", "transação", "transaction")

    /** Só notificações de pagamento da Carteira (passes, ofertas e avisos ficam como notificação comum). */
    fun parse(sbn: StatusBarNotification, art: Bitmap?): WalletPay? {
        if (sbn.packageName != PKG) return null
        val e = sbn.notification.extras
        val title = e.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (e.getCharSequence(android.app.Notification.EXTRA_BIG_TEXT) ?: e.getCharSequence(android.app.Notification.EXTRA_TEXT))?.toString().orEmpty()
        val all = "$title\n$text"
        val amount = amountRe.find(all)?.value?.replace(Regex("""\s+"""), " ").orEmpty()
        val low = all.lowercase()
        if (amount.isEmpty() && payWords.none { it in low }) return null
        val merchant = when {
            amount.isNotEmpty() && amount in title -> text.lineSequence().firstOrNull().orEmpty()
            else -> title
        }.replace(amount, "").trim(' ', '-', '·', ':')
        val last4 = last4Re.find(all)?.groupValues?.get(1).orEmpty()
        // A Carteira costuma mandar a arte do cartão como ícone grande (retangular).
        val cardArt = art?.takeIf { it.width > it.height * 1.2f }
        return WalletPay(amount, merchant, last4, cardArt, sbn.notification.color)
    }
}

/**
 * Animação do pagamento, como no iPhone: o cartão sobe e assenta, o círculo se completa,
 * vira verde com o visto e aparece "Concluído" com o valor.
 */
@Composable
internal fun WalletPayBanner(p: WalletPay) {
    val col = LocalIslandColors.current
    val cardY = remember { Animatable(1f) }
    val cardTilt = remember { Animatable(-10f) }
    val ring = remember { Animatable(0f) }
    val done = remember { Animatable(0f) }
    val check = remember { Animatable(0f) }
    val textIn = remember { Animatable(0f) }
    LaunchedEffect(p) {
        launch { cardY.animateTo(0f, spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessLow)) }
        launch { cardTilt.animateTo(0f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessLow)) }
        delay(250)
        ring.animateTo(1f, tween(650, easing = FastOutSlowInEasing))
        launch { done.animateTo(1f, tween(220)) }
        launch { textIn.animateTo(1f, tween(320)) }
        check.animateTo(1f, tween(320, easing = LinearEasing))
    }
    val k = LocalIconScale.current
    Row(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        // Cartão
        Box(
            Modifier.size(92.dp * k, 58.dp * k)
                .graphicsLayer {
                    translationY = cardY.value * 60.dp.toPx()
                    rotationZ = cardTilt.value
                    val s = 0.82f + 0.18f * (1f - cardY.value.coerceIn(0f, 1f))
                    scaleX = s; scaleY = s
                    alpha = (1.4f - cardY.value).coerceIn(0f, 1f)
                }
                .clip(RoundedCornerShape(8.dp * k)),
        ) {
            val art = remember(p.art) { p.art?.asImageBitmap() }
            if (art != null) {
                Image(art, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                val base = if (p.color != 0) Color(p.color).copy(alpha = 1f) else Color(0xFF2C3E66)
                Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(lerp(base, Color.White, 0.18f), base, lerp(base, Color.Black, 0.35f)))))
                Canvas(Modifier.fillMaxSize()) {
                    // Chip e ondas de aproximação
                    val u = size.height / 58f
                    drawRoundRect(Color(0xFFE2C46A), Offset(9 * u, 18 * u), Size(15 * u, 11 * u), CornerRadius(2.5f * u))
                    for (i in 0..2) drawArc(
                        Color.White.copy(alpha = 0.8f), -40f, 80f, false,
                        topLeft = Offset(size.width - (22 - i * 4) * u - (6 + i * 4) * u, (23.5f - (3 + i * 3)) * u),
                        size = Size((6 + i * 4) * 2 * u, (3 + i * 3) * 2 * u),
                        style = Stroke(1.4f * u, cap = StrokeCap.Round),
                    )
                }
                if (p.last4.isNotEmpty()) Text(
                    "•••• ${p.last4}", color = Color.White.copy(alpha = 0.92f), fontSize = 9.sp,
                    modifier = Modifier.align(Alignment.BottomStart).padding(start = 9.dp * k, bottom = 6.dp * k),
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f).graphicsLayer { alpha = textIn.value; translationX = (1f - textIn.value) * 12.dp.toPx() }) {
            Text("Concluído", color = col.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            val line = listOf(p.amount, p.merchant).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "Google Carteira" }
            Text(line, color = col.dim, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(10.dp))
        // Círculo que se completa e vira visto verde
        Canvas(Modifier.size(42.dp * k).graphicsLayer { val s = 1f + 0.12f * kotlin.math.sin(done.value * Math.PI).toFloat(); scaleX = s; scaleY = s }) {
            val sw = 3.2.dp.toPx()
            val blue = Color(0xFF0A84FF)
            val green = Color(0xFF34C759)
            val c = lerp(blue, green, done.value)
            drawCircle(c.copy(alpha = 0.18f), size.minDimension / 2f - sw / 2f, style = Stroke(sw))
            drawArc(c, -90f, 360f * ring.value, false, topLeft = Offset(sw / 2f, sw / 2f), size = Size(size.width - sw, size.height - sw), style = Stroke(sw, cap = StrokeCap.Round))
            if (done.value > 0f) drawCircle(green.copy(alpha = done.value), size.minDimension / 2f - sw * 1.6f)
            if (check.value > 0f) {
                val w = size.width; val h = size.height
                val path = Path().apply { moveTo(w * 0.29f, h * 0.52f); lineTo(w * 0.44f, h * 0.66f); lineTo(w * 0.72f, h * 0.37f) }
                val pm = PathMeasure().apply { setPath(path, false) }
                val part = Path()
                pm.getSegment(0f, pm.length * check.value, part, true)
                drawPath(part, Color.White, style = Stroke(sw * 1.1f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}
