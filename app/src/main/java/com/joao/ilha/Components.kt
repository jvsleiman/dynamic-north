package com.joao.ilha

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBackIos
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Paleta das telas, no estilo dos Ajustes do iPhone. */
data class UiPalette(
    val bg: Color, val card: Color, val text: Color, val secondary: Color, val separator: Color,
)

val Accent = Color(0xFF0A84FF)
val SwitchGreen = Color(0xFF34C759)

private val LightPalette = UiPalette(Color(0xFFF2F2F6), Color.White, Color.Black, Color(0xFF8E8E93), Color(0xFFD1D1D6))
private val DarkPalette = UiPalette(Color(0xFF1C1C1E), Color(0xFF2C2C2E), Color.White, Color(0xFF8E8E93), Color(0xFF3A3A3C))

val LocalPalette = compositionLocalOf { LightPalette }

@Composable
fun IlhaTheme(theme: AppTheme, content: @Composable () -> Unit) {
    val dark = when (theme) {
        AppTheme.System -> isSystemInDarkTheme()
        AppTheme.Light -> false
        AppTheme.Dark -> true
    }
    val p = if (dark) DarkPalette else LightPalette
    val scheme = if (dark) darkColorScheme(primary = Accent, background = p.bg, surface = p.card)
    else lightColorScheme(primary = Accent, background = p.bg, surface = p.card)
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(LocalPalette provides p, content = content)
    }
}

/** Página com título grande e botão de voltar. */
@Composable
fun Page(title: String, onBack: (() -> Unit)?, action: (@Composable () -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val p = LocalPalette.current
    Column(
        Modifier
            .fillMaxSize()
            .background(p.bg)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                Row(
                    Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onBack).padding(vertical = 6.dp, horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBackIos, null, tint = Accent, modifier = Modifier.size(18.dp))
                    Text("Voltar", color = Accent, fontSize = 17.sp)
                }
            }
            Spacer(Modifier.weight(1f))
            action?.invoke()
        }
        Text(title, color = p.text, fontSize = 32.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp, bottom = 12.dp))
        content()
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
fun TextAction(label: String, onClick: () -> Unit) {
    Text(label, color = Accent, fontSize = 17.sp, modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(6.dp))
}

/** Grupo de linhas num cartão arredondado, com título e rodapé opcionais. */
@Composable
fun Group(header: String? = null, footer: String? = null, content: @Composable ColumnScope.() -> Unit) {
    val p = LocalPalette.current
    Column(Modifier.padding(bottom = 22.dp)) {
        if (header != null) {
            Text(header.uppercase(), color = p.secondary, fontSize = 13.sp, modifier = Modifier.padding(start = 16.dp, bottom = 6.dp))
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(p.card)) { content() }
        if (footer != null) {
            Text(footer, color = p.secondary, fontSize = 13.sp, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp))
        }
    }
}

@Composable
fun Divider() {
    Box(Modifier.padding(start = 16.dp).fillMaxWidth().height(0.6.dp).background(LocalPalette.current.separator))
}

/** Ícone colorido em quadradinho, como nos Ajustes. */
@Composable
fun RowIcon(icon: ImageVector, color: Color) {
    Box(Modifier.size(30.dp).clip(RoundedCornerShape(7.dp)).background(color), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(19.dp))
    }
}

@Composable
fun NavRow(title: String, value: String? = null, icon: ImageVector? = null, iconColor: Color = Accent, divider: Boolean = true, onClick: () -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 48.dp).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            RowIcon(icon, iconColor)
            Spacer(Modifier.width(14.dp))
        }
        Text(title, color = p.text, fontSize = 17.sp, modifier = Modifier.weight(1f))
        if (value != null) Text(value, color = p.secondary, fontSize = 15.sp)
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = p.secondary, modifier = Modifier.size(22.dp))
    }
    if (divider) Divider()
}

@Composable
fun SwitchRow(title: String, checked: Boolean, subtitle: String? = null, icon: ImageVector? = null, iconColor: Color = Accent, divider: Boolean = true, onChange: (Boolean) -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.heightIn(min = 48.dp).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            RowIcon(icon, iconColor)
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = p.text, fontSize = 17.sp)
            if (subtitle != null) Text(subtitle, color = p.secondary, fontSize = 13.sp)
        }
        Spacer(Modifier.width(8.dp))
        Switch(
            checked, onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = SwitchGreen, checkedThumbColor = Color.White,
                uncheckedTrackColor = p.separator, uncheckedThumbColor = Color.White, uncheckedBorderColor = Color.Transparent,
            ),
        )
    }
    if (divider) Divider()
}

@Composable
fun SliderRow(title: String, value: Int, range: IntRange, unit: String = "", divider: Boolean = true, onChange: (Int) -> Unit) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row {
            Text(title, color = p.text, fontSize = 17.sp, modifier = Modifier.weight(1f))
            Text("$value$unit", color = p.secondary, fontSize = 15.sp)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.toInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Accent, inactiveTrackColor = p.separator),
        )
    }
    if (divider) Divider()
}

/** Lista de opções com um ✓ na escolhida. */
@Composable
fun <T> ChoiceRows(options: List<T>, selected: T, label: (T) -> String, onPick: (T) -> Unit) {
    val p = LocalPalette.current
    options.forEachIndexed { i, o ->
        Row(
            Modifier.fillMaxWidth().clickable { onPick(o) }.heightIn(min = 46.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label(o), color = p.text, fontSize = 17.sp, modifier = Modifier.weight(1f))
            if (o == selected) Icon(Icons.Rounded.Check, null, tint = Accent, modifier = Modifier.size(20.dp))
        }
        if (i < options.lastIndex) Divider()
    }
}

val Swatches = listOf(
    0xFF000000, 0xFF1C1C1E, 0xFF3A3A3C, 0xFFFFFFFF, 0xFFFF3B30, 0xFFFF9500, 0xFFFFCC00,
    0xFF34C759, 0xFF00C7BE, 0xFF0A84FF, 0xFF5E5CE6, 0xFFAF52DE, 0xFFFF2D55, 0xFFA2845E,
)

@Composable
fun ColorRow(title: String, value: Long, divider: Boolean = true, onPick: (Long) -> Unit) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(title, color = p.text, fontSize = 17.sp)
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Swatches.chunked(7).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { c ->
                        val sel = c == value
                        Box(
                            Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color(c))
                                .border(if (sel) 3.dp else 1.dp, if (sel) Accent else p.separator, CircleShape)
                                .clickable { onPick(c) },
                        )
                    }
                }
            }
        }
    }
    if (divider) Divider()
}

@Composable
fun InfoText(text: String) {
    Text(text, color = LocalPalette.current.secondary, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
}

fun onOff(v: Boolean) = if (v) "ATIVADO" else "DESATIVADO"
