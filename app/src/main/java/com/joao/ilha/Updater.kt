package com.joao.ilha

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Atualizações pelo branch `releases` do repositório público:
 * version.json diz a versão mais nova e onde está o APK assinado.
 */
object Updater {
    private const val BASE = "https://raw.githubusercontent.com/jvsleiman/dynamic-north/releases/"

    data class Release(val versionCode: Int, val versionName: String, val apk: String, val notes: String)

    fun currentVersion(ctx: Context): Pair<Int, String> {
        val info = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        val code = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode.toInt() else @Suppress("DEPRECATION") info.versionCode
        return code to (info.versionName ?: "?")
    }

    suspend fun latest(): Release = withContext(Dispatchers.IO) {
        val json = JSONObject(get(BASE + "version.json?t=" + System.currentTimeMillis()).readBytes().decodeToString())
        Release(
            versionCode = json.getInt("versionCode"),
            versionName = json.getString("versionName"),
            apk = json.getString("apk"),
            notes = json.optString("notes"),
        )
    }

    /** Baixa o APK e devolve o arquivo; [progress] recebe 0..1. */
    suspend fun download(ctx: Context, r: Release, progress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(ctx.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val out = File(dir, "Ilha-${r.versionName}.apk")
        val conn = get(BASE + r.apk)
        val total = conn.contentLengthLong
        conn.inputStream.use { input ->
            out.outputStream().use { o ->
                val buf = ByteArray(64 * 1024)
                var done = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    o.write(buf, 0, n)
                    done += n
                    if (total > 0) progress(done.toFloat() / total)
                }
            }
        }
        out
    }

    fun install(ctx: Context, apk: File) {
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", apk)
        val i = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
    }

    private fun get(url: String): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15000
        c.readTimeout = 30000
        c.useCaches = false
        if (c.responseCode !in 200..299) throw IllegalStateException("HTTP ${c.responseCode}")
        return c
    }

    private fun HttpURLConnection.readBytes(): ByteArray = inputStream.use { it.readBytes() }
}
