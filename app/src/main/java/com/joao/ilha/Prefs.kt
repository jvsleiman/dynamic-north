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
enum class IdleStyle(val label: String) {
    Simple("Simples (só a ilha)"),
    AppIcons("Ícones dos apps com notificação"),
    Count("Número de notificações"),
    Clock("Hora"),
    Date("Data"),
    Battery("Bateria"),
    ClockBattery("Hora e bateria"),
    NetSpeed("Velocidade da internet"),
    Memory("Memória livre"),
    Storage("Armazenamento"),
    Alarm("Próximo alarme"),
    Greeting("Saudação"),
    Pulse("Luz pulsando"),
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
    val idleStyle: IdleStyle = IdleStyle.Simple,
    val bgColor: Long = 0xFF000000,
    val textColor: Long = 0xFFFFFFFF,
    val autoColor: Boolean = false,
    val showInLandscape: Boolean = false,
    val borderSmall: Boolean = false,
    val borderLarge: Boolean = false,
    val borderColor: Long = 0xFF3A3A3C,
    val motion: Motion = Motion.Liquid,

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
        _state.value = sp.getString("settings", null)
            ?.let { runCatching { json.decodeFromString<Settings>(it) }.getOrNull() }
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
