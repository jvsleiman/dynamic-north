package com.joao.ilha

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.MainScope

/** Só existe na versão de testes: `am broadcast -a com.joao.ilha.TESTE --es caso <nome>`. */
class TestReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val ctx = c.applicationContext
        when (i.getStringExtra("caso")) {
            "whatsapp" -> Demo.whatsapp(ctx, false)
            "grupo" -> Demo.whatsapp(ctx, true)
            "musica" -> Demo.music(ctx)
            "parar-musica" -> Demo.stopMusic()
            "chamada" -> Demo.call(ctx, true)
            "chamada-ativa" -> Demo.call(ctx, false)
            "reuniao" -> Demo.meeting(ctx)
            "fim-chamada" -> Demo.endCall()
            "timer" -> Demo.live(ctx, LiveKind.Timer)
            "cronometro" -> Demo.live(ctx, LiveKind.Stopwatch)
            "alarme" -> Demo.live(ctx, LiveKind.Alarm)
            "rota" -> Demo.live(ctx, LiveKind.Navigation)
            "gravacao" -> Demo.live(ctx, LiveKind.Recording)
            "download" -> Demo.download(ctx, MainScope())
            "corrida" -> Demo.trip(ctx)
            "pedido" -> Demo.order(ctx)
            "treino" -> Demo.workout(ctx)
            "parar-live" -> Demo.stopLive()
            "camera" -> Demo.privacy(true)
            "bluetooth" -> IslandBus.show(Popup.Bluetooth(BluetoothInfo("Fones de teste", BtKind.Earbuds, true, 80)))
            "principal" -> { Demo.weather(); IslandBus.expand?.invoke(ExpandKind.Home) }
            "clima" -> { Demo.nextSky(); IslandBus.expand?.invoke(ExpandKind.Home) }
            "controles" -> IslandBus.expand?.invoke(ExpandKind.Controls)
            "atividade" -> IslandBus.expand?.invoke(ExpandKind.Activity)
            "intro" -> IslandBus.show(Popup.Intro)
            "teste" -> IslandBus.show(Popup.Test)
            "limpar" -> Demo.clearAll()
        }
    }
}
