package com.lazybrowser.app.reader

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.lazybrowser.app.databinding.ActivityNovelReaderBinding
import kotlinx.coroutines.launch

/**
 * 小说阅读 Activity
 * 全屏沉浸式阅读，支持章节切换、阅读设置、进度记忆
 */
class NovelReaderActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNovelReaderBinding
    private lateinit var reader: NovelReader
    private var currentBookUrl: String = ""
    private var currentChapterUrl: String = ""
    private var chapters: List<NovelReader.Chapter> = emptyList()
    private var currentChapterIndex: Int = -1
    private var toolbarVisible = false

    // 自动滚动 + 眼动检测
    private var autoScrollManager: AutoScrollManager? = null
    private var eyeTracker: EyeTrackerService? = null
    private var eyeTrackingEnabled = false

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startEyeTracking()
        } else {
            Toast.makeText(this, "需要摄像头权限才能使用眼动检测", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNovelReaderBinding.inflate(layoutInflater)
        setContentView(binding.root)

        reader = NovelReader(this)

        enterImmersiveMode()
        setupWebView()
        setupToolbar()
        setupSettingsPanel()

        // 接收传入的 URL
        val url = intent.getStringExtra(EXTRA_URL) ?: ""
        currentBookUrl = intent.getStringExtra(EXTRA_BOOK_URL) ?: url

        // 初始化自动滚动
        autoScrollManager = AutoScrollManager(binding.readerWebView) {
            runOnUiThread {
                Toast.makeText(this, "已到本章末尾", Toast.LENGTH_SHORT).show()
                updateAutoScrollUI()
            }
        }

        if (url.isNotEmpty()) {
            loadChapter(url)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        autoScrollManager?.stop()
        eyeTracker?.stop()
    }

    // ── 沉浸模式 ─────────────────────────────────────────────────────

    private fun enterImmersiveMode() {
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    // ── WebView ──────────────────────────────────────────────────────

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        binding.readerWebView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            builtInZoomControls = false
            displayZoomControls = false
            setSupportZoom(false)
            cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
        }

        // JavaScript Bridge
        binding.readerWebView.addJavascriptInterface(object {
            @JavascriptInterface
            fun onScroll(percent: Int) {
                runOnUiThread {
                    binding.readProgress.text = "${percent}%"
                    // 保存进度
                    reader.saveProgress(NovelReader.ReadProgress(
                        bookUrl = currentBookUrl,
                        chapterUrl = currentChapterUrl,
                        chapterTitle = binding.tvChapterTitle.text.toString(),
                        scrollPercent = percent
                    ))
                }
            }

            @JavascriptInterface
            fun onNextChapter() {
                runOnUiThread { goNextChapter() }
            }

            @JavascriptInterface
            fun onPrevChapter() {
                runOnUiThread { goPrevChapter() }
            }

            @JavascriptInterface
            fun onToggleToolbar() {
                runOnUiThread { toggleToolbar() }
            }
        }, "AndroidBridge")

        binding.readerWebView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // 恢复阅读进度
                restoreScrollPosition()
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false
                // 检测是否是章节链接
                if (isChapterLink(url)) {
                    loadChapter(url)
                    return true
                }
                return false
            }
        }
    }

    // ── 章节加载 ─────────────────────────────────────────────────────

    private fun loadChapter(url: String) {
        currentChapterUrl = url
        binding.loadingView.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                binding.readerWebView.loadUrl(url)

                binding.readerWebView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, pageUrl: String?) {
                        super.onPageFinished(view, pageUrl)
                        lifecycleScope.launch {
                            try {
                                val content = reader.extractContent(binding.readerWebView)
                                val html = reader.renderChapter(content, currentBookUrl)
                                binding.readerWebView.loadDataWithBaseURL(
                                    currentChapterUrl, html, "text/html", "UTF-8", null
                                )
                                binding.tvChapterTitle.text = content.title
                                binding.loadingView.visibility = View.GONE

                                reader.saveProgress(NovelReader.ReadProgress(
                                    bookUrl = currentBookUrl,
                                    chapterUrl = currentChapterUrl,
                                    chapterTitle = content.title,
                                    scrollPercent = 0
                                ))

                                reader.addBook(NovelReader.Book(
                                    url = currentBookUrl,
                                    title = content.title,
                                    lastChapter = content.title
                                ))
                            } catch (e: Exception) {
                                binding.loadingView.visibility = View.GONE
                                Toast.makeText(
                                    this@NovelReaderActivity,
                                    "解析失败: ${e.message}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                binding.loadingView.visibility = View.GONE
                Toast.makeText(
                    this@NovelReaderActivity,
                    "加载失败: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun goNextChapter() {
        if (currentChapterIndex in 0 until chapters.size - 1) {
            currentChapterIndex++
            loadChapter(chapters[currentChapterIndex].url)
        }
    }

    private fun goPrevChapter() {
        if (currentChapterIndex > 0) {
            currentChapterIndex--
            loadChapter(chapters[currentChapterIndex].url)
        }
    }

    private fun restoreScrollPosition() {
        val progress = reader.getProgress(currentBookUrl)
        if (progress != null && progress.chapterUrl == currentChapterUrl) {
            val percent = progress.scrollPercent
            binding.readerWebView.evaluateJavascript(
                "window.scrollTo(0, document.body.scrollHeight * $percent / 100);",
                null
            )
        }
    }

    private fun isChapterLink(url: String): Boolean {
        return url.matches(Regex(".*(?:chapter|read|\\d{3,}\\.html?).*", RegexOption.IGNORE_CASE))
    }

    // ── 工具栏 ──────────────────────────────────────────────────────

    private fun setupToolbar() {
        binding.btnBack.setOnClickListener { finish() }
        binding.btnChapterList.setOnClickListener { showChapterList() }
        binding.btnSettings.setOnClickListener { toggleSettingsPanel() }
        binding.btnPrevChapter.setOnClickListener { goPrevChapter() }
        binding.btnNextChapter.setOnClickListener { goNextChapter() }

        // 加入书架
        binding.btnAddShelf.setOnClickListener {
            val title = binding.tvChapterTitle.text.toString()
            reader.addBook(NovelReader.Book(
                url = currentBookUrl,
                title = title,
                lastChapter = title
            ))
            binding.btnAddShelf.text = "已在书架"
            binding.btnAddShelf.alpha = 0.5f
            Toast.makeText(this, "已加入书架", Toast.LENGTH_SHORT).show()
        }

        // 缓存全本
        binding.btnCacheBook.setOnClickListener {
            Toast.makeText(this, "开始缓存...", Toast.LENGTH_SHORT).show()
            binding.btnCacheBook.text = "缓存中..."
            binding.btnCacheBook.alpha = 0.5f
            // TODO: 触发下载管理器缓存全本
        }

        // 自动滚动
        binding.btnAutoScroll.setOnClickListener {
            autoScrollManager?.toggle()
            updateAutoScrollUI()
        }

        // 长按调节速度
        binding.btnAutoScroll.setOnLongClickListener {
            showSpeedPicker()
            true
        }

        // 眼动检测
        binding.btnEyeTrack.setOnClickListener {
            if (eyeTrackingEnabled) {
                stopEyeTracking()
            } else {
                // 需要先开启自动滚动
                if (autoScrollManager?.isActive != true) {
                    autoScrollManager?.start()
                    updateAutoScrollUI()
                }
                requestEyeTracking()
            }
        }

        // 点击中间区域切换工具栏
        binding.tapZone.setOnClickListener { toggleToolbar() }

        // 检查是否已在书架
        updateShelfButton()
        updateOfflineIndicator()
    }

    private fun updateShelfButton() {
        val isInShelf = reader.getBooks().any { it.url == currentBookUrl }
        binding.btnAddShelf.text = if (isInShelf) "已在书架" else "+ 书架"
        binding.btnAddShelf.alpha = if (isInShelf) 0.5f else 1f
    }

    private fun updateOfflineIndicator() {
        // 检查是否已缓存
        val isCached = false // TODO: 从下载管理器查询
        binding.offlineIndicator.visibility = if (isCached) View.VISIBLE else View.GONE
    }

    private fun toggleToolbar() {
        toolbarVisible = !toolbarVisible
        binding.topBar.animate().translationY(if (toolbarVisible) 0f else -binding.topBar.height.toFloat()).setDuration(200).start()
        binding.bottomBar.animate().translationY(if (toolbarVisible) 0f else binding.bottomBar.height.toFloat()).setDuration(200).start()
        binding.topBar.visibility = if (toolbarVisible) View.VISIBLE else View.GONE
        binding.bottomBar.visibility = if (toolbarVisible) View.VISIBLE else View.GONE

        if (toolbarVisible) {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        } else {
            enterImmersiveMode()
        }
    }

    // ── 自动滚动 UI ──────────────────────────────────────────────────

    private fun updateAutoScrollUI() {
        val active = autoScrollManager?.isActive == true
        binding.btnAutoScroll.text = if (active) "⏸ 暂停" else "▶ 自动"
        binding.btnAutoScroll.backgroundTintList = android.content.res.ColorStateList.valueOf(
            Color.parseColor(if (active) "#1A73E8" else "#333333")
        )
    }

    private fun showSpeedPicker() {
        val speeds = arrayOf("慢速 (15px/s)", "中速 (30px/s)", "快速 (60px/s)", "极速 (120px/s)")
        android.app.AlertDialog.Builder(this)
            .setTitle("滚动速度")
            .setItems(speeds) { _, which ->
                autoScrollManager?.setSpeed(which + 1)
                Toast.makeText(this, "速度已调整", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    // ── 眼动检测 ─────────────────────────────────────────────────────

    private fun requestEyeTracking() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startEyeTracking()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startEyeTracking() {
        eyeTracker = EyeTrackerService(this, this).apply {
            setCallback(object : EyeTrackerService.Callback {
                override fun onGazeLost() {
                    runOnUiThread {
                        autoScrollManager?.pauseByEyeTracker()
                        updateEyeTrackUI(true)
                    }
                }

                override fun onGazeRestored() {
                    runOnUiThread {
                        autoScrollManager?.resumeByEyeTracker()
                        updateEyeTrackUI(false)
                    }
                }

                override fun onTrackingStarted() {
                    runOnUiThread {
                        eyeTrackingEnabled = true
                        updateEyeTrackUI(false)
                        Toast.makeText(this@NovelReaderActivity, "眼动检测已开启", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onTrackingFailed(reason: String) {
                    runOnUiThread {
                        eyeTrackingEnabled = false
                        updateEyeTrackUI(false)
                        Toast.makeText(this@NovelReaderActivity, "眼动检测失败: $reason", Toast.LENGTH_SHORT).show()
                    }
                }
            })
            start()
        }
    }

    private fun stopEyeTracking() {
        eyeTracker?.stop()
        eyeTracker = null
        eyeTrackingEnabled = false
        autoScrollManager?.resumeByEyeTracker()
        updateEyeTrackUI(false)
        Toast.makeText(this, "眼动检测已关闭", Toast.LENGTH_SHORT).show()
    }

    private fun updateEyeTrackUI(gazeLost: Boolean) {
        binding.btnEyeTrack.text = when {
            !eyeTrackingEnabled -> "👁 眼动"
            gazeLost -> "👁 暂停"
            else -> "👁 检测中"
        }
        binding.btnEyeTrack.backgroundTintList = android.content.res.ColorStateList.valueOf(
            Color.parseColor(
                when {
                    !eyeTrackingEnabled -> "#333333"
                    gazeLost -> "#FF9800"
                    else -> "#4CAF50"
                }
            )
        )
    }

    // ── 章节列表 ─────────────────────────────────────────────────────

    private fun showChapterList() {
        lifecycleScope.launch {
            val chapterList = reader.extractChapterList(binding.readerWebView)
            if (chapterList.isNotEmpty()) {
                chapters = chapterList
                currentChapterIndex = chapterList.indexOfFirst { it.url == currentChapterUrl }

                val items = chapterList.mapIndexed { index, ch ->
                    val prefix = if (index == currentChapterIndex) "▶ " else "  "
                    "$prefix${ch.title}"
                }.toTypedArray()

                android.app.AlertDialog.Builder(this@NovelReaderActivity)
                    .setTitle("目录 (${chapterList.size}章)")
                    .setItems(items) { _, which ->
                        currentChapterIndex = which
                        loadChapter(chapterList[which].url)
                    }
                    .show()
            }
        }
    }

    // ── 阅读设置面板 ────────────────────────────────────────────────

    private fun setupSettingsPanel() {
        val s = reader.settings

        // 字号
        binding.fontSizeBar.progress = s.fontSize - 12
        binding.fontSizeText.text = "${s.fontSize}sp"
        binding.fontSizeBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                val size = progress + 12
                binding.fontSizeText.text = "${size}sp"
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {
                val size = (sb?.progress ?: 0) + 12
                reader.settings = reader.settings.copy(fontSize = size)
                reloadContent()
            }
        })

        // 行距
        binding.lineHeightBar.progress = ((s.lineHeight - 1.0f) * 10).toInt()
        binding.lineHeightText.text = "${s.lineHeight}"
        binding.lineHeightBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                val lh = 1.0f + progress / 10f
                binding.lineHeightText.text = String.format("%.1f", lh)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {
                val lh = 1.0f + (sb?.progress ?: 0) / 10f
                reader.settings = reader.settings.copy(lineHeight = lh)
                reloadContent()
            }
        })

        // 主题颜色
        setupThemeButtons()

        // 翻页模式
        setupPageModeButtons()
    }

    private fun setupThemeButtons() {
        val themeNames = NovelReader.THEMES.keys.toList()
        binding.themeGroup.removeAllViews()
        themeNames.forEach { name ->
            val (bg, _) = NovelReader.THEMES[name]!!
            val btn = android.widget.TextView(this).apply {
                text = name
                textSize = 12f
                setPadding(24, 12, 24, 12)
                setBackgroundColor(Color.parseColor(bg))
                setTextColor(Color.parseColor(
                    if (bg in listOf("#000000", "#333333", "#1A237E", "#2D1B4E")) "#CCCCCC" else "#333333"
                ))
                setBackgroundResource(com.lazybrowser.app.R.drawable.bg_theme_btn)
            }
            btn.setOnClickListener {
                val (bgColor, textColor) = NovelReader.THEMES[name]!!
                reader.settings = reader.settings.copy(bgColor = bgColor, textColor = textColor)
                reloadContent()
            }
            binding.themeGroup.addView(btn)
        }
    }

    private fun setupPageModeButtons() {
        binding.btnModeScroll.setOnClickListener {
            reader.settings = reader.settings.copy(pageMode = NovelReader.PageMode.SCROLL)
            updatePageModeUI()
            reloadContent()
        }
        binding.btnModeFlip.setOnClickListener {
            reader.settings = reader.settings.copy(pageMode = NovelReader.PageMode.FLIP_LR)
            updatePageModeUI()
            reloadContent()
        }
    }

    private fun updatePageModeUI() {
        val isScroll = reader.settings.pageMode == NovelReader.PageMode.SCROLL
        binding.btnModeScroll.alpha = if (isScroll) 1f else 0.5f
        binding.btnModeFlip.alpha = if (isScroll) 0.5f else 1f
    }

    private fun toggleSettingsPanel() {
        val panel = binding.settingsPanel
        if (panel.visibility == View.VISIBLE) {
            panel.animate().translationY(panel.height.toFloat()).setDuration(200).withEndAction {
                panel.visibility = View.GONE
            }.start()
        } else {
            panel.visibility = View.VISIBLE
            panel.translationY = panel.height.toFloat()
            panel.animate().translationY(0f).setDuration(200).start()
        }
    }

    private fun reloadContent() {
        binding.readerWebView.evaluateJavascript(
            "document.body.style.fontSize='${reader.settings.fontSize}px';" +
            "document.body.style.lineHeight='${reader.settings.lineHeight}';" +
            "document.body.style.background='${reader.settings.bgColor}';" +
            "document.body.style.color='${reader.settings.textColor}';" +
            "document.body.style.fontFamily='${reader.settings.fontFamily}';",
            null
        )
    }

    // ── Companion ────────────────────────────────────────────────────

    companion object {
        const val EXTRA_URL = "novel_url"
        const val EXTRA_BOOK_URL = "book_url"

        fun open(context: Context, url: String, bookUrl: String = url) {
            context.startActivity(Intent(context, NovelReaderActivity::class.java).apply {
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_BOOK_URL, bookUrl)
            })
        }
    }
}
