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
        IslandBus.live.value = null
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
        firstSeen.remove(sbn.key)
        textBase.remove(sbn.key)
        refresh()
    }

    private fun isCall(sbn: StatusBarNotification) = sbn.notification.category == Notification.CATEGORY_CALL ||
        (sbn.packageName in meetApps && sbn.isOngoing)

    private fun isRegular(sbn: StatusBarNotification): Boolean {
        val n = sbn.notification
        if (sbn.packageName == packageName || sbn.isOngoing || isCall(sbn) || liveKind(sbn) != null) return false
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
        IslandBus.live.value = list
            .mapNotNull { sbn -> liveKind(sbn)?.let { sbn to it } }
            // Alarme tocando tem prioridade; depois navegação; depois timers.
            .sortedWith(compareBy<Pair<StatusBarNotification, LiveKind>>({ it.second != LiveKind.Alarm }, { it.second != LiveKind.Navigation }, { it.second == LiveKind.Progress }, { -it.first.postTime }))
            .firstOrNull()
            ?.let { (sbn, kind) -> toLive(sbn, kind) }
        watchDone(IslandBus.live.value)
    }

    /** Descobre se a notificação é um timer, cronômetro, alarme tocando ou navegação. */
    private fun liveKind(sbn: StatusBarNotification): LiveKind? {
        val s = Prefs.state.value
        val n = sbn.notification
        val pkg = sbn.packageName
        if (pkg == packageName || isCall(sbn) || n.category == Notification.CATEGORY_TRANSPORT) return null
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return null
        val clockApp = "clock" in pkg || "alarm" in pkg || pkg == "com.sec.android.app.clockpackage"
        if (n.category == Notification.CATEGORY_NAVIGATION || pkg in navApps) return if (s.navEnabled && sbn.isOngoing) LiveKind.Navigation else null
        if (n.category == Notification.CATEGORY_ALARM && (n.fullScreenIntent != null || clockApp && sbn.isOngoing)) {
            // Lembrete de "próximo alarme" não tem tela cheia e não é contínuo.
            return if (s.alarmEnabled && n.fullScreenIntent != null) LiveKind.Alarm else null
        }
        if (!sbn.isOngoing) return null
        val ex = n.extras
        // Gravador de tela/voz: o Notch não grava nada, só mostra o tempo da gravação do outro app.
        val text = "${ex.getCharSequence(Notification.EXTRA_TITLE)} ${ex.getCharSequence(Notification.EXTRA_TEXT)}".lowercase()
        if (s.recorderEnabled && (recorderWords.any { it in text } || "recorder" in pkg || "screenrecord" in pkg)) return LiveKind.Recording
        if (!s.liveEnabled) return null
        if (pkg in tripApps) return LiveKind.Trip
        if (pkg in fitnessApps) return LiveKind.Workout
        val chrono = ex.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER)
        val words = "${ex.getCharSequence(Notification.EXTRA_TITLE)} ${ex.getCharSequence(Notification.EXTRA_TEXT)} ${n.channelIdSafe()}".lowercase()
        val isStopwatch = listOf("cronômetro", "cronometro", "stopwatch").any { it in words }
        val isTimer = listOf("timer", "temporizador").any { it in words }
        return when {
            chrono && Build.VERSION.SDK_INT >= 24 && ex.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN) -> LiveKind.Timer
            clockApp && isStopwatch -> LiveKind.Stopwatch
            clockApp && isTimer -> LiveKind.Timer
            clockApp && chrono -> LiveKind.Stopwatch
            // Download, envio ou instalação com barra de progresso.
            !clockApp && ex.getInt(Notification.EXTRA_PROGRESS_MAX, 0) > 0 && !ex.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE) -> LiveKind.Progress
            else -> null
        }
    }

    private var lastProgress: LiveInfo? = null

    /** Download que sumiu ou chegou ao fim perto de 100%: avisa "Concluído". */
    private fun watchDone(now: LiveInfo?) {
        val prev = lastProgress
        lastProgress = now?.takeIf { it.kind == LiveKind.Progress && it.progress < 1f }
        if (prev == null) return
        val finished = (now?.key == prev.key && now.progress >= 1f) || (now?.key != prev.key && prev.progress >= 0.9f)
        if (finished) IslandBus.show(Popup.Done(prev.title, prev.appName, prev.smallIcon, prev.color))
    }

    /** Primeira vez que cada notificação apareceu (a hora de postagem muda a cada atualização). */
    private val firstSeen = HashMap<String, Long>()
    private val textBase = HashMap<String, Long>()

    /** "01:23" ou "1:02:03" → milissegundos. */
    private fun parseElapsed(s: String): Long? {
        val m = Regex("(?<!\\d)(\\d{1,2}):(\\d{2})(?::(\\d{2}))?(?!\\d)").find(s) ?: return null
        val a = m.groupValues[1].toLong()
        val b = m.groupValues[2].toLong()
        val c = m.groupValues[3].toLongOrNull()
        val secs = if (c != null) a * 3600 + b * 60 + c else a * 60 + b
        return secs * 1000
    }

    private fun Notification.channelIdSafe(): String = if (Build.VERSION.SDK_INT >= 26) channelId.orEmpty() else ""

    private fun toLive(sbn: StatusBarNotification, kind: LiveKind): LiveInfo {
        val n = sbn.notification
        val ex = n.extras
        val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (ex.getCharSequence(Notification.EXTRA_TEXT) ?: ex.getCharSequence(Notification.EXTRA_SUB_TEXT))?.toString().orEmpty()
        val chrono = ex.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER) && n.`when` > 0
        return LiveInfo(
            key = sbn.key,
            pkg = sbn.packageName,
            kind = kind,
            title = title,
            text = text,
            icon = runCatching { n.getLargeIcon()?.loadDrawable(this)?.toBitmapSafe() }.getOrNull(),
            chronoBase = when {
                chrono -> n.`when`
                // Sem cronômetro do sistema: sincroniza pelo tempo escrito na notificação
                // (o gravador atualiza o texto) e conta sozinho entre uma atualização e outra.
                kind == LiveKind.Recording || kind == LiveKind.Stopwatch ->
                    // Cada atualização chega igual ou atrasada, nunca adiantada: o menor valor é o início real.
                    // O "when" da notificação, quando o app coloca a hora de início (bem antes da postagem),
                    // é o início verdadeiro; o texto do gravador às vezes anda 1 s atrás dele.
                    run {
                        val explicitWhen = n.`when`.takeIf { it in 1 until sbn.postTime - 1500 }
                        val fromText = parseElapsed("$title $text")?.let { sbn.postTime - it }
                        val guess = listOfNotNull(explicitWhen, fromText).minOrNull()
                        if (guess == null) {
                            if (kind == LiveKind.Recording) firstSeen.getOrPut(sbn.key) { sbn.postTime } else 0L
                        } else {
                            val best = minOf(textBase[sbn.key] ?: guess, guess)
                            textBase[sbn.key] = best
                            best
                        }
                    } - Prefs.state.value.liveOffsetMs
                else -> 0L
            },
            countDown = Build.VERSION.SDK_INT >= 24 && ex.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN),
            progress = ex.getInt(Notification.EXTRA_PROGRESS_MAX, 0).let { max -> if (max > 0) ex.getInt(Notification.EXTRA_PROGRESS, 0).toFloat() / max else -1f },
            appName = appLabel(this, sbn.packageName),
            smallIcon = n.smallIcon,
            color = n.color,
            contentIntent = n.contentIntent,
            actions = n.actions.orEmpty().mapNotNull { a ->
                val t = a.title?.toString()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                NotifAction(t, a.actionIntent ?: return@mapNotNull null, null)
            }.take(3),
        )
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
            messages = if (text == "Conteúdo sensível oculto") emptyList() else messagesOf(ex),
            color = n.color,
        )
    }

    /** Conversa inteira: estilo de mensagens (WhatsApp, Telegram) ou, se não houver, as linhas da caixa de entrada. */
    private fun messagesOf(ex: android.os.Bundle): List<Msg> {
        @Suppress("DEPRECATION")
        val raw = runCatching { ex.getParcelableArray(Notification.EXTRA_MESSAGES) }.getOrNull()
        val msgs = raw.orEmpty().mapNotNull { p ->
            val b = p as? android.os.Bundle ?: return@mapNotNull null
            val text = b.getCharSequence("text")?.toString()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            @Suppress("DEPRECATION")
            val person = if (Build.VERSION.SDK_INT >= 28) runCatching { b.getParcelable<android.app.Person>("sender_person")?.name }.getOrNull() else null
            Msg((person ?: b.getCharSequence("sender"))?.toString().orEmpty(), text)
        }
        if (msgs.isNotEmpty()) return msgs.takeLast(20)
        return ex.getCharSequenceArray(Notification.EXTRA_TEXT_LINES).orEmpty()
            .mapNotNull { it?.toString()?.takeIf { t -> t.isNotBlank() }?.let { t -> Msg("", t) } }
            .takeLast(20)
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
            appName = appLabel(this, sbn.packageName),
            video = sbn.packageName in meetApps,
            actions = n.actions.orEmpty().mapNotNull { a ->
                val t = a.title?.toString()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                if (a.actionIntent == null || a.actionIntent == answer) return@mapNotNull null
                NotifAction(t, a.actionIntent, null)
            }.take(5),
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

    private var lastMediaPlaying = false

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
        val key = "${c.packageName}|$title"
        val wasPlaying = lastMediaPlaying
        lastMediaPlaying = playing
        if (IslandBus.hiddenMedia != null) {
            // Tirada da ilha: só volta se for outra faixa ou se a pessoa der play de novo.
            if (IslandBus.hiddenMedia == key && !(playing && !wasPlaying)) {
                IslandBus.media.value = null
                return
            }
            IslandBus.hiddenMedia = null
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

private val recorderWords = listOf("gravando", "gravação de tela", "gravacao de tela", "recording", "screen record")

/** Reuniões de vídeo: a notificação contínua da reunião vira uma chamada de vídeo na ilha. */
private val meetApps = setOf(
    "com.google.android.apps.meetings", "com.google.android.apps.tachyon", "us.zoom.videomeetings", "com.microsoft.teams",
)

/** Treino em andamento (Google Fit, Strava, Samsung Health, Nike Run). */
private val fitnessApps = setOf(
    "com.google.android.apps.fitness", "com.strava", "com.sec.android.app.shealth", "com.nike.plusgps",
)

/** Corridas e entregas: a notificação contínua vira atividade ao vivo. */
private val tripApps = setOf(
    "com.ubercab", "com.ubercab.eats", "com.taxis99", "br.com.brainweb.ifood", "com.grability.rappi",
    "com.cabify.rider", "com.indriver", "sinet.startup.inDriver", "com.mercadolibre", "com.shopee.br",
)

private val navApps = setOf("com.google.android.apps.maps", "com.waze", "com.here.app.maps", "com.sygic.aura", "ru.yandex.yandexnavi")

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
