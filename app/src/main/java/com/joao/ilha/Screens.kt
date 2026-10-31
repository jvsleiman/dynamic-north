package com.joao.ilha

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PhoneIphone
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Screen {
    Home, Features, Test, Display, Customize, Idle, Live, Controls, ControlsApps, Notifications, Calls, Music, Charging, Bluetooth, Border, Gestures, HiddenApps, Other,
    BlockedNotifApps, BlockedMusicApps, Update, Help,
}

private fun Settings.set(change: Settings.() -> Settings) = Prefs.update { it.change() }

// ---------- início ----------

@Composable
fun HomeScreen(go: (Screen) -> Unit) {
    val ctx = LocalContext.current
    val s by Prefs.state.collectAsState()
    var a11y by remember { mutableStateOf(a11yEnabled(ctx)) }
    var a11yShortcut by remember { mutableStateOf(a11yShortcutOn(ctx)) }
    var listener by remember { mutableStateOf(listenerEnabled(ctx)) }
    val lifecycle = LocalLifecycleOwner.current
    LaunchedEffect(Unit) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            a11y = a11yEnabled(ctx)
            listener = listenerEnabled(ctx)
            a11yShortcut = a11yShortcutOn(ctx)
        }
    }
    Page("Notch", onBack = null) {
        IslandPreview(s)
        if (!a11y || !listener) {
            Group(
                "Permissões",
                footer = "Se a opção aparecer bloqueada (Android 13+), abra as informações do app, toque em ⋮ e em \"Permitir configurações restritas\".",
            ) {
                NavRow("Acessibilidade", onOff(a11y), Icons.Rounded.PhoneIphone, Color(0xFF5E5CE6)) { open(ctx, AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS) }
                NavRow("Acesso às notificações", onOff(listener), Icons.Rounded.Notifications, Color(0xFFFF3B30)) {
                    open(ctx, "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
                }
                NavRow("Informações do app", divider = false) {
                    runCatching { ctx.startActivity(Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null))) }
                }
            }
        }
        if (a11y && a11yShortcut) {
            Group(footer = "É o atalho de acessibilidade do Android, não faz parte da ilha. Na tela que abrir, desligue \"Atalho do Notch\" e deixe só \"Usar Notch\" ligado.") {
                NavRow("Tirar o botão flutuante da lateral", icon = Icons.Rounded.PhoneIphone, iconColor = Color(0xFFFF9500), divider = false) { openA11yDetails(ctx) }
            }
        }
        if (remember { CrashLog.read(ctx) } != null) {
            Group {
                NavRow("O app fechou sozinho: ver o erro", icon = Icons.AutoMirrored.Rounded.HelpOutline, iconColor = Color(0xFFFF3B30), divider = false) { go(Screen.Help) }
            }
        }
        Group {
            SwitchRow("Barra da Ilha", s.enabled) { v -> s.set { copy(enabled = v) } }
            NavRow("Exibição") { go(Screen.Display) }
            NavRow("Personalizar layouts") { go(Screen.Customize) }
            NavRow("Ilha parada", s.idleStyle.label, divider = false) { go(Screen.Idle) }
        }
        Group {
            NavRow("Recursos", "Notificações, música, controles…", Icons.Rounded.Apps, Color(0xFF5E5CE6), divider = false) { go(Screen.Features) }
        }
        Group {
            NavRow("Toque e segurar", icon = Icons.Rounded.TouchApp, iconColor = Color(0xFFFF9500)) { go(Screen.Gestures) }
            NavRow("Ocultar a ilha nos apps", if (s.hiddenApps.isEmpty()) null else s.hiddenApps.size.toString(), Icons.Rounded.VisibilityOff, Color(0xFF8E8E93)) { go(Screen.HiddenApps) }
            NavRow("Outras configurações", icon = Icons.Rounded.Settings, iconColor = Color(0xFF8E8E93), divider = false) { go(Screen.Other) }
        }
        Group {
            NavRow("Testar a ilha", icon = Icons.Rounded.Science, iconColor = Color(0xFF00C7BE)) { go(Screen.Test) }
            NavRow("Verificar atualização", icon = Icons.Rounded.SystemUpdate, iconColor = Accent) { go(Screen.Update) }
            NavRow("Ajuda", icon = Icons.AutoMirrored.Rounded.HelpOutline, iconColor = Color(0xFF5AC8FA), divider = false) { go(Screen.Help) }
        }
    }
}

/** Desenho de um celular com a ilha, para ver o tamanho e a cor escolhidos. */
@Composable
fun IslandPreview(s: Settings) {
    val p = LocalPalette.current
    Box(
        Modifier.fillMaxWidth().padding(bottom = 22.dp).clip(RoundedCornerShape(16.dp)).background(p.card).padding(vertical = 22.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            Modifier.width(220.dp).height(120.dp).clip(RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp)).background(Color(0xFF101014)).padding(top = 12.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            val scale = 220f / 400f
            Box(
                Modifier
                    .width((s.width * scale * 1.6f).dp)
                    .height((s.height * scale * 1.6f).dp)
                    .clip(RoundedCornerShape((s.height * scale * 1.6f * s.smallRadius / 100f).dp))
                    .background(if (s.bgColor == 0xFF000000) Color.Black else Color(s.bgColor)),
            )
        }
    }
}

// ---------- exibição ----------

