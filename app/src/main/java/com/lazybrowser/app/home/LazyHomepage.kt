package com.lazybrowser.app.home

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.lazybrowser.app.R
import com.lazybrowser.app.reader.NovelReader
import com.lazybrowser.app.reader.NovelReaderActivity

/**
 * 懒人首页
 *
 * 设计原则：
 * - 能不输入就不输入
 * - 能不翻页就不翻页
 * - 能一步到的绝不两步
 */
class LazyHomepage(
    private val context: Context,
    private val novelReader: NovelReader,
    private val onSiteClick: (String) -> Unit,
    private val onSearch: (String) -> Unit,
    private val onBookClick: (NovelReader.Book) -> Unit
) {

    fun createView(): View {
        val view = LayoutInflater.from(context).inflate(R.layout.home_lazy, null)

        setupSearch(view)
        setupQuickSites(view)
        setupBookshelf(view)
        setupDiscover(view)

        return view
    }

    // ── 搜索：一个输入框，啥都能搜 ──────────────────────────────────

    private fun setupSearch(view: View) {
        val searchInput = view.findViewById<EditText>(R.id.homeSearchInput)
        val btnSearch = view.findViewById<View>(R.id.btnSearch)
        val btnNovelSearch = view.findViewById<View>(R.id.btnNovelSearch)

        // 普通搜索
        btnSearch.setOnClickListener {
            val query = searchInput.text.toString().trim()
            if (query.isNotEmpty()) onSearch(query)
        }

        // 小说搜索 → 一键搜多个小说站
        btnNovelSearch.setOnClickListener {
            val query = searchInput.text.toString().trim()
            if (query.isNotEmpty()) {
                NovelSearchDialog.show(context, query, onSiteClick)
            }
        }

        // 回车直接搜
        searchInput.setOnEditorActionListener { _, _, _ ->
            val query = searchInput.text.toString().trim()
            if (query.isNotEmpty()) onSearch(query)
            true
        }
    }

    // ── 快捷站点：一点就开 ──────────────────────────────────────────

    private fun setupQuickSites(view: View) {
        val recyclerView = view.findViewById<RecyclerView>(R.id.quickSitesRecycler)
        recyclerView.layoutManager = GridLayoutManager(context, 4)

        val sites = listOf(
            QuickSite("百度", "https://m.baidu.com", "🔍"),
            QuickSite("B站", "https://m.bilibili.com", "📺"),
            QuickSite("知乎", "https://www.zhihu.com", "💡"),
            QuickSite("微博", "https://m.weibo.cn", "📰"),
            QuickSite("抖音", "https://www.douyin.com", "🎵"),
            QuickSite("淘宝", "https://m.taobao.com", "🛒"),
            QuickSite("豆瓣", "https://m.douban.com", "🎬"),
            QuickSite("GitHub", "https://github.com", "💻"),
            QuickSite("起点", "https://m.qidian.com", "📖"),
            QuickSite("笔趣阁", "https://m.biquge.com", "📕"),
            QuickSite("番茄", "https://fanqienovel.com", "🍅"),
            QuickSite("晋江", "https://m.jjwxc.net", "✏️")
        )

        recyclerView.adapter = QuickSiteAdapter(sites) { site ->
            onSiteClick(site.url)
        }
    }

    // ── 书架：有书就显示，没有就隐藏 ────────────────────────────────

    private fun setupBookshelf(view: View) {
        val container = view.findViewById<View>(R.id.bookshelfSection)
        val books = novelReader.getBooks()

        if (books.isEmpty()) {
            container.visibility = View.GONE
            return
        }

        container.visibility = View.VISIBLE

        // 继续阅读
        val latest = books.first()
        view.findViewById<TextView>(R.id.continueTitle).text = latest.title
        view.findViewById<TextView>(R.id.continueChapter).text =
            latest.lastChapter.ifEmpty { "点击继续" }
        view.findViewById<View>(R.id.btnContinue).setOnClickListener {
            onBookClick(latest)
        }

        // 书架列表（横向滚动，不用翻页）
        val shelfRecycler = view.findViewById<RecyclerView>(R.id.bookshelfRecycler)
        shelfRecycler.layoutManager =
            LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        shelfRecycler.adapter = BookCardAdapter(books) { onBookClick(it) }
    }

    // ── 发现：热门小说推荐 ──────────────────────────────────────────

    private fun setupDiscover(view: View) {
        val recyclerView = view.findViewById<RecyclerView>(R.id.discoverRecycler)
        recyclerView.layoutManager = LinearLayoutManager(context)

        val categories = listOf(
            DiscoverCategory("玄幻", listOf("斗破苍穹", "完美世界", "遮天", "凡人修仙传", "斗罗大陆")),
            DiscoverCategory("都市", listOf("全职高手", "大奉打更人", "夜的命名术", "诡秘之主", "深空彼岸")),
            DiscoverCategory("历史", listOf("赘婿", "庆余年", "雪中悍刀行", "剑来", "大道朝天")),
            DiscoverCategory("科幻", listOf("三体", "流浪地球", "银河帝国", "沙丘", "基地"))
        )

        recyclerView.adapter = DiscoverAdapter(categories) { query ->
            onSearch(query)
        }
    }

    // ── 数据类 ──────────────────────────────────────────────────────

    data class QuickSite(val name: String, val url: String, val icon: String)

    data class DiscoverCategory(val name: String, val books: List<String>)

    // ── 适配器 ──────────────────────────────────────────────────────

    inner class QuickSiteAdapter(
        private val sites: List<QuickSite>,
        private val onClick: (QuickSite) -> Unit
    ) : RecyclerView.Adapter<QuickSiteAdapter.VH>() {

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val icon: TextView = view.findViewById(R.id.siteIcon)
            val name: TextView = view.findViewById(R.id.siteName)
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(context)
                .inflate(R.layout.item_quick_site, parent, false)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val site = sites[position]
            holder.icon.text = site.icon
            holder.name.text = site.name
            holder.itemView.setOnClickListener { onClick(site) }
        }

        override fun getItemCount() = sites.size
    }

    inner class BookCardAdapter(
        private val books: List<NovelReader.Book>,
        private val onClick: (NovelReader.Book) -> Unit
    ) : RecyclerView.Adapter<BookCardAdapter.VH>() {

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val title: TextView = view.findViewById(R.id.cardTitle)
            val chapter: TextView = view.findViewById(R.id.cardChapter)
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(context)
                .inflate(R.layout.item_book_card, parent, false)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val book = books[position]
            holder.title.text = book.title
            holder.chapter.text = book.lastChapter.ifEmpty { "继续阅读" }
            holder.itemView.setOnClickListener { onClick(book) }
        }

        override fun getItemCount() = books.size
    }

    inner class DiscoverAdapter(
        private val categories: List<DiscoverCategory>,
        private val onBookClick: (String) -> Unit
    ) : RecyclerView.Adapter<DiscoverAdapter.VH>() {

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val category: TextView = view.findViewById(R.id.categoryName)
            val books: LinearLayout = view.findViewById(R.id.bookChips)
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(context)
                .inflate(R.layout.item_discover, parent, false)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val cat = categories[position]
            holder.category.text = cat.name
            holder.books.removeAllViews()
            cat.books.forEach { bookName ->
                val chip = TextView(context).apply {
                    text = bookName
                    textSize = 13f
                    setPadding(28, 14, 28, 14)
                    setBackgroundResource(R.drawable.bg_chip)
                }
                chip.setOnClickListener { onBookClick(bookName) }
                holder.books.addView(chip)
            }
        }

        override fun getItemCount() = categories.size
    }
}
