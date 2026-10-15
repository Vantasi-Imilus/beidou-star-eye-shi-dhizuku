package org.beidou.stareye.feature.tianquan

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlin.math.roundToInt
import kotlin.concurrent.thread
import org.beidou.star.eye.furry.vantasiimilus.god.api.feature.tianquan.R
import org.beidou.stareye.ui.theme.BeidouTheme
import org.beidou.stareye.ui.theme.DeepSpace
import org.beidou.stareye.ui.theme.StarWhite
import org.beidou.stareye.ui.theme.starAccent
import org.beidou.stareye.ui.theme.beidouShape

/**
 * 天权中枢 —— 系统级悬浮终端（浮动窗）。
 *
 * 设计要点：
 *  · 前台服务（specialUse）+ WindowManager `TYPE_APPLICATION_OVERLAY` 承载 Compose 视图，
 *    退出应用后浮动窗继续存在（前台服务保活），通知提供关闭入口；
 *  · 浮动会话与主界面会话**相互独立**（各自持有 [TianquanSession]）；
 *  · ComposeView 挂到 overlay 窗口时没有 Activity 环境，必须手工注入
 *    ViewTree LifecycleOwner / SavedStateRegistryOwner（服务自身实现这两个接口）；
 *  · 不设 FLAG_NOT_FOCUSABLE：窗口需要获得焦点以接收键盘输入并唤起软键盘；
 *  · 点按输入复用主界面的「隐形输入框」方案。
 */
object TianquanFloatingWindow {

    /** 悬浮窗权限是否已授予 */
    fun granted(context: Context): Boolean = Settings.canDrawOverlays(context)

    /** 引导去系统设置授予「显示在其他应用上层」权限 */
    fun requestPermission(context: Context) {
        runCatching {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}"),
            )
            if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    /**
     * 统一入口：已授权 → 启动浮动窗服务；未授权 → 引导去系统设置。
     *
     * @param newSession true 时强制新建浮动会话（否则复用已有存活会话）
     * @param command 会话就绪后执行的一行命令（可为 null）
     */
    fun request(context: Context, newSession: Boolean = false, command: String? = null): Boolean {
        if (!granted(context)) {
            requestPermission(context)
            if (context is Activity) {
                Toast.makeText(context, "请先授予「显示在其他应用上层」权限", Toast.LENGTH_SHORT).show()
            }
            return false
        }
        start(context, newSession, command)
        return true
    }

    /** 启动前台服务（内部使用；请先确保 [granted]） */
    fun start(context: Context, newSession: Boolean = false, command: String? = null) {
        val intent = Intent(context, TianquanFloatingService::class.java)
            .setAction(TianquanFloatingService.ACTION_START)
            .putExtra(TianquanFloatingService.EXTRA_NEW_SESSION, newSession)
        if (command != null) intent.putExtra(TianquanFloatingService.EXTRA_COMMAND, command)
        ContextCompat.startForegroundService(context, intent)
    }

    /** 停止浮动窗（通知「关闭」动作使用） */
    fun stop(context: Context) {
        context.stopService(Intent(context, TianquanFloatingService::class.java))
    }
}

/**
 * 浮动终端前台服务：持有悬浮窗视图与独立会话。
 */
class TianquanFloatingService : Service(), LifecycleOwner, SavedStateRegistryOwner {

    companion object {
        internal const val ACTION_START = "tianquan.float.START"
        internal const val ACTION_SHOW = "tianquan.float.SHOW"
        internal const val ACTION_STOP = "tianquan.float.STOP"
        internal const val EXTRA_NEW_SESSION = "new_session"
        internal const val EXTRA_COMMAND = "command"

        private const val CHANNEL_ID = "tianquan_floating"
        private const val NOTIFICATION_ID = 0x7A02
    }

