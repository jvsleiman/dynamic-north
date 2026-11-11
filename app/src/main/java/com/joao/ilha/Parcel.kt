package com.joao.ilha

import android.app.Notification
import android.service.notification.StatusBarNotification
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.LocalShipping
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Lojas e rastreio: o status da encomenda vira a atividade "Encomenda" na ilha. */
val parcelApps = setOf(
    "com.mercadolibre", "com.shopee.br", "br.com.correios.preatendimento", "com.amazon.mShop.android.shopping",
    "com.alibaba.aliexpresshd", "com.zzkko", "com.einnovation.temu", "com.luizalabs.mlapp", "com.b2w.americanas", "yqtrack.app",
)

/** Etapas da encomenda. A barra tem 4 partes; [filled] é quantas ficam verdes. */
enum class ParcelStep(val filled: Int, val label: String, val hint: String) {
    Shipped(1, "Enviado", "O vendedor despachou o pacote."),
    InTransit(2, "Em trânsito", "O pacote está a caminho."),
    OutForDelivery(3, "Saiu para entrega", "O pacote chega hoje."),
    Pickup(3, "Aguardando retirada", "O pacote está disponível para retirar."),
    Delivered(4, "Entregue", "O pacote foi entregue."),
    Problem(0, "Problema na entrega", "Veja os detalhes no app."),
}

data class ParcelUpdate(val step: ParcelStep?, val eta: String, val title: String, val text: String)

object Parcels {
    private fun has(t: String, vararg w: String) = w.any { it in t }

    /** Só vale quando o texto fala de uma encomenda (cupons e ofertas também usam "enviado", "chega hoje"…). */
    private val nouns = listOf("pedido", "pacote", "compra", "encomenda", "produto", "objeto", "entrega", "envio", "package", "order", "parcel")

    fun stepOf(t: String): ParcelStep? {
        if (nouns.none { it in t }) return null
        return when {
            has(t, "não foi possível entregar", "nao foi possivel entregar", "tentativa de entrega", "entrega não realizada", "entrega nao realizada",
                "destinatário ausente", "destinatario ausente", "extraviad", "devolvid", "atrasad", "atraso na entrega") -> ParcelStep.Problem
            has(t, "aguardando retirada", "disponível para retirada", "disponivel para retirada", "pronto para retirada", "pode retirar",
                "ponto de retirada", "retire seu", "retire sua") -> ParcelStep.Pickup
            has(t, "saiu para entrega", "chega hoje", "será entregue hoje", "sera entregue hoje", "rota de entrega", "com o entregador",
                "a caminho do seu endereço", "a caminho do seu endereco", "out for delivery") -> ParcelStep.OutForDelivery
            has(t, "foi entregue", "entregue", "já chegou", "ja chegou", "pacote chegou", "pedido chegou", "compra chegou", "produto chegou", "delivered") -> ParcelStep.Delivered
            has(t, "em trânsito", "em transito", "a caminho", "em transferência", "em transferencia", "encaminhado", "chegou na unidade",
                "chegou ao centro", "centro de distribuição", "centro de distribuicao", "chegou na sua cidade", "chega amanhã", "chega amanha",
                "fiscalização", "fiscalizacao", "alfândega", "alfandega", "in transit") -> ParcelStep.InTransit
            has(t, "foi enviado", "foi enviada", "enviado", "postado", "despachad", "coletado", "shipped") -> ParcelStep.Shipped
            else -> null
        }
    }

    private val whenRe = Regex("(?:chega|chegar|entrega|entregue|previs[ãa]o|at[ée])[^.\\n]{0,24}?(hoje|amanh[ãa]|\\d{1,2}/\\d{1,2}|\\d{1,2} de [a-zç]{3,9})")

    /** "chega amanhã", "entrega até 14/11", "previsão: 12 de novembro" → "Amanhã", "14/11", "12 de novembro". */
    fun etaOf(t: String): String = whenRe.find(t)?.groupValues?.get(1)?.replaceFirstChar { it.uppercase() }.orEmpty()