@Composable
fun DisplayScreen(back: () -> Unit) {
    val s by Prefs.state.collectAsState()
    Page("Exibição", back, action = { TextAction("Redefinir") { Prefs.resetDisplay() } }) {
        IslandPreview(s)
        Group("Tamanho") {
            SliderRow("Largura", s.width, 40..240, " dp") { v -> s.set { copy(width = v) } }
            SliderRow("Altura", s.height, 16..60, " dp", divider = false) { v -> s.set { copy(height = v) } }
        }
        Group("Posição", footer = "Com \"Centralizar na câmera\", a ilha começa em cima da câmera frontal e os ajustes movem a partir dali.") {
            SwitchRow("Centralizar na câmera", s.alignToCamera) { v -> s.set { copy(alignToCamera = v) } }
            SliderRow("Horizontal", s.offsetX, -180..180, " dp") { v -> s.set { copy(offsetX = v) } }
            SliderRow("Vertical", s.offsetY, -40..160, " dp", divider = false) { v -> s.set { copy(offsetY = v) } }
        }
        Group(footer = "A ilha cresce um pouco quando há música ou chamada, como no iPhone.") {
            SwitchRow("Expandir quando ativa", s.growWhenActive, divider = false) { v -> s.set { copy(growWhenActive = v) } }
        }
        Group("Arredondamento") {
            SliderRow("Ilha pequena", s.smallRadius, 0..50, "%") { v -> s.set { copy(smallRadius = v) } }
            SliderRow("Ilha grande", s.largeRadius, 8..48, " dp", divider = false) { v -> s.set { copy(largeRadius = v) } }
        }
        WidthGroup(s)
        Group("Cores", footer = "A cor automática usa a capa da música ou a foto da notificação.") {
            SwitchRow("Cor de fundo automática", s.autoColor) { v -> s.set { copy(autoColor = v) } }
            ColorRow("Cor de fundo", s.bgColor) { v -> s.set { copy(bgColor = v) } }
            ColorRow("Cor do texto", s.textColor, divider = false) { v -> s.set { copy(textColor = v) } }
        }
        Group("Animação") {
            ChoiceRows(Motion.entries, s.motion, { it.label }) { v -> s.set { copy(motion = v) } }
        }
        Group("Borda", footer = "Uma borda fina ajuda a ver a ilha em telas escuras.") {
            SwitchRow("Borda na ilha pequena", s.borderSmall) { v -> s.set { copy(borderSmall = v) } }
            SwitchRow("Borda na ilha grande", s.borderLarge) { v -> s.set { copy(borderLarge = v) } }
            ColorRow("Cor da borda", s.borderColor, divider = false) { v -> s.set { copy(borderColor = v) } }
        }
        Group("Modo paisagem") {
            SwitchRow("Mostrar na horizontal", s.showInLandscape, divider = false) { v -> s.set { copy(showInLandscape = v) } }
        }
    }
}

// ---------- notificações ----------

@Composable
fun NotificationsScreen(back: () -> Unit, go: (Screen) -> Unit) {
    val s by Prefs.state.collectAsState()
    Page("Notificação", back) {
        Group(footer = "Mostra as notificações na ilha.") {
            SwitchRow("Notificação", s.notifEnabled, divider = false) { v -> s.set { copy(notifEnabled = v) } }
        }
        Group("Estilo da notificação") {
            ChoiceRows(listOf(true, false), s.notifExpanded, { if (it) "Pop-up expandido" else "Pop-up pequeno" }) { v -> s.set { copy(notifExpanded = v) } }
        }
        Group("Tempo do pop-up") {
            SwitchRow("Ficar até ser lida", s.keepUntilRead, "Some só quando você tocar ou deslizar") { v -> s.set { copy(keepUntilRead = v) } }
            SliderRow("Some depois de", s.popupSeconds, 2..15, " s", divider = false) { v -> s.set { copy(popupSeconds = v) } }
        }
        Group {
            NavRow("Apps bloqueados", if (s.blockedNotifApps.isEmpty()) "Nenhum" else s.blockedNotifApps.size.toString()) { go(Screen.BlockedNotifApps) }
            SwitchRow("Deslizar para remover", s.swipeToDismiss, "Desliza para o lado e a notificação é apagada") { v -> s.set { copy(swipeToDismiss = v) } }
            SwitchRow("Mostrar conteúdo sensível", s.showSensitive, "Como códigos de verificação") { v -> s.set { copy(showSensitive = v) } }
            SwitchRow("Na tela de bloqueio", s.notifOnLockscreen) { v -> s.set { copy(notifOnLockscreen = v) } }
            SwitchRow("Botões da notificação", s.showNotifActions, "Responder, marcar como lida e outros, direto na ilha") { v -> s.set { copy(showNotifActions = v) } }
            NavRow("Ilha parada", s.idleStyle.label, divider = false) { go(Screen.Idle) }
        }
        Group("Não aparecem?", footer = "Desative e ative de novo o acesso às notificações do Notch.") {
            val ctx = LocalContext.current
            NavRow("Abrir acesso às notificações", divider = false) { open(ctx, "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS") }
        }
    }
}

// ---------- chamadas ----------

@Composable
fun CallsScreen(back: () -> Unit) {
    val s by Prefs.state.collectAsState()
    Page("Chamada", back) {
        Group(footer = "Chamadas recebidas aparecem na ilha com os botões de atender e recusar. Funciona com o app de telefone e com apps como WhatsApp, quando eles usam notificação de chamada.") {
            SwitchRow("Chamada", s.callEnabled, divider = false) { v -> s.set { copy(callEnabled = v) } }
        }
        Group("Chamada em andamento") {
            SwitchRow("Mostrar durante a chamada", s.showOngoingCall, "Duração e onda na ilha") { v -> s.set { copy(showOngoingCall = v) } }
            ColorRow("Cor da onda", s.callWaveColor, divider = false) { v -> s.set { copy(callWaveColor = v) } }
        }
    }
}

// ---------- música ----------

@Composable
fun MusicScreen(back: () -> Unit, go: (Screen) -> Unit) {
    val s by Prefs.state.collectAsState()
    Page("Reprodutor de música", back) {
        Group(footer = "Toque na ilha durante a música para abrir o player.") {
            SwitchRow("Reprodutor de música", s.musicEnabled, divider = false) { v -> s.set { copy(musicEnabled = v) } }
        }
        ReactiveBarsGroup(s)
        Group("Outras configurações") {
            SwitchRow("Capa desfocada no fundo", s.blurArt) { v -> s.set { copy(blurArt = v) } }
            SwitchRow("Botões de 10 segundos", s.seekButtons) { v -> s.set { copy(seekButtons = v) } }
            SwitchRow("Barra de volume no player", s.volumeInPlayer) { v -> s.set { copy(volumeInPlayer = v) } }
            SwitchRow("Deslizar troca a música", s.swipeChangesTrack, "Na ilha pequena: direita volta, esquerda avança") { v -> s.set { copy(swipeChangesTrack = v) } }
            SwitchRow("Só durante a reprodução", s.musicOnlyWhilePlaying, "Some quando a música é pausada") { v -> s.set { copy(musicOnlyWhilePlaying = v) } }
            NavRow("Apps bloqueados", if (s.blockedMusicApps.isEmpty()) "Nenhum" else s.blockedMusicApps.size.toString(), divider = false) { go(Screen.BlockedMusicApps) }
        }
    }
}

