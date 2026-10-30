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
    Home, Display, Notifications, Calls, Music, Charging, Bluetooth, Border, Gestures, HiddenApps, Other,
    BlockedNotifApps, BlockedMusicApps, Update, Help,
}

private fun Settings.set(change: Settings.() -> Settings) = Prefs.update { it.change() }

// ---------- início ----------

@Composable
fun HomeScreen(go: (Screen) -> Unit) {
    val ctx = LocalContext.current
    val s by Prefs.state.collectAsState()
    var a11y by remember { mutableStateOf(a11yEnabled(ctx)) }
    var listener by remember { mutableStateOf(listenerEnabled(ctx)) }
    val lifecycle = LocalLifecycleOwner.current
    LaunchedEffect(Unit) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            a11y = a11yEnabled(ctx)
            listener = listenerEnabled(ctx)
        }
    }
    Page("Ilha", onBack = null) {
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
        Group {
            SwitchRow("Barra da Ilha", s.enabled) { v -> s.set { copy(enabled = v) } }
            NavRow("Exibição", divider = false) { go(Screen.Display) }
        }
        Group("Recursos") {
            NavRow("Notificação", onOff(s.notifEnabled), Icons.Rounded.Notifications, Color(0xFFFF3B30)) { go(Screen.Notifications) }
            NavRow("Chamada", onOff(s.callEnabled), Icons.Rounded.Call, SwitchGreen) { go(Screen.Calls) }
            NavRow("Reprodutor de música", onOff(s.musicEnabled), Icons.Rounded.MusicNote, Color(0xFFFF2D55)) { go(Screen.Music) }
            NavRow("Animação de carregamento", onOff(s.chargingEnabled), Icons.Rounded.BatteryChargingFull, Color(0xFF30B94D)) { go(Screen.Charging) }
            NavRow("Conexão Bluetooth", onOff(s.bluetoothEnabled), Icons.Rounded.Bluetooth, Accent) { go(Screen.Bluetooth) }
            NavRow("Borda animada", onOff(s.borderAnim != BorderAnim.Off), Icons.Rounded.Animation, Color(0xFFAF52DE), divider = false) { go(Screen.Border) }
        }
        Group {
            NavRow("Toque e segurar", icon = Icons.Rounded.TouchApp, iconColor = Color(0xFFFF9500)) { go(Screen.Gestures) }
            NavRow("Ocultar a ilha nos apps", if (s.hiddenApps.isEmpty()) null else s.hiddenApps.size.toString(), Icons.Rounded.VisibilityOff, Color(0xFF8E8E93)) { go(Screen.HiddenApps) }
            NavRow("Outras configurações", icon = Icons.Rounded.Settings, iconColor = Color(0xFF8E8E93), divider = false) { go(Screen.Other) }
        }
        Group {
            NavRow("Testar a ilha", icon = Icons.Rounded.Science, iconColor = Color(0xFF00C7BE)) { IslandBus.show(Popup.Test) }
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
        Group("Ilha grande", footer = "Largura máxima da ilha aberta (notificações, música, chamadas).") {
            SliderRow("Largura máxima", s.largeMaxWidth, 260..440, " dp", divider = false) { v -> s.set { copy(largeMaxWidth = v) } }
        }
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
            SwitchRow("Mostrar número de notificações", s.showCount, divider = false) { v -> s.set { copy(showCount = v) } }
        }
        Group("Não aparecem?", footer = "Desative e ative de novo o acesso às notificações do Ilha.") {
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
        Group("Outras configurações") {
            SwitchRow("Capa desfocada no fundo", s.blurArt) { v -> s.set { copy(blurArt = v) } }
            SwitchRow("Botões de 10 segundos", s.seekButtons) { v -> s.set { copy(seekButtons = v) } }
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
            Group(footer = "Sem essa permissão o Android não avisa o Ilha quando um aparelho conecta.") {
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
    Page("Ajuda", back) {
        Group("A ilha some sozinha?") {
            InfoText("Alguns celulares (Motorola, Xiaomi, Samsung) fecham apps em segundo plano. Em Informações do app → Bateria, escolha \"Sem restrição\" e, se existir, ative a inicialização automática.")
        }
        Group("Não consigo ativar a acessibilidade") {
            InfoText("No Android 13 ou mais novo, apps instalados fora da Play Store precisam de liberação: Informações do app → ⋮ → \"Permitir configurações restritas\". Depois ative em Acessibilidade.")
        }
        Group("A ilha não fica em cima da câmera") {
            InfoText("Em Exibição, ajuste Horizontal e Vertical. Se o celular não informar a posição da câmera, desligue \"Centralizar na câmera\" e posicione à mão.")
        }
        Group("O que o Ilha lê?") {
            InfoText("Só as notificações, o player de música e o nome do app aberto (para esconder a ilha nos apps escolhidos). Nada sai do celular; a internet só é usada para procurar atualizações.")
        }
    }
}

// ---------- utilidades ----------

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
