package com.joao.ilha

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.Icon
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Exemplos para testar a ilha sem esperar mensagem, música ou chamada de verdade.
 * Tudo vai direto para o IslandBus; nada é postado como notificação.
 */
object Demo {
    private const val PKG_WHATS = "com.whatsapp"
    private var session: MediaSession? = null
    private var playing = true
    private var position = 0L
    private var positionAt = 0L
    private const val DURATION = 214_000L

    private fun icon(ctx: Context) = Icon.createWithResource(ctx, R.drawable.ic_launcher_mono)

    private fun openApp(ctx: Context) = PendingIntent.getActivity(
        ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE,
    )

    /** Foto redonda com a inicial do nome. */
    private fun avatar(name: String, color: Int): Bitmap {
        val b = Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        c.drawCircle(80f, 80f, 80f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = android.graphics.Color.WHITE
            textSize = 76f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        c.drawText(name.take(1).uppercase(), 80f, 80f - (p.descent() + p.ascent()) / 2, p)
        return b
    }

    private fun art(): Bitmap {
        val b = Bitmap.createBitmap(300, 300, Bitmap.Config.ARGB_8888)
        val p = Paint().apply {
            shader = LinearGradient(0f, 0f, 300f, 300f, 0xFFFF5E62.toInt(), 0xFF7B2FF7.toInt(), Shader.TileMode.CLAMP)
        }
        Canvas(b).drawRect(0f, 0f, 300f, 300f, p)
        return b
    }

    // ---------- mensagens ----------

    fun whatsapp(ctx: Context, group: Boolean) {
        val msgs = if (group) listOf(
            Msg("Rafael", "Futebol quinta às 20h, quem confirma?"),
            Msg("Pedro", "Confirmado!"),
            Msg("Rafael", "Fechou, levo a bola"),
            Msg("Lucas", "Tô dentro também"),
        ) else listOf(
            Msg("Carlos (trabalho)", "Bom dia! A reunião passou para as 15h"),
            Msg("Carlos (trabalho)", "Consegue levar o relatório impresso?"),
            Msg("Carlos (trabalho)", "Valeu!"),
        )
        val item = NotifItem(
            key = "demo-whats-$group", pkg = PKG_WHATS, appName = "WhatsApp",
            title = if (group) "Futebol de quinta ⚽" else "Carlos (trabalho)",
            text = msgs.last().let { if (group) "${it.sender}: ${it.text}" else it.text },
            smallIcon = icon(ctx), largeIcon = avatar(if (group) "F" else "Carlos", if (group) 0xFFFF9500.toInt() else 0xFF5E5CE6.toInt()),
            contentIntent = openApp(ctx), postedAt = System.currentTimeMillis(), clearable = true,
            actions = listOf(NotifAction("Marcar como lida", openApp(ctx), null)),
            messages = msgs, color = 0xFF25D366.toInt(),
        )
        addToList(item)
        IslandBus.show(Popup.Notification(item))
    }

    fun simpleNotif(ctx: Context) {
        val item = NotifItem(
            key = "demo-mail", pkg = ctx.packageName, appName = "E-mail",
            title = "Sua encomenda saiu para entrega", text = "Chega hoje até as 18h.",
            smallIcon = icon(ctx), largeIcon = null, contentIntent = openApp(ctx),
            postedAt = System.currentTimeMillis(), clearable = true,
        )
        addToList(item)
        IslandBus.show(Popup.Notification(item))
    }

    private fun addToList(item: NotifItem) {
        IslandBus.notifications.value = listOf(item) + IslandBus.notifications.value.filter { it.key != item.key }
    }

    // ---------- música ----------

    fun music(ctx: Context) {
        val s = session ?: MediaSession(ctx, "notch-teste").also { session = it }
        s.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, "Música de teste")
                .putString(MediaMetadata.METADATA_KEY_ARTIST, "Notch")
                .putLong(MediaMetadata.METADATA_KEY_DURATION, DURATION)
                .putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, art())
                .build(),
        )
        s.setCallback(object : MediaSession.Callback() {
            override fun onPlay() = setPlaying(true)
            override fun onPause() = setPlaying(false)
            override fun onSkipToNext() { position = 0; positionAt = System.currentTimeMillis(); publish() }
            override fun onSkipToPrevious() = onSkipToNext()
            override fun onSeekTo(pos: Long) { position = pos; positionAt = System.currentTimeMillis(); publish() }
        })
        s.isActive = true
        playing = true
        position = 30_000
        positionAt = System.currentTimeMillis()
        publish()
    }

    private fun setPlaying(p: Boolean) {
        position = currentPos()
        positionAt = System.currentTimeMillis()
        playing = p
        publish()
    }

    private fun currentPos() = if (playing) (position + System.currentTimeMillis() - positionAt).coerceAtMost(DURATION) else position

    private fun publish() {
        val s = session ?: return
        s.setPlaybackState(
            PlaybackState.Builder()
                .setActions(
                    PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or
                        PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS or PlaybackState.ACTION_SEEK_TO,
                )
                .setState(if (playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED, position, 1f, android.os.SystemClock.elapsedRealtime())
                .build(),
        )
        IslandBus.media.value = MediaInfo(
            pkg = "notch.teste", appName = "Teste", title = "Música de teste", artist = "Notch", art = art(),
            playing = playing, durationMs = DURATION, positionMs = position, positionAt = positionAt, controller = s.controller,
        )
    }

    fun stopMusic() {
        session?.release()
        session = null
        IslandBus.media.value = null
        IslandBus.refresh?.invoke()
    }

    // ---------- chamada ----------

    fun call(ctx: Context, incoming: Boolean) {
        IslandBus.call.value = CallInfo(
            key = "demo-call", pkg = ctx.packageName, name = "Carlos (trabalho)", photo = avatar("Carlos", 0xFF5E5CE6.toInt()),
            incoming = incoming, startedAt = if (incoming) 0 else System.currentTimeMillis(),
            answer = null, decline = null, contentIntent = openApp(ctx),
            appName = "WhatsApp",
            actions = if (incoming) emptyList() else listOf(
                NotifAction("Alto-falante", openApp(ctx), null), NotifAction("Mudo", openApp(ctx), null),
                NotifAction("Vídeo", openApp(ctx), null), NotifAction("Desligar", openApp(ctx), null),
            ),
        )
    }

    fun endCall() {
        IslandBus.call.value = null
        IslandBus.refresh?.invoke()
    }

    // ---------- ao vivo ----------

    fun live(ctx: Context, kind: LiveKind) {
        val now = System.currentTimeMillis()
        IslandBus.live.value = when (kind) {
            LiveKind.Timer -> LiveInfo("demo-live", ctx.packageName, kind, "Timer", "Macarrão", null, now + 5 * 60_000, true, null, listOf(NotifAction("Pausar", openApp(ctx), null), NotifAction("Cancelar", openApp(ctx), null)))
            LiveKind.Stopwatch -> LiveInfo("demo-live", ctx.packageName, kind, "Cronômetro", "", null, now, false, null, listOf(NotifAction("Volta", openApp(ctx), null), NotifAction("Parar", openApp(ctx), null)))
            LiveKind.Alarm -> LiveInfo("demo-live", ctx.packageName, kind, "Alarme", "07:00 · Acordar", null, 0, false, null, listOf(NotifAction("Soneca", openApp(ctx), null), NotifAction("Parar", openApp(ctx), null)))
            LiveKind.Navigation -> LiveInfo("demo-live", ctx.packageName, kind, "200 m", "Vire à direita na Av. Paulista", null, 0, false, null, listOf(NotifAction("Sair", openApp(ctx), null)))
            LiveKind.Recording -> LiveInfo("demo-live", ctx.packageName, kind, "Gravação 2", "", null, now, false, null, listOf(NotifAction("Parar", openApp(ctx), null), NotifAction("Pausar", openApp(ctx), null)))
            LiveKind.Progress, LiveKind.Trip -> null
        }
    }

    fun download(ctx: Context, scope: kotlinx.coroutines.CoroutineScope) {
        scope.launch {
            for (p in 0..20) {
                IslandBus.live.value = LiveInfo(
                    "demo-dl", ctx.packageName, LiveKind.Progress, "App de exemplo", "${p * 5}%", null, 0, false, null, emptyList(),
                    progress = p / 20f, appName = "Play Store", smallIcon = Icon.createWithResource(ctx, android.R.drawable.stat_sys_download), color = 0xFF01875F.toInt(),
                )
                delay(250)
            }
            IslandBus.live.value = null
            IslandBus.show(Popup.Done("App de exemplo", "Play Store", Icon.createWithResource(ctx, android.R.drawable.stat_sys_download_done), 0xFF01875F.toInt()))
        }
    }

    fun trip(ctx: Context) {
        IslandBus.live.value = LiveInfo(
            "demo-trip", ctx.packageName, LiveKind.Trip, "Chega em 3 min", "Motorista ⭐ 4,9 · Carro prata · ABC1D23",
            avatar("M", 0xFF34C759.toInt()), 0, false, null, listOf(NotifAction("Ver no mapa", openApp(ctx), null)),
            appName = "Corrida", smallIcon = icon(ctx), color = 0xFF7B2FF7.toInt(),
        )
    }

    fun stopLive() {
        IslandBus.live.value = null
        IslandBus.refresh?.invoke()
    }

    fun privacy(camera: Boolean) {
        if (camera) IslandBus.cameraInUse.value = true else IslandBus.micInUse.value = true
    }

    fun clearAll() {
        stopMusic()
        endCall()
        stopLive()
        IslandBus.cameraInUse.value = false
        IslandBus.micInUse.value = false
        IslandBus.notifications.value = IslandBus.notifications.value.filterNot { it.key.startsWith("demo-") }
        IslandBus.refresh?.invoke()
    }
}