// ---------- carregamento ----------

@Composable
fun ChargingScreen(back: () -> Unit) {
    val s by Prefs.state.collectAsState()
    val ctx = LocalContext.current
    val info = remember { ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) }
    Page("Carregamento", back) {
        Group(footer = "Mostra uma animação na ilha ao ligar e desligar o carregador.") {
            SwitchRow("Animação de carregamento", s.chargingEnabled, divider = false) { v -> s.set { copy(chargingEnabled = v) } }
        }
        Group("Informações da bateria") {
            val level = info?.let { it.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) * 100 / it.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1) }
            val status = when (info?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)) {
                BatteryManager.BATTERY_STATUS_CHARGING -> "Carregando"
                BatteryManager.BATTERY_STATUS_FULL -> "Completa"
                BatteryManager.BATTERY_STATUS_DISCHARGING, BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Na bateria"
                else -> "—"
            }
            val health = when (info?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)) {
                BatteryManager.BATTERY_HEALTH_GOOD -> "Boa"
                BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Superaquecida"
                BatteryManager.BATTERY_HEALTH_COLD -> "Fria"
                BatteryManager.BATTERY_HEALTH_DEAD -> "Ruim"
                else -> "—"
            }
            InfoLine("Nível", level?.let { "$it%" } ?: "—")
            InfoLine("Estado", status)
            InfoLine("Saúde", health)
            InfoLine("Temperatura", info?.let { "%.1f °C".format(it.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f) } ?: "—")
            InfoLine("Tensão", info?.let { "%.2f V".format(it.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) / 1000f) } ?: "—", divider = false)
        }
        Group {
            NavRow("Testar animação", divider = false) {
                val lvl = (ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                IslandBus.show(Popup.Charging(ChargingInfo(lvl, true)))
            }
        }
    }
}

@Composable
private fun InfoLine(title: String, value: String, divider: Boolean = true) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth().heightIn(min = 46.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = p.text, fontSize = 17.sp, modifier = Modifier.weight(1f))
        Text(value, color = p.secondary, fontSize = 17.sp)
    }
    if (divider) Divider()
}

// ---------- bluetooth ----------

@Composable
fun BluetoothScreen(back: () -> Unit) {
    val s by Prefs.state.collectAsState()
    val ctx = LocalContext.current
    var granted by remember { mutableStateOf(btGranted(ctx)) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    Page("Conexão Bluetooth", back) {
        Group(footer = "Mostra na ilha o fone, relógio ou caixa de som que acabou de conectar, com a bateria quando o aparelho informa.") {
            SwitchRow("Conexão Bluetooth", s.bluetoothEnabled, divider = false) { v ->
                s.set { copy(bluetoothEnabled = v) }
                if (v && !granted && Build.VERSION.SDK_INT >= 31) ask.launch(Manifest.permission.BLUETOOTH_CONNECT)
            }
        }
        if (!granted && Build.VERSION.SDK_INT >= 31) {
            Group(footer = "Sem essa permissão o Android não avisa o Notch quando um aparelho conecta.") {
                NavRow("Permitir dispositivos próximos", divider = false) { ask.launch(Manifest.permission.BLUETOOTH_CONNECT) }
            }
        }
    }
}

private fun btGranted(ctx: Context) = Build.VERSION.SDK_INT < 31 ||
    ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

// ---------- borda animada ----------

@Composable
fun BorderScreen(back: () -> Unit) {
    val s by Prefs.state.collectAsState()
    Page("Borda animada", back) {
        Group("Tipo de animação", footer = "Uma borda colorida gira em volta da ilha pequena.") {
            ChoiceRows(BorderAnim.entries, s.borderAnim, { it.label }) { v -> s.set { copy(borderAnim = v) } }
        }
        Group {
            SliderRow("Velocidade", s.borderAnimSpeed, 1..6) { v -> s.set { copy(borderAnimSpeed = v) } }
            SliderRow("Espessura", s.borderAnimWidth, 1..4, " dp", divider = s.borderAnim == BorderAnim.Single) { v -> s.set { copy(borderAnimWidth = v) } }
            if (s.borderAnim == BorderAnim.Single) ColorRow("Cor", s.borderAnimColor, divider = false) { v -> s.set { copy(borderAnimColor = v) } }
        }
    }
}

// ---------- gestos ----------

@Composable
fun GesturesScreen(back: () -> Unit) {
    val s by Prefs.state.collectAsState()
    Page("Toque e segurar", back) {
        Group("Toque", footer = "Tocar numa notificação sempre abre a notificação.") {
            ChoiceRows(Gesture.entries, s.tapGesture, { it.label }) { v -> s.set { copy(tapGesture = v) } }
        }
        Group("Pressionar e segurar") {
            ChoiceRows(Gesture.entries, s.holdGesture, { it.label }) { v -> s.set { copy(holdGesture = v) } }
        }
        Group(footer = "Deslize a ilha para o lado para fechar um aviso ou recolher a ilha aberta.") {}
    }
}

// ---------- outras ----------

@Composable
fun OtherScreen(back: () -> Unit) {
    val s by Prefs.state.collectAsState()
    Page("Outras configurações", back) {
        Group("Ilha dupla", footer = "Quando há duas atividades (por exemplo, chamada e música), a segunda aparece numa bolinha ao lado.") {
            SwitchRow("Ativar ilha dupla", s.dualIsland) { v -> s.set { copy(dualIsland = v) } }
            ChoiceRows(listOf(false, true), s.dualOnRight, { if (it) "Lado direito" else "Lado esquerdo" }) { v -> s.set { copy(dualOnRight = v) } }
        }
        Group("Visibilidade e ocultação automática") {
            SwitchRow("Ocultar na tela de bloqueio", s.hideOnLockscreen) { v -> s.set { copy(hideOnLockscreen = v) } }
            SwitchRow("Ocultar quando inativa", s.autoHideSeconds > 0, "Some quando não há nada acontecendo") { v -> s.set { copy(autoHideSeconds = if (v) 5 else 0) } }
            if (s.autoHideSeconds > 0) {
                SliderRow("Ocultar depois de", s.autoHideSeconds, 2..30, " s") { v -> s.set { copy(autoHideSeconds = v) } }
                SwitchRow("Manter mini ilha visível", s.keepMiniWhenHidden, "Uma bolinha em cima da câmera continua tocável") { v -> s.set { copy(keepMiniWhenHidden = v) } }
            }
        }
        Group("Vibração") {
            SwitchRow("Ativar vibração", s.vibrate, divider = false) { v -> s.set { copy(vibrate = v) } }
        }
        Group("Aparência") {
            ChoiceRows(AppTheme.entries, s.appTheme, { it.label }) { v -> s.set { copy(appTheme = v) } }
        }
    }
}

// ---------- seletor de apps ----------

private data class AppEntry(val pkg: String, val label: String, val icon: Bitmap?)

@Composable
fun AppPickerScreen(title: String, footer: String, selected: Set<String>, back: () -> Unit, onChange: (Set<String>) -> Unit) {
    val ctx = LocalContext.current
    val p = LocalPalette.current
    var query by remember { mutableStateOf("") }
    val apps by produceState<List<AppEntry>?>(null) { value = withContext(Dispatchers.IO) { launchableApps(ctx) } }
    Page(title, back) {
        OutlinedTextField(
            value = query, onValueChange = { query = it }, singleLine = true,
            placeholder = { Text("Pesquisar") },
            modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
        )
        val list = apps
        if (list == null) {
            InfoText("Carregando apps…")
        } else {
            val shown = list.filter { query.isBlank() || it.label.contains(query, ignoreCase = true) }
                .sortedWith(compareByDescending<AppEntry> { it.pkg in selected }.thenBy { it.label.lowercase() })
            Group(footer = footer) {
                shown.forEachIndexed { i, a ->
                    val on = a.pkg in selected
                    Row(
                        Modifier.fillMaxWidth().clickable { onChange(if (on) selected - a.pkg else selected + a.pkg) }
                            .heightIn(min = 52.dp).padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val img = remember(a.pkg) { a.icon?.asImageBitmap() }
                        if (img != null) Image(img, null, Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)))
                        Spacer(Modifier.width(12.dp))
                        Text(a.label, color = p.text, fontSize = 17.sp, modifier = Modifier.weight(1f))
                        if (on) Icon(Icons.Rounded.Check, null, tint = Accent, modifier = Modifier.size(22.dp))
                    }
                    if (i < shown.lastIndex) Divider()
                }
            }
        }
    }
}

