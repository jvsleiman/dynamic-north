package com.joao.ilha

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.audiofx.Visualizer
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max

/**
 * Lê o som que está tocando (saída geral do celular) e transforma em 4 faixas 0..1
 * para as barrinhas da música. Precisa da permissão de microfone, que o Android exige
 * para o Visualizer, mas nada é gravado: só a intensidade de cada faixa é usada.
 */
object AudioLevels {
    val levels = MutableStateFlow(FloatArray(BANDS))
    val active = MutableStateFlow(false)
    private var viz: Visualizer? = null
    private val peak = FloatArray(BANDS) { 1f }

    fun granted(ctx: Context) =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    fun start(ctx: Context) {
        if (viz != null || !granted(ctx)) return
        runCatching {
            val v = Visualizer(0)
            v.enabled = false
            v.captureSize = Visualizer.getCaptureSizeRange()[0].coerceAtLeast(128)
            v.setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                override fun onWaveFormDataCapture(v: Visualizer?, data: ByteArray?, rate: Int) {}
                override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, rate: Int) {
                    if (fft != null) levels.value = bands(fft)
                }
            }, (Visualizer.getMaxCaptureRate() / 2).coerceAtMost(20000), false, true)
            v.enabled = true
            viz = v
            active.value = true
        }.onFailure { stop() }
    }

    fun stop() {
        runCatching { viz?.enabled = false; viz?.release() }
        viz = null
        active.value = false
        levels.value = FloatArray(BANDS)
    }

    /** Agrupa o FFT em faixas (graves → agudos) com escala logarítmica e ganho automático. */
    private fun bands(fft: ByteArray): FloatArray {
        val n = fft.size / 2
        val edges = intArrayOf(1, 3, 8, 20, n.coerceAtLeast(21))
        val out = FloatArray(BANDS)
        for (b in 0 until BANDS) {
            var sum = 0f
            var count = 0
            for (k in edges[b] until edges[b + 1].coerceAtMost(n)) {
                sum += hypot(fft[2 * k].toFloat(), fft[2 * k + 1].toFloat())
                count++
            }
            val mag = if (count > 0) ln(1f + sum / count) else 0f
            peak[b] = max(peak[b] * 0.995f, max(mag, 0.5f))
            out[b] = (mag / peak[b]).coerceIn(0f, 1f)
        }
        return out
    }

    private const val BANDS = 4
}