@Composable
fun TestScreen(back: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var tour by remember { mutableStateOf<Job?>(null) }
    val running = IslandBus.serviceRunning.value
    Page("Testar", back) {
        if (!running) Group { InfoText("Ative a acessibilidade do Notch para a ilha aparecer.") }
        Group(footer = "Mostra cada recurso por alguns segundos, um depois do outro.") {
            NavRow(if (tour == null) "Tudo em sequência" else "Parar a sequência", icon = if (tour == null) Icons.Rounded.PlayArrow else Icons.Rounded.Stop, iconColor = Color(0xFF34C759), divider = false) {
                tour?.let { it.cancel(); tour = null; Demo.clearAll(); return@NavRow }
                tour = scope.launch {
                    val steps: List<Pair<Long, () -> Unit>> = listOf(
                        4_500L to { Demo.whatsapp(ctx, false) },
                        4_500L to { Demo.whatsapp(ctx, true) },
                        4_000L to { Demo.simpleNotif(ctx) },
                        4_000L to { Demo.music(ctx) },
                        3_000L to { IslandBus.show(Popup.Volume(9, 15, false)) },
                        4_000L to { Demo.stopMusic(); Demo.call(ctx, true) },
                        4_000L to { Demo.call(ctx, false) },
                        3_500L to { Demo.endCall(); Demo.live(ctx, LiveKind.Timer) },
                        3_500L to { Demo.live(ctx, LiveKind.Stopwatch) },
                        3_500L to { Demo.live(ctx, LiveKind.Navigation) },
                        3_500L to { Demo.live(ctx, LiveKind.Recording) },
                        4_000L to { Demo.live(ctx, LiveKind.Alarm) },
                        6_500L to { Demo.stopLive(); Demo.download(ctx, scope) },
                        4_000L to { Demo.trip(ctx) },
                        3_500L to { Demo.stopLive(); IslandBus.show(Popup.Charging(ChargingInfo(72, true))) },
                        3_500L to { IslandBus.show(Popup.Bluetooth(BluetoothInfo("Fones de ouvido", BtKind.Earbuds, true, 80))) },
                        3_500L to { IslandBus.show(Popup.Reminder(ReminderKind.BatteryLow)) },
                        2_500L to { IslandBus.show(Popup.Status(StatusKind.Silent)) },
                        3_500L to { Demo.privacy(true); Demo.privacy(false) },
                    )
                    try {
                        for ((wait, step) in steps) { step(); delay(wait) }
                    } finally {
                        Demo.clearAll()
                        tour = null
                    }
                }
            }
        }
        Group("Mensagens") {
            NavRow("WhatsApp: conversa", icon = Icons.Rounded.Chat, iconColor = Color(0xFF25D366)) { Demo.whatsapp(ctx, false) }
            NavRow("WhatsApp: grupo") { Demo.whatsapp(ctx, true) }
            NavRow("Notificação comum", divider = false) { Demo.simpleNotif(ctx) }
        }
        Group("Música", footer = "Os botões de tocar, pausar e pular funcionam na ilha.") {
            NavRow("Tocar música de teste", icon = Icons.Rounded.MusicNote, iconColor = Color(0xFFFF2D55)) { Demo.music(ctx) }
            NavRow("Parar música", divider = false) { Demo.stopMusic() }
        }
        Group("Chamada") {
            NavRow("Chamada recebida", icon = Icons.Rounded.Call, iconColor = SwitchGreen) { Demo.call(ctx, true) }
            NavRow("Chamada em andamento") { Demo.call(ctx, false) }
            NavRow("Encerrar chamada", divider = false) { Demo.endCall() }
        }
        Group("Ao vivo") {
            NavRow("Timer", icon = Icons.Rounded.Timer, iconColor = Color(0xFFFF9500)) { Demo.live(ctx, LiveKind.Timer) }
            NavRow("Cronômetro") { Demo.live(ctx, LiveKind.Stopwatch) }
            NavRow("Gravação de tela") { Demo.live(ctx, LiveKind.Recording) }
            NavRow("Download") { Demo.download(ctx, scope) }
            NavRow("Corrida") { Demo.trip(ctx) }
            NavRow("Navegação", icon = Icons.Rounded.Navigation, iconColor = Color(0xFF0A84FF)) { Demo.live(ctx, LiveKind.Navigation) }
            NavRow("Alarme tocando", icon = Icons.Rounded.Alarm, iconColor = Color(0xFFFF9500)) { Demo.live(ctx, LiveKind.Alarm) }
            NavRow("Parar ao vivo", divider = false) { Demo.stopLive() }
        }
        Group("Sistema") {
            NavRow("Carregando", icon = Icons.Rounded.BatteryChargingFull, iconColor = Color(0xFF30B94D)) { IslandBus.show(Popup.Charging(ChargingInfo(72, true))) }
            NavRow("Bateria baixa") { IslandBus.show(Popup.Reminder(ReminderKind.BatteryLow)) }
            NavRow("Bateria cheia") { IslandBus.show(Popup.Reminder(ReminderKind.BatteryFull)) }
            NavRow("Fone Bluetooth conectado", icon = Icons.Rounded.Bluetooth, iconColor = Accent) { IslandBus.show(Popup.Bluetooth(BluetoothInfo("Fones de ouvido", BtKind.Earbuds, true, 80))) }
            NavRow("Volume", icon = Icons.AutoMirrored.Rounded.VolumeUp, iconColor = Color(0xFF8E8E93)) { IslandBus.show(Popup.Volume(9, 15, false)) }
            NavRow("Modo silencioso") { IslandBus.show(Popup.Status(StatusKind.Silent)) }
            NavRow("Não perturbe") { IslandBus.show(Popup.Status(StatusKind.DndOn)) }
            NavRow("Desbloqueio") { IslandBus.show(Popup.Status(StatusKind.Unlocked)) }
            NavRow("Câmera em uso") { Demo.privacy(true) }
            NavRow("Microfone em uso") { Demo.privacy(false) }
            NavRow("Aviso de teste", divider = false) { IslandBus.show(Popup.Test) }
        }
        Group("Ilha aberta", footer = "Abre a ilha grande direto no layout escolhido.") {
            NavRow("Controles rápidos", icon = Icons.Rounded.Tune, iconColor = Color(0xFF0A84FF)) { IslandBus.expand?.invoke(ExpandKind.Controls) }
            NavRow("Relógio") { IslandBus.expand?.invoke(ExpandKind.Clock) }
            NavRow("Bateria") { IslandBus.expand?.invoke(ExpandKind.Battery) }
            NavRow("Saúde") { IslandBus.expand?.invoke(ExpandKind.Health) }
            NavRow("Lista de notificações", divider = false) { IslandBus.expand?.invoke(ExpandKind.Notifications) }
        }
        Group {
            NavRow("Limpar todos os testes", icon = Icons.Rounded.Stop, iconColor = Color(0xFFFF3B30), divider = false) {
                tour?.cancel(); tour = null; Demo.clearAll()
            }
        }
    }
}
