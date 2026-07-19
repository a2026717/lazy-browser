package com.lazybrowser.app

import android.app.Application
import android.os.Build
import android.webkit.WebView
import java.io.File

/**
 * Application 类
 * - 全局异常捕获
 * - WebView 多进程兼容
 * - 内存优化
 */
class LazyApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // 1. 全局异常捕获 — 避免未处理异常直接闪退
        setupCrashHandler()

        // 2. WebView 多进程兼容 — 避免数据目录锁冲突
        fixWebViewMultiprocess()

        // 3. 清理旧 WebView 缓存 — 解决 GPU 缓存溢出
        cleanWebViewCache()
    }

    /**
     * 全局异常捕获处理器
     * 捕获所有未处理异常，避免应用直接闪退
     */
    private fun setupCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            // 记录崩溃日志
            try {
                val logFile = File(filesDir, "crash_log.txt")
                logFile.appendText(
                    "\n\n[${System.currentTimeMillis()}] Thread: ${thread.name}\n" +
                    "${throwable.stackTraceToString()}"
                )
            } catch (_: Exception) {}

            // 交给默认处理器（系统会弹出"应用已停止"对话框）
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    /**
     * WebView 多进程兼容修复
     * Android 9.0+ 多进程使用 WebView 需要设置不同的数据目录
     */
    private fun fixWebViewMultiprocess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val processName = resolveProcessName()
            if (processName != packageName) {
                // 子进程 WebView 数据目录隔离
                WebView.setDataDirectorySuffix(processName?.split(":")?.lastOrNull() ?: "webview")
            }
        }
    }

    /**
     * 清理旧版本 WebView 缓存
     * 版本升级后首次启动时清理，解决 GPU 缓存溢出
     */
    private fun cleanWebViewCache() {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val lastVersion = prefs.getInt("last_version", 0)
        val currentVersion = try {
            packageManager.getPackageInfo(packageName, 0).longVersionCode.toInt()
        } catch (_: Exception) { 1 }

        if (lastVersion < currentVersion) {
            try {
                // 清理 WebView 缓存目录
                val webViewDirs = listOf(
                    "app_webview", "app_hws_webview", "WebView",
                    "GPUCache", "ShaderCache"
                )
                val dataDir = filesDir.parentFile
                webViewDirs.forEach { dir ->
                    File(dataDir, dir).deleteRecursively()
                }
                prefs.edit().putInt("last_version", currentVersion).apply()
            } catch (_: Exception) {}
        }
    }

    private fun resolveProcessName(): String? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            android.app.Application.getProcessName()
        } else {
            try {
                val pid = android.os.Process.myPid()
                val am = getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
                am.runningAppProcesses?.find { it.pid == pid }?.processName
            } catch (_: Exception) { null }
        }
    }
}
