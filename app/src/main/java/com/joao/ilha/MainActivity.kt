package com.joao.ilha

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch

private val Card = Color(0xFF1A1A1F)
private val Accent = Color(0xFF7CF5B0)
private val Dim = Color(0xFF9C9CA6)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Accent, background = Color(0xFF0E0E10))) {
                Home()
            }
        }
    }

    fun a11yEnabled(): Boolean {
        val list = AndroidSettings.Secure.getString(contentResolver, AndroidSettings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        val me = ComponentName(this, IslandService::class.java).flattenToString()
        return list.split(':').any { it.equals(me, ignoreCase = true) }
    }

    fun listenerEnabled(): Boolean {
        val list = AndroidSettings.Secure.getString(contentResolver, "enabled_notification_listeners").orEmpty()
        return list.contains(ComponentName(this, NotifListener::class.java).flattenToString())
    }

    fun open(action: String) {
        runCatching { startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    fun openAppInfo() {
        runCatching {
            startActivity(Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
        }
    }
}

@Composable
private fun Home() {
    val act = androidx.compose.ui.platform.LocalContext.current as MainActivity
    val s by Prefs.state.collectAsState()
    var a11y by remember { mutableStateOf(act.a11yEnabled()) }
    var listener by remember { mutableStateOf(act.listenerEnabled()) }
    val lifecycle = LocalLifecycleOwner.current
    androidx.compose.runtime.LaunchedEffect(Unit) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            a11y = act.a11yEnabled()
            listener = act.listenerEnabled()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF0E0E10))
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(36.dp))
        Text("Ilha", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
        Text("Uma ilha em volta da câmera com notificações, música e carregamento.", color = Dim, fontSize = 14.sp)

        Section("Permissões") {
            Permission("Acessibilidade", "Desenha a ilha por cima dos apps.", a11y) {
                act.open(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS)
            }
            Permission("Acesso às notificações", "Mostra notificações e o player de música.", listener) {
                act.open("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
            }
            if (!a11y || !listener) {
                Text(
                    "Se a opção aparecer bloqueada (Android 13+), abra as informações do app, toque em ⋮ e em \"Permitir configurações restritas\".",
                    color = Dim, fontSize = 12.sp,
                )
                OutlinedButton(onClick = { act.openAppInfo() }) { Text("Abrir informações do app") }
            }
        }

        Section("Geral") {
            Toggle("Mostrar a ilha", s.enabled) { v -> Prefs.update { it.copy(enabled = v) } }
            Toggle("Notificações", s.showNotifications) { v -> Prefs.update { it.copy(showNotifications = v) } }
            Toggle("Música", s.showMedia) { v -> Prefs.update { it.copy(showMedia = v) } }
            Toggle("Carregamento", s.showCharging) { v -> Prefs.update { it.copy(showCharging = v) } }
            Toggle("Esconder na horizontal", s.hideInLandscape) { v -> Prefs.update { it.copy(hideInLandscape = v) } }
            Toggle("Vibrar", s.vibrate) { v -> Prefs.update { it.copy(vibrate = v) } }
            Range("Tempo do aviso", s.popupSeconds, 2..10, "s") { v -> Prefs.update { it.copy(popupSeconds = v) } }
        }

        Section("Tamanho e posição") {
            Toggle("Centralizar na câmera", s.alignToCamera) { v -> Prefs.update { it.copy(alignToCamera = v) } }
            Range("Largura", s.width, 60..220, "dp") { v -> Prefs.update { it.copy(width = v) } }
            Range("Altura", s.height, 20..60, "dp") { v -> Prefs.update { it.copy(height = v) } }
            Range("Horizontal", s.offsetX, -150..150, "dp") { v -> Prefs.update { it.copy(offsetX = v) } }
            Range("Vertical", s.offsetY, -30..120, "dp") { v -> Prefs.update { it.copy(offsetY = v) } }
            OutlinedButton(onClick = { Prefs.reset() }) { Text("Restaurar padrão") }
        }

        UpdateSection()

        Button(
            onClick = { IslandBus.show(Popup.Test) },
            enabled = a11y,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.Black),
        ) { Text("Testar a ilha") }
        Spacer(Modifier.height(24.dp))
    }
}

private sealed interface UpdateStatus {
    data object Idle : UpdateStatus
    data object Checking : UpdateStatus
    data object UpToDate : UpdateStatus
    data class Available(val r: Updater.Release) : UpdateStatus
    data class Downloading(val p: Float) : UpdateStatus
    data class Failed(val msg: String) : UpdateStatus
}

@Composable
private fun UpdateSection() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val (code, name) = remember { Updater.currentVersion(ctx) }
    var status by remember { mutableStateOf<UpdateStatus>(UpdateStatus.Idle) }

    fun check() {
        status = UpdateStatus.Checking
        scope.launch {
            status = runCatching { Updater.latest() }.fold(
                onSuccess = { if (it.versionCode > code) UpdateStatus.Available(it) else UpdateStatus.UpToDate },
                onFailure = { UpdateStatus.Failed("Não consegui verificar (sem internet?)") },
            )
        }
    }
    androidx.compose.runtime.LaunchedEffect(Unit) { check() }

    Section("Atualização") {
        Text("Versão instalada: $name", color = Color.White, fontSize = 15.sp)
        when (val st = status) {
            UpdateStatus.Idle, UpdateStatus.Checking -> Text("Verificando…", color = Dim, fontSize = 13.sp)
            UpdateStatus.UpToDate -> Text("Você está na versão mais nova.", color = Accent, fontSize = 13.sp)
            is UpdateStatus.Failed -> Text(st.msg, color = Dim, fontSize = 13.sp)
            is UpdateStatus.Downloading -> Text("Baixando… ${(st.p * 100).toInt()}%", color = Dim, fontSize = 13.sp)
            is UpdateStatus.Available -> {
                Text("Nova versão ${st.r.versionName} disponível", color = Accent, fontSize = 13.sp)
                if (st.r.notes.isNotBlank()) Text(st.r.notes, color = Dim, fontSize = 12.sp)
                Button(onClick = {
                    scope.launch {
                        runCatching {
                            val f = Updater.download(ctx, st.r) { p -> status = UpdateStatus.Downloading(p) }
                            Updater.install(ctx, f)
                            status = UpdateStatus.Available(st.r)
                        }.onFailure { status = UpdateStatus.Failed("O download falhou. Tente de novo.") }
                    }
                }) { Text("Baixar e instalar") }
            }
        }
        if (status !is UpdateStatus.Available && status !is UpdateStatus.Downloading) {
            OutlinedButton(onClick = { check() }) { Text("Verificar atualizações") }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Card).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, color = Accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        content()
    }
}

@Composable
private fun Permission(title: String, desc: String, ok: Boolean, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 15.sp)
            Text(if (ok) "Ativado" else desc, color = if (ok) Accent else Dim, fontSize = 12.sp)
        }
        if (!ok) Button(onClick = onClick) { Text("Ativar") }
    }
}

@Composable
private fun Toggle(title: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Switch(value, onChange, colors = SwitchDefaults.colors(checkedTrackColor = Accent, checkedThumbColor = Color.Black))
    }
}

@Composable
private fun Range(title: String, value: Int, range: IntRange, unit: String, onChange: (Int) -> Unit) {
    Column {
        Row {
            Text(title, color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Text("$value $unit", color = Dim, fontSize = 13.sp)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.toInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
        )
    }
}
