package app.tarjuman.overlay

import android.animation.ValueAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.Icon
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowManager
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import app.tarjuman.MainActivity
import app.tarjuman.R
import app.tarjuman.core.Languages
import app.tarjuman.core.Prefs
import app.tarjuman.core.TextUtil
import app.tarjuman.data.HistoryStore
import app.tarjuman.engine.Engine
import app.tarjuman.engine.ModelMissing
import app.tarjuman.ocr.OcrEngine
import app.tarjuman.ocr.OcrResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Foreground service that owns the MediaProjection, the floating bubble and the result windows.
 * One screen-capture consent per session; frames are kept in memory only and never saved.
 */
class TarjumanService : Service() {

    companion object {
        @Volatile var running = false
        const val ACTION_STOP = "app.tarjuman.action.STOP"
        const val ACTION_TRANSLATE = "app.tarjuman.action.TRANSLATE"
        const val ACTION_TOGGLE = "app.tarjuman.action.TOGGLE"
        private const val CH = "tarjuman_service"
        private const val NID = 7
        private const val TYPE = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        private val SURFACE = 0xF5142140.toInt()
        private val BURGUNDY = 0xFF9B1B30.toInt()
        private val MUTED = 0xFF9AA6C4.toInt()

        fun start(ctx: Context, code: Int, data: Intent) {
            ctx.startForegroundService(
                Intent(ctx, TarjumanService::class.java).putExtra("code", code).putExtra("data", data)
            )
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, TarjumanService::class.java))
        }
    }

    private class Cache(
        val items: List<Pair<Rect, String>>,
        val original: String,
        val translated: String,
        val src: String?,
        val engine: String
    )

    private lateinit var wm: WindowManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null

    private var projection: MediaProjection? = null
    private var projCallback: MediaProjection.Callback? = null
    private var reader: ImageReader? = null
    private var vdisplay: VirtualDisplay? = null
    private var sw = 0
    private var sh = 0
    private var sdpi = 0

    private var bubble: BubbleView? = null
    private var bubbleLp: WindowManager.LayoutParams? = null
    private var bubbleHidden = false
    private var menu: View? = null
    private var selection: View? = null
    private var panel: View? = null
    private var overlayText: TranslationOverlayView? = null
    private var ctrl: View? = null

    private var ocrCache: OcrResult? = null
    private var offX = 0
    private var offY = 0
    private var cache: Cache? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(CH, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { stopSelf(); return START_NOT_STICKY }
            ACTION_TRANSLATE -> { if (projection != null) translateScreen(null); return START_NOT_STICKY }
            ACTION_TOGGLE -> { toggleBubbleHidden(); return START_NOT_STICKY }
        }
        if (projection != null) return START_NOT_STICKY

        val n = notification()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NID, n)
        }
        val code = intent?.getIntExtra("code", 0) ?: 0
        val data: Intent? = if (Build.VERSION.SDK_INT >= 33) {
            intent?.getParcelableExtra("data", Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra<Intent>("data")
        }
        if (data == null) { stopSelf(); return START_NOT_STICKY }
        try {
            setupProjection(code, data)
        } catch (e: Exception) {
            toast(getString(R.string.error_prefix, e.message ?: e.toString()))
            stopSelf()
            return START_NOT_STICKY
        }
        running = true
        showBubble()
        return START_NOT_STICKY
    }

    // ---------- notification ----------

    private fun pi(action: String, req: Int) = PendingIntent.getService(
        this, req, Intent(this, TarjumanService::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun action(label: Int, action: String, req: Int) = Notification.Action.Builder(
        Icon.createWithResource(this, R.drawable.ic_notif), getString(label), pi(action, req)
    ).build()

    private fun notification(): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CH)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notif_text))
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(action(R.string.notif_translate, ACTION_TRANSLATE, 1))
            .addAction(action(R.string.notif_toggle, ACTION_TOGGLE, 2))
            .addAction(action(R.string.notif_stop, ACTION_STOP, 3))
            .build()
    }

    // ---------- screen capture ----------

    private fun readMetrics() {
        val dm = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(dm)
        sw = dm.widthPixels; sh = dm.heightPixels; sdpi = dm.densityDpi
    }

    private fun setupProjection(code: Int, data: Intent) {
        readMetrics()
        val mpm = getSystemService(MediaProjectionManager::class.java)
        val p = mpm.getMediaProjection(code, data)
        val cb = object : MediaProjection.Callback() {
            override fun onStop() { scope.launch { stopSelf() } }
        }
        p.registerCallback(cb, Handler(Looper.getMainLooper())) // required on Android 14+
        projection = p
        projCallback = cb
        val r = ImageReader.newInstance(sw, sh, PixelFormat.RGBA_8888, 2)
        reader = r
        vdisplay = p.createVirtualDisplay(
            "tarjuman", sw, sh, sdpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, r.surface, null, null
        )
    }

    private fun rebuildDisplay() {
        val old = reader
        readMetrics()
        val nr = ImageReader.newInstance(sw, sh, PixelFormat.RGBA_8888, 2)
        vdisplay?.resize(sw, sh, sdpi)
        vdisplay?.surface = nr.surface
        reader = nr
        old?.close()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (projection == null) return
        clearResult()
        closeMenu()
        rebuildDisplay()
        bubbleLp?.let { lp ->
            lp.x = lp.x.coerceIn(0, (sw - lp.width).coerceAtLeast(0))
            lp.y = lp.y.coerceIn(0, (sh - lp.height).coerceAtLeast(0))
            bubble?.let { try { wm.updateViewLayout(it, lp) } catch (e: Exception) { } }
        }
    }

    private suspend fun grabFrame(): Bitmap? {
        for (n in 0 until 12) {
            val img = try { reader?.acquireLatestImage() } catch (e: Exception) { null }
            if (img != null) {
                try {
                    val p = img.planes[0]
                    val rowPad = p.rowStride - p.pixelStride * img.width
                    val wide = Bitmap.createBitmap(img.width + rowPad / p.pixelStride, img.height, Bitmap.Config.ARGB_8888)
                    wide.copyPixelsFromBuffer(p.buffer)
                    if (rowPad == 0) return wide
                    val out = Bitmap.createBitmap(wide, 0, 0, img.width, img.height)
                    wide.recycle()
                    return out
                } finally {
                    img.close()
                }
            }
            delay(120)
        }
        return null
    }

    /** A virtual display only emits frames when pixels change; this forces one. */
    private suspend fun nudge() {
        val v = View(this).apply { setBackgroundColor(0x01FFFFFF) }
        val lp = WindowManager.LayoutParams(
            2, 2, TYPE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.LEFT }
        try {
            wm.addView(v, lp)
            delay(120)
        } finally {
            safeRemove(v)
        }
    }

    // ---------- translate flow ----------

    private fun translateScreen(region: Rect?) {
        job?.cancel()
        job = scope.launch {
            closeMenu()
            closeResult()
            setUiVisible(false)
            nudge()
            delay(150)
            val frame = grabFrame()
            setUiVisible(true)
            if (frame == null) { toast(getString(R.string.no_frame)); return@launch }
            var dx = 0
            var dy = 0
            var bmp = frame
            if (region != null) {
                val r = Rect(region)
                if (r.intersect(0, 0, frame.width, frame.height) && r.width() > 8 && r.height() > 8) {
                    bmp = Bitmap.createBitmap(frame, r.left, r.top, r.width(), r.height())
                    dx = r.left; dy = r.top
                }
            }
            showMessage(getString(R.string.translating))
            try {
                val ocr = OcrEngine.recognize(bmp)
                if (ocr.blocks.isEmpty()) { showMessage(getString(R.string.no_text)); return@launch }
                ocrCache = ocr
                offX = dx; offY = dy
                translateAndShow()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showMessage(getString(R.string.error_prefix, e.message ?: e.toString()))
            }
        }
    }

    private suspend fun translateAndShow() {
        val ocr = ocrCache ?: return
        showMessage(getString(R.string.translating))
        val tgt = Prefs.targetLang
        val full = ocr.fullText
        val src = Engine.detect(full) ?: TextUtil.guessLang(full)
        val items = ArrayList<Pair<Rect, String>>()
        val orig = StringBuilder()
        val tr = StringBuilder()
        var engine = ""
        try {
            for (b in ocr.blocks) {
                val r = Engine.translate(b.text, src, tgt)
                engine = r.engine
                items.add(Rect(b.box).apply { offset(offX, offY) } to r.text)
                if (orig.isNotEmpty()) { orig.append("\n\n"); tr.append("\n\n") }
                orig.append(b.text)
                tr.append(r.text)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: ModelMissing) {
            showMessage(getString(R.string.model_missing, e.langs.joinToString { Languages.label(it) }))
            return
        } catch (e: Exception) {
            showMessage(getString(R.string.error_prefix, e.message ?: e.toString()))
            return
        }
        cache = Cache(items, orig.toString(), tr.toString(), src, engine)
        if (Prefs.historyScreen) HistoryStore.add(orig.toString(), tr.toString(), src, tgt, engine)
        showResult()
    }

    private fun retranslate() {
        job?.cancel()
        job = scope.launch { translateAndShow() }
    }

    private fun cycleLang() {
        val q = Languages.quick
        Prefs.targetLang = q[(q.indexOf(Prefs.targetLang) + 1) % q.size]
        retranslate()
    }

    // ---------- bubble ----------

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun round(color: Int, r: Int) = GradientDrawable().apply { setColor(color); cornerRadius = r.toFloat() }

    private fun button(label: String, onClick: () -> Unit) = TextView(this).apply {
        text = label
        setTextColor(Color.WHITE)
        textSize = 14f
        gravity = Gravity.CENTER
        typeface = Typeface.DEFAULT_BOLD
        setPadding(dp(14), dp(9), dp(14), dp(9))
        background = round(BURGUNDY, dp(14))
        setOnClickListener { onClick() }
    }

    private fun showBubble() {
        if (bubble != null) return
        val size = dp(Prefs.bubbleSizeDp)
        val v = BubbleView(this, size).apply { alpha = Prefs.bubbleAlpha / 100f }
        val lp = WindowManager.LayoutParams(
            size, size, TYPE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            x = Prefs.bubbleX.coerceIn(0, (sw - size).coerceAtLeast(0))
            y = Prefs.bubbleY.coerceIn(0, (sh - size).coerceAtLeast(0))
        }
        attachDrag(v, lp)
        wm.addView(v, lp)
        bubble = v
        bubbleLp = lp
    }

    private fun attachDrag(v: View, lp: WindowManager.LayoutParams) {
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var moved = false
        v.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY; startX = lp.x; startY = lp.y; moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (abs(dx) > dp(6) || abs(dy) > dp(6)) moved = true
                    if (moved) {
                        lp.x = (startX + dx).toInt().coerceIn(0, (sw - lp.width).coerceAtLeast(0))
                        lp.y = (startY + dy).toInt().coerceIn(0, (sh - lp.height).coerceAtLeast(0))
                        wm.updateViewLayout(v, lp)
                        closeMenu()
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (moved) snapToEdge(lp) else toggleMenu()
                    true
                }
                else -> false
            }
        }
    }

    private fun snapToEdge(lp: WindowManager.LayoutParams) {
        val b = bubble ?: return
        val target = if (lp.x + lp.width / 2 < sw / 2) 0 else (sw - lp.width).coerceAtLeast(0)
        ValueAnimator.ofInt(lp.x, target).apply {
            duration = 160
            addUpdateListener {
                lp.x = it.animatedValue as Int
                try { wm.updateViewLayout(b, lp) } catch (e: Exception) { }
            }
        }.start()
        Prefs.bubbleX = target
        Prefs.bubbleY = lp.y
    }

    private fun toggleBubbleHidden() {
        bubbleHidden = !bubbleHidden
        bubble?.visibility = if (bubbleHidden) View.GONE else View.VISIBLE
        if (bubbleHidden) { closeMenu(); toast(getString(R.string.bubble_hidden_hint)) }
    }

    private fun toggleMenu() { if (menu != null) closeMenu() else showMenu() }

    private fun showMenu() {
        val b = bubbleLp ?: return
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = round(SURFACE, dp(18))
            setPadding(dp(6), dp(6), dp(6), dp(6))
        }
        fun item(res: Int, act: () -> Unit) {
            col.addView(
                button(getString(res)) { closeMenu(); act() },
                LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { setMargins(0, dp(3), 0, dp(3)) }
            )
        }
        item(R.string.menu_translate_screen) { translateScreen(null) }
        item(R.string.menu_select_region) { startSelection() }
        item(R.string.menu_type) { openTyping() }
        item(R.string.menu_hide) { toggleBubbleHidden() }
        item(R.string.menu_stop) { stopSelf() }
        col.measure(0, 0)
        val lp = WindowManager.LayoutParams(
            WRAP_CONTENT, WRAP_CONTENT, TYPE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            x = if (b.x + b.width / 2 < sw / 2) b.x + b.width + dp(6)
            else (b.x - col.measuredWidth - dp(6)).coerceAtLeast(0)
            y = b.y.coerceAtMost(sh - col.measuredHeight - dp(8)).coerceAtLeast(0)
        }
        col.setOnTouchListener { _, e ->
            if (e.action == MotionEvent.ACTION_OUTSIDE) { closeMenu(); true } else false
        }
        wm.addView(col, lp)
        menu = col
    }

    private fun closeMenu() { safeRemove(menu); menu = null }

    private fun openTyping() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra("route", "text")
        )
    }

    // ---------- region selection ----------

    private fun startSelection() {
        closeResult()
        val v = SelectionView(this, getString(R.string.select_hint)) { rect ->
            selection?.let { s -> s.post { safeRemove(s) } }
            selection = null
            if (rect != null) translateScreen(rect)
        }
        wm.addView(v, fullscreenLp(touchable = true))
        selection = v
    }

    private fun fullscreenLp(touchable: Boolean): WindowManager.LayoutParams {
        var f = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        if (!touchable) f = f or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        val lp = WindowManager.LayoutParams(MATCH_PARENT, MATCH_PARENT, TYPE, f, PixelFormat.TRANSLUCENT)
        if (Build.VERSION.SDK_INT >= 28) {
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        return lp
    }

    // ---------- result windows ----------

    private fun setUiVisible(v: Boolean) {
        val vis = if (v) View.VISIBLE else View.INVISIBLE
        bubble?.visibility = if (bubbleHidden) View.GONE else vis
        menu?.visibility = vis
        panel?.visibility = vis
        overlayText?.visibility = vis
        ctrl?.visibility = vis
    }

    private fun closeResult() {
        safeRemove(panel); panel = null
        safeRemove(overlayText); overlayText = null
        safeRemove(ctrl); ctrl = null
    }

    private fun clearResult() { closeResult(); cache = null }

    private fun safeRemove(v: View?) {
        if (v == null) return
        try { wm.removeView(v) } catch (e: Exception) { }
    }

    private fun bottomLp(width: Int, bottomMargin: Int) = WindowManager.LayoutParams(
        width, WRAP_CONTENT, TYPE,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
        y = bottomMargin
    }

    private fun showResult() {
        val c = cache ?: return
        closeResult()
        if (Prefs.displayMode == "overlay" && c.items.isNotEmpty()) {
            val ov = TranslationOverlayView(this).apply { items = c.items }
            wm.addView(ov, fullscreenLp(touchable = false))
            overlayText = ov
            val bar = controlsBar(c)
            val holder = LinearLayout(this).apply {
                background = round(SURFACE, dp(18))
                setPadding(dp(8), dp(8), dp(8), dp(8))
                addView(bar)
            }
            wm.addView(holder, bottomLp(sw - dp(20), dp(28)))
            ctrl = holder
        } else {
            showPanel(c)
        }
    }

    private fun text(s: String, size: Float, color: Int) = TextView(this).apply {
        text = s
        textSize = size
        setTextColor(color)
        textDirection = View.TEXT_DIRECTION_FIRST_STRONG
        textAlignment = View.TEXT_ALIGNMENT_TEXT_START
    }

    private fun showPanel(c: Cache) {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = round(SURFACE, dp(20))
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        val head = (c.src?.let { Languages.label(it) } ?: "?") + " → " + Languages.label(Prefs.targetLang) +
            " · " + c.engine + if (c.engine == "libretranslate") " · " + getString(R.string.online_note) else ""
        col.addView(text(head, 12f, MUTED))
        val inner = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        inner.addView(text(c.translated, 18f, Color.WHITE))
        inner.addView(text("— " + getString(R.string.original) + " —", 12f, MUTED).apply { setPadding(0, dp(10), 0, dp(4)) })
        inner.addView(text(c.original, 13f, MUTED))
        val scroll = MaxHeightScroll(this, (sh * 0.38).toInt()).apply { addView(inner) }
        col.addView(scroll, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { setMargins(0, dp(6), 0, dp(8)) })
        col.addView(controlsBar(c))
        wm.addView(col, bottomLp(sw - dp(20), dp(28)))
        panel = col
    }

    private fun showMessage(msg: String) {
        closeResult()
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = round(SURFACE, dp(20))
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        col.addView(text(msg, 15f, Color.WHITE))
        col.addView(
            button(getString(R.string.panel_close)) { clearResult() },
            LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { topMargin = dp(8) }
        )
        wm.addView(col, bottomLp(sw - dp(20), dp(28)))
        panel = col
    }

    private fun controlsBar(c: Cache): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun add(label: String, act: () -> Unit) {
            row.addView(
                button(label, act),
                LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { setMargins(dp(3), 0, dp(3), 0) }
            )
        }
        add(getString(R.string.panel_copy_tr)) { copy(c.translated) }
        add(getString(R.string.panel_copy_orig)) { copy(c.original) }
        add("→ " + Languages.label(Prefs.targetLang)) { cycleLang() }
        add(getString(if (Prefs.displayMode == "overlay") R.string.panel_as_panel else R.string.panel_as_overlay)) {
            Prefs.displayMode = if (Prefs.displayMode == "overlay") "panel" else "overlay"
            showResult()
        }
        add(getString(R.string.panel_retry)) { retranslate() }
        add(getString(R.string.panel_close)) { clearResult() }
        return HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(row)
        }
    }

    private fun copy(s: String) {
        getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("tarjuman", s))
        toast(getString(R.string.copied))
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    override fun onDestroy() {
        running = false
        job?.cancel()
        scope.cancel()
        closeMenu()
        safeRemove(selection)
        clearResult()
        safeRemove(bubble)
        bubble = null
        vdisplay?.release()
        reader?.close()
        projCallback?.let { projection?.unregisterCallback(it) }
        projection?.stop()
        projection = null
        super.onDestroy()
    }
}
