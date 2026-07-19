package com.lazybrowser.app

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.webkit.*
import android.widget.PopupMenu
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.lazybrowser.app.adblock.AdBlocker
import com.lazybrowser.app.data.Bookmark
import com.lazybrowser.app.data.HistoryEntry
import com.lazybrowser.app.data.LazyDatabase
import com.lazybrowser.app.databinding.ActivityMainBinding
import com.lazybrowser.app.reader.ReaderMode
import com.lazybrowser.app.reader.NovelReader
import com.lazybrowser.app.reader.NovelReaderActivity
import com.lazybrowser.app.home.LazyHomepage
import com.lazybrowser.app.settings.SettingsManager
import com.lazybrowser.app.tab.TabManager
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var db: LazyDatabase
    private lateinit var settings: SettingsManager
    private lateinit var adBlocker: AdBlocker
    private lateinit var tabManager: TabManager

    private var isFullscreen = false

    private lateinit var novelReader: NovelReader

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = LazyDatabase.getInstance(this)
        settings = SettingsManager(this)
        adBlocker = AdBlocker(this)
        tabManager = TabManager(this, binding.webViewContainer)
        novelReader = NovelReader(this)

        setupToolbar()
        setupWebView()
        setupBottomBar()
        setupSwipeRefresh()
        setupSearchBar()

        // Load homepage or last URL
        val url = intent?.data?.toString()
        if (url != null) {
            loadUrl(url)
        } else {
            // 显示懒人首页
            showBookshelf()
        }
    }

    // ── Toolbar ──────────────────────────────────────────────────────

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        binding.btnBack.setOnClickListener { binding.webView.goBack() }
        binding.btnForward.setOnClickListener { binding.webView.goForward() }

        binding.urlBar.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                loadUrl(binding.urlBar.text.toString())
                true
            } else false
        }

        binding.urlBar.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                binding.urlBar.selectAll()
            }
        }
    }

    // ── WebView ──────────────────────────────────────────────────────

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        binding.webView.settings.apply {
            javaScriptEnabled = settings.javaScriptEnabled
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            builtInZoomControls = true
            displayZoomControls = false
            loadWithOverviewMode = true
            useWideViewPort = true
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            cacheMode = if (settings.offlineMode)
                WebSettings.LOAD_CACHE_ELSE_NETWORK
            else
                WebSettings.LOAD_DEFAULT
            userAgentString = settings.userAgent
            setSupportMultipleWindows(false)
        }

        binding.webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView?, request: WebResourceRequest?
            ): WebResourceResponse? {
                request?.let {
                    if (adBlocker.isAd(it.url.toString())) {
                        return WebResourceResponse("text/plain", "utf-8", "".byteInputStream())
                    }
                }
                return super.shouldInterceptRequest(view, request)
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                binding.progressBar.visibility = View.VISIBLE
                binding.urlBar.setText(url)
                updateNavigationButtons()
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                binding.progressBar.visibility = View.GONE
                binding.swipeRefresh.isRefreshing = false

                // Save to history
                url?.let { u ->
                    val title = view?.title ?: u
                    lifecycleScope.launch {
                        db.historyDao().insert(HistoryEntry(url = u, title = title))
                    }
                }

                // Apply dark mode if needed
                if (settings.darkMode) {
                    view?.evaluateJavascript(
                        "document.body.style.filter='invert(1) hue-rotate(180deg)'; null",
                        null
                    )
                }
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?, request: WebResourceRequest?
            ): Boolean {
                val url = request?.url ?: return false
                // Handle special schemes
                when (url.scheme) {
                    "http", "https" -> return false // let WebView handle
                    "mailto", "tel", "sms" -> {
                        startActivity(Intent(Intent.ACTION_VIEW, url))
                        return true
                    }
                }
                return false
            }
        }

        binding.webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                binding.progressBar.progress = newProgress
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                super.onReceivedTitle(view, title)
                binding.titleText.text = title
            }

            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                // File upload support
                return super.onShowFileChooser(webView, filePathCallback, fileChooserParams)
            }
        }

        // Long press for context menu
        binding.webView.setOnLongClickListener { v ->
            val hitTestResult = binding.webView.hitTestResult
            when (hitTestResult.type) {
                WebView.HitTestResult.SRC_ANCHOR_TYPE,
                WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> {
                    showLinkContextMenu(hitTestResult.extra)
                    true
                }
                else -> false
            }
        }
    }

    // ── Bottom Navigation Bar ────────────────────────────────────────

    private fun setupBottomBar() {
        binding.bottomBar.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> showBookshelfOrHomepage()
                R.id.nav_tabs -> tabManager.showTabSwitcher()
                R.id.nav_bookmarks -> showBookmarks()
                R.id.nav_menu -> showMainMenu()
            }
            true
        }

        // 长按主页按钮 → 打开书架
        binding.bottomBar.findViewById<View>(R.id.nav_home)?.setOnLongClickListener {
            showBookshelf()
            true
        }
    }

    // 显示懒人首页（有书有站点推荐，没书有发现推荐）
    private fun showBookshelfOrHomepage() {
        showBookshelf()
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            binding.webView.reload()
        }
    }

    private fun setupSearchBar() {
        binding.searchEngineBtn.setOnClickListener {
            showSearchEnginePicker()
        }
        updateSearchEngineIcon()
    }

    // ── URL Loading ──────────────────────────────────────────────────

    private fun loadUrl(input: String) {
        val url = when {
            input.isBlank() -> return
            input.startsWith("http://") || input.startsWith("https://") -> input
            input.contains(".") && !input.contains(" ") -> "https://$input"
            else -> "${settings.searchEngine.url}${Uri.encode(input)}"
        }
        binding.bookshelfContainer.visibility = View.GONE
        binding.webView.visibility = View.VISIBLE
        binding.webView.loadUrl(url)
        binding.urlBar.clearFocus()
        hideKeyboard()
    }

    // ── Navigation ───────────────────────────────────────────────────

    private fun updateNavigationButtons() {
        binding.btnBack.isEnabled = binding.webView.canGoBack()
        binding.btnForward.isEnabled = binding.webView.canGoForward()
        binding.btnBack.alpha = if (binding.webView.canGoBack()) 1f else 0.3f
        binding.btnForward.alpha = if (binding.webView.canGoForward()) 1f else 0.3f
    }

    // ── Search Engine ────────────────────────────────────────────────

    private fun showSearchEnginePicker() {
        val popup = PopupMenu(this, binding.searchEngineBtn)
        settings.searchEngines.forEachIndexed { index, engine ->
            popup.menu.add(0, index, 0, engine.name)
        }
        popup.setOnMenuItemClickListener { item ->
            settings.setSearchEngine(item.itemId)
            updateSearchEngineIcon()
            true
        }
        popup.show()
    }

    private fun updateSearchEngineIcon() {
        binding.searchEngineBtn.setImageResource(settings.searchEngine.iconRes)
    }

    // ── Bookmarks ────────────────────────────────────────────────────

    private fun showBookmarks() {
        lifecycleScope.launch {
            val bookmarks = db.bookmarkDao().getAll()
            BookmarkDialog.show(this@MainActivity, bookmarks,
                onOpen = { loadUrl(it.url) },
                onDelete = { lifecycleScope.launch { db.bookmarkDao().delete(it) } }
            )
        }
    }

    private fun toggleBookmark() {
        val url = binding.webView.url ?: return
        val title = binding.webView.title ?: url
        lifecycleScope.launch {
            val existing = db.bookmarkDao().getByUrl(url)
            if (existing != null) {
                db.bookmarkDao().delete(existing)
            } else {
                db.bookmarkDao().insert(Bookmark(url = url, title = title))
            }
        }
    }

    // ── Main Menu ────────────────────────────────────────────────────

    private fun showMainMenu() {
        val popup = PopupMenu(this, binding.bottomBar.menu.findItem(R.id.nav_menu)?.actionView
            ?: binding.bottomBar)
        popup.menuInflater.inflate(R.menu.menu_main, popup.menu)

        // Toggle dark mode text
        popup.menu.findItem(R.id.action_dark_mode)?.title =
            if (settings.darkMode) "☀️ 日间模式" else "🌙 夜间模式"

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_bookmark -> toggleBookmark()
                R.id.action_dark_mode -> toggleDarkMode()
                R.id.action_reader -> toggleReaderMode()
                R.id.action_bookshelf -> showBookshelf()
                R.id.action_share -> sharePage()
                R.id.action_find -> showFindBar()
                R.id.action_settings -> openSettings()
                R.id.action_history -> showHistory()
            }
            true
        }
        popup.show()
    }

    // ── Features ─────────────────────────────────────────────────────

    private fun toggleDarkMode() {
        settings.darkMode = !settings.darkMode
        binding.webView.reload()
    }

    private fun toggleReaderMode() {
        val url = binding.webView.url ?: return
        // 检测是否是小说页面，自动进入小说阅读模式
        lifecycleScope.launch {
            val isNovel = detectNovelPage()
            if (isNovel) {
                NovelReaderActivity.open(this@MainActivity, url, url)
            } else {
                // 普通阅读模式
                val html = ReaderMode.extract(binding.webView)
                binding.webView.loadDataWithBaseURL(url, html, "text/html", "UTF-8", url)
            }
        }
    }

    private suspend fun detectNovelPage(): Boolean {
        // 检查页面是否是小说章节页
        return try {
            val result = binding.webView.evaluateJavascriptAsync("""
                (function() {
                    var text = document.body.innerText;
                    var len = text.length;
                    // 小说特征：文字量大，段落多，链接少
                    var links = document.querySelectorAll('a').length;
                    var paras = document.querySelectorAll('p').length;
                    return (len > 2000 && paras > 10 && links < paras * 2);
                })()
            """.trimIndent())
            result == "true"
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun android.webkit.WebView.evaluateJavascriptAsync(script: String): String? {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
            kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
                evaluateJavascript(script) { result ->
                    continuation.resume(result) {}
                }
            }
        }
    }

    private fun sharePage() {
        val url = binding.webView.url ?: return
        val title = binding.webView.title ?: ""
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "$title\n$url")
        }
        startActivity(Intent.createChooser(intent, "分享页面"))
    }

    private fun showFindBar() {
        binding.findBar.visibility = View.VISIBLE
        binding.findEditText.requestFocus()
        binding.findNextBtn.setOnClickListener {
            binding.webView.findAllAsync(binding.findEditText.text.toString())
        }
        binding.findCloseBtn.setOnClickListener {
            binding.findBar.visibility = View.GONE
            binding.webView.clearMatches()
        }
    }

    private fun showHistory() {
        lifecycleScope.launch {
            val entries = db.historyDao().getRecent(100)
            HistoryDialog.show(this@MainActivity, entries,
                onOpen = { loadUrl(it.url) },
                onDelete = { lifecycleScope.launch { db.historyDao().delete(it) } }
            )
        }
    }

    private fun openSettings() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    private fun showBookshelf() {
        val homepage = LazyHomepage(
            context = this,
            novelReader = novelReader,
            onSiteClick = { url -> loadUrl(url) },
            onSearch = { query -> loadUrl(query) },
            onBookClick = { book ->
                NovelReaderActivity.open(this, book.url)
            }
        )

        // 隐藏 WebView，显示懒人首页
        binding.webView.visibility = View.GONE
        binding.bookshelfContainer.visibility = View.VISIBLE
        binding.bookshelfContainer.removeAllViews()
        binding.bookshelfContainer.addView(homepage.createView())

        // 更新 URL 栏
        binding.urlBar.setText("")
        binding.urlBar.hint = "🔍 搜索或输入网址"
    }

    private fun showLinkContextMenu(url: String?) {
        url ?: return
        val popup = PopupMenu(this, binding.webView)
        popup.menu.add("在新标签页打开")
        popup.menu.add("复制链接")
        popup.menu.add("分享链接")
        popup.setOnMenuItemClickListener { item ->
            when (item.title) {
                "在新标签页打开" -> tabManager.openInNewTab(url)
                "复制链接" -> copyToClipboard(url)
                "分享链接" -> shareUrl(url)
            }
            true
        }
        popup.show()
    }

    // ── Helpers ──────────────────────────────────────────────────────

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("url", text))
    }

    private fun shareUrl(url: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, url)
        }
        startActivity(Intent.createChooser(intent, "分享链接"))
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.hideSoftInputFromWindow(binding.urlBar.windowToken, 0)
    }

    // ── Key Events ───────────────────────────────────────────────────

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && binding.webView.canGoBack()) {
            binding.webView.goBack()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onResume() {
        super.onResume()
        binding.webView.onResume()
    }

    override fun onPause() {
        binding.webView.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        binding.webView.destroy()
        super.onDestroy()
    }
}
