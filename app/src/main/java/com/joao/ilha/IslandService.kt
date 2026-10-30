package com.joao.ilha

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Rect
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.media.AudioRecordingConfiguration
import android.os.Handler
import android.os.Looper
import android.hardware.display.DisplayManager
import android.os.BatteryManager
import android.os.Build
import android.view.Display
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** Estado da ilha que a interface desenha. */
enum class ExpandKind { Activity, Battery, Clock, Notifications, System, Health, Breathe, Live }

class IslandUiState {
    val popup = MutableStateFlow<Popup?>(null)
    val expanded = MutableStateFlow(false)
    /** De onde a ilha foi aberta: decide qual layout aparece. */
    val expandKind = MutableStateFlow(ExpandKind.Activity)
    val asleep = MutableStateFlow(false)
    val screenWidthDp = MutableStateFlow(360)
}

/** Serviço de acessibilidade que desenha a ilha por cima de todos os apps. */
class IslandService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var wm: WindowManager
    private var view: ComposeView? = null
    private var owner: OverlayOwner? = null
    private var params: WindowManager.LayoutParams? = null
    private var popupJob: Job? = null
    private var sleepJob: Job? = null

    val ui = IslandUiState()
    private val foregroundPkg = MutableStateFlow("")
    private val locked = MutableStateFlow(false)
    private var lastBt: BluetoothInfo? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            val s = Prefs.state.value
            when (i.action) {
                Intent.ACTION_POWER_CONNECTED, Intent.ACTION_POWER_DISCONNECTED -> if (s.chargingEnabled) {
                    show(Popup.Charging(ChargingInfo(batteryLevel(), i.action == Intent.ACTION_POWER_CONNECTED)))
                }
                Intent.ACTION_BATTERY_CHANGED -> batteryAlert(i)
                Intent.ACTION_SCREEN_OFF -> {
                    locked.value = true
                    Health.screenOff()
                }
                Intent.ACTION_SCREEN_ON -> {
                    locked.value = (getSystemService(KEYGUARD_SERVICE) as KeyguardManager).isKeyguardLocked
                    if (!locked.value) Health.screenOn()
                }
                Intent.ACTION_USER_PRESENT -> {
                    locked.value = false
                    Health.screenOn()
                }
                BluetoothDevice.ACTION_ACL_CONNECTED, BluetoothDevice.ACTION_ACL_DISCONNECTED -> if (s.bluetoothEnabled) {
                    val d = device(i) ?: return
                    val info = BluetoothInfo(deviceName(d), deviceKind(d), i.action == BluetoothDevice.ACTION_ACL_CONNECTED, null)
                    lastBt = info
                    show(Popup.Bluetooth(info))
                }
                ACTION_BT_BATTERY -> if (s.bluetoothEnabled) {
                    val level = i.getIntExtra(EXTRA_BT_BATTERY, -1)
                    val d = device(i) ?: return
                    val prev = lastBt
                    // Mostra a bateria só logo depois de conectar (para não repetir o aviso).
                    if (level >= 0 && prev != null && prev.connected && prev.battery == null && prev.name == deviceName(d)) {
                        val info = prev.copy(battery = level)
                        lastBt = info
                        if (ui.popup.value is Popup.Bluetooth) show(Popup.Bluetooth(info), haptic = false)
                    }
                }
            }
        }
    }

    override fun onServiceConnected() {
        Prefs.init(this)
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        IslandBus.serviceRunning.value = true
        locked.value = (getSystemService(KEYGUARD_SERVICE) as KeyguardManager).isKeyguardLocked
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(ACTION_BT_BATTERY)
        }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, RECEIVER_EXPORTED) else registerReceiver(receiver, filter)
        addOverlay()
        Health.init(this)
        watchPrivacy()
        if (!locked.value) Health.screenOn()
        scope.launch { IslandBus.popups.collect { show(it) } }
        scope.launch { Prefs.state.collect { reposition(); applyVisibility(); Health.startSteps(this@IslandService) } }
        scope.launch { healthReminders() }
        scope.launch { combine(foregroundPkg, locked) { _, _ -> }.collect { applyVisibility() } }
        // Chamada recebida sempre abre a ilha; quando acaba, recolhe.
        scope.launch {
            IslandBus.call.collect { c ->
                if (c?.incoming == true) {
                    ui.popup.value = null
                    vibrate()
                }
                if (c == null && ui.expanded.value && IslandBus.media.value == null && IslandBus.notifications.value.isEmpty()) ui.expanded.value = false
                scheduleSleep()
            }
        }
        scope.launch { IslandBus.media.collect { scheduleSleep() } }
        // Timer ou navegação acabou com a ilha aberta nele: recolhe.
        scope.launch {
            IslandBus.live.collect { l ->
                if (l == null && ui.expanded.value && ui.expandKind.value == ExpandKind.Live) ui.expanded.value = false
                if (l?.kind == LiveKind.Alarm) ui.popup.value = null
                scheduleSleep()
            }
        }
        scope.launch { ui.expanded.collect { scheduleSleep() } }
        scope.launch { ui.asleep.collect { applyVisibility() } }
        // Barrinhas que reagem ao som: o Visualizer só fica ligado enquanto a música toca.
        scope.launch {
            combine(IslandBus.media, Prefs.state) { m, st -> m?.playing == true && st.reactiveBars }.collect { on ->
                if (on) AudioLevels.start(this@IslandService) else AudioLevels.stop()
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val type = event?.eventType ?: return
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && type != AccessibilityEvent.TYPE_WINDOWS_CHANGED) return
        // Com vídeo em janelinha (PiP), o app do vídeo continua mandando eventos mesmo estando em
        // segundo plano. Por isso o app "aberto" vem da lista de janelas, ignorando a janela PiP.
        val fromWindows = mainAppFromWindows()
        if (fromWindows != null) {
            foregroundPkg.value = fromWindows
            return
        }
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (Build.VERSION.SDK_INT >= 26 && windows.any { it.isInPictureInPictureMode && it.root?.packageName?.toString() == event.packageName?.toString() }) return
        val pkg = event.packageName?.toString() ?: return
        if (ignoredPkg(pkg)) return
        foregroundPkg.value = pkg
    }

    // Painel de notificações e teclado não contam como "app aberto".
    private fun ignoredPkg(pkg: String) =
        pkg == "com.android.systemui" || pkg == packageName || pkg.contains("inputmethod") || pkg.contains("keyboard")

    /** App em tela cheia de verdade: a janela de app ativa que não está em PiP. */
    private fun mainAppFromWindows(): String? = runCatching {
        val apps = windows.filter { w ->
            w.type == AccessibilityWindowInfo.TYPE_APPLICATION && !(Build.VERSION.SDK_INT >= 26 && w.isInPictureInPictureMode)
        }
        val main = apps.firstOrNull { it.isActive || it.isFocused } ?: apps.firstOrNull()
        val pkg = main?.root?.packageName?.toString()
        pkg?.takeUnless { ignoredPkg(it) }
    }.getOrNull()

    override fun onInterrupt() {}

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        reposition()
        applyVisibility()
    }

    override fun onDestroy() {
        IslandBus.serviceRunning.value = false
        runCatching { unregisterReceiver(receiver) }
        AudioLevels.stop()
        Health.stopSteps()
        unwatchPrivacy()
        removeOverlay()
        scope.cancel()
        super.onDestroy()
    }

    // ---------- janela ----------

    @SuppressLint("ClickableViewAccessibility")
    private fun addOverlay() {
        if (view != null) return
        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            BASE_FLAGS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            if (Build.VERSION.SDK_INT >= 30) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else if (Build.VERSION.SDK_INT >= 28) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        val o = OverlayOwner()
        val v = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                IslandUi(
                    state = ui,
                    actions = IslandActions(
                        onTap = { gesture(Prefs.state.value.tapGesture, tap = true) },
                        onHold = { gesture(Prefs.state.value.holdGesture, tap = false) },
                        onSwipe = ::swipe,
                        onAction = ::runAction,
                        onDismiss = { key -> IslandBus.dismiss?.invoke(key) },
                        onSize = ::setSize,
                        onOpen = ::openPkg,
                        onCollapse = ::collapse,
                    ),
                )
            }
            setOnTouchListener { _, e ->
                if (e.actionMasked == MotionEvent.ACTION_OUTSIDE) collapse()
                false
            }
        }
        o.attach(v)
        params = p
        owner = o
        view = v
        reposition()
        runCatching { wm.addView(v, p) }.onFailure { view = null; owner = null; o.destroy() }
        scheduleSleep()
    }

    /**
     * Largura da janela em px. Com WRAP_CONTENT o Android limita a janela à largura de um
     * diálogo (~320 dp), o que impedia a ilha aberta de ocupar a tela toda. A interface só
     * pede para encolher depois que a animação termina.
     */
    private fun setSize(wPx: Int, hPx: Int) {
        val p = params ?: return
        if (p.width != wPx || p.height != hPx) {
            p.width = wPx
            p.height = hPx
            view?.let { if (it.isAttachedToWindow) runCatching { wm.updateViewLayout(it, p) } }
        }
    }

    private fun removeOverlay() {
        view?.let { runCatching { wm.removeViewImmediate(it) } }
        owner?.destroy()
        view = null
        owner = null
    }

    /** Mostra ou esconde sem recriar a janela (apps ocultos, tela de bloqueio, paisagem). */
    private fun applyVisibility() {
        val v = view ?: return
        val p = params ?: return
        val s = Prefs.state.value
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val hasCall = IslandBus.call.value != null
        val visible = s.enabled &&
            !(landscape && !s.showInLandscape) &&
            !(locked.value && s.hideOnLockscreen && !hasCall) &&
            (foregroundPkg.value !in s.hiddenApps || hasCall)
        val sleepingGone = ui.asleep.value && !s.keepMiniWhenHidden
        v.visibility = if (visible) View.VISIBLE else View.INVISIBLE
        val flags = if (visible && !sleepingGone) BASE_FLAGS else BASE_FLAGS or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        if (p.flags != flags) {
            p.flags = flags
            if (v.isAttachedToWindow) runCatching { wm.updateViewLayout(v, p) }
        }
    }

    /** Centraliza a ilha na câmera frontal, somando os ajustes do usuário. */
    private fun reposition() {
        val p = params ?: return
        val s = Prefs.state.value
        val d = resources.displayMetrics.density
        val screenW = if (Build.VERSION.SDK_INT >= 30) wm.currentWindowMetrics.bounds.width() else resources.displayMetrics.widthPixels
        ui.screenWidthDp.value = (screenW / d).toInt()
        val cam = if (s.alignToCamera) cameraRect() else null
        val baseX = cam?.let { it.centerX() - screenW / 2 } ?: 0
        val pillH = (s.height * d).toInt()
        val baseY = cam?.let { (it.centerY() - pillH / 2).coerceAtLeast(0) } ?: (8 * d).toInt()
        p.x = baseX + (s.offsetX * d).toInt()
        // A ilha tem 2 dp de margem em volta (para a borda).
        p.y = (baseY + (s.offsetY * d).toInt() - (2 * d).toInt()).coerceAtLeast(0)
        view?.let { if (it.isAttachedToWindow) runCatching { wm.updateViewLayout(it, p) } }
    }

    private fun cameraRect(): Rect? {
        val cutout = when {
            Build.VERSION.SDK_INT >= 30 -> wm.currentWindowMetrics.windowInsets.displayCutout
            Build.VERSION.SDK_INT >= 29 ->
                (getSystemService(DISPLAY_SERVICE) as DisplayManager).getDisplay(Display.DEFAULT_DISPLAY)?.cutout
            else -> null
        } ?: return null
        val top = if (Build.VERSION.SDK_INT >= 29) cutout.boundingRectTop else cutout.boundingRects.firstOrNull()
        return top?.takeIf { !it.isEmpty }
    }

    // ---------- comportamento ----------

    fun show(p: Popup, haptic: Boolean = true) {
        if (view == null) return
        if (IslandBus.call.value?.incoming == true && p !is Popup.Test) return
        if (p is Popup.Notification && locked.value && !Prefs.state.value.notifOnLockscreen) return
        if (ui.expanded.value && p !is Popup.Test) return
        ui.asleep.value = false
        ui.popup.value = p
        if (haptic) vibrate()
        popupJob?.cancel()
        val s = Prefs.state.value
        if (p is Popup.Notification && s.keepUntilRead) return
        // Com botões, o aviso fica um pouco mais para dar tempo de tocar.
        val extra = if (p is Popup.Notification && s.showNotifActions && p.item.actions.isNotEmpty()) 3000L else 0L
        popupJob = scope.launch {
            delay(s.popupSeconds * 1000L + extra)
            ui.popup.value = null
            scheduleSleep()
        }
    }

    // ---------- bateria fraca / cheia ----------

    private var lowShown = false
    private var fullShown = false

    private fun batteryAlert(i: Intent) {
        val s = Prefs.state.value
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) * 100 / i.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
        if (level < 0) return
        val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        if (charging || level > s.lowBatteryLevel + 5) lowShown = false
        if (!charging) fullShown = false
        if (!s.batteryAlerts) return
        if (!charging && level <= s.lowBatteryLevel && !lowShown) {
            lowShown = true
            show(Popup.Reminder(ReminderKind.BatteryLow))
        } else if (charging && level >= 100 && !fullShown) {
            fullShown = true
            show(Popup.Reminder(ReminderKind.BatteryFull))
        }
    }

    // ---------- câmera e microfone em uso ----------

    private val busyCams = HashSet<String>()
    private val camCallback = object : CameraManager.AvailabilityCallback() {
        override fun onCameraUnavailable(id: String) {
            busyCams.add(id)
            IslandBus.cameraInUse.value = true
        }

        override fun onCameraAvailable(id: String) {
            busyCams.remove(id)
            IslandBus.cameraInUse.value = busyCams.isNotEmpty()
        }
    }
    private val micCallback = object : AudioManager.AudioRecordingCallback() {
        override fun onRecordingConfigChanged(configs: MutableList<AudioRecordingConfiguration>?) {
            IslandBus.micInUse.value = !configs.isNullOrEmpty()
        }
    }

    private fun watchPrivacy() {
        val main = Handler(Looper.getMainLooper())
        runCatching { (getSystemService(CAMERA_SERVICE) as CameraManager).registerAvailabilityCallback(camCallback, main) }
        runCatching {
            val am = getSystemService(AUDIO_SERVICE) as AudioManager
            am.registerAudioRecordingCallback(micCallback, main)
            IslandBus.micInUse.value = am.activeRecordingConfigurations.isNotEmpty()
        }
    }

    private fun unwatchPrivacy() {
        runCatching { (getSystemService(CAMERA_SERVICE) as CameraManager).unregisterAvailabilityCallback(camCallback) }
        runCatching { (getSystemService(AUDIO_SERVICE) as AudioManager).unregisterAudioRecordingCallback(micCallback) }
        IslandBus.cameraInUse.value = false
        IslandBus.micInUse.value = false
    }

    /** Lembretes de saúde: pausa para os olhos, beber água e hora de dormir. */
    private suspend fun healthReminders() {
        var lastBreak = 0L
        var lastWater = System.currentTimeMillis()
        var bedDay = -1
        while (true) {
            delay(30_000)
            val s = Prefs.state.value
            val now = System.currentTimeMillis()
            val busy = IslandBus.call.value != null || ui.expanded.value || ui.popup.value != null
            if (busy || locked.value) continue
            val using = Health.minutesUsing(now)
            if (s.breakReminder && using >= s.breakMinutes && now - lastBreak > s.breakMinutes * 60_000L) {
                lastBreak = now
                show(Popup.Reminder(ReminderKind.Break))
            } else if (s.waterReminder && Health.water.value < s.waterGoal && now - lastWater > 90 * 60_000L) {
                lastWater = now
                show(Popup.Reminder(ReminderKind.Water))
            } else if (s.idleStyle == IdleStyle.Bedtime && Health.minutesToBed(s.bedtime) == 30) {
                val day = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_YEAR)
                if (day != bedDay) {
                    bedDay = day
                    show(Popup.Reminder(ReminderKind.Bedtime))
                }
            }
        }
    }

    private fun vibrate() {
        if (!Prefs.state.value.vibrate) return
        val c = if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY
        view?.performHapticFeedback(c)
    }

    /** Some com a ilha depois de um tempo sem nenhuma atividade (se ligado). */
    private fun scheduleSleep() {
        sleepJob?.cancel()
        ui.asleep.value = false
        val secs = Prefs.state.value.autoHideSeconds
        if (secs <= 0) return
        sleepJob = scope.launch {
            delay(secs * 1000L)
            val busy = ui.popup.value != null || ui.expanded.value || IslandBus.call.value != null || IslandBus.media.value != null || IslandBus.live.value != null
            if (!busy) ui.asleep.value = true
        }
    }

    private fun canExpand() = IslandBus.call.value != null || IslandBus.media.value != null || IslandBus.live.value != null ||
        IslandBus.notifications.value.isNotEmpty() || isCharging() || Prefs.state.value.idleStyle != IdleStyle.Simple

    /** Abre o layout que corresponde ao que a ilha está mostrando agora. */
    private fun kindForNow(): ExpandKind {
        if (IslandBus.call.value != null) return ExpandKind.Activity
        if (IslandBus.live.value != null) return ExpandKind.Live
        if (IslandBus.media.value != null) return ExpandKind.Activity
        if (ui.popup.value is Popup.Charging) return ExpandKind.Battery
        return when (Prefs.state.value.idleStyle) {
            IdleStyle.Battery -> ExpandKind.Battery
            IdleStyle.Clock, IdleStyle.Date, IdleStyle.ClockBattery, IdleStyle.Alarm, IdleStyle.Greeting -> ExpandKind.Clock
            IdleStyle.NetSpeed, IdleStyle.Memory, IdleStyle.Storage -> ExpandKind.System
            IdleStyle.AppIcons, IdleStyle.Count -> ExpandKind.Notifications
            IdleStyle.Health, IdleStyle.Steps, IdleStyle.Water, IdleStyle.ScreenBreak, IdleStyle.Bedtime -> ExpandKind.Health
            IdleStyle.Breathing -> ExpandKind.Breathe
            IdleStyle.Simple, IdleStyle.Pulse -> if (IslandBus.notifications.value.isEmpty()) ExpandKind.Battery else ExpandKind.Notifications
        }
    }

    private fun gesture(g: Gesture, tap: Boolean) {
        if (ui.asleep.value) {
            scheduleSleep()
            return
        }
        val popup = ui.popup.value
        if (tap && popup is Popup.Notification) {
            runCatching { popup.item.contentIntent?.send() }
            ui.popup.value = null
            return
        }
        // Tocar no lembrete já registra: pausa feita ou um copo de água.
        if (tap && popup is Popup.Reminder) {
            when (popup.kind) {
                ReminderKind.Break -> Health.breakTaken()
                ReminderKind.Water -> Health.addWater(1)
                else -> {}
            }
            ui.popup.value = null
            scheduleSleep()
            return
        }
        when (g) {
            Gesture.Expand -> when {
                ui.expanded.value -> collapse()
                canExpand() -> {
                    ui.expandKind.value = kindForNow()
                    popupJob?.cancel()
                    ui.popup.value = null
                    ui.expanded.value = true
                    if (!tap) vibrate()
                }
            }
            Gesture.OpenApp -> {
                val live = IslandBus.live.value
                if (IslandBus.call.value == null && live?.contentIntent != null && popup == null) {
                    runCatching { live.contentIntent.send() }
                    collapse()
                    return
                }
                val pkg = IslandBus.call.value?.pkg
                    ?: (popup as? Popup.Notification)?.item?.pkg
                    ?: IslandBus.media.value?.pkg
                    ?: IslandBus.notifications.value.firstOrNull()?.pkg
                openPkg(pkg)
            }
            Gesture.Nothing -> {}
        }
    }

    /** [dir] > 0 deslizou para a direita. */
    private fun swipe(dir: Float) {
        val popup = ui.popup.value
        val media = IslandBus.media.value
        if (popup == null && !ui.expanded.value && media != null && Prefs.state.value.swipeChangesTrack) {
            if (dir > 0) media.controller.transportControls.skipToPrevious() else media.controller.transportControls.skipToNext()
            vibrate()
            return
        }
        if (popup is Popup.Notification) {
            if (Prefs.state.value.swipeToDismiss && popup.item.clearable) IslandBus.dismiss?.invoke(popup.item.key)
            ui.popup.value = null
        } else if (popup != null) {
            ui.popup.value = null
        } else if (ui.expanded.value) {
            collapse()
        }
    }

    private fun runAction(item: NotifItem, action: NotifAction) {
        popupJob?.cancel()
        ui.popup.value = null
        if (action.remoteInput != null) {
            IslandBus.pendingReply = PendingReply(item.title, action)
            val i = Intent(this, ReplyActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            runCatching { startActivity(i) }
        } else {
            runCatching { action.intent.send() }
        }
        collapse()
    }

    private fun openPkg(pkg: String?) {
        val intent = pkg?.let { packageManager.getLaunchIntentForPackage(it) } ?: Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { startActivity(intent) }
        collapse()
    }

    private fun collapse() {
        if (ui.expanded.value) ui.expanded.value = false
    }

    private fun isCharging(): Boolean {
        val i = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return false
        val st = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        return st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL
    }

    private fun batteryLevel(): Int =
        (getSystemService(BATTERY_SERVICE) as BatteryManager).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

    @Suppress("DEPRECATION")
    private fun device(i: Intent): BluetoothDevice? =
        if (Build.VERSION.SDK_INT >= 33) i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        else i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)

    @SuppressLint("MissingPermission")
    private fun deviceName(d: BluetoothDevice): String = runCatching { (if (Build.VERSION.SDK_INT >= 30) d.alias else null) ?: d.name }.getOrNull() ?: "Dispositivo Bluetooth"

    @SuppressLint("MissingPermission")
    private fun deviceKind(d: BluetoothDevice): BtKind {
        val c = runCatching { d.bluetoothClass }.getOrNull() ?: return BtKind.Other
        return when {
            c.majorDeviceClass == BluetoothClass.Device.Major.WEARABLE -> BtKind.Watch
            c.deviceClass == BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES -> BtKind.Headphones
            c.deviceClass == BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET ||
                c.deviceClass == BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE -> BtKind.Earbuds
            c.deviceClass == BluetoothClass.Device.AUDIO_VIDEO_LOUDSPEAKER ||
                c.deviceClass == BluetoothClass.Device.AUDIO_VIDEO_PORTABLE_AUDIO ||
                c.deviceClass == BluetoothClass.Device.AUDIO_VIDEO_HIFI_AUDIO -> BtKind.Speaker
            c.majorDeviceClass == BluetoothClass.Device.Major.AUDIO_VIDEO -> BtKind.Earbuds
            else -> BtKind.Other
        }
    }

    companion object {
        private const val BASE_FLAGS = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH

        // Bateria de fones: broadcast do sistema que existe na maioria dos aparelhos, mas não é API pública.
        private const val ACTION_BT_BATTERY = "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED"
        private const val EXTRA_BT_BATTERY = "android.bluetooth.device.extra.BATTERY_LEVEL"
    }
}

class IslandActions(
    val onTap: () -> Unit,
    val onHold: () -> Unit,
    val onSwipe: (Float) -> Unit,
    val onAction: (NotifItem, NotifAction) -> Unit,
    val onDismiss: (String) -> Unit,
    val onSize: (Int, Int) -> Unit,
    val onOpen: (String?) -> Unit,
    val onCollapse: () -> Unit,
)
