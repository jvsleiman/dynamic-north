package com.joao.ilha

import android.app.Notification
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/** Lê notificações, chamadas e o player ativo e publica tudo no [IslandBus]. */
class NotifListener : NotificationListenerService() {

    private val lastShown = HashMap<String, String>()
    private var sessions: MediaSessionManager? = null
    private var controller: MediaController? = null
    private val callback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = publishMedia()
        override fun onMetadataChanged(metadata: MediaMetadata?) = publishMedia()
        override fun onSessionDestroyed() = pickController()
    }
    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { pickController() }

    override fun onListenerConnected() {
        Prefs.init(this)
        IslandBus.listenerConnected.value = true
        IslandBus.dismiss = { key -> if (key == null) cancelAllNotifications() else cancelNotification(key) }
        IslandBus.refresh = { pickController(); refresh() }
        sessions = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        runCatching { sessions?.addOnActiveSessionsChangedListener(sessionsListener, ComponentName(this, NotifListener::class.java)) }
        pickController()
        refresh()
    }

    override fun onListenerDisconnected() {
        IslandBus.listenerConnected.value = false
        IslandBus.dismiss = null
        IslandBus.refresh = null
        runCatching { sessions?.removeOnActiveSessionsChangedListener(sessionsListener) }
        controller?.unregisterCallback(callback)
        controller = null
        IslandBus.media.value = null
        IslandBus.call.value = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        refresh()
        val s = Prefs.state.value
        if (!s.notifEnabled || sbn.packageName in s.blockedNotifApps) return
        if (!isRegular(sbn)) return
        val item = toItem(sbn) ?: return
        // Atualizações da mesma notificação com o mesmo texto não reaparecem.
        val sig = item.title + "\u0000" + item.text
        if (lastShown[sbn.key] == sig) return
        lastShown[sbn.key] = sig
        IslandBus.show(Popup.Notification(item))
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        lastShown.remove(sbn.key)
        refresh()
    }

    private fun isCall(sbn: StatusBarNotification) = sbn.notification.category == Notification.CATEGORY_CALL

    private fun isRegular(sbn: StatusBarNotification): Boolean {
        val n = sbn.notification
        if (sbn.packageName == packageName || sbn.isOngoing || isCall(sbn)) return false
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return false
        if (n.category == Notification.CATEGORY_TRANSPORT) return false
        return !n.extras.getCharSequence(Notification.EXTRA_TITLE).isNullOrBlank()
    }

    private fun refresh() {
        val list = runCatching { activeNotifications }.getOrNull() ?: return
        val blocked = Prefs.state.value.blockedNotifApps
        IslandBus.notifications.value = list
            .filter { isRegular(it) && it.packageName !in blocked }
            .sortedByDescending { it.postTime }
            .mapNotNull { toItem(it) }
        val s = Prefs.state.value
        IslandBus.call.value = if (!s.callEnabled) null else list
            .filter { isCall(it) }
            .maxByOrNull { it.postTime }
            ?.let { toCall(it) }
    }

    private fun toItem(sbn: StatusBarNotification): NotifItem? {
        val n = sbn.notification
        val ex = n.extras
        val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: return null
        var text = (ex.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: ex.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
        if (!Prefs.state.value.showSensitive && isSensitive(title, text)) text = "Conteúdo sensível oculto"
        return NotifItem(
            key = sbn.key,
            pkg = sbn.packageName,
            appName = appLabel(this, sbn.packageName),
            title = title,
            text = text,
            smallIcon = n.smallIcon,
            largeIcon = runCatching { n.getLargeIcon()?.loadDrawable(this)?.toBitmapSafe() }.getOrNull(),
            contentIntent = n.contentIntent,
            postedAt = sbn.postTime,
            clearable = sbn.isClearable,
            actions = n.actions.orEmpty().mapNotNull { a ->
                val t = a.title?.toString()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                NotifAction(t, a.actionIntent ?: return@mapNotNull null, a.remoteInputs?.firstOrNull { it.allowFreeFormInput })
            }.take(3),
        )
    }

    private fun isSensitive(title: String, text: String): Boolean {
        val all = "$title $text".lowercase()
        return Regex("\\b\\d{4,8}\\b").containsMatchIn(all) &&
            listOf("código", "codigo", "code", "otp", "senha", "verifica", "token").any { it in all }
    }

    private fun toCall(sbn: StatusBarNotification): CallInfo {
        val n = sbn.notification
        val ex = n.extras
        val name = (ex.getCharSequence(Notification.EXTRA_TITLE) ?: ex.getCharSequence(Notification.EXTRA_TEXT))?.toString() ?: "Chamada"
        var answer: PendingIntent? = null
        var decline: PendingIntent? = null
        var incoming = !(n.flags and Notification.FLAG_ONGOING_EVENT != 0 && ex.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))
        if (Build.VERSION.SDK_INT >= 31) {
            when (ex.getInt(Notification.EXTRA_CALL_TYPE, 0)) {
                Notification.CallStyle.CALL_TYPE_INCOMING -> incoming = true
                Notification.CallStyle.CALL_TYPE_ONGOING -> incoming = false
            }
            answer = getParcelable(ex, Notification.EXTRA_ANSWER_INTENT)
            decline = getParcelable(ex, Notification.EXTRA_DECLINE_INTENT) ?: getParcelable(ex, Notification.EXTRA_HANG_UP_INTENT)
        }
        // Sem CallStyle: procura os botões pelo nome.
        n.actions?.forEach { a ->
            val t = a.title?.toString()?.lowercase().orEmpty()
            if (answer == null && listOf("atender", "aceitar", "answer", "accept").any { it in t }) answer = a.actionIntent
            if (decline == null && listOf("recusar", "rejeitar", "desligar", "encerrar", "decline", "reject", "hang up", "end").any { it in t }) decline = a.actionIntent
        }
        if (answer == null) incoming = false
        val photo = runCatching { n.getLargeIcon()?.loadDrawable(this)?.toBitmapSafe() }.getOrNull()
        return CallInfo(
            key = sbn.key,
            pkg = sbn.packageName,
            name = name,
            photo = photo,
            incoming = incoming,
            startedAt = if (n.`when` > 0) n.`when` else sbn.postTime,
            answer = answer,
            decline = decline,
            contentIntent = n.contentIntent,
        )
    }

    @Suppress("DEPRECATION")
    private fun getParcelable(b: android.os.Bundle, key: String): PendingIntent? = runCatching { b.getParcelable<PendingIntent>(key) }.getOrNull()

    private fun pickController() {
        val list = runCatching { sessions?.getActiveSessions(ComponentName(this, NotifListener::class.java)) }.getOrNull().orEmpty()
        val blocked = Prefs.state.value.blockedMusicApps
        val usable = list.filter { it.packageName !in blocked }
        val best = usable.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: usable.firstOrNull()
        if (best?.sessionToken != controller?.sessionToken) {
            controller?.unregisterCallback(callback)
            controller = best
            best?.registerCallback(callback)
        }
        publishMedia()
    }

    private fun publishMedia() {
        val c = controller
        val md = c?.metadata
        val s = Prefs.state.value
        if (c == null || md == null || !s.musicEnabled || c.packageName in s.blockedMusicApps) {
            IslandBus.media.value = null
            return
        }
        val st = c.playbackState
        val playing = st?.state == PlaybackState.STATE_PLAYING
        val title = md.getString(MediaMetadata.METADATA_KEY_TITLE) ?: md.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
        if (title.isNullOrBlank() || (s.musicOnlyWhilePlaying && !playing)) {
            IslandBus.media.value = null
            return
        }
        IslandBus.media.value = MediaInfo(
            pkg = c.packageName,
            appName = appLabel(this, c.packageName),
            title = title,
            artist = md.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: md.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST).orEmpty(),
            art = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: md.getBitmap(MediaMetadata.METADATA_KEY_ART),
            playing = playing,
            durationMs = md.getLong(MediaMetadata.METADATA_KEY_DURATION),
            positionMs = st?.position ?: 0,
            positionAt = st?.lastPositionUpdateTime ?: SystemClock.elapsedRealtime(),
            controller = c,
        )
    }
}

fun appLabel(ctx: Context, pkg: String): String = runCatching {
    val pm = ctx.packageManager
    pm.getApplicationLabel(pm.getApplicationInfo(pkg, PackageManager.GET_META_DATA)).toString()
}.getOrDefault(pkg)

fun Drawable.toBitmapSafe(): Bitmap? {
    if (this is BitmapDrawable && bitmap != null) return bitmap
    val w = intrinsicWidth.takeIf { it > 0 } ?: 96
    val h = intrinsicHeight.takeIf { it > 0 } ?: 96
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    setBounds(0, 0, w, h)
    draw(canvas)
    return bmp
}
