package com.joao.ilha

import android.app.ActivityManager
import android.app.AlarmManager
import android.content.Context
import android.net.TrafficStats
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Velocidade de download+upload somando todas as interfaces (bytes por segundo). */
object NetMeter {
    private var lastBytes = -1L
    private var lastAt = 0L

    fun sample(): Long {
        val bytes = TrafficStats.getTotalRxBytes() + TrafficStats.getTotalTxBytes()
        val now = SystemClock.elapsedRealtime()
        val speed = if (lastBytes >= 0 && now > lastAt) (bytes - lastBytes) * 1000 / (now - lastAt) else 0
        lastBytes = bytes
        lastAt = now
        return speed.coerceAtLeast(0)
    }
}

object SystemInfo {
    fun freeRamPercent(ctx: Context): Int {
        val mi = ActivityManager.MemoryInfo()
        (ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(mi)
        return if (mi.totalMem > 0) (mi.availMem * 100 / mi.totalMem).toInt() else 0
    }

    fun freeStorageGb(): Int = runCatching {
        val st = StatFs(Environment.getDataDirectory().path)
        (st.availableBytes / 1_000_000_000L).toInt()
    }.getOrDefault(0)

    fun nextAlarm(ctx: Context): String? {
        val t = (ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager).nextAlarmClock?.triggerTime ?: return null
        return SimpleDateFormat("EEE HH:mm", Locale("pt", "BR")).format(Date(t)).replace(".", "")
    }
}
