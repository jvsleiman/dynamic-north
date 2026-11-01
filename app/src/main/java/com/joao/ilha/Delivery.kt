package com.joao.ilha

import android.app.Notification
import android.service.notification.StatusBarNotification
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeliveryDining
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.SoupKitchen
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Apps de pedido de comida acompanhados como uma atividade só ("Pedido"). */
val deliveryApps = setOf("br.com.brainweb.ifood", "com.grability.rappi", "com.ubercab.eats", "com.app.aiqfome", "com.ifood.ifood")

/** Etapas do pedido, como no próprio iFood. A barra tem 4 partes; [filled] é quantas ficam verdes. */
enum class OrderStep(val filled: Int, val label: String, val hint: String) {
    Waiting(1, "Aguardando confirmação", "O restaurante vai confirmar o pedido."),
    Preparing(2, "Preparando pedido", "O restaurante confirmou o pedido."),
    Ready(3, "Pronto para retirada", "Seu pedido está pronto."),
    OnTheWay(3, "Saiu para entrega", "O entregador está a caminho."),
    Arriving(3, "Chegando", "O entregador está perto."),
    Delivered(4, "Pedido entregue", "Bom apetite!"),
    Cancelled(0, "Pedido cancelado", "O pedido foi cancelado."),
}

/** O que dá para tirar de uma notificação do app de comida. */
data class OrderUpdate(val step: OrderStep?, val eta: String, val place: String, val text: String)

object Orders {
    private fun has(t: String, vararg w: String) = w.any { it in t }

    fun stepOf(t: String): OrderStep? = when {
        has(t, "cancelad", "cancelou", "cancelamos") -> OrderStep.Cancelled
        has(t, "entregue", "foi entregue", "bom apetite", "pedido concluído", "pedido concluido", "aproveite seu pedido") -> OrderStep.Delivered
        has(t, "chegando", "está perto", "esta perto", "chegou ao endereço", "chegou no endereço", "na sua porta", "entregador chegou") -> OrderStep.Arriving
        has(t, "saiu para entrega", "a caminho", "saiu do restaurante", "rota de entrega", "coletou seu pedido", "retirou seu pedido") -> OrderStep.OnTheWay
        has(t, "pronto para retirada", "pronto para retirar", "pode retirar", "já está pronto", "ja esta pronto") -> OrderStep.Ready
        has(t, "preparando", "em preparo", "sendo preparado", "confirmou", "confirmado", "aceitou") -> OrderStep.Preparing
        has(t, "aguardando confirmação", "aguardando confirmacao", "pedido enviado", "pedido realizado", "recebemos seu pedido", "pedido feito") -> OrderStep.Waiting
        else -> null
    }

    private val range = Regex("(\\d{1,2})[:h](\\d{2})\\s*(?:-|–|a|e|até|ate)\\s*(\\d{1,2})[:h](\\d{2})")
    private val single = Regex("(?<![\\d:])(\\d{1,2})[:h](\\d{2})(?![\\d:])")
    private val minutes = Regex("(\\d{1,3})\\s*(?:-|–|a)\\s*(\\d{1,3})\\s*min")
    private val oneMinute = Regex("(?:em|daqui a|chega em)\\s*(\\d{1,3})\\s*min")
    private val number = Regex("pedido\\s*(?:n[ºo°]\\.?|#|número|numero)\\s*(\\d{2,})", RegexOption.IGNORE_CASE)

    /** "19:46 - 19:56", "19:46" ou, de "20-30 min", o horário calculado a partir de agora. */
    fun etaOf(t: String, now: Long = System.currentTimeMillis()): String {
        range.find(t)?.let { m -> val g = m.groupValues; return "${g[1].padStart(2, '0')}:${g[2]} - ${g[3].padStart(2, '0')}:${g[4]}" }
        val f = SimpleDateFormat("HH:mm", Locale("pt", "BR"))
        minutes.find(t)?.let { m -> return "${f.format(Date(now + m.groupValues[1].toLong() * 60_000))} - ${f.format(Date(now + m.groupValues[2].toLong() * 60_000))}" }
        oneMinute.find(t)?.let { m -> return f.format(Date(now + m.groupValues[1].toLong() * 60_000)) }
        single.find(t)?.let { m -> return "${m.groupValues[1].padStart(2, '0')}:${m.groupValues[2]}" }
        return ""
    }

