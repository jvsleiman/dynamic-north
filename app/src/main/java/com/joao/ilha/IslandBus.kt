package com.joao.ilha

import android.app.PendingIntent
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.media.session.MediaController
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow

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
)

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

data class ChargingInfo(val level: Int, val charging: Boolean)

/** Eventos que aparecem rapidamente na ilha e depois somem. */
sealed interface Popup {
    data class Notification(val item: NotifItem) : Popup
    data class Charging(val info: ChargingInfo) : Popup
    data object Test : Popup
}

/** Liga o leitor de notificações ao serviço que desenha a ilha (mesmo processo). */
object IslandBus {
    val media = MutableStateFlow<MediaInfo?>(null)
    val notifications = MutableStateFlow<List<NotifItem>>(emptyList())
    val listenerConnected = MutableStateFlow(false)
    val serviceRunning = MutableStateFlow(false)

    private val _popups = MutableSharedFlow<Popup>(extraBufferCapacity = 8)
    val popups = _popups.asSharedFlow()

    fun show(p: Popup) {
        _popups.tryEmit(p)
    }
}
