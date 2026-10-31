package com.joao.ilha

import android.app.PendingIntent
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.media.session.MediaController
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Botão de uma notificação; com [remoteInput] é uma resposta rápida. */
data class NotifAction(val title: String, val intent: PendingIntent, val remoteInput: android.app.RemoteInput?)

data class NotifItem(
    val key: String,
    val pkg: String,
    val appName: String,
    val title: String,
    val text: String,
    val smallIcon: Icon?,
    val largeIcon: Bitmap?,
    val contentIntent: PendingIntent?,
    val postedAt: Long,
    val clearable: Boolean,
    val actions: List<NotifAction> = emptyList(),
    /** Mensagens da conversa (WhatsApp, Telegram…), da mais antiga para a mais nova. */
    val messages: List<Msg> = emptyList(),
    /** Cor do app (verde do WhatsApp etc.), usada no selinho sobre a foto. */
    val color: Int = 0,
)

data class Msg(val sender: String, val text: String)

data class MediaInfo(
    val pkg: String,
    val appName: String,
    val title: String,
    val artist: String,
    val art: Bitmap?,
    val playing: Boolean,
    val durationMs: Long,
    val positionMs: Long,
    val positionAt: Long,
    val controller: MediaController,
)

/** Chamada lida da notificação do app de telefone (ou de VoIP). */
data class CallInfo(
    val key: String,
    val pkg: String,
    val name: String,
    val photo: Bitmap?,
    val incoming: Boolean,
    /** Início da chamada (System.currentTimeMillis), quando em andamento. */
    val startedAt: Long,
    val answer: PendingIntent?,
    val decline: PendingIntent?,
    val contentIntent: PendingIntent?,
)

enum class LiveKind { Timer, Stopwatch, Alarm, Navigation, Recording }

/** Atividade ao vivo lida de outra notificação: timer, cronômetro, alarme tocando ou navegação. */
data class LiveInfo(
    val key: String,
    val pkg: String,
    val kind: LiveKind,
    val title: String,
    val text: String,
    val icon: Bitmap?,
    /** Base do cronômetro (System.currentTimeMillis); 0 quando o app só manda o texto pronto. */
    val chronoBase: Long,
    val countDown: Boolean,
    val contentIntent: PendingIntent?,
    val actions: List<NotifAction>,
)

data class ChargingInfo(val level: Int, val charging: Boolean)

enum class BtKind { Earbuds, Headphones, Watch, Speaker, Other }

data class BluetoothInfo(val name: String, val kind: BtKind, val connected: Boolean, val battery: Int?)

enum class ReminderKind { Break, Water, Bedtime, BatteryLow, BatteryFull }

/** Avisos rápidos que aparecem e somem sozinhos. */
sealed interface Popup {
    data class Notification(val item: NotifItem) : Popup
    data class Charging(val info: ChargingInfo) : Popup
    data class Bluetooth(val info: BluetoothInfo) : Popup
    data object Test : Popup
    data class Reminder(val kind: ReminderKind) : Popup
    data class Volume(val level: Int, val max: Int, val ring: Boolean) : Popup
}

/** Liga o leitor de notificações, o serviço da ilha e as telas (tudo no mesmo processo). */
object IslandBus {
    val media = MutableStateFlow<MediaInfo?>(null)
    val notifications = MutableStateFlow<List<NotifItem>>(emptyList())
    val call = MutableStateFlow<CallInfo?>(null)
    val live = MutableStateFlow<LiveInfo?>(null)
    /** Algum app usando a câmera / o microfone agora. */
    val cameraInUse = MutableStateFlow(false)
    val micInUse = MutableStateFlow(false)
    val listenerConnected = MutableStateFlow(false)
    val serviceRunning = MutableStateFlow(false)

    /** Ações que precisam do leitor de notificações (remover uma ou todas). */
    @Volatile var dismiss: ((String?) -> Unit)? = null

    /** Resposta rápida aguardando o texto na [ReplyActivity]. */
    @Volatile var pendingReply: PendingReply? = null

    /** Relê notificações e player depois de mudar um ajuste (apps bloqueados etc.). */
    @Volatile var refresh: (() -> Unit)? = null

    private val _popups = MutableSharedFlow<Popup>(extraBufferCapacity = 8)
    val popups = _popups.asSharedFlow()

    fun show(p: Popup) {
        _popups.tryEmit(p)
    }
}