    fun parse(sbn: StatusBarNotification): ParcelUpdate {
        val ex = sbn.notification.extras
        val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (ex.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: ex.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
        val all = "$title $text".lowercase()
        // Nome do produto/loja: o título quando ele não é o próprio status.
        val name = title.takeIf { it.isNotBlank() && stepOf(it.lowercase()) == null }.orEmpty()
        return ParcelUpdate(stepOf(all), etaOf(all), name, text.ifBlank { title })
    }

    /** Quanto tempo cada status fica na ilha depois de chegar. */
    fun showFor(step: ParcelStep): Long = when (step) {
        ParcelStep.OutForDelivery -> 3 * 3600_000L
        ParcelStep.Delivered -> 2 * 60_000L
        else -> 5 * 60_000L
    }
}

internal fun LiveInfo.parcelStep(): ParcelStep = ParcelStep.entries.getOrNull(step) ?: ParcelStep.InTransit

private val ParcelDone = Color(0xFF2BD47D)
private val ParcelTrack = Color(0xFF3A3A3C)
private val ParcelBad = Color(0xFFFF453A)
internal val ParcelColor = Color(0xFFFFB340)

private fun ParcelStep.icon(): ImageVector = when (this) {
    ParcelStep.Shipped -> Icons.Rounded.Inventory2
    ParcelStep.InTransit, ParcelStep.OutForDelivery -> Icons.Rounded.LocalShipping
    ParcelStep.Pickup -> Icons.Rounded.Storefront
    ParcelStep.Delivered -> Icons.Rounded.CheckCircle
    ParcelStep.Problem -> Icons.Rounded.ErrorOutline
}

private fun LiveInfo.parcelTint(): Color = when {
    parcelStep() == ParcelStep.Problem -> ParcelBad
    color != 0 -> Color(color).copy(alpha = 1f)
    else -> ParcelColor
}

/** Ilha pequena: ícone da etapa num anel de 4 partes e, à direita, a previsão ou a etapa. */
@Composable
internal fun CompactParcel(l: LiveInfo, h: Dp) {
    val col = LocalIslandColors.current
    val st = l.parcelStep()
    Row(Modifier.fillMaxSize().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(h - 6.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val w = 2.6.dp.toPx()
                val gap = 14f
                for (i in 0 until 4) {
                    val c = if (st == ParcelStep.Problem) ParcelBad else if (i < st.filled) ParcelDone else ParcelTrack
                    drawArc(
                        c, -90f + i * 90f + gap / 2, 90f - gap, false,
                        topLeft = Offset(w / 2, w / 2), size = Size(size.width - w, size.height - w), style = Stroke(w, cap = StrokeCap.Round),
                    )
                }
            }
            Icon(st.icon(), null, tint = l.parcelTint(), modifier = Modifier.size((h - 6.dp) * 0.5f))
        }
        Spacer(Modifier.weight(1f))
        Text(
            l.eta.ifBlank { st.label.substringBefore(" ") },
            color = col.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(end = 8.dp),
        )
    }
}

/** Ilha aberta: etapa (ou previsão), barra de 4 partes, cartão com o texto do app e o nome do produto. */
@Composable
internal fun ExpandedParcel(l: LiveInfo) {
    val col = LocalIslandColors.current
    val st = l.parcelStep()
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(if (l.eta.isNotBlank()) "Previsão de entrega" else l.appName.ifBlank { "Encomenda" }, color = col.text.copy(alpha = 0.85f), fontSize = 12.sp, maxLines = 1)
                Text(
                    l.eta.ifBlank { st.label }, color = col.text, fontSize = if (l.eta.isNotBlank()) 30.sp else 22.sp,
                    fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(Icons.Rounded.Inventory2, null, tint = l.parcelTint(), modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth().height(4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (i in 0 until 4) {
                val fill by animateFloatAsState(if (i < st.filled || st == ParcelStep.Problem) 1f else 0f, tween(600), label = "enc$i")
                Box(Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(2.dp)).background(ParcelTrack)) {
                    Box(Modifier.fillMaxWidth(fill).fillMaxSize().background(if (st == ParcelStep.Problem) ParcelBad else ParcelDone))
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(14.dp)).background(col.text.copy(alpha = 0.08f)).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(st.icon(), null, tint = l.parcelTint(), modifier = Modifier.size(40.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(st.label, color = col.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(l.text.takeIf { it.isNotBlank() && !it.equals(st.label, true) } ?: st.hint, color = col.dim, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (l.title.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(l.title, color = col.dim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