    fun parse(sbn: StatusBarNotification): OrderUpdate {
        val ex = sbn.notification.extras
        val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (ex.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: ex.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
        val sub = ex.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString().orEmpty()
        val all = "$title $text $sub".lowercase()
        val step = stepOf(all)
        // Restaurante: o título quando ele não é o próprio status; mais o número do pedido.
        val store = title.takeIf { it.isNotBlank() && stepOf(it.lowercase()) == null && etaOf(it).isEmpty() }.orEmpty()
        val num = number.find("$title $text $sub")?.groupValues?.get(1)
        val place = listOfNotNull(store.ifBlank { null }, num?.let { "Pedido Nº $it" }).joinToString(" • ")
        return OrderUpdate(step, etaOf(all), place, text.ifBlank { title })
    }
}

private val Done = Color(0xFF2BD47D)
private val Track = Color(0xFF3A3A3C)

private fun OrderStep.icon(): ImageVector = when (this) {
    OrderStep.Waiting -> Icons.Rounded.HourglassTop
    OrderStep.Preparing -> Icons.Rounded.SoupKitchen
    OrderStep.Ready -> Icons.Rounded.ShoppingBag
    OrderStep.OnTheWay -> Icons.Rounded.DeliveryDining
    OrderStep.Arriving -> Icons.Rounded.LocationOn
    OrderStep.Delivered -> Icons.Rounded.CheckCircle
    OrderStep.Cancelled -> Icons.Rounded.Cancel
}

internal fun LiveInfo.orderStep(): OrderStep = OrderStep.entries.getOrNull(step) ?: OrderStep.Waiting

private fun LiveInfo.brand(): Color = if (color != 0) Color(color).copy(alpha = 1f) else Color(0xFFEA1D2C)

/** Ícone do app instalado (lido do sistema). */
@Composable
private fun AppIcon(pkg: String, size: Dp) {
    val ctx = LocalContext.current
    val bmp = remember(pkg) { runCatching { ctx.packageManager.getApplicationIcon(pkg).toBitmapSafe()?.asImageBitmap() }.getOrNull() }
    if (bmp != null) Image(bmp, null, modifier = Modifier.size(size).clip(RoundedCornerShape(size * 0.28f)))
}

/** Ilha pequena: ícone da etapa dentro de um anel de 4 partes e, à direita, o horário previsto. */
@Composable
internal fun CompactOrder(l: LiveInfo, h: Dp) {
    val col = LocalIslandColors.current
    val st = l.orderStep()
    Row(Modifier.fillMaxSize().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(h - 6.dp), contentAlignment = Alignment.Center) {
            StepRing(st, Modifier.fillMaxSize())
            Icon(st.icon(), null, tint = if (st == OrderStep.Cancelled) Color(0xFFFF453A) else l.brand(), modifier = Modifier.size((h - 6.dp) * 0.55f))
        }
        Spacer(Modifier.weight(1f))
        Text(
            l.eta.substringBefore(" - ").ifBlank { st.label.substringBefore(" ") },
            color = col.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
            modifier = Modifier.padding(end = 8.dp),
        )
    }
}

@Composable
private fun StepRing(st: OrderStep, modifier: Modifier) {
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(0.35f, 1f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "a")
    Canvas(modifier) {
        val w = 2.6.dp.toPx()
        val gap = 14f
        for (i in 0 until 4) {
            val done = i < st.filled
            val c = when {
                st == OrderStep.Cancelled -> Color(0xFFFF453A)
                done && i == st.filled - 1 && st == OrderStep.Waiting -> Done.copy(alpha = pulse)
                done -> Done
                else -> Track
            }
            drawArc(
                c, -90f + i * 90f + gap / 2, 90f - gap, false,
                topLeft = Offset(w / 2, w / 2), size = Size(size.width - w, size.height - w), style = Stroke(w, cap = StrokeCap.Round),
            )
        }
    }
}

/** Ilha aberta: previsão de entrega, barra de 4 etapas, cartão do status e o restaurante. */
@Composable
internal fun ExpandedOrder(l: LiveInfo) {
    val col = LocalIslandColors.current
    val st = l.orderStep()
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(if (l.eta.isNotBlank()) "Previsão de entrega" else l.appName.ifBlank { "Pedido" }, color = col.text.copy(alpha = 0.85f), fontSize = 12.sp, maxLines = 1)
                Text(
                    l.eta.ifBlank { st.label }, color = col.text, fontSize = if (l.eta.isNotBlank()) 30.sp else 22.sp,
                    fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            AppIcon(l.pkg, 26.dp)
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth().height(4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (i in 0 until 4) {
                val target = if (i < st.filled) 1f else 0f
                val fill by animateFloatAsState(target, tween(600), label = "seg$i")
                Box(Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(2.dp)).background(Track)) {
                    val c = if (st == OrderStep.Cancelled) Color(0xFFFF453A) else Done
                    Box(Modifier.fillMaxWidth(if (st == OrderStep.Cancelled) 1f else fill).fillMaxSize().background(c))
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(14.dp)).background(col.text.copy(alpha = 0.08f)).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(st.icon(), null, tint = if (st == OrderStep.Cancelled) Color(0xFFFF453A) else l.brand(), modifier = Modifier.size(40.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(st.label, color = col.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val sub = l.text.takeIf { it.isNotBlank() && !it.equals(st.label, true) } ?: st.hint
                Text(sub, color = col.dim, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (l.title.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Storefront, null, tint = col.dim, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(8.dp))
                Text(l.title, color = col.dim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.alpha(0.95f))
            }
        }
    }
}