    // ComposeView 所需的 ViewTree 生命周期环境（overlay 窗口无 Activity）
    private val lifecycleRegistry = LifecycleRegistry.createUnsafe(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    private lateinit var windowManager: WindowManager
    private var composeView: ComposeView? = null
    private var windowParams: WindowManager.LayoutParams? = null

    /** 浮动会话（与主界面会话相互独立）；用 Compose 状态包装以便重组合 */
    private var session by mutableStateOf<TianquanSession?>(null)
    private var revision by mutableIntStateOf(0)
    private var status by mutableStateOf("正在启动终端…")

    /** 会话尚未就绪时暂存的待执行命令（启动线程在会话就绪后执行） */
    @Volatile
    private var pendingCommand: String? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    private val density: Float get() = resources.displayMetrics.density

    // ───────────────────────── 生命周期 ─────────────────────────

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        savedStateController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        ensureChannel()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(),
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
        addOverlay()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopSelf()
            ACTION_SHOW -> addOverlay()
            ACTION_START -> {
                val command = intent.getStringExtra(EXTRA_COMMAND)
                val fresh = intent.getBooleanExtra(EXTRA_NEW_SESSION, false)
                if (fresh) destroySession()
                if (command != null && command.isNotBlank()) {
                    val alive = session?.takeIf { it.isAlive }
                    if (alive?.started == true) {
                        alive.write(command)
                        alive.write("\r")
                    } else {
                        // 会话不存在或尚未就绪：先暂存，由启动线程在会话就绪后执行
                        pendingCommand = command
                    }
                }
                ensureSession()
                addOverlay()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        removeOverlay()
        destroySession()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        super.onDestroy()
    }

    // ───────────────────────── 悬浮窗 ─────────────────────────

    private fun addOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        if (composeView != null) return

        windowParams = WindowManager.LayoutParams(
            (300 * density).toInt(),
            (430 * density).toInt(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // 不设 FLAG_NOT_FOCUSABLE：浮动窗需要焦点以接收输入与软键盘；
            // NOT_TOUCH_MODAL：窗外触摸仍交还下方应用
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (24 * density).toInt()
            y = (120 * density).toInt()
        }

        val view = ComposeView(this).apply {
            // overlay 窗口没有 Activity：手工注入 ViewTree 生命周期所有者
            setViewTreeLifecycleOwner(this@TianquanFloatingService)
            setViewTreeSavedStateRegistryOwner(this@TianquanFloatingService)
            setContent {
                BeidouTheme {
                    val s = session
                    FloatingTerminalContent(
                        session = s,
                        revision = revision,
                        status = status,
                        onClose = { stopSelf() },
                        onMoveBy = { dx, dy -> moveBy(dx, dy) },
                        onResizeBy = { dw, dh -> resizeBy(dw, dh) },
                    )
                }
            }
        }
        composeView = view
        windowManager.addView(view, windowParams)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    private fun removeOverlay() {
        val view = composeView ?: return
        runCatching { windowManager.removeView(view) }
        composeView = null
        windowParams = null
    }

    /** 拖动：更新窗口左上角坐标（限定在屏幕内） */
    private fun moveBy(dx: Int, dy: Int) {
        windowParams?.apply {
            val display = resources.displayMetrics
            x = (x + dx).coerceIn(0, display.widthPixels - width)
            y = (y + dy).coerceIn(0, display.heightPixels - height)
        }
        applyParams()
    }

    /** 右下角把手缩放：更新窗口宽高（限制最小尺寸与屏幕范围） */
    private fun resizeBy(dw: Int, dh: Int) {
        windowParams?.apply {
            val display = resources.displayMetrics
            width = (width + dw).coerceIn((160 * density).toInt(), display.widthPixels)
            height = (height + dh).coerceIn((200 * density).toInt(), display.heightPixels)
        }
        applyParams()
    }

    private fun applyParams() {
        val view = composeView ?: return
        val params = windowParams ?: return
        runCatching { windowManager.updateViewLayout(view, params) }
    }

    // ───────────────────────── 会话 ─────────────────────────

    /** 返回现存活的浮动会话；无则新建（启动在后台线程） */
    private fun ensureSession(): TianquanSession {
        session?.takeIf { it.isAlive }?.let { return it }
        destroySession()
        val s = TianquanSession(
            context = applicationContext,
            shellPath = TianquanPrefs.shellPath(this),
            rows = 24,
            cols = 80,
            palette = TerminalPalette(TianquanPrefs.theme(this)),
            channel = TianquanPrefs.channel(this),
            scrollbackRows = TianquanPrefs.scrollbackRows(this),
            trueColor = TianquanPrefs.trueColor(this),
            onUpdate = { mainHandler.post { revision++ } },
            onExit = { msg -> mainHandler.post { status = msg } },
            onTitle = { mainHandler.post { revision++ } },
            onClipboard = { text ->
                mainHandler.post {
                    val cm = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("beidou", text))
                }
            },
            onBell = {
                if (TianquanPrefs.bellVibrate(this)) {
                    runCatching {
                        val v = getSystemService(VIBRATOR_SERVICE) as android.os.Vibrator
                        v.vibrate(30)
                    }
                }
            },
            // 浮动窗不处理程序发起的尺寸请求（网格由窗口尺寸决定）
        )
        session = s
        thread(name = "tianquan-float-start", isDaemon = true) {
            val result = runCatching { s.start() }
            val cmd = pendingCommand
            pendingCommand = null
            mainHandler.post {
                status = result.fold(
                    onSuccess = { s.modeLabel },
                    onFailure = { "启动失败：${it.message ?: "未知错误"}" },
                )
                // 会话就绪后强制刷新一次，确保标题栏状态与终端首帧即时更新
                revision++
            }
            if (cmd != null && cmd.isNotBlank()) {
                s.write(cmd)
                s.write("\r")
            }
        }
        return s
    }

    private fun destroySession() {
        session?.destroy()
        session = null
        pendingCommand = null
    }

    // ───────────────────────── 通知 ─────────────────────────

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "浮动终端", NotificationManager.IMPORTANCE_LOW)
                .apply { description = "天权中枢 · 系统级悬浮终端运行中" },
        )
    }

    private fun buildNotification(): Notification {
        val showIntent = PendingIntent.getService(
            this, 1,
            Intent(this, TianquanFloatingService::class.java).setAction(ACTION_SHOW),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            this, 2,
            Intent(this, TianquanFloatingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setContentTitle("天权 · 浮动终端")
            .setContentText("悬浮终端运行中 · 点按通知返回窗口")
            .setSmallIcon(R.drawable.ic_tianquan_float)
            .setOngoing(true)
            .setContentIntent(showIntent)
            .addAction(Notification.Action.Builder(null, "关闭", stopIntent).build())
            .build()
    }
}

/** 浮动窗的输入框文本镜像（非 Compose 状态，供 diff 即时读取） */
private class FloatTextMirror {
    var value: String = ""
}

/**
 * 浮动终端内容（星空液态玻璃风格，与主界面一致）。
 *
 * 结构：标题栏（拖动把手 + 关闭）→ 终端区（含右下角缩放手柄）→ 隐形输入框。
 */
@Composable
private fun FloatingTerminalContent(
    session: TianquanSession?,
    revision: Int,
    status: String,
    onClose: () -> Unit,
    onMoveBy: (Int, Int) -> Unit,
    onResizeBy: (Int, Int) -> Unit,
) {
    val context = LocalContext.current
    val accent = starAccent(TianquanFeature.star.order)
    val fontSize = TianquanPrefs.fontSize(context)
    val inputFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val mirror = remember { FloatTextMirror() }
    var input by remember { mutableStateOf("") }

    fun send(text: String) {
        session?.write(text)
    }

    fun sendByte(value: Int) {
        session?.write(byteArrayOf(value.toByte()))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(beidouShape(18.dp))
            .background(DeepSpace)
            .border(1.dp, Color.White.copy(alpha = 0.12f), beidouShape(18.dp)),
    ) {
        Column(Modifier.fillMaxSize()) {
            // ── 标题栏（拖动把手）──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(session) {
                        detectDragGestures { change, drag ->
                            change.consume()
                            onMoveBy(drag.x.roundToInt(), drag.y.roundToInt())
                        }
                    }
                    .padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    session?.emulator?.title?.takeIf { it.isNotBlank() } ?: "天权 · 浮动终端",
                    color = StarWhite,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    if (session?.isAlive == true) "" else status,
                    color = StarWhite.copy(alpha = 0.5f),
                    fontSize = 10.sp,
                    maxLines = 1,
                    modifier = Modifier.padding(end = 8.dp),
                )
                IconButton(onClick = onClose, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "关闭", tint = Color(0xFFFF6B9D), modifier = Modifier.size(16.dp))
                }
            }

            // ── 终端区 ──
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(start = 4.dp, end = 4.dp, bottom = 4.dp),
            ) {
                val density = LocalDensity.current
                val charWpx = with(density) { (fontSize * 0.6f).dp.toPx() }
                val rowHpx = with(density) { (fontSize * 1.35f).dp.toPx() }
                LaunchedEffect(constraints.maxWidth, constraints.maxHeight, charWpx, rowHpx) {
                    val cols = (constraints.maxWidth / charWpx).toInt().coerceIn(10, 300)
                    val rows = (constraints.maxHeight / rowHpx).toInt().coerceIn(3, 200)
                    session?.resize(rows, cols)
                }

                TianquanTerminalView(
                    session = session,
                    revision = revision,
                    fontSize = fontSize,
                    cursorStyle = TianquanPrefs.cursorStyle(context),
                    cursorBlinkMs = TianquanPrefs.cursorBlinkMs(context),
                    selection = null,
                    onSelectionChange = {},
                    onMetrics = {},
                    onTap = { line, col ->
                        val emu = session?.emulator
                        val mouseActive = emu != null && emu.mouseMode != MouseMode.NONE &&
                            TianquanPrefs.mouseTrackingEnabled(context)
                        if (mouseActive) {
                            val screenLine = line - emu!!.buffer.scrollback.size
                            if (screenLine >= 0) {
                                val px = ((col + 1) * charWpx).toInt()
                                val py = ((screenLine + 1) * rowHpx).toInt()
                                emu.reportMouse(MouseButton.LEFT, col, screenLine, press = true, motion = false, pixelCol = px, pixelRow = py)
                                emu.reportMouse(MouseButton.LEFT, col, screenLine, press = false, motion = false, pixelCol = px, pixelRow = py)
                            }
                        } else {
                            // 点按输入：聚焦隐形输入框并唤起软键盘
                            runCatching { inputFocus.requestFocus() }
                            keyboard?.show()
                        }
                    },
                    onLongPressStart = { _, _ -> },
                    onDragSelect = { _, _ -> },
                    onPinch = {},
                    statusText = status,
                    modifier = Modifier.fillMaxSize(),
                )

                // ── 右下角缩放手柄 ──
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .size(26.dp)
                        .pointerInput(session) {
                            detectDragGestures { change, drag ->
                                change.consume()
                                onResizeBy(drag.x.roundToInt(), drag.y.roundToInt())
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.OpenInFull,
                        contentDescription = "缩放",
                        tint = accent.copy(alpha = 0.75f),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            // ── 隐形输入捕获（同主界面方案）──
            BasicTextField(
                value = input,
                onValueChange = { new ->
                    val old = mirror.value
                    when {
                        new.length > old.length -> {
                            val added = new.substring(old.length)
                            for (c in added) {
                                when {
                                    c == '\n' || c == '\r' -> sendByte(0x0D)
                                    else -> send(c.toString())
                                }
                            }
                        }
                        new.length < old.length -> repeat(old.length - new.length) { sendByte(0x7F) }
                    }
                    val cleaned = new.replace("\n", "").replace("\r", "")
                    val capped = if (cleaned.length > 200) "" else cleaned
                    mirror.value = capped
                    input = capped
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .alpha(0f)
                    .focusRequester(inputFocus),
                singleLine = false,
            )
        }
    }
}
