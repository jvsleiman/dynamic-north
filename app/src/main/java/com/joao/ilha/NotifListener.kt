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
        IslandBus.report = { report() }
        sessions = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        runCatching { sessions?.addOnActiveSessionsChangedListener(sessionsListener, ComponentName(this, NotifListener::class.java)) }
        pickController()
        // Pedido ou encomenda que já estava nas notificações quando o leitor ligou.
        runCatching { activeNotifications }.getOrNull().orEmpty()
            .filterNot { Banks.isBank(it.packageName) }.sortedBy { it.postTime }
            .forEach { if (!trackOrder(it)) trackParcel(it) }
        refresh()
    }

    override fun onListenerDisconnected() {
        IslandBus.listenerConnected.value = false
        IslandBus.dismiss = null
        IslandBus.refresh = null
        IslandBus.report = null
        runCatching { sessions?.removeOnActiveSessionsChangedListener(sessionsListener) }
        controller?.unregisterCallback(callback)
        controller = null
        IslandBus.media.value = null
        IslandBus.call.value = null
        // O Foco é do próprio Notch: continua na ilha mesmo sem o leitor.
        IslandBus.live.value = IslandBus.own.value
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        // Notificações de banco, carteira e pagamento não passam pelo Notch.
        if (Banks.isBank(sbn.packageName)) return
        val isOrder = trackOrder(sbn) || trackParcel(sbn)
        refresh()
        // Status do pedido ou da encomenda vira atividade na ilha (não aparece de novo como notificação comum).
        if (isOrder) return
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
        itemCache.remove(sbn.key)
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
        val list = runCatching { activeNotifications }.getOrNull()?.filterNot { Banks.isBank(it.packageName) } ?: return
        val blocked = Prefs.state.value.blockedNotifApps
        IslandBus.notifications.value = if (!Prefs.state.value.notifEnabled) emptyList() else list
            .filter { isRegular(it) && it.packageName !in blocked }
            .sortedByDescending { it.postTime }
            .mapNotNull { cachedItem(it) }
        val s = Prefs.state.value
        IslandBus.call.value = if (!s.callEnabled) null else list
            .filter { isCall(it) }
            .maxByOrNull { it.postTime }
            ?.let { toCall(it) }
        val best = list
            .mapNotNull { sbn -> liveKind(sbn)?.let { sbn to it } }
            // Alarme tocando tem prioridade; depois navegação; depois timers.
            .sortedWith(compareBy<Pair<StatusBarNotification, LiveKind>>({ it.second != LiveKind.Alarm }, { it.second != LiveKind.Navigation }, { it.second == LiveKind.Progress || it.second == LiveKind.Share }, { it.first.key != lastProgress?.key }, { -it.first.postTime }))
            .firstOrNull()
            ?.let { (sbn, kind) -> toLive(sbn, kind) }
        // Pedido de comida em andamento vem logo depois de alarme e navegação.
        val ord = currentOrder()
        val own = IslandBus.own.value
        IslandBus.live.value = when {
            best != null && (best.kind == LiveKind.Alarm || best.kind == LiveKind.Navigation) -> best
            ord != null -> ord
            // Download ou envio de arquivo não tira o Foco nem a encomenda da ilha.
            best != null && best.kind != LiveKind.Progress && best.kind != LiveKind.Share -> best
            own != null -> own
            else -> currentParcel() ?: best
        }
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
        val ex = n.extras
        val hasBar = ex.getInt(Notification.EXTRA_PROGRESS_MAX, 0) > 0 || ex.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE)
        // App de mapas com barra de progresso está baixando um mapa, não navegando.
        if (n.category == Notification.CATEGORY_NAVIGATION || (pkg in navApps && !hasBar)) return if (s.navEnabled && sbn.isOngoing) LiveKind.Navigation else null
        if (n.category == Notification.CATEGORY_ALARM && (n.fullScreenIntent != null || clockApp && sbn.isOngoing)) {
            // Lembrete de "próximo alarme" não tem tela cheia e não é contínuo.
            return if (s.alarmEnabled && n.fullScreenIntent != null) LiveKind.Alarm else null
        }
        // Relógio do Google: o timer não é "contínuo", não tem título e guarda a hora de terminar no "when"
        // (achado na bateria de testes). Reconhece pelo canal e pela hora no futuro.
        if (clockApp && s.liveEnabled && !sbn.isOngoing) {
            val ch = n.channelIdSafe().lowercase()
            if (("timer" in ch || "temporizador" in ch) && n.actions?.isNotEmpty() == true) return LiveKind.Timer
            if (("stopwatch" in ch || "cronômetro" in ch || "cronometro" in ch) && n.actions?.isNotEmpty() == true) return LiveKind.Stopwatch
        }
        if (!sbn.isOngoing) return null
        // Gravador de tela/voz: o Notch não grava nada, só mostra o tempo da gravação do outro app.
        val text = "${ex.getCharSequence(Notification.EXTRA_TITLE)} ${ex.getCharSequence(Notification.EXTRA_TEXT)}".lowercase()
        if (s.recorderEnabled && (recorderWords.any { it in text } || "recorder" in pkg || "screenrecord" in pkg)) return LiveKind.Recording
        // Tela sendo transmitida ou compartilhada (o Notch só mostra o aviso que o sistema já dá).
        if (s.shareEnabled && castWords.any { it in text }) return LiveKind.Cast
        // Arquivo indo ou vindo pelo Quick Share.
        if (s.shareEnabled && hasBar && (pkg in shareApps || shareWords.any { it in text }) && (pkg !in gmsApps || shareWords.any { it in text })) return LiveKind.Share
        if (pkg in deliveryApps) return null // pedidos: acompanhados à parte (Orders)
        if (s.activitiesEnabled) {
            if (pkg in tripApps) return LiveKind.Trip
            // Treino: pelo app ou pela categoria que o próprio app informa ao Android.
            if (pkg in fitnessApps || n.category == CATEGORY_WORKOUT) return LiveKind.Workout
        }
        val chrono = ex.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER)
        val words = "${ex.getCharSequence(Notification.EXTRA_TITLE)} ${ex.getCharSequence(Notification.EXTRA_TEXT)} ${n.channelIdSafe()}".lowercase()
        val isStopwatch = listOf("cronômetro", "cronometro", "stopwatch").any { it in words }
        val isTimer = listOf("timer", "temporizador").any { it in words }
        val clockKind = when {
            chrono && Build.VERSION.SDK_INT >= 24 && ex.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN) -> LiveKind.Timer
            n.category == CATEGORY_STOPWATCH && chrono -> LiveKind.Stopwatch
            clockApp && isStopwatch -> LiveKind.Stopwatch
            clockApp && isTimer -> LiveKind.Timer
            clockApp && chrono -> LiveKind.Stopwatch
            else -> null
        }
        if (clockKind != null) return if (s.liveEnabled) clockKind else null
        if (!s.activitiesEnabled) return null
        return when {
            // Download, envio ou instalação com barra de progresso.
            !clockApp && ex.getInt(Notification.EXTRA_PROGRESS_MAX, 0) > 0 && !ex.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE) -> LiveKind.Progress
            // Fase sem porcentagem (preparando, verificando, instalando): continua na ilha, com o anel girando.
            !clockApp && ex.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE) &&
                (sbn.key == lastProgress?.key || progressWords.any { it in words }) -> LiveKind.Progress
            else -> null
        }
    }

    /** Uma linha por notificação: de qual app é e como o Notch a leu (para descobrir por que algo não aparece). */
    private fun report(): String {
        val list = runCatching { activeNotifications }.getOrNull().orEmpty().filterNot { Banks.isBank(it.packageName) }
        if (list.isEmpty()) return "Nenhuma notificação agora."
        return list.sortedByDescending { it.postTime }.joinToString("\n\n") { sbn ->
            val n = sbn.notification
            val ex = n.extras
            val read = when {
                sbn.packageName == packageName -> "do próprio Notch"
                isCall(sbn) -> "chamada"
                sbn.packageName in deliveryApps -> "pedido: " + (Orders.parse(sbn).step?.label ?: "sem etapa reconhecida")
                sbn.packageName in parcelApps && Parcels.parse(sbn).step != null -> "encomenda: " + Parcels.parse(sbn).step?.label
                liveKind(sbn) != null -> "ao vivo: ${liveKind(sbn)}"
                isRegular(sbn) -> "notificação comum"
                else -> "ignorada"
            }
            val bar = if (ex.getInt(Notification.EXTRA_PROGRESS_MAX, 0) > 0 || ex.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE)) " · barra" else ""
            val chrono = if (ex.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER)) " · cronômetro" else ""
            val layout = if (n.contentView != null || n.bigContentView != null) " · layout próprio" else ""
            "${appLabel(this, sbn.packageName)} (${sbn.packageName})\n" +
                "→ $read\n" +
                "categoria ${n.category ?: "-"} · canal ${n.channelIdSafe().ifBlank { "-" }} · ${if (sbn.isOngoing) "contínua" else "comum"}$bar$chrono$layout\n" +
                "\"${ex.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty().take(50)}\" / \"${ex.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty().take(70)}\""
        }
    }

    // ---------- pedido de comida (iFood e parecidos) ----------

    private var order: LiveInfo? = null
    private var orderAt = 0L

    /** Lê o status do pedido de cada notificação do app de comida; true se era um status de pedido. */
    private fun trackOrder(sbn: StatusBarNotification): Boolean {
        if (sbn.packageName !in deliveryApps || !Prefs.state.value.activitiesEnabled) return false
        val u = Orders.parse(sbn)
        val prev = order?.takeIf { System.currentTimeMillis() - orderAt < 3 * 3600_000L }
        val step = u.step ?: if (prev != null && u.eta.isNotBlank()) prev.orderStep() else return false
        val n = sbn.notification
        order = LiveInfo(
            key = "pedido-${sbn.packageName}-${step.ordinal}", pkg = sbn.packageName, kind = LiveKind.Delivery,
            title = u.place.ifBlank { prev?.title.orEmpty() }, text = u.text, icon = null, chronoBase = 0, countDown = false,
            contentIntent = n.contentIntent ?: prev?.contentIntent, actions = emptyList(),
            appName = appLabel(this, sbn.packageName), smallIcon = n.smallIcon, color = if (n.color != 0) n.color else prev?.color ?: 0,
            step = step.ordinal, eta = u.eta.ifBlank { prev?.eta.orEmpty() },
        )
        orderAt = System.currentTimeMillis()
        return true
    }

    /** Pedido ainda valendo: some 1 min depois de entregue/cancelado, ou 3 h depois do último status. */
    private fun currentOrder(): LiveInfo? {
        val o = order ?: return null
        val age = System.currentTimeMillis() - orderAt
        val st = o.orderStep()
        val limit = if (st == OrderStep.Delivered || st == OrderStep.Cancelled) 60_000L else 3 * 3600_000L
        if (age > limit) { order = null; return null }
        if (age > limit - 60_000L || st == OrderStep.Delivered || st == OrderStep.Cancelled) {
            // Confere de novo quando o prazo acabar, para a ilha tirar o pedido sozinha.
            handler.removeCallbacks(expireOrder)
            handler.postDelayed(expireOrder, limit - age + 500)
        }
        return o
    }

    // ---------- encomenda (lojas e rastreio) ----------

    private var parcel: LiveInfo? = null
    private var parcelAt = 0L

    /** Lê o status da encomenda da notificação da loja; true se era um status de encomenda. */
    private fun trackParcel(sbn: StatusBarNotification): Boolean {
        if (sbn.packageName !in parcelApps || !Prefs.state.value.parcelEnabled) return false
        if (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return false
        val u = Parcels.parse(sbn)
        val step = u.step ?: return false
        val n = sbn.notification
        parcel = LiveInfo(
            key = "encomenda-${sbn.packageName}-${step.ordinal}-${sbn.id}", pkg = sbn.packageName, kind = LiveKind.Parcel,
            title = u.title, text = u.text, icon = null, chronoBase = 0, countDown = false,
            contentIntent = n.contentIntent, actions = emptyList(),
            appName = appLabel(this, sbn.packageName), smallIcon = n.smallIcon, color = n.color,
            step = step.ordinal, eta = u.eta,
        )
        parcelAt = System.currentTimeMillis()
        return true
    }

    /** A encomenda fica na ilha por um tempo depois de cada status (mais quando saiu para entrega). */
    private fun currentParcel(): LiveInfo? {
        val p = parcel ?: return null
        val age = System.currentTimeMillis() - parcelAt
        val limit = Parcels.showFor(p.parcelStep())
        if (age > limit) { parcel = null; return null }
        handler.removeCallbacks(expireOrder)
        handler.postDelayed(expireOrder, limit - age + 500)
        return p
    }

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val expireOrder = Runnable { refresh() }

    private var lastProgress: LiveInfo? = null
    private var maxSeen = 0f
    private val progressWords = listOf("baixando", "download", "instalando", "installing", "enviando", "upload")

    /**
     * "Concluído" só quando o download que estava na ilha chega a 100% ou some depois de ter passado de 90%
     * (a Play Store tira a notificação quando termina de instalar).
     */
    private fun watchDone(now: LiveInfo?) {
        val prev = lastProgress
        val cur = now?.takeIf { (it.kind == LiveKind.Progress || it.kind == LiveKind.Share) && it.progress < 1f }
        if (cur != null && cur.key != prev?.key) maxSeen = 0f
        if (cur != null) maxSeen = maxOf(maxSeen, cur.progress)
        lastProgress = cur
        if (prev == null) return
        val stillThere = runCatching { activeNotifications.any { it.key == prev.key } }.getOrDefault(false)
        val finished = (now?.key == prev.key && now.progress >= 1f) || (now?.key != prev.key && !stillThere && maxSeen >= 0.9f)
        if (now?.key != prev.key && !stillThere && cur == null) maxSeen = 0f
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

    /**
     * Notificação com layout próprio: monta o layout (como um widget) e lê o cronômetro dele.
     * Devolve a hora (relógio de parede) do cronômetro e se ele conta para baixo.
     */
    private fun chronoInLayout(n: Notification): Pair<Long, Boolean>? = runCatching {
        val rv = n.contentView ?: n.bigContentView ?: return null
        val root = rv.apply(this, android.widget.FrameLayout(this))
        fun find(v: android.view.View): android.widget.Chronometer? = when (v) {
            is android.widget.Chronometer -> v
            is android.view.ViewGroup -> (0 until v.childCount).firstNotNullOfOrNull { find(v.getChildAt(it)) }
            else -> null
        }
        val c = find(root) ?: return null
        val wall = c.base - android.os.SystemClock.elapsedRealtime() + System.currentTimeMillis()
        wall to (Build.VERSION.SDK_INT >= 24 && c.isCountDown)
    }.getOrNull()

    private fun Notification.channelIdSafe(): String = if (Build.VERSION.SDK_INT >= 26) channelId.orEmpty() else ""

    private fun toLive(sbn: StatusBarNotification, kind: LiveKind): LiveInfo {
        val n = sbn.notification
        val ex = n.extras
        val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (ex.getCharSequence(Notification.EXTRA_TEXT) ?: ex.getCharSequence(Notification.EXTRA_SUB_TEXT))?.toString().orEmpty()
        val chrono = ex.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER) && n.`when` > 0
        val customChrono = if (!chrono && (kind == LiveKind.Timer || kind == LiveKind.Stopwatch)) chronoInLayout(n) else null
        return LiveInfo(
            key = sbn.key,
            pkg = sbn.packageName,
            kind = kind,
            title = title.ifBlank { if (kind == LiveKind.Timer) "Timer" else if (kind == LiveKind.Stopwatch) "Cronômetro" else "" },
            text = text,
            icon = runCatching { n.getLargeIcon()?.loadDrawable(this)?.toBitmapSafe() }.getOrNull(),
            chronoBase = when {
                chrono -> n.`when`
                // Timer com layout próprio (Relógio do Google): lê o cronômetro de dentro do layout.
                (kind == LiveKind.Timer || kind == LiveKind.Stopwatch) && customChrono != null -> customChrono.first
                // Sem cronômetro do sistema: sincroniza pelo tempo escrito na notificação
                // (o gravador atualiza o texto) e conta sozinho entre uma atualização e outra.
                kind == LiveKind.Recording || kind == LiveKind.Stopwatch || kind == LiveKind.Cast ->
                    // Cada atualização chega igual ou atrasada, nunca adiantada: o menor valor é o início real.
                    // O "when" da notificação, quando o app coloca a hora de início (bem antes da postagem),
                    // é o início verdadeiro; o texto do gravador às vezes anda 1 s atrás dele.
                    run {
                        val explicitWhen = n.`when`.takeIf { it in 1 until sbn.postTime - 1500 }
                        val fromText = parseElapsed("$title $text")?.let { sbn.postTime - it }
                        val guess = listOfNotNull(explicitWhen, fromText).minOrNull()
                        if (guess == null) {
                            if (kind == LiveKind.Recording || kind == LiveKind.Cast) firstSeen.getOrPut(sbn.key) { sbn.postTime } else 0L
                        } else {
                            val best = minOf(textBase[sbn.key] ?: guess, guess)
                            textBase[sbn.key] = best
                            best
                        }
                    } - Prefs.state.value.liveOffsetMs
                else -> 0L
            },
            countDown = (Build.VERSION.SDK_INT >= 24 && ex.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN)) ||
                (!chrono && customChrono?.second == true),
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

    /** Cada atualização relia todas as notificações e suas fotos; agora só as que mudaram. */
    private val itemCache = HashMap<String, Pair<String, NotifItem?>>()

    private fun cachedItem(sbn: StatusBarNotification): NotifItem? {
        val sig = "${sbn.postTime}|${Prefs.state.value.showSensitive}"
        itemCache[sbn.key]?.let { (s, item) -> if (s == sig) return item }
        val item = toItem(sbn)
        itemCache[sbn.key] = sig to item
        return item
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

private val recorderWords = listOf("gravando", "gravação de tela", "gravacao de tela", "gravação em andamento", "gravacao em andamento", "recording", "screen record")

/** Categorias que o próprio app informa ao Android (existem desde o Android 12). */
private const val CATEGORY_WORKOUT = "workout"
private const val CATEGORY_STOPWATCH = "stopwatch"

private val castWords = listOf(
    "transmitindo", "transmissão de tela", "transmissao de tela", "espelhando", "espelhamento de tela", "compartilhando a tela",
    "compartilhando sua tela", "compartilhando a sua tela", "casting", "screen cast", "sharing your screen", "screen mirroring",
)

/** Quick Share: o do Google mora dentro do Google Play Services, que também manda outros avisos. */
private val gmsApps = setOf("com.google.android.gms")
private val shareApps = setOf("com.google.android.gms", "com.google.android.apps.nearby.sharing", "com.samsung.android.app.sharelive", "com.samsung.android.aware.service")
private val shareWords = listOf(
    "quick share", "compartilhar por proximidade", "compartilhamento por proximidade", "nearby share",
    "enviando para", "recebendo de", "sending to", "receiving from",
)

/** Reuniões de vídeo: a notificação contínua da reunião vira uma chamada de vídeo na ilha. */
private val meetApps = setOf(
    "com.google.android.apps.meetings", "com.google.android.apps.tachyon", "us.zoom.videomeetings", "com.microsoft.teams",
)

/** Treino em andamento (Google Fit, Strava, Samsung Health, Nike Run). */
private val fitnessApps = setOf(
    "com.google.android.apps.fitness", "com.strava", "com.sec.android.app.shealth", "com.nike.plusgps",
    "com.runtastic.android", "com.garmin.android.apps.connectmobile", "com.fitbit.FitbitMobile", "com.mi.health",
    "com.xiaomi.wearable", "com.huami.watch.hmwatchmanager", "cc.pacer.androidapp", "com.fitnesskeeper.runkeeper.pro",
)

/** Corridas e entregas: a notificação contínua vira atividade ao vivo. */
private val tripApps = setOf(
    "com.ubercab", "com.ubercab.eats", "com.taxis99", "br.com.brainweb.ifood", "com.grability.rappi",
    "com.cabify.rider", "com.indriver", "sinet.startup.inDriver", "com.ubercab.uberlite", "com.lalamove.huolala.client",
)

private val navApps = setOf(
    "com.google.android.apps.maps", "com.google.android.apps.mapslite", "com.waze", "com.here.app.maps", "com.sygic.aura",
    "ru.yandex.yandexnavi", "net.osmand", "net.osmand.plus", "app.organicmaps", "com.mapswithme.maps.pro", "com.tomtom.gplay.navapp",
)

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
