package com.lazybrowser.app.reader

import android.content.Context
import android.content.SharedPreferences
import android.webkit.WebView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * 小说阅读模式
 * - 自动识别小说章节内容
 * - 连续阅读（自动加载下一章）
 * - 阅读进度记忆
 * - 字号/行距/背景/翻页方式自定义
 */
class NovelReader(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("novel_reader", Context.MODE.PREFS_PRIVATE)

    // ── 阅读设置 ─────────────────────────────────────────────────────

    data class ReadSettings(
        val fontSize: Int = 18,          // sp
        val lineHeight: Float = 1.8f,    // 行距倍数
        val bgColor: String = "#F5F0E8", // 护眼淡黄
        val textColor: String = "#333333",
        val fontFamily: String = "serif", // serif / sans-serif / monospace
        val pageMode: PageMode = PageMode.SCROLL, // 滚动 or 翻页
        val paragraphSpacing: Int = 16,  // 段落间距 dp
        val firstLineIndent: Boolean = true, // 首行缩进
        val brightness: Int = -1          // -1 = 跟随系统
    )

    enum class PageMode {
        SCROLL,   // 上下滚动
        FLIP_LR,  // 左右翻页
        FLIP_UD   // 上下翻页
    }

    var settings: ReadSettings
        get() = ReadSettings(
            fontSize = prefs.getInt("font_size", 18),
            lineHeight = prefs.getFloat("line_height", 1.8f),
            bgColor = prefs.getString("bg_color", "#F5F0E8") ?: "#F5F0E8",
            textColor = prefs.getString("text_color", "#333333") ?: "#333333",
            fontFamily = prefs.getString("font_family", "serif") ?: "serif",
            pageMode = PageMode.entries.getOrElse(prefs.getInt("page_mode", 0)) { PageMode.SCROLL },
            paragraphSpacing = prefs.getInt("para_spacing", 16),
            firstLineIndent = prefs.getBoolean("first_indent", true),
            brightness = prefs.getInt("brightness", -1)
        )
        set(value) {
            prefs.edit().apply {
                putInt("font_size", value.fontSize)
                putFloat("line_height", value.lineHeight)
                putString("bg_color", value.bgColor)
                putString("text_color", value.textColor)
                putString("font_family", value.fontFamily)
                putInt("page_mode", value.pageMode.ordinal)
                putInt("para_spacing", value.paragraphSpacing)
                putBoolean("first_indent", value.firstLineIndent)
                putInt("brightness", value.brightness)
                apply()
            }
        }

    // ── 阅读进度 ─────────────────────────────────────────────────────

    data class ReadProgress(
        val bookUrl: String,     // 小说目录页 URL
        val chapterUrl: String,  // 当前章节 URL
        val chapterTitle: String,
        val scrollPercent: Int,  // 滚动百分比 0-100
        val timestamp: Long = System.currentTimeMillis()
    )

    fun saveProgress(progress: ReadProgress) {
        prefs.edit().apply {
            putString("progress_book", progress.bookUrl)
            putString("progress_chapter", progress.chapterUrl)
            putString("progress_title", progress.chapterTitle)
            putInt("progress_scroll", progress.scrollPercent)
            putLong("progress_time", progress.timestamp)
            apply()
        }
    }

    fun getProgress(bookUrl: String): ReadProgress? {
        val saved = prefs.getString("progress_book", null) ?: return null
        if (saved != bookUrl) return null
        return ReadProgress(
            bookUrl = saved,
            chapterUrl = prefs.getString("progress_chapter", "") ?: "",
            chapterTitle = prefs.getString("progress_title", "") ?: "",
            scrollPercent = prefs.getInt("progress_scroll", 0),
            timestamp = prefs.getLong("progress_time", 0)
        )
    }

    // ── 章节解析 ─────────────────────────────────────────────────────

    data class Chapter(
        val title: String,
        val url: String
    )

    /**
     * 从当前页面提取章节列表（目录页）
     */
    suspend fun extractChapterList(webView: WebView): List<Chapter> = withContext(Dispatchers.Main) {
        val js = """
        (function() {
            var chapters = [];
            // 常见小说站章节链接选择器
            var selectors = [
                '#list dl dd a',           // 笔趣阁系列
                '.listmain dl dd a',       // 变体
                '.chapter-list a',         // 通用
                '.volume-wrap a',          // 起点目录
                '.catalog-content a',      // 番茄
                'a[href*="chapter"]',      // URL 含 chapter
                'a[href*=".html"]',        // 静态页面链接
                '.book-list a',
                'ul.chapter-list li a',
                '#chapterList a',
                '.read-list a'
            ];
            var links = [];
            for (var i = 0; i < selectors.length; i++) {
                var found = document.querySelectorAll(selectors[i]);
                if (found.length > 5) { // 过滤掉太短的列表
                    links = found;
                    break;
                }
            }
            // 兜底：收集页面上所有指向章节的链接
            if (links.length === 0) {
                links = document.querySelectorAll('a');
            }
            var seen = {};
            for (var j = 0; j < links.length; j++) {
                var a = links[j];
                var href = a.href;
                var text = a.textContent.trim();
                if (href && text && text.length >= 2 && text.length <= 50 && !seen[href]) {
                    // 过滤明显非章节的链接
                    if (!/^第?\s*[\d一二三四五六七八九十百千万]+\s*[章节回卷集部篇]/.test(text) &&
                        !/^\d+$/.test(text)) {
                        // 检查是否像章节
                        if (!/chapter|read|\d{3,}/i.test(href)) continue;
                    }
                    seen[href] = true;
                    chapters.push({ title: text, url: href });
                }
            }
            return JSON.stringify(chapters);
        })()
        """.trimIndent()

        val result = webView.evaluateJavascriptAsync(js)
        parseChapters(result)
    }

    /**
     * 从当前页面提取正文内容（章节页）
     */
    suspend fun extractContent(webView: WebView): ChapterContent = withContext(Dispatchers.Main) {
        val js = """
        (function() {
            // 移除干扰元素
            var remove = 'script,style,iframe,nav,header,footer,aside,.ad,.ads,.comment,.sidebar,.recommend';
            document.querySelectorAll(remove).forEach(function(el) { el.remove(); });

            // 尝试找正文容器
            var selectors = [
                '#content', '#BookText', '#chaptercontent', '#readtext',
                '.chapter-content', '.read-content', '.novel-content',
                '.text-content', '.book-content', '#text_c',
                'article', '[itemprop="articleBody"]', '.post-content',
                '#htmlContent', '.content', 'main'
            ];
            var container = null;
            for (var i = 0; i < selectors.length; i++) {
                var el = document.querySelector(selectors[i]);
                if (el && el.textContent.trim().length > 100) {
                    container = el;
                    break;
                }
            }
            if (!container) container = document.body;

            // 提取标题
            var title = '';
            var titleSelectors = ['.bookname h1', '.chapter-title', 'h1', '.title', 'header h1'];
            for (var t = 0; t < titleSelectors.length; t++) {
                var titleEl = document.querySelector(titleSelectors[t]);
                if (titleEl && titleEl.textContent.trim()) {
                    title = titleEl.textContent.trim();
                    break;
                }
            }
            if (!title) title = document.title;

            // 提取正文文本（保留段落结构）
            var paragraphs = [];
            var walker = document.createTreeWalker(container, NodeFilter.SHOW_TEXT, null, false);
            var currentP = '';
            var nodes = container.querySelectorAll('p, br, div');
            if (nodes.length > 3) {
                // 有明确段落标签
                for (var p = 0; p < nodes.length; p++) {
                    var text = nodes[p].textContent.trim();
                    if (text && text.length > 1) {
                        paragraphs.push(text);
                    }
                }
            } else {
                // 没有段落标签，按换行分割
                var raw = container.innerText;
                paragraphs = raw.split(/\n+/).map(function(s) { return s.trim(); }).filter(function(s) { return s.length > 1; });
            }

            // 查找下一章链接
            var nextUrl = '';
            var nextSelectors = ['a.next', 'a#next_chapter', '.bottem1 a:last-child', 'a[title*="下一"]', 'a[title*="下一页"]'];
            for (var n = 0; n < nextSelectors.length; n++) {
                var nextEl = document.querySelector(nextSelectors[n]);
                if (nextEl && nextEl.href) {
                    nextUrl = nextEl.href;
                    break;
                }
            }
            if (!nextUrl) {
                // 兜底：找含"下一页"或"下一章"文字的链接
                var allLinks = document.querySelectorAll('a');
                for (var l = 0; l < allLinks.length; l++) {
                    var txt = allLinks[l].textContent.trim();
                    if (/下一[页章节]/.test(txt) && allLinks[l].href) {
                        nextUrl = allLinks[l].href;
                        break;
                    }
                }
            }

            // 查找上一章链接
            var prevUrl = '';
            var prevSelectors = ['a.prev', 'a#prev_chapter', '.bottem1 a:first-child', 'a[title*="上一"]'];
            for (var pr = 0; pr < prevSelectors.length; pr++) {
                var prevEl = document.querySelector(prevSelectors[pr]);
                if (prevEl && prevEl.href) {
                    prevUrl = prevEl.href;
                    break;
                }
            }

            return JSON.stringify({
                title: title,
                paragraphs: paragraphs,
                nextUrl: nextUrl,
                prevUrl: prevUrl
            });
        })()
        """.trimIndent()

        val result = webView.evaluateJavascriptAsync(js)
        parseChapterContent(result)
    }

    // ── 渲染 ─────────────────────────────────────────────────────────

    fun renderChapter(content: ChapterContent, bookUrl: String): String {
        val s = settings
        val paragraphsHtml = content.paragraphs.joinToString("\n") { text ->
            val indent = if (s.firstLineIndent) "text-indent: 2em;" else ""
            "<p style=\"margin-bottom: ${s.paragraphSpacing}px; $indent\">$text</p>"
        }

        val scrollScript = if (s.pageMode == PageMode.SCROLL) """
            window.addEventListener('scroll', function() {
                var percent = Math.round(window.scrollY / (document.body.scrollHeight - window.innerHeight) * 100);
                window.AndroidBridge && window.AndroidBridge.onScroll(percent);
            });
        """ else ""

        val flipScript = when (s.pageMode) {
            PageMode.FLIP_LR -> """
                var currentPage = 0;
                var pages = [];
                document.body.addEventListener('click', function(e) {
                    var x = e.clientX / window.innerWidth;
                    if (x < 0.3) prevPage();
                    else if (x > 0.7) nextPage();
                });
                // Swipe detection
                var touchStartX = 0;
                document.addEventListener('touchstart', function(e) { touchStartX = e.touches[0].clientX; });
                document.addEventListener('touchend', function(e) {
                    var dx = e.changedTouches[0].clientX - touchStartX;
                    if (Math.abs(dx) > 50) {
                        if (dx > 0) prevPage(); else nextPage();
                    }
                });
                function nextPage() { /* pagination logic */ }
                function prevPage() { /* pagination logic */ }
            """.trimIndent()
            PageMode.FLIP_UD -> """
                var touchStartY = 0;
                document.addEventListener('touchstart', function(e) { touchStartY = e.touches[0].clientY; });
                document.addEventListener('touchend', function(e) {
                    var dy = e.changedTouches[0].clientY - touchStartY;
                    if (Math.abs(dy) > 80) {
                        if (dy > 0) prevPage(); else nextPage();
                    }
                });
            """.trimIndent()
            else -> ""
        }

        return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
            <title>${content.title}</title>
            <style>
                * { margin: 0; padding: 0; box-sizing: border-box; }
                body {
                    font-family: '${s.fontFamily}', serif;
                    font-size: ${s.fontSize}sp;
                    line-height: ${s.lineHeight};
                    color: ${s.textColor};
                    background: ${s.bgColor};
                    padding: 16px;
                    max-width: 100%;
                    overflow-x: hidden;
                    -webkit-text-size-adjust: none;
                    word-wrap: break-word;
                }
                .chapter-title {
                    font-size: ${(s.fontSize * 1.4).toInt()}px;
                    font-weight: bold;
                    text-align: center;
                    margin-bottom: 24px;
                    padding-bottom: 12px;
                    border-bottom: 1px solid rgba(0,0,0,0.1);
                    color: ${s.textColor};
                }
                p {
                    margin-bottom: ${s.paragraphSpacing}px;
                    ${if (s.firstLineIndent) "text-indent: 2em;" else ""}
                }
                .nav-bar {
                    display: flex;
                    justify-content: space-between;
                    align-items: center;
                    padding: 16px 0;
                    margin-top: 32px;
                    border-top: 1px solid rgba(0,0,0,0.1);
                }
                .nav-bar a {
                    color: #1A73E8;
                    text-decoration: none;
                    padding: 10px 20px;
                    border: 1px solid #1A73E8;
                    border-radius: 20px;
                    font-size: 14px;
                }
                .nav-bar .chapter-nav {
                    color: #666;
                    font-size: 13px;
                }
                .read-progress {
                    text-align: center;
                    color: #999;
                    font-size: 12px;
                    margin-top: 16px;
                    padding: 8px;
                }
                .reader-toolbar {
                    position: fixed;
                    bottom: 0;
                    left: 0;
                    right: 0;
                    background: rgba(0,0,0,0.85);
                    color: #fff;
                    padding: 12px 16px;
                    display: none;
                    z-index: 100;
                    backdrop-filter: blur(10px);
                }
                .reader-toolbar .settings-row {
                    display: flex;
                    align-items: center;
                    justify-content: space-between;
                    margin: 8px 0;
                }
                .reader-toolbar button {
                    background: rgba(255,255,255,0.2);
                    color: #fff;
                    border: none;
                    padding: 6px 14px;
                    border-radius: 16px;
                    margin: 0 4px;
                    font-size: 14px;
                }
                .reader-toolbar button.active {
                    background: #1A73E8;
                }
                .tap-zone {
                    position: fixed;
                    top: 0; left: 0; right: 0; bottom: 0;
                    z-index: 50;
                }
                .tap-center {
                    position: absolute;
                    top: 30%; left: 30%; right: 30%; bottom: 30%;
                }
            </style>
        </head>
        <body>
            <h1 class="chapter-title">${content.title}</h1>
            $paragraphsHtml

            <div class="nav-bar">
                ${if (content.prevUrl.isNotEmpty()) "<a href='${content.prevUrl}'>← 上一章</a>" else "<span></span>"}
                <span class="chapter-nav">${content.title}</span>
                ${if (content.nextUrl.isNotEmpty()) "<a href='${content.nextUrl}'>下一章 →</a>" else "<span></span>"}
            </div>

            <div class="read-progress" id="progress">阅读进度 0%</div>

            <!-- Tap zones for toolbar toggle -->
            <div class="tap-zone" id="tapZone">
                <div class="tap-center" id="tapCenter"></div>
            </div>

            <script>
                // Tap center to toggle toolbar
                var toolbar = document.getElementById('toolbar');
                document.getElementById('tapCenter').addEventListener('click', function(e) {
                    e.stopPropagation();
                    if (toolbar) toolbar.style.display = toolbar.style.display === 'none' ? 'block' : 'none';
                });

                // Scroll progress tracking
                window.addEventListener('scroll', function() {
                    var percent = Math.round(window.scrollY / (document.body.scrollHeight - window.innerHeight) * 100);
                    var el = document.getElementById('progress');
                    if (el) el.textContent = '阅读进度 ' + percent + '%';
                    if (window.AndroidBridge) window.AndroidBridge.onScroll(percent);
                });

                // Auto-save progress periodically
                setInterval(function() {
                    var percent = Math.round(window.scrollY / (document.body.scrollHeight - window.innerHeight) * 100);
                    if (window.AndroidBridge) window.AndroidBridge.onScroll(percent);
                }, 5000);

                $scrollScript
                $flipScript
            </script>
        </body>
        </html>
        """
    }

    // ── 书架（已加入的小说）─────────────────────────────────────────

    data class Book(
        val url: String,        // 目录页 URL
        val title: String,
        val lastChapter: String,
        val lastReadTime: Long = System.currentTimeMillis()
    )

    fun addBook(book: Book) {
        val books = getBooks().toMutableList()
        books.removeAll { it.url == book.url }
        books.add(0, book)
        saveBooks(books)
    }

    fun getBooks(): List<Book> {
        val json = prefs.getString("bookshelf", "[]") ?: "[]"
        return try {
            val arr = org.json.JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                Book(
                    url = obj.getString("url"),
                    title = obj.getString("title"),
                    lastChapter = obj.optString("lastChapter", ""),
                    lastReadTime = obj.optLong("lastReadTime", 0)
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveBooks(books: List<Book>) {
        val arr = org.json.JSONArray()
        books.forEach { book ->
            arr.put(JSONObject().apply {
                put("url", book.url)
                put("title", book.title)
                put("lastChapter", book.lastChapter)
                put("lastReadTime", book.lastReadTime)
            })
        }
        prefs.edit().putString("bookshelf", arr.toString()).apply()
    }

    // ── 内部解析 ─────────────────────────────────────────────────────

    private fun parseChapters(json: String?): List<Chapter> {
        if (json.isNullOrEmpty() || json == "null") return emptyList()
        return try {
            val cleaned = json.removeSurrounding("\"").replace("\\\"", "\"").replace("\\/", "/")
            val arr = org.json.JSONArray(cleaned)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                Chapter(
                    title = obj.getString("title"),
                    url = obj.getString("url")
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseChapterContent(json: String?): ChapterContent {
        if (json.isNullOrEmpty() || json == "null") {
            return ChapterContent("未知章节", emptyList(), "", "")
        }
        return try {
            val cleaned = json.removeSurrounding("\"").replace("\\\"", "\"").replace("\\/", "/")
            val obj = JSONObject(cleaned)
            val paras = obj.getJSONArray("paragraphs")
            ChapterContent(
                title = obj.optString("title", "未知章节"),
                paragraphs = (0 until paras.length()).map { paras.getString(it) },
                nextUrl = obj.optString("nextUrl", ""),
                prevUrl = obj.optString("prevUrl", "")
            )
        } catch (_: Exception) {
            ChapterContent("解析失败", listOf("无法解析本章内容，请尝试刷新。"), "", "")
        }
    }

    private suspend fun WebView.evaluateJavascriptAsync(script: String): String? {
        return withContext(Dispatchers.Main) {
            kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
                evaluateJavascript(script) { result ->
                    continuation.resume(result) {}
                }
            }
        }
    }

    data class ChapterContent(
        val title: String,
        val paragraphs: List<String>,
        val nextUrl: String,
        val prevUrl: String
    )

    companion object {
        // 预设主题
        val THEMES = mapOf(
            "护眼淡黄" to ("#F5F0E8" to "#333333"),
            "纯白" to ("#FFFFFF" to "#333333"),
            "羊皮纸" to ("#F5E6D0" to "#5B4636"),
            "淡绿" to ("#E8F5E9" to "#2E7D32"),
            "深灰" to ("#333333" to "#CCCCCC"),
            "纯黑" to ("#000000" to "#AAAAAA"),
            "暮光紫" to ("#2D1B4E" to "#D4C5F9"),
            "暗夜蓝" to ("#1A237E" to "#90CAF9")
        )
    }
}
