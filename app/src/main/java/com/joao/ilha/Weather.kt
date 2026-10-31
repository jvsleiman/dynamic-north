package com.joao.ilha

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.provider.CalendarContract
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Grain
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Thunderstorm
import androidx.compose.material.icons.rounded.Umbrella
import androidx.compose.material.icons.rounded.WbCloudy
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Calendar

data class HourForecast(val hour: Int, val code: Int, val temp: Int, val day: Boolean)

data class WeatherInfo(val temp: Int, val code: Int, val day: Boolean, val hours: List<HourForecast>, val at: Long)

/** Clima pela Open-Meteo (sem chave): localização aproximada do celular, atualiza a cada 30 min. */
object Weather {
    val now = MutableStateFlow<WeatherInfo?>(null)

    fun hasPermission(ctx: Context) =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    suspend fun refresh(ctx: Context, force: Boolean = false) {
        val last = now.value
        if (!force && last != null && System.currentTimeMillis() - last.at < 30 * 60_000) return
        if (!hasPermission(ctx)) return
        val loc = location(ctx) ?: return
        val info = withContext(Dispatchers.IO) { runCatching { fetch(loc) }.getOrNull() } ?: return
        now.value = info
    }

    @SuppressLint("MissingPermission")
    private fun location(ctx: Context): Location? {
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER, LocationManager.GPS_PROVIDER)
            .mapNotNull { p -> runCatching { lm.getLastKnownLocation(p) }.getOrNull() }
            .maxByOrNull { it.time }
    }

    private fun fetch(loc: Location): WeatherInfo {
        val url = "https://api.open-meteo.com/v1/forecast?latitude=%.3f&longitude=%.3f".format(java.util.Locale.US, loc.latitude, loc.longitude) +
            "&current=temperature_2m,weather_code,is_day&hourly=temperature_2m,weather_code,is_day&forecast_hours=6&timezone=auto"
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 8000
        val json = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
        val cur = json.getJSONObject("current")
        val h = json.getJSONObject("hourly")
        val times = h.getJSONArray("time")
        val temps = h.getJSONArray("temperature_2m")
        val codes = h.getJSONArray("weather_code")
        val days = h.getJSONArray("is_day")
        val hours = (0 until times.length()).map { i ->
            HourForecast(times.getString(i).substringAfter('T').take(2).toInt(), codes.getInt(i), Math.round(temps.getDouble(i)).toInt(), days.getInt(i) == 1)
        }
        // A primeira hora da lista é a atual: mostra as 4 seguintes.
        return WeatherInfo(
            Math.round(cur.getDouble("temperature_2m")).toInt(), cur.getInt("weather_code"), cur.optInt("is_day", 1) == 1,
            hours.drop(1).take(4), System.currentTimeMillis(),
        )
    }
}

/** Ícone e nome do tempo pelo código WMO da Open-Meteo. */
fun weatherIcon(code: Int, day: Boolean): ImageVector = when (code) {
    0, 1 -> if (day) Icons.Rounded.WbSunny else Icons.Rounded.NightsStay
    2 -> if (day) Icons.Rounded.WbCloudy else Icons.Rounded.Cloud
    3, 45, 48 -> Icons.Rounded.Cloud
    in 51..57 -> Icons.Rounded.Grain
    in 61..67, in 80..82 -> Icons.Rounded.Umbrella
    in 71..77, 85, 86 -> Icons.Rounded.AcUnit
    in 95..99 -> Icons.Rounded.Thunderstorm
    else -> Icons.Rounded.Cloud
}

fun weatherLabel(code: Int): String = when (code) {
    0 -> "Céu limpo"
    1 -> "Quase limpo"
    2 -> "Parcialmente nublado"
    3 -> "Nublado"
    45, 48 -> "Neblina"
    in 51..57 -> "Garoa"
    in 61..67 -> "Chuva"
    in 71..77, 85, 86 -> "Neve"
    in 80..82 -> "Pancadas de chuva"
    in 95..99 -> "Tempestade"
    else -> ""
}

data class AgendaEvent(val title: String, val begin: Long, val end: Long, val allDay: Boolean)

data class AgendaInfo(val next: AgendaEvent?, val daysWithEvents: Set<Int>)

/** Próximo compromisso da agenda do celular e os dias da semana que têm algo marcado. */
object Agenda {
    val now = MutableStateFlow<AgendaInfo?>(null)

    fun hasPermission(ctx: Context) =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    suspend fun refresh(ctx: Context) {
        if (!hasPermission(ctx)) return
        now.value = withContext(Dispatchers.IO) { runCatching { query(ctx) }.getOrNull() }
    }

    private fun query(ctx: Context): AgendaInfo {
        val start = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val end = start + 14L * 24 * 3600_000
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, start)
            ContentUris.appendId(it, end)
        }.build()
        val cols = arrayOf(CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN, CalendarContract.Instances.END, CalendarContract.Instances.ALL_DAY)
        val events = ArrayList<AgendaEvent>()
        ctx.contentResolver.query(uri, cols, null, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
            while (c.moveToNext()) {
                events += AgendaEvent(c.getString(0).orEmpty().ifBlank { "Compromisso" }, c.getLong(1), c.getLong(2), c.getInt(3) == 1)
            }
        }
        val nowMs = System.currentTimeMillis()
        val weekEnd = start + 7L * 24 * 3600_000
        val days = events.filter { it.begin in start until weekEnd }.map { e ->
            Calendar.getInstance().apply { timeInMillis = e.begin }.get(Calendar.DAY_OF_YEAR)
        }.toSet()
        val next = events.firstOrNull { !it.allDay && it.end > nowMs } ?: events.firstOrNull { it.end > nowMs }
        return AgendaInfo(next, days)
    }
}
