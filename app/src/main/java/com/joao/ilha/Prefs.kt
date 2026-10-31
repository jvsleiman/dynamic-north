package com.joao.ilha

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** O que a ilha faz ao ser tocada ou segurada. */
enum class Gesture(val label: String) {
    Expand("Expandir a ilha"),
    OpenApp("Abrir o app da atividade"),
    Controls("Abrir os controles rápidos"),
    Nothing("Nada"),
}

/** Física da mola usada ao crescer e encolher. */
enum class Motion(val label: String, val damping: Float, val stiffness: Float) {
    Liquid("Líquido", 0.82f, 220f),
    Bouncy("Saltitante", 0.55f, 380f),
    Drop("Gota", 0.95f, 700f),
}

enum class BorderAnim(val label: String) { Off("Desligada"), Rainbow("Multicolorida"), Single("Uma cor") }

/** O que a ilha mostra quando não há nada acontecendo. */
enum class IdleStyle(val label: String, val hidden: Boolean = false) {
    Simple("Simples (só a ilha)"),
    AppIcons("Ícones dos apps com notificação"),
    Count("Número de notificações"),
    Clock("Hora"),
    Date("Data", hidden = true),
    Battery("Bateria"),
    ClockBattery("Hora e bateria"),
    NetSpeed("Velocidade da internet", hidden = true),
    Memory("Memória livre", hidden = true),
    Storage("Armazenamento", hidden = true),
    Alarm("Próximo alarme"),
    Greeting("Saudação", hidden = true),
    Pulse("Luz pulsando", hidden = true),
    Health("Saúde (passos e água)"),
    Steps("Passos do dia", hidden = true),
    Water("Copos de água", hidden = true),
    ScreenBreak("Pausa para os olhos"),
    Breathing("Respiração guiada"),
    Bedtime("Hora de dormir"),
    ;

    /** Estilos tirados da lista (0.9.5) continuam lidos dos ajustes antigos e viram o mais parecido. */
    fun current(): IdleStyle = when (this) {
        Date -> Clock
        Steps, Water -> Health
        else -> if (hidden) Simple else this
    }
}

enum class AppTheme(val label: String) { System("Automático (sistema)"), Light("Claro"), Dark("Escuro") }

/** Todos os ajustes. Tamanhos e posições em dp; cores em ARGB. */
@Serializable
data class Settings(
    val enabled: Boolean = true,

    // Exibição
    val width: Int = 110,
    val height: Int = 34,
    val offsetX: Int = 0,
    val offsetY: Int = 0,
    val alignToCamera: Boolean = true,
    val growWhenActive: Boolean = true,
    val smallRadius: Int = 50,
    val largeRadius: Int = 36,
    val largeMaxWidth: Int = 400,
    val fullWidth: Boolean = true,
    val sideMargin: Int = 8,
    /** Espaço entre a câmera e a ilha quando ela abre grande. */
    val largeTopGap: Int = 14,
    val idleStyle: IdleStyle = IdleStyle.Simple,
    val bgColor: Long = 0xFF000000,
    val textColor: Long = 0xFFFFFFFF,
    val autoColor: Boolean = false,
    val showInLandscape: Boolean = false,
    val borderSmall: Boolean = false,
    val borderLarge: Boolean = false,
    val borderColor: Long = 0xFF3A3A3C,
    val motion: Motion = Motion.Liquid,

    // Saúde
    val stepGoal: Int = 8000,
    val waterGoal: Int = 8,
    val bedtime: Int = 23 * 60,
    val breakMinutes: Int = 20,
    val breakReminder: Boolean = true,
    val waterReminder: Boolean = false,

    // Personalizar layouts
    val textScale: Int = 100,
    val iconScale: Int = 100,
    val layoutScale: Int = 100,
    val listRows: Int = 4,
    val showNotifActions: Boolean = true,

    // Notificações
    val notifEnabled: Boolean = true,
    val notifExpanded: Boolean = true,
    val blockedNotifApps: Set<String> = emptySet(),
    val showSensitive: Boolean = false,
    val popupSeconds: Int = 5,
    val keepUntilRead: Boolean = false,
    val swipeToDismiss: Boolean = true,
    val notifOnLockscreen: Boolean = true,
    val showCount: Boolean = true,

    // Chamadas
    val callEnabled: Boolean = true,
    val showOngoingCall: Boolean = true,
    val callWaveColor: Long = 0xFF34C759,

    // Música
    val musicEnabled: Boolean = true,
    val blurArt: Boolean = true,
    val seekButtons: Boolean = true,
    val musicOnlyWhilePlaying: Boolean = false,
    val blockedMusicApps: Set<String> = emptySet(),
    val reactiveBars: Boolean = true,
    val swipeChangesTrack: Boolean = true,
    val volumeInPlayer: Boolean = true,

    // Controles rápidos, volume, jogo
    val controlsApps: Set<String> = emptySet(),
    val volumePopup: Boolean = true,
    val gameMode: Boolean = true,
    val respectDnd: Boolean = true,
    val recorderEnabled: Boolean = true,

    // Ao vivo
    val liveEnabled: Boolean = true,
    val navEnabled: Boolean = true,
    val alarmEnabled: Boolean = true,
    val privacyDot: Boolean = true,
    val batteryAlerts: Boolean = true,
    val lowBatteryLevel: Int = 15,

    // Outros recursos
    val chargingEnabled: Boolean = true,
    val bluetoothEnabled: Boolean = true,
    val borderAnim: BorderAnim = BorderAnim.Off,
    val borderAnimColor: Long = 0xFF0A84FF,
    val borderAnimSpeed: Int = 3,
    val borderAnimWidth: Int = 2,

    // Gestos e visibilidade
    val tapGesture: Gesture = Gesture.Expand,
    val holdGesture: Gesture = Gesture.OpenApp,
    val hiddenApps: Set<String> = emptySet(),
    val dualIsland: Boolean = true,
    val dualOnRight: Boolean = true,
    val hideOnLockscreen: Boolean = false,
    val autoHideSeconds: Int = 0,
    val keepMiniWhenHidden: Boolean = true,
    val vibrate: Boolean = true,
    val appTheme: AppTheme = AppTheme.System,
)

