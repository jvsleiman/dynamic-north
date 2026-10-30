package com.joao.ilha

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Dados de saúde do dia: passos (sensor do celular), copos de água, tempo de tela
 * desde a última pausa. Tudo fica só no aparelho e zera à meia-noite.
 */
object Health : SensorEventListener {
    val steps = MutableStateFlow(0)
    val water = MutableStateFlow(0)
    val stepsAvailable = MutableStateFlow(false)

    /** Desde quando a tela está em uso sem pausa (0 = tela apagada). */
    val usingSince = MutableStateFlow(0L)

    private lateinit var sp: SharedPreferences
    private var sensors: SensorManager? = null
    private var listening = false

    fun init(ctx: Context) {
        if (::sp.isInitialized) return
        sp = ctx.applicationContext.getSharedPreferences("saude", Context.MODE_PRIVATE)
        rollDay()
        steps.value = sp.getInt("steps", 0)
        water.value = sp.getInt("water", 0)
    }

    fun hasStepPermission(ctx: Context) = Build.VERSION.SDK_INT < 29 ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED

    /** Liga o contador de passos se houver sensor e permissão. Pode ser chamado várias vezes. */
    fun startSteps(ctx: Context) {
        init(ctx)
        if (listening || !hasStepPermission(ctx)) return
        val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) ?: return
        sensors = sm
        listening = sm.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        stepsAvailable.value = listening
    }

    fun stopSteps() {
        sensors?.unregisterListener(this)
        listening = false
    }

    override fun onSensorChanged(e: SensorEvent) {
        rollDay()
        // O sensor conta desde que o celular ligou; guardamos só a diferença de cada leitura.
        val counter = e.values[0].toLong()
        val last = sp.getLong("lastCounter", -1)
        val delta = when {
            last < 0 -> 0
            counter < last -> counter // celular reiniciou
            else -> counter - last
        }
        val total = steps.value + delta.toInt()
        steps.value = total
        sp.edit().putLong("lastCounter", counter).putInt("steps", total).apply()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    fun addWater(n: Int) {
        rollDay()
        val v = (water.value + n).coerceIn(0, 30)
        water.value = v
        sp.edit().putInt("water", v).apply()
    }

    fun screenOn() {
        if (usingSince.value == 0L) usingSince.value = System.currentTimeMillis()
    }

    fun screenOff() {
        usingSince.value = 0L
    }

    /** "Fiz uma pausa": recomeça a contagem do tempo de tela. */
    fun breakTaken() {
        usingSince.value = System.currentTimeMillis()
    }

    fun minutesUsing(now: Long = System.currentTimeMillis()): Int =
        usingSince.value.let { if (it == 0L) 0 else ((now - it) / 60_000).toInt() }

    /** Minutos até a hora de dormir (de 0 a 24 h). */
    fun minutesToBed(bedtime: Int): Int {
        val c = Calendar.getInstance()
        val nowMin = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
        return ((bedtime - nowMin) + 24 * 60) % (24 * 60)
    }

    private fun rollDay() {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        if (sp.getString("day", null) == today) return
        sp.edit().putString("day", today).putInt("steps", 0).putInt("water", 0).apply()
        steps.value = 0
        water.value = 0
    }
}

fun formatSteps(n: Int): String = String.format(Locale("pt", "BR"), "%,d", n)

fun formatMinutes(m: Int): String = when {
    m < 60 -> "$m min"
    m % 60 == 0 -> "${m / 60} h"
    else -> "${m / 60} h ${m % 60}"
}
