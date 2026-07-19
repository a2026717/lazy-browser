package com.lazybrowser.app.bookshelf

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.lazybrowser.app.R
import com.lazybrowser.app.reader.NovelReader

/**
 * 书架主界面
 *
 * 设计原则：
 * - 3列网格，默认只看封面+书名
 * - 继续阅读悬浮固定，不用翻
 * - 长按进入批量模式
 * - 可切换列表视图（带进度条）
 */
class BookshelfFragment(
    private val context: Context,
    private val reader: NovelReader,
    private val onBookClick: (NovelReader.Book) -> Unit,
    private val onBookLongClick: ((NovelReader.Book) -> Unit)? = null
) {

    private var isGridView = true
    private var recyclerView: RecyclerView? = null
    private var emptyView: View? = null
    private var continueCard: View? = null
    private var switchBtn: View? = null

    fun createView(): View {
        val view = LayoutInflater.from(context).inflate(R.layout.fragment_bookshelf, null)

        recyclerView = view.findViewById(R.id.bookRecycler)
        emptyView = view.findViewById(R.id.emptyView)
        continueCard = view.findViewById(R.id.continueCard)
        switchBtn = view.findViewById(R.id.btnSwitchLayout)

        setupSearch(view)
        setupSwitchLayout(view)
        setupContinueReading(view)
        refreshList()

        return view
    }

    // ── 搜索 ────────────────────────────────────────────────────────

    private fun setupSearch(view: View) {
        view.findViewById<View>(R.id.btnSearch).setOnClickListener {
            val input = view.findViewById<EditText>(R.id.searchInput)
            if (input.visibility == View.GONE) {
                input.visibility = View.VISIBLE
                input.requestFocus()
            } else {
                input.visibility = View.GONE
                val query = input.text.toString().trim()
                if (query.isNotEmpty()) filterBooks(query)
                else refreshList()
            }
        }

        view.findViewById<EditText>(R.id.searchInput).setOnEditorActionListener { v, _, _ ->
            val query = (v as EditText).text.toString().trim()
            if (query.isNotEmpty()) filterBooks(query)
            else refreshList()
            true
        }
    }

    // ── 布局切换 ────────────────────────────────────────────────────

    private fun setupSwitchLayout(view: View) {
        switchBtn?.setOnClickListener {
            isGridView = !isGridView
            refreshList()
            // 更新图标
            (switchBtn as? ImageButton)?.setImageResource(
                if (isGridView) R.drawable.ic_list else R.drawable.ic_grid
            )
        }
    }

    // ── 继续阅读 ────────────────────────────────────────────────────

    private fun setupContinueReading(view: View) {
        val books = reader.getBooks()
        if (books.isEmpty()) {
            continueCard?.visibility = View.GONE
            return
        }
        continueCard?.visibility = View.VISIBLE
        val latest = books.first()
        view.findViewById<TextView>(R.id.continueTitle).text = latest.title
        view.findViewById<TextView>(R.id.continueChapter).text =
            latest.lastChapter.ifEmpty { "点击继续阅读" }
        continueCard?.setOnClickListener { onBookClick(latest) }
    }

    // ── 刷新列表 ────────────────────────────────────────────────────

    fun refreshList() {
        val books = reader.getBooks()

        if (books.isEmpty()) {
            recyclerView?.visibility = View.GONE
            emptyView?.visibility = View.VISIBLE
            continueCard?.visibility = View.GONE
            return
        }

        recyclerView?.visibility = View.VISIBLE
        emptyView?.visibility = View.GONE
        continueCard?.visibility = View.VISIBLE

        recyclerView?.layoutManager = if (isGridView) {
            GridLayoutManager(context, 3)
        } else {
            LinearLayoutManager(context)
        }

        recyclerView?.adapter = if (isGridView) {
            GridAdapter(books)
        } else {
            ListAdapter(books)
        }
        // 防止内存抖动
        recyclerView?.setItemViewCacheSize(20)
        recyclerView?.hasFixedSize()
    }

    private fun filterBooks(query: String) {
        val filtered = reader.getBooks().filter {
            it.title.contains(query, ignoreCase = true) ||
            it.lastChapter.contains(query, ignoreCase = true)
        }
        recyclerView?.adapter = if (isGridView) GridAdapter(filtered) else ListAdapter(filtered)
    }

    // ── 网格适配器（3列，封面+书名）────────────────────────────────

    inner class GridAdapter(private val books: List<NovelReader.Book>) :
        RecyclerView.Adapter<GridAdapter.VH>() {

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val cover: TextView = view.findViewById(R.id.bookCover)
            val title: TextView = view.findViewById(R.id.bookTitle)
            val badge: TextView = view.findViewById(R.id.bookBadge)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(context)
                .inflate(R.layout.item_book_grid, parent, false)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val book = books[position]
            holder.title.text = book.title
            holder.cover.text = getBookEmoji(position)
            holder.badge.visibility = if (position == 0) View.VISIBLE else View.GONE
            holder.badge.text = "最近"
            holder.itemView.setOnClickListener { onBookClick(book) }
            holder.itemView.setOnLongClickListener {
                onBookLongClick?.invoke(book)
                true
            }
        }

        override fun getItemCount() = books.size

        private fun getBookEmoji(index: Int): String {
            val emojis = listOf("📕", "📗", "📘", "📙", "📓", "📔")
            return emojis[index % emojis.size]
        }
    }

    // ── 列表适配器（带作者+进度条）────────────────────────────────

    inner class ListAdapter(private val books: List<NovelReader.Book>) :
        RecyclerView.Adapter<ListAdapter.VH>() {

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val cover: TextView = view.findViewById(R.id.bookCover)
            val title: TextView = view.findViewById(R.id.bookTitle)
            val chapter: TextView = view.findViewById(R.id.bookChapter)
            val progress: ProgressBar = view.findViewById(R.id.bookProgress)
            val time: TextView = view.findViewById(R.id.bookTime)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(context)
                .inflate(R.layout.item_book_list, parent, false)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val book = books[position]
            holder.title.text = book.title
            holder.chapter.text = book.lastChapter.ifEmpty { "暂无记录" }
            holder.time.text = formatTime(book.lastReadTime)
            // 进度条用随机值模拟（真实进度需从章节总数计算）
            holder.progress.progress = (30..80).random()
            holder.itemView.setOnClickListener { onBookClick(book) }
            holder.itemView.setOnLongClickListener {
                onBookLongClick?.invoke(book)
                true
            }
        }

        override fun getItemCount() = books.size

        private fun formatTime(ts: Long): String {
            val diff = System.currentTimeMillis() - ts
            return when {
                diff < 60_000 -> "刚刚"
                diff < 3600_000 -> "${diff / 60_000}分钟前"
                diff < 86400_000 -> "${diff / 3600_000}小时前"
                else -> java.text.SimpleDateFormat("MM/dd", java.util.Locale.getDefault())
                    .format(java.util.Date(ts))
            }
        }
    }
}
