package com.joao.ilha

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

/**
 * Pulso entre [low] e [high]. Com a tela apagada a espera suspende o laço,
 * em vez de pedir quadro para sempre.
 */
@Composable
internal fun rememberScreenOnPulse(low: Float, high: Float, periodMs: Int): Float {
    var v by remember(low, high) { mutableFloatStateOf(low) }
    LaunchedEffect(low, high, periodMs) {
        while (true) {
            IslandBus.awaitScreenOn()
            val phase = (System.currentTimeMillis() % periodMs) / periodMs.toFloat()
            val tri = if (phase < 0.5f) phase / 0.5f else (1f - phase) / 0.5f
            v = low + (high - low) * tri
            delay(32)
        }
    }
    return v
}