private fun launchableApps(ctx: Context): List<AppEntry> {
    val pm = ctx.packageManager
    val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(i, 0)
        .distinctBy { it.activityInfo.packageName }
        .filter { it.activityInfo.packageName != ctx.packageName }
        .map {
            AppEntry(
                it.activityInfo.packageName,
                it.loadLabel(pm).toString(),
                runCatching { it.loadIcon(pm).toBitmapSafe()?.let { b -> Bitmap.createScaledBitmap(b, 96, 96, true) } }.getOrNull(),
            )
        }
}

// ---------- atualização ----------

private sealed interface UpdateStatus {
    data object Checking : UpdateStatus
    data object UpToDate : UpdateStatus
    data class Available(val r: Updater.Release) : UpdateStatus
    data class Downloading(val p: Float) : UpdateStatus
    data class Failed(val msg: String) : UpdateStatus
}

@Composable
fun UpdateScreen(back: () -> Unit) {
    val ctx = LocalContext.current
    val p = LocalPalette.current
    val scope = rememberCoroutineScope()
    val (code, name) = remember { Updater.currentVersion(ctx) }
    var status by remember { mutableStateOf<UpdateStatus>(UpdateStatus.Checking) }

    fun check() {
        status = UpdateStatus.Checking
        scope.launch {
            status = runCatching { Updater.latest() }.fold(
                onSuccess = { if (it.versionCode > code) UpdateStatus.Available(it) else UpdateStatus.UpToDate },
                onFailure = { UpdateStatus.Failed("Não consegui verificar (sem internet?)") },
            )
        }
    }
    LaunchedEffect(Unit) { check() }

    Page("Atualização", back) {
        Group {
            InfoLine("Versão instalada", name, divider = false)
        }
        Group {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (val st = status) {
                    UpdateStatus.Checking -> Text("Verificando…", color = p.secondary, fontSize = 15.sp)
                    UpdateStatus.UpToDate -> Text("Você está na versão mais nova.", color = SwitchGreen, fontSize = 15.sp)
                    is UpdateStatus.Failed -> Text(st.msg, color = p.secondary, fontSize = 15.sp)
                    is UpdateStatus.Downloading -> Text("Baixando… ${(st.p * 100).toInt()}%", color = p.secondary, fontSize = 15.sp)
                    is UpdateStatus.Available -> {
                        Text("Nova versão ${st.r.versionName}", color = p.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        if (st.r.notes.isNotBlank()) Text(st.r.notes, color = p.secondary, fontSize = 14.sp)
                        Button(
                            onClick = {
                                scope.launch {
                                    runCatching {
                                        val f = Updater.download(ctx, st.r) { pr -> status = UpdateStatus.Downloading(pr) }
                                        Updater.install(ctx, f)
                                        status = UpdateStatus.Available(st.r)
                                    }.onFailure { status = UpdateStatus.Failed("O download falhou. Tente de novo.") }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Accent),
                        ) { Text("Baixar e instalar") }
                    }
                }
            }
        }
        if (status !is UpdateStatus.Available && status !is UpdateStatus.Downloading && status !is UpdateStatus.Checking) {
            Group { NavRow("Verificar de novo", divider = false) { check() } }
        }
    }
}

// ---------- ajuda ----------

@Composable
fun HelpScreen(back: () -> Unit) {
    val ctx = LocalContext.current
    var crash by remember { mutableStateOf(CrashLog.read(ctx)) }
    Page("Ajuda", back) {
        crash?.let { log ->
            Group("O app fechou sozinho", footer = "Toque em Copiar e cole a mensagem na conversa para eu corrigir.") {
                InfoText(log.lineSequence().take(4).joinToString("\n"))
                NavRow("Copiar relatório do erro") {
                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("Erro do Notch", log))
                    android.widget.Toast.makeText(ctx, "Copiado", android.widget.Toast.LENGTH_SHORT).show()
                }
                NavRow("Apagar", divider = false) {
                    CrashLog.clear(ctx)
                    crash = null
                }
            }
        }
        Group("A ilha some sozinha?") {
            InfoText("Alguns celulares (Motorola, Xiaomi, Samsung) fecham apps em segundo plano. Em Informações do app → Bateria, escolha \"Sem restrição\" e, se existir, ative a inicialização automática.")
        }
        Group("Apareceu um ícone do Notch na lateral da tela") {
            InfoText("É o botão de atalho da acessibilidade do Android, que alguns celulares ligam junto com o serviço. Ele não é a ilha e pode ser desligado sem afetar nada: na tela abaixo, desligue \"Atalho do Notch\" (ou \"Botão de acessibilidade\") e deixe só \"Usar Notch\" ligado.")
            NavRow("Abrir ajuste da acessibilidade do Notch", divider = false) { openA11yDetails(ctx) }
        }
        Group("Não consigo ativar a acessibilidade") {
            InfoText("No Android 13 ou mais novo, apps instalados fora da Play Store precisam de liberação: Informações do app → ⋮ → \"Permitir configurações restritas\". Depois ative em Acessibilidade.")
        }
        Group("A ilha não fica em cima da câmera") {
            InfoText("Em Exibição, ajuste Horizontal e Vertical. Se o celular não informar a posição da câmera, desligue \"Centralizar na câmera\" e posicione à mão.")
        }
        Group("O que o Notch lê?") {
            InfoText("Só as notificações, o player de música e o nome do app aberto (para esconder a ilha nos apps escolhidos). Nada sai do celular; a internet só é usada para procurar atualizações.")
        }
    }
}

