package com.joao.ilha

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/** Lê notificações e o player ativo e publica tudo no [IslandBus]. */
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
        sessions = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        val me = ComponentName(this, NotifListener::class.java)
        runCatching { sessions?.addOnActiveSessionsChangedListener(sessionsListener, me) }
        pickController()
        refreshList()
    }

    override fun onListenerDisconnected() {
        IslandBus.listenerConnected.value = false
        runCatching { sessions?.removeOnActiveSessionsChangedListener(sessionsListener) }
        controller?.unregisterCallback(callback)
        controller = null
        IslandBus.media.value = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        refreshList()
        val s = Prefs.state.value
        if (!s.showNotifications || sbn.packageName in s.blockedApps) return
        if (!isInteresting(sbn)) return
        val item = toItem(sbn) ?: return
        // Atualizações da mesma notificação com o mesmo texto não reaparecem.
        val sig = item.title + "\u0000" + item.text
        if (lastShown[sbn.key] == sig) return
        lastShown[sbn.key] = sig
        IslandBus.show(Popup.Notification(item))
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        lastShown.remove(sbn.key)
        refreshList()
    }

    private fun isInteresting(sbn: StatusBarNotification): Boolean {
        val n = sbn.notification
        if (sbn.packageName == packageName) return false
        if (sbn.isOngoing) return false
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return false
        if (n.category == Notification.CATEGORY_TRANSPORT) return false
        val title = n.extras.getCharSequence(Notification.EXTRA_TITLE)
        return !title.isNullOrBlank()
    }

    private fun refreshList() {
        val list = runCatching { activeNotifications }.getOrNull() ?: return
        IslandBus.notifications.value = list
            .filter { isInteresting(it) }
            .sortedByDescending { it.postTime }
            .mapNotNull { toItem(it) }
    }

    private fun toItem(sbn: StatusBarNotification): NotifItem? {
        val n = sbn.notification
        val ex = n.extras
        val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: return null
        val text = (ex.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: ex.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
        val large = runCatching { n.getLargeIcon()?.loadDrawable(this)?.toBitmapSafe() }.getOrNull()
        return NotifItem(
            key = sbn.key,
            pkg = sbn.packageName,
            appName = appLabel(this, sbn.packageName),
            title = title,
            text = text,
            smallIcon = n.smallIcon,
            largeIcon = large,
            contentIntent = n.contentIntent,
            postedAt = sbn.postTime,
        )
    }

    private fun pickController() {
        val list = runCatching { sessions?.getActiveSessions(ComponentName(this, NotifListener::class.java)) }.getOrNull().orEmpty()
        val best = list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: list.firstOrNull()
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
        if (c == null || md == null) {
            IslandBus.media.value = null
            return
        }
        val st = c.playbackState
        val title = md.getString(MediaMetadata.METADATA_KEY_TITLE) ?: md.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
        if (title.isNullOrBlank()) {
            IslandBus.media.value = null
            return
        }
        IslandBus.media.value = MediaInfo(
            pkg = c.packageName,
            appName = appLabel(this, c.packageName),
            title = title,
            artist = md.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: md.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST).orEmpty(),
            art = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: md.getBitmap(MediaMetadata.METADATA_KEY_ART),
            playing = st?.state == PlaybackState.STATE_PLAYING,
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

fun android.graphics.drawable.Drawable.toBitmapSafe(): Bitmap? {
    if (this is BitmapDrawable && bitmap != null) return bitmap
    val w = intrinsicWidth.takeIf { it > 0 } ?: 96
    val h = intrinsicHeight.takeIf { it > 0 } ?: 96
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    setBounds(0, 0, w, h)
    draw(canvas)
    return bmp
}
