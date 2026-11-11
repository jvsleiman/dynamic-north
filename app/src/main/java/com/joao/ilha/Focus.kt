package com.joao.ilha

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Foco (Pomodoro) do próprio Notch: um período de foco, uma pausa, e de novo.
 * Não depende de outro app nem de notificação; aparece na ilha como uma atividade ao vivo.
 */
object Focus {
    enum class Phase { Focus, Break }

    data class State(val phase: Phase, val endAt: Long, val remainingMs: Long, val paused: Boolean, val round: Int)

    val state = MutableStateFlow<State?>(null)

    private const val FOCUS_COLOR = 0xFFFF6B5E.toInt()
    private const val BREAK_COLOR = 0xFF34C759.toInt()

    private val handler = Handler(Looper.getMainLooper())
    private val phaseEnd = Runnable { next(announce = true) }
    private var app: Context? = null

    fun start(ctx: Context) {
        app = ctx.applicationContext
        begin(Phase.Focus, 1)
    }

    fun pause() {
        val st = state.value?.takeIf { !it.paused } ?: return
        handler.removeCallbacks(phaseEnd)
        state.value = st.copy(paused = true, remainingMs = (st.endAt - System.currentTimeMillis()).coerceAtLeast(0))
        publish()
    }

    fun resume() {
        val st = state.value?.takeIf { it.paused } ?: return
        state.value = st.copy(paused = false, endAt = System.currentTimeMillis() + st.remainingMs)
        handler.postDelayed(phaseEnd, st.remainingMs)
        publish()
    }

    /** Pula para a próxima etapa (do foco para a pausa, da pausa para o próximo foco). */
    fun skip() = next(announce = false)

    fun stop() {
        handler.removeCallbacks(phaseEnd)
        state.value = null
        publish()
    }

    private fun begin(phase: Phase, round: Int) {
        val s = Prefs.state.value
        val ms = (if (phase == Phase.Focus) s.focusMinutes else s.focusBreakMinutes).coerceAtLeast(1) * 60_000L
        handler.removeCallbacks(phaseEnd)
        state.value = State(phase, System.currentTimeMillis() + ms, ms, false, round)
        handler.postDelayed(phaseEnd, ms)
        publish()
    }

    private fun next(announce: Boolean) {
        val st = state.value ?: return
        val focus = st.phase == Phase.Focus
        if (announce) IslandBus.show(
            Popup.Done(if (focus) "Foco terminado: hora da pausa" else "Pausa terminada: hora de focar", "Foco", null, if (focus) FOCUS_COLOR else BREAK_COLOR),
        )
        if (focus) begin(Phase.Break, st.round) else begin(Phase.Focus, st.round + 1)
    }

    private fun action(ctx: Context, what: String, code: Int): PendingIntent = PendingIntent.getBroadcast(
        ctx, code, Intent(ctx, FocusReceiver::class.java).setAction(what), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun publish() {
        val ctx = app ?: return
        val st = state.value
        val info = st?.let {
            val focus = it.phase == Phase.Focus
            val left = it.remainingMs / 1000
            LiveInfo(
                key = "foco-${it.phase}-${it.round}", pkg = ctx.packageName, kind = LiveKind.Focus,
                // Em pausa o relógio fica parado no tempo que falta.
                title = if (it.paused) "%d:%02d".format(left / 60, left % 60) else "",
                text = "Rodada ${it.round}", icon = null,
                chronoBase = if (it.paused) 0 else it.endAt, countDown = true, contentIntent = null,
                actions = listOf(
                    if (it.paused) NotifAction("Retomar", action(ctx, FocusReceiver.RESUME, 1), null) else NotifAction("Pausar", action(ctx, FocusReceiver.PAUSE, 2), null),
                    NotifAction("Pular", action(ctx, FocusReceiver.SKIP, 3), null),
                    NotifAction("Parar", action(ctx, FocusReceiver.STOP, 4), null),
                ),
                appName = if (focus) "Foco" else "Pausa", color = if (focus) FOCUS_COLOR else BREAK_COLOR,
            )
        }
        IslandBus.own.value = info
        val refresh = IslandBus.refresh
        // Sem o leitor de notificações ligado, o Foco aparece direto na ilha.
        if (refresh != null) refresh() else if (info != null || IslandBus.live.value?.kind == LiveKind.Focus) IslandBus.live.value = info
    }
}

/** Botões do Foco na ilha (pausar, retomar, pular, parar). */
class FocusReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, i: Intent) {
        when (i.action) {
            PAUSE -> Focus.pause()
            RESUME -> Focus.resume()
            SKIP -> Focus.skip()
            STOP -> Focus.stop()
        }
    }

    companion object {
        const val PAUSE = "com.joao.ilha.foco.PAUSAR"
        const val RESUME = "com.joao.ilha.foco.RETOMAR"
        const val SKIP = "com.joao.ilha.foco.PULAR"
        const val STOP = "com.joao.ilha.foco.PARAR"
    }
}