// ---------- utilidades ----------

/** O Android pôs o Notch no botão flutuante da acessibilidade? */
fun a11yShortcutOn(ctx: Context): Boolean {
    val me = ComponentName(ctx, IslandService::class.java)
    val v = runCatching { AndroidSettings.Secure.getString(ctx.contentResolver, "accessibility_button_targets") }.getOrNull().orEmpty()
    return v.split(':').any { ComponentName.unflattenFromString(it) == me }
}

/** Abre a tela da acessibilidade do próprio Notch, onde fica o interruptor do atalho. */
fun openA11yDetails(ctx: Context) {
    if (Build.VERSION.SDK_INT >= 31) {
        val i = Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS")
            .putExtra(Intent.EXTRA_COMPONENT_NAME, ComponentName(ctx, IslandService::class.java).flattenToString())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { ctx.startActivity(i) }.isSuccess) return
    }
    open(ctx, AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS)
}

fun a11yEnabled(ctx: Context): Boolean {
    val list = AndroidSettings.Secure.getString(ctx.contentResolver, AndroidSettings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
    val me = ComponentName(ctx, IslandService::class.java).flattenToString()
    return list.split(':').any { it.equals(me, ignoreCase = true) }
}

fun listenerEnabled(ctx: Context): Boolean {
    val list = AndroidSettings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners").orEmpty()
    return list.contains(ComponentName(ctx, NotifListener::class.java).flattenToString())
}

fun open(ctx: Context, action: String) {
    runCatching { ctx.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

// ---------- barrinhas que reagem ao som ----------

@Composable
private fun ReactiveBarsGroup(s: Settings) {
    val ctx = LocalContext.current
    var granted by remember { mutableStateOf(AudioLevels.granted(ctx)) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        if (ok) s.set { copy(reactiveBars = true) }
    }
    Group(
        "Animação do som",
        footer = "As barrinhas seguem a música de verdade. O Android pede a permissão de microfone para isso, mas o Notch não grava nada: só lê a intensidade do som que está tocando.",
    ) {
        SwitchRow("Barrinhas reagem ao som", s.reactiveBars && granted, divider = false) { v ->
            if (v && !granted) ask.launch(Manifest.permission.RECORD_AUDIO) else s.set { copy(reactiveBars = v) }
        }
    }
}

// ---------- personalizar ----------

@Composable
fun CustomizeScreen(back: () -> Unit) {
    val s by Prefs.state.collectAsState()
    Page("Personalizar", back, action = {
        TextAction("Redefinir") {
            val d = Settings()
            s.set { copy(textScale = d.textScale, iconScale = d.iconScale, layoutScale = d.layoutScale, listRows = d.listRows) }
        }
    }) {
        Group("Testar", footer = "Mude os valores e toque em um teste para ver na ilha.") {
            NavRow("Mostrar aviso de teste") { IslandBus.show(Popup.Test) }
            NavRow("Mostrar carregamento", divider = false) {
                IslandBus.show(Popup.Charging(ChargingInfo(80, true)))
            }
        }
        Group("Texto e ícones", footer = "Se o texto da ilha ficar cortado ou grande demais, ajuste aqui. A ilha não segue o tamanho de fonte do sistema.") {
            SliderRow("Tamanho do texto", s.textScale, 70..150, "%") { v -> s.set { copy(textScale = v) } }
            SliderRow("Tamanho dos ícones e fotos", s.iconScale, 60..150, "%", divider = false) { v -> s.set { copy(iconScale = v) } }
        }
        Group("Ilha aberta", footer = "Altura das ilhas grandes (notificação, música, chamada, bateria).") {
            SliderRow("Altura dos layouts", s.layoutScale, 70..150, "%") { v -> s.set { copy(layoutScale = v) } }
            SliderRow("Itens visíveis na lista de notificações", s.listRows, 2..8, divider = false) { v -> s.set { copy(listRows = v) } }
        }
        Group("Tamanho e posição da ilha") {
            SliderRow("Largura", s.width, 40..240, " dp") { v -> s.set { copy(width = v) } }
            SliderRow("Altura", s.height, 16..60, " dp") { v -> s.set { copy(height = v) } }
            SliderRow("Horizontal", s.offsetX, -180..180, " dp") { v -> s.set { copy(offsetX = v) } }
            SliderRow("Vertical", s.offsetY, -40..160, " dp", divider = false) { v -> s.set { copy(offsetY = v) } }
        }
        WidthGroup(s)
    }
}

@Composable
private fun WidthGroup(s: Settings) {
    Group("Ilha aberta", footer = "Largura das notificações, da música e das chamadas quando a ilha abre.") {
        SwitchRow("Ocupar a largura da tela", s.fullWidth) { v -> s.set { copy(fullWidth = v) } }
        if (s.fullWidth) {
            SliderRow("Margem dos lados", s.sideMargin, 0..32, " dp") { v -> s.set { copy(sideMargin = v) } }
        } else {
            SliderRow("Largura máxima", s.largeMaxWidth, 260..440, " dp") { v -> s.set { copy(largeMaxWidth = v) } }
        }
        SliderRow("Distância da câmera ao abrir", s.largeTopGap, 0..48, " dp", divider = false) { v -> s.set { copy(largeTopGap = v) } }
    }
}

// ---------- recursos ----------

@Composable
fun FeaturesScreen(back: () -> Unit, go: (Screen) -> Unit) {
    val s by Prefs.state.collectAsState()
    Page("Recursos", back) {
        Group {
            NavRow("Controles rápidos", null, Icons.Rounded.Tune, Color(0xFF0A84FF)) { go(Screen.Controls) }
            NavRow("Ao vivo na ilha", onOff(s.liveEnabled || s.navEnabled || s.alarmEnabled), Icons.Rounded.Timer, Color(0xFFFF9500)) { go(Screen.Live) }
            NavRow("Notificação", onOff(s.notifEnabled), Icons.Rounded.Notifications, Color(0xFFFF3B30)) { go(Screen.Notifications) }
            NavRow("Chamada", onOff(s.callEnabled), Icons.Rounded.Call, SwitchGreen) { go(Screen.Calls) }
            NavRow("Reprodutor de música", onOff(s.musicEnabled), Icons.Rounded.MusicNote, Color(0xFFFF2D55)) { go(Screen.Music) }
            NavRow("Animação de carregamento", onOff(s.chargingEnabled), Icons.Rounded.BatteryChargingFull, Color(0xFF30B94D)) { go(Screen.Charging) }
            NavRow("Conexão Bluetooth", onOff(s.bluetoothEnabled), Icons.Rounded.Bluetooth, Accent) { go(Screen.Bluetooth) }
            NavRow("Borda animada", onOff(s.borderAnim != BorderAnim.Off), Icons.Rounded.Animation, Color(0xFFAF52DE), divider = false) { go(Screen.Border) }
        }
    }
}

// ---------- controles rápidos ----------

@Composable
fun ControlsScreen(back: () -> Unit, go: (Screen) -> Unit) {
    val s by Prefs.state.collectAsState()
    val ctx = LocalContext.current
    val nm = remember { ctx.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager }
    var canWrite by remember { mutableStateOf(AndroidSettings.System.canWrite(ctx)) }
    var dnd by remember { mutableStateOf(nm.isNotificationPolicyAccessGranted) }
    val lifecycle = LocalLifecycleOwner.current
    LaunchedEffect(Unit) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            canWrite = AndroidSettings.System.canWrite(ctx)
            dnd = nm.isNotificationPolicyAccessGranted
        }
    }
    Page("Controles rápidos", back) {
        Group(footer = "Com a ilha parada no estilo Simples, tocar nela abre os controles: lanterna, Wi‑Fi, Bluetooth, Não perturbe, som, rotação, brilho, volume e seus atalhos. Também dá para pôr em Toque e segurar.") {
            NavRow("Atalhos de apps", if (s.controlsApps.isEmpty()) "Nenhum" else "${s.controlsApps.size}", divider = false) { go(Screen.ControlsApps) }
        }
        Group("Permissões", footer = "Brilho e rotação precisam de \"Alterar configurações do sistema\". Não perturbe e o modo silencioso precisam do acesso ao Não perturbe.") {
            NavRow("Alterar configurações do sistema", onOff(canWrite)) {
                runCatching { ctx.startActivity(Intent(AndroidSettings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${ctx.packageName}"))) }
            }
            NavRow("Acesso ao Não perturbe", onOff(dnd), divider = false) {
                runCatching { ctx.startActivity(Intent(AndroidSettings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) }
            }
        }
        Group("Volume") {
            SwitchRow("Mostrar o volume na ilha", s.volumePopup, subtitle = "Barra na ilha ao usar os botões de volume", divider = false) { v -> s.set { copy(volumePopup = v) } }
        }
        Group("Jogos e Não perturbe", footer = "Os jogos são reconhecidos pela categoria que o próprio app informa ao Android.") {
            SwitchRow("Modo de jogo", s.gameMode, subtitle = "Em jogos, a ilha some e só chamadas aparecem") { v -> s.set { copy(gameMode = v) } }
            SwitchRow("Respeitar o Não perturbe", s.respectDnd, subtitle = "Sem avisos de notificação com o Não perturbe ligado", divider = false) { v -> s.set { copy(respectDnd = v) } }
        }
    }
}

// ---------- ao vivo ----------

@Composable
fun LiveScreen(back: () -> Unit) {
    val s by Prefs.state.collectAsState()
    Page("Ao vivo na ilha", back) {
        Group(
            "Atividades",
            footer = "A ilha lê as notificações do app de relógio e do Maps/Waze. Tocar abre o painel com os botões do app (pausar, parar, sair); segurar abre o app.",
        ) {
            SwitchRow("Timer e cronômetro", s.liveEnabled, subtitle = "O tempo conta ao vivo na ilha") { v -> s.set { copy(liveEnabled = v) } }
            SwitchRow("Alarme tocando", s.alarmEnabled, subtitle = "Ilha grande com soneca e parar") { v -> s.set { copy(alarmEnabled = v) } }
            SwitchRow("Navegação (Maps, Waze)", s.navEnabled, subtitle = "Próxima curva e distância", divider = false) { v -> s.set { copy(navEnabled = v) } }
        }
        Group(footer = "O Notch não grava nada: só mostra o tempo enquanto o gravador do celular estiver gravando.") {
            SwitchRow("Gravação de tela e de voz", s.recorderEnabled) { v -> s.set { copy(recorderEnabled = v) } }
            SliderRow("Ajuste fino do tempo", s.liveOffsetMs / 100, -15..15, divider = false, format = { v ->
                if (v == 0) "0 s" else "%+.1f s".format(v / 10f).replace('.', ',')
            }) { v -> s.set { copy(liveOffsetMs = v * 100) } }
        }
        Group(footer = "Um pontinho verde aparece quando algum app usa a câmera e laranja quando usa o microfone.") {
            SwitchRow("Indicador de câmera e microfone", s.privacyDot, divider = false) { v -> s.set { copy(privacyDot = v) } }
        }
        Group("Bateria") {
            SwitchRow("Avisar bateria fraca e cheia", s.batteryAlerts, divider = s.batteryAlerts) { v -> s.set { copy(batteryAlerts = v) } }
            if (s.batteryAlerts) SliderRow("Bateria fraca em", s.lowBatteryLevel, 5..30, "%", divider = false) { v -> s.set { copy(lowBatteryLevel = v) } }
        }
        Group {
            NavRow("Testar aviso de bateria fraca", divider = false) { IslandBus.show(Popup.Reminder(ReminderKind.BatteryLow)) }
        }
    }
}

// ---------- ilha parada ----------

@Composable
fun IdleScreen(back: () -> Unit) {
    val s by Prefs.state.collectAsState()
    val p = LocalPalette.current
    val styles = IdleStyle.entries.filterNot { it.hidden }
    val pager = rememberPagerState(initialPage = styles.indexOf(s.idleStyle).coerceAtLeast(0)) { styles.size }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    // Passos precisam da permissão de "atividade física" (Android 10+).
    var pendingStyle by remember { mutableStateOf<IdleStyle?>(null) }
    val askSteps = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        pendingStyle?.let { st -> s.set { copy(idleStyle = st) } }
        Health.startSteps(ctx)
    }
    val pick: (IdleStyle) -> Unit = { st ->
        val needsSteps = st == IdleStyle.Steps || st == IdleStyle.Health
        if (needsSteps && !Health.hasStepPermission(ctx)) {
            pendingStyle = st
            askSteps.launch(Manifest.permission.ACTIVITY_RECOGNITION)
        } else s.set { copy(idleStyle = st) }
    }
    Page("Ilha parada", back) {
        Text(
            "Deslize para ver os estilos e toque no card para usar. Tocar na ilha abre o layout do estilo.",
            color = p.secondary, fontSize = 14.sp, modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 14.dp),
        )
        // Painel com degradê roxo → rosa e o carrossel de cards.
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Brush.horizontalGradient(listOf(Color(0xFF7E2E9C), Color(0xFFB0408F), Color(0xFFE2487A))))
                .padding(vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Estilos", color = Color(0xFFF6E8FA), fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(18.dp))
            HorizontalPager(
                state = pager,
                contentPadding = PaddingValues(horizontal = 56.dp),
                pageSpacing = 12.dp,
                modifier = Modifier.fillMaxWidth(),
            ) { page ->
                val style = styles[page]
                // O card do meio fica maior; os dos lados encolhem e ficam um pouco transparentes.
                val offset = ((pager.currentPage - page) + pager.currentPageOffsetFraction).let { kotlin.math.abs(it) }.coerceIn(0f, 1f)
                val scale = 1f - 0.12f * offset
                IdleCard(
                    style, style == s.idleStyle,
                    Modifier.graphicsLayer { scaleX = scale; scaleY = scale; alpha = 1f - 0.35f * offset },
                ) {
                    if (pager.currentPage != page) scope.launch { pager.animateScrollToPage(page) }
                    else pick(style)
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                styles.indices.forEach { i ->
                    Box(
                        Modifier.height(6.dp).width(if (i == pager.currentPage) 18.dp else 6.dp).clip(CircleShape)
                            .background(Color.White.copy(alpha = if (i == pager.currentPage) 0.95f else 0.45f)),
                    )
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Group(footer = "Em \"Ícones dos apps\" e \"Número\", a ilha volta ao tamanho simples quando não há notificações.") {
            NavRow("Usando agora", s.idleStyle.label) { scope.launch { pager.animateScrollToPage(styles.indexOf(s.idleStyle)) } }
            NavRow("Testar", divider = false) { IslandBus.show(Popup.Test) }
        }
    }
}

@Composable
private fun HealthGroup(s: Settings) {
    Group(
        "Saúde",
        footer = "Os passos vêm do sensor do celular e a água você marca tocando na ilha aberta. Tudo fica só no aparelho e zera à meia-noite.",
    ) {
        SliderRow("Meta de passos", s.stepGoal / 500, 4..30, format = { formatSteps(it * 500) }) { v -> s.set { copy(stepGoal = v * 500) } }
        SliderRow("Meta de água", s.waterGoal, 4..16, " copos") { v -> s.set { copy(waterGoal = v) } }
        // De 20:00 a 02:00, de meia em meia hora.
        val bedIdx = ((s.bedtime - 20 * 60 + 24 * 60) % (24 * 60)) / 30
        SliderRow("Hora de dormir", bedIdx.coerceIn(0, 12), 0..12, format = { i ->
            val m = (20 * 60 + i * 30) % (24 * 60)
            "%02d:%02d".format(m / 60, m % 60)
        }) { i -> s.set { copy(bedtime = (20 * 60 + i * 30) % (24 * 60)) } }
        SwitchRow("Lembrar de fazer pausas", s.breakReminder, subtitle = "Avisa depois de muito tempo seguido na tela") { v -> s.set { copy(breakReminder = v) } }
        if (s.breakReminder) SliderRow("Pausa a cada", s.breakMinutes, 10..90, " min") { v -> s.set { copy(breakMinutes = v) } }
        SwitchRow("Lembrar de beber água", s.waterReminder, subtitle = "A cada 1 h 30 até bater a meta", divider = false) { v -> s.set { copy(waterReminder = v) } }
    }
}

private val idleHints = mapOf(
    IdleStyle.Simple to "Só a pílula preta",
    IdleStyle.AppIcons to "Abre a lista de notificações",
    IdleStyle.Count to "Abre a lista de notificações",
    IdleStyle.Clock to "Abre o relógio",
    IdleStyle.Date to "Abre o relógio",
    IdleStyle.Battery to "Abre a bateria",
    IdleStyle.ClockBattery to "Abre o relógio",
    IdleStyle.NetSpeed to "Abre o painel do sistema",
    IdleStyle.Memory to "Abre o painel do sistema",
    IdleStyle.Storage to "Abre o painel do sistema",
    IdleStyle.Alarm to "Abre o relógio",
    IdleStyle.Greeting to "Abre o relógio",
    IdleStyle.Pulse to "Uma luz verde respirando",
    IdleStyle.Health to "Abre o painel de saúde",
    IdleStyle.Steps to "Abre o painel de saúde",
    IdleStyle.Water to "Toque na ilha aberta: +1 copo",
    IdleStyle.ScreenBreak to "Avisa a hora de descansar",
    IdleStyle.Breathing to "Abre a respiração guiada",
    IdleStyle.Bedtime to "Quanto falta para dormir",
)

/** Card claro como na referência: área de imagem com a ilha de exemplo, título e descrição. */
@Composable
private fun IdleCard(style: IdleStyle, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xFFFBF3FD))
            .border(if (selected) 3.dp else 0.dp, if (selected) Color.White else Color.Transparent, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        Box(
            Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFFEFC6F7)),
            contentAlignment = Alignment.Center,
        ) {
            IdlePill(style, Modifier)
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(style.label, color = Color(0xFF3B1748), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1)
            if (selected) Icon(Icons.Rounded.CheckCircle, null, tint = Color(0xFFB0408F), modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(6.dp))
        Box(Modifier.clip(RoundedCornerShape(10.dp)).background(Color(0xFFEFC6F7)).padding(horizontal = 10.dp, vertical = 4.dp)) {
            Text(if (selected) "Em uso" else idleHints[style].orEmpty(), color = Color(0xFF7E2E9C), fontSize = 12.sp, maxLines = 1)
        }
    }
}

/** Desenho da ilha com dados de exemplo. */
@Composable
private fun IdlePill(style: IdleStyle, modifier: Modifier) {
    val small = style == IdleStyle.Simple
    val txt: @Composable (String) -> Unit = { Text(it, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
    val dot: @Composable (Color) -> Unit = { c -> Box(Modifier.size(16.dp).clip(CircleShape).background(c)) }
    val icon: @Composable (androidx.compose.ui.graphics.vector.ImageVector, Color) -> Unit = { v, c -> Icon(v, null, tint = c, modifier = Modifier.size(15.dp)) }
    Row(
        modifier.width(if (small) 84.dp else 156.dp).height(28.dp).clip(RoundedCornerShape(14.dp)).background(Color.Black).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        when (style) {
            IdleStyle.Simple -> {}
            IdleStyle.AppIcons -> { dot(Color(0xFF25D366)); dot(Color(0xFFE1306C)) }
            IdleStyle.Count -> { icon(Icons.Rounded.Notifications, Color(0xFFB4B4BC)); txt("3") }
            IdleStyle.Clock -> { txt("02:12"); Spacer(Modifier.width(1.dp)) }
            IdleStyle.Date -> { txt("qui 30"); Spacer(Modifier.width(1.dp)) }
            IdleStyle.Battery -> { Spacer(Modifier.width(1.dp)); BatteryMini() }
            IdleStyle.ClockBattery -> { txt("02:12"); BatteryMini() }
            IdleStyle.NetSpeed -> { icon(Icons.Rounded.SwapVert, Color(0xFF64D2FF)); txt("1.2 MB/s") }
            IdleStyle.Memory -> { icon(Icons.Rounded.Memory, Color(0xFFBF5AF2)); txt("41%") }
            IdleStyle.Storage -> { icon(Icons.Rounded.Storage, Color(0xFFFF9F0A)); txt("64 GB") }
            IdleStyle.Alarm -> { icon(Icons.Rounded.Alarm, Color(0xFFFF9F0A)); txt("sex 04:00") }
            IdleStyle.Greeting -> { Text("🌙", fontSize = 11.sp); txt("Boa noite") }
            IdleStyle.Pulse -> { Spacer(Modifier.width(1.dp)); Box(Modifier.size(9.dp).clip(CircleShape).background(SwitchGreen)) }
            IdleStyle.Health -> {
                Ring(0.62f, StepColor, 20.dp, 2.dp) { Icon(Icons.AutoMirrored.Rounded.DirectionsWalk, null, tint = StepColor, modifier = Modifier.size(11.dp)) }
                Row(verticalAlignment = Alignment.CenterVertically) { icon(Icons.Rounded.WaterDrop, WaterColor); txt("5") }
            }
            IdleStyle.Steps -> { icon(Icons.AutoMirrored.Rounded.DirectionsWalk, StepColor); txt("4.230") }
            IdleStyle.Water -> { icon(Icons.Rounded.WaterDrop, WaterColor); txt("5/8") }
            IdleStyle.ScreenBreak -> { icon(Icons.Rounded.Visibility, EyeColor); txt("18 min") }
            IdleStyle.Breathing -> { icon(Icons.Rounded.Air, BreathColor); Box(Modifier.size(11.dp).clip(CircleShape).background(BreathColor)) }
            IdleStyle.Bedtime -> { icon(Icons.Rounded.Bedtime, SleepColor); txt("2 h 10") }
        }
    }
}

@Composable
private fun BatteryMini() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("58%", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(3.dp))
        Box(Modifier.width(17.dp).height(9.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF3A3A3C))) {
            Box(Modifier.fillMaxWidth(0.58f).height(9.dp).background(SwitchGreen))
        }
    }
}
