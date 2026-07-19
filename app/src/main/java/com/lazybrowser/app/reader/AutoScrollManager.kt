package com.lazybrowser.app.reader

import android.os.Handler
import android.os.Looper
import android.webkit.WebView

/**
 * 自动滚动管理器
 *
 * 功能：
 * - 以可配置速度自动滚动 WebView
 * - 支持暂停/恢复（供眼动检测调用）
 * - 到达底部自动停止并通知翻页
 */
class AutoScrollManager(
    private val webView: WebView,
    private val onReachBottom: () -> Unit = {}
) {

    private val handler = Handler(Looper.getMainLooper())
    private var isRunning = false
    private var isPausedByEyeTracker = false

    // 滚动速度：像素/秒，默认 30（很慢，适合阅读）
    var speedPxPerSec: Int = 30

    // 滚动间隔 ms，越小越平滑
    private val intervalMs = 50L

    private val scrollRunnable = object : Runnable {
        override fun run() {
            if (!isRunning) return

            if (!isPausedByEyeTracker) {
                val dy = (speedPxPerSec * intervalMs / 1000f).toInt().coerceAtLeast(1)
                webView.evaluateJavascript(
                    """
                    (function() {
                        var maxScroll = document.body.scrollHeight - window.innerHeight;
                        if (window.scrollY >= maxScroll - 2) {
                            return 'bottom';
                        }
                        window.scrollBy(0, $dy);
                        return 'ok';
                    })();
                    """.trimIndent()
                ) { result ->
                    if (result?.contains("bottom") == true) {
                        stop()
                        onReachBottom()
                    }
                }
            }

            handler.postDelayed(this, intervalMs)
        }
    }

    // ── 公开 API ──────────────────────────────────────────────────

    fun start() {
        if (isRunning) return
        isRunning = true
        isPausedByEyeTracker = false
        handler.post(scrollRunnable)
    }

    fun stop() {
        isRunning = false
        handler.removeCallbacks(scrollRunnable)
    }

    fun toggle() {
        if (isRunning) stop() else start()
    }

    val isActive: Boolean get() = isRunning

    // ── 眼动控制 ──────────────────────────────────────────────────

    /**
     * 眼动检测回调：视线离开屏幕 → 暂停
     */
    fun pauseByEyeTracker() {
        isPausedByEyeTracker = true
    }

    /**
     * 眼动检测回调：视线回到屏幕 → 恢复
     */
    fun resumeByEyeTracker() {
        isPausedByEyeTracker = false
    }

    val isPaused: Boolean get() = isPausedByEyeTracker

    fun setSpeed(level: Int) {
        // level: 1=慢(15) 2=中(30) 3=快(60) 4=极速(120)
        speedPxPerSec = when (level) {
            1 -> 15
            2 -> 30
            3 -> 60
            4 -> 120
            else -> 30
        }
    }
}
