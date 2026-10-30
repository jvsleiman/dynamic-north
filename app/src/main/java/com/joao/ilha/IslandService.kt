package com.joao.ilha

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.os.BatteryManager
import android.os.Build
import android.view.Display
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** Serviço de acessibilidade que desenha a ilha por cima de todos os apps. */
class IslandService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var wm: WindowManager
    private var view: ComposeView? = null
    private var owner: OverlayOwner? = null
    private var params: WindowManager.LayoutParams? = null
    private var popupJob: Job? = null

    /** O que a ilha está mostrando agora (lido pela interface). */
    val ui = IslandUiState(
        popup = MutableStateFlow(null),
        expanded = MutableStateFlow(false),
        screenWidthDp = MutableStateFlow(360),
    )

    private val power = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            if (!Prefs.state.value.showCharging) return
            val charging = i.action == Intent.ACTION_POWER_CONNECTED
            show(Popup.Charging(ChargingInfo(batteryLevel(), charging)))
        }
    }

    override fun onServiceConnected() {
        Prefs.init(this)
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        IslandBus.serviceRunning.value = true
        registerReceiver(power, IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        })
        scope.launch { IslandBus.popups.collect { show(it) } }
        scope.launch { Prefs.state.collect { applyVisibility(); reposition() } }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyVisibility()
        reposition()
    }

    override fun onDestroy() {
        IslandBus.serviceRunning.value = false
        runCatching { unregisterReceiver(power) }
        removeOverlay()
        scope.cancel()
        super.onDestroy()
    }

    private fun shouldShow(): Boolean {
        val s = Prefs.state.value
        if (!s.enabled) return false
        val landscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        return !(landscape && s.hideInLandscape)
    }

    private fun applyVisibility() {
        if (shouldShow()) addOverlay() else removeOverlay()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun addOverlay() {
        if (view != null) return
        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
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
                    onTap = ::onTap,
                    onLongPress = ::onLongPress,
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
    }

    private fun removeOverlay() {
        view?.let { runCatching { wm.removeViewImmediate(it) } }
        owner?.destroy()
        view = null
        owner = null
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
        p.y = (baseY + (s.offsetY * d).toInt()).coerceAtLeast(0)
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

    fun show(p: Popup) {
        if (view == null) return
        if (ui.expanded.value && p !is Popup.Test) return
        ui.popup.value = p
        if (Prefs.state.value.vibrate) view?.performHapticFeedback(HapticFeedbackConstants.CONFIRM.takeIf { Build.VERSION.SDK_INT >= 30 } ?: HapticFeedbackConstants.VIRTUAL_KEY)
        popupJob?.cancel()
        popupJob = scope.launch {
            delay(Prefs.state.value.popupSeconds * 1000L)
            ui.popup.value = null
        }
    }

    private fun onTap() {
        val popup = ui.popup.value
        when {
            popup is Popup.Notification -> {
                runCatching { popup.item.contentIntent?.send() }
                ui.popup.value = null
            }
            ui.expanded.value -> collapse()
            IslandBus.media.value != null -> {
                popupJob?.cancel()
                ui.popup.value = null
                ui.expanded.value = true
            }
            IslandBus.notifications.value.isNotEmpty() -> {
                popupJob?.cancel()
                ui.popup.value = null
                ui.expanded.value = true
            }
        }
    }

    private fun onLongPress() {
        val media = IslandBus.media.value
        val pkg = media?.pkg ?: (ui.popup.value as? Popup.Notification)?.item?.pkg
        val intent = pkg?.let { packageManager.getLaunchIntentForPackage(it) }
            ?: Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { startActivity(intent) }
        collapse()
    }

    private fun collapse() {
        ui.expanded.value = false
    }

    private fun batteryLevel(): Int {
        val bm = getSystemService(BATTERY_SERVICE) as BatteryManager
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }
}

class IslandUiState(
    val popup: MutableStateFlow<Popup?>,
    val expanded: MutableStateFlow<Boolean>,
    val screenWidthDp: MutableStateFlow<Int>,
)