object Prefs {
    private lateinit var sp: SharedPreferences
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val _state = MutableStateFlow(Settings())
    val state: StateFlow<Settings> get() = _state

    fun init(context: Context) {
        if (::sp.isInitialized) return
        sp = context.applicationContext.getSharedPreferences("ilha", Context.MODE_PRIVATE)
        CrashLog.install(context.applicationContext)
        _state.value = sp.getString("settings", null)
            ?.let { runCatching { json.decodeFromString<Settings>(it) }.getOrNull() }
            ?.let { it.copy(idleStyle = it.idleStyle.current()) }
            ?: Settings()
    }

    fun update(change: (Settings) -> Settings) {
        val s = change(_state.value)
        _state.value = s
        sp.edit().putString("settings", json.encodeToString(Settings.serializer(), s)).apply()
        IslandBus.refresh?.invoke()
    }

    fun resetDisplay() = update {
        val d = Settings()
        it.copy(
            width = d.width, height = d.height, offsetX = d.offsetX, offsetY = d.offsetY,
            alignToCamera = d.alignToCamera, growWhenActive = d.growWhenActive,
            smallRadius = d.smallRadius, largeRadius = d.largeRadius, largeMaxWidth = d.largeMaxWidth,
            fullWidth = d.fullWidth, sideMargin = d.sideMargin,
            bgColor = d.bgColor, textColor = d.textColor, autoColor = d.autoColor,
            borderSmall = d.borderSmall, borderLarge = d.borderLarge, borderColor = d.borderColor,
            motion = d.motion,
            textScale = d.textScale, iconScale = d.iconScale, layoutScale = d.layoutScale, listRows = d.listRows,
        )
    }
}

/** Guarda o último erro que fechou o app, para mostrar em Ajuda e facilitar a correção. */
object CrashLog {
    private const val FILE = "ultimo-erro.txt"

    fun install(ctx: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        if (previous is Handler) return
        Thread.setDefaultUncaughtExceptionHandler(Handler(ctx, previous))
    }

    fun read(ctx: Context): String? = runCatching { java.io.File(ctx.filesDir, FILE).readText() }.getOrNull()?.takeIf { it.isNotBlank() }

    fun clear(ctx: Context) {
        runCatching { java.io.File(ctx.filesDir, FILE).delete() }
    }

    private class Handler(val ctx: Context, val previous: Thread.UncaughtExceptionHandler?) : Thread.UncaughtExceptionHandler {
        override fun uncaughtException(t: Thread, e: Throwable) {
            runCatching {
                val when_ = java.text.SimpleDateFormat("dd/MM HH:mm:ss", java.util.Locale.US).format(java.util.Date())
                val info = "Ilha ${BuildConfig.VERSION_NAME} · Android ${android.os.Build.VERSION.RELEASE} · ${android.os.Build.MODEL} · $when_\n"
                java.io.File(ctx.filesDir, FILE).writeText(info + e.stackTraceToString().take(6000))
            }
            previous?.uncaughtException(t, e)
        }
    }
}
