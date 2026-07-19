package com.lazybrowser.app.reader

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.lazybrowser.app.R

/**
 * 书架视图 — 首页展示，一目了然
 */
class BookshelfView(
    private val context: Context,
    private val reader: NovelReader,
    private val onBookClick: (NovelReader.Book) -> Unit,
    private val onBookLongClick: (NovelReader.Book) -> Unit
) {

    fun createView(): View {
        val books = reader.getBooks()

        return LayoutInflater.from(context).inflate(R.layout.view_bookshelf, null).apply {
            val recyclerView = findViewById<RecyclerView>(R.id.bookshelfRecycler)
            val emptyView = findViewById<View>(R.id.bookshelfEmpty)
            val continueBtn = findViewById<View>(R.id.btnContinueReading)
            val continueTitle = findViewById<TextView>(R.id.tvContinueTitle)
            val continueChapter = findViewById<TextView>(R.id.tvContinueChapter)

            if (books.isEmpty()) {
                recyclerView.visibility = View.GONE
                emptyView.visibility = View.VISIBLE
                continueBtn.visibility = View.GONE
            } else {
                recyclerView.visibility = View.VISIBLE
                emptyView.visibility = View.GONE

                // 继续阅读按钮（最近一本）
                val latest = books.first()
                continueBtn.visibility = View.VISIBLE
                continueTitle.text = latest.title
                continueChapter.text = latest.lastChapter.ifEmpty { "点击继续阅读" }
                continueBtn.setOnClickListener { onBookClick(latest) }

                // 书架列表
                recyclerView.layoutManager = LinearLayoutManager(context)
                recyclerView.adapter = BookAdapter(books.drop(1)) // 去掉第一本（已在继续阅读区）
            }
        }
    }

    inner class BookAdapter(private val books: List<NovelReader.Book>) :
        RecyclerView.Adapter<BookAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val title: TextView = view.findViewById(R.id.bookTitle)
            val chapter: TextView = view.findViewById(R.id.bookChapter)
            val time: TextView = view.findViewById(R.id.bookTime)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(context).inflate(R.layout.item_book, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val book = books[position]
            holder.title.text = book.title
            holder.chapter.text = book.lastChapter.ifEmpty { "暂无记录" }
            holder.time.text = formatTime(book.lastReadTime)
            holder.itemView.setOnClickListener { onBookClick(book) }
            holder.itemView.setOnLongClickListener { onBookLongClick(book); true }
        }

        override fun getItemCount() = books.size

        private fun formatTime(ts: Long): String {
            val now = System.currentTimeMillis()
            val diff = now - ts
            return when {
                diff < 60_000 -> "刚刚"
                diff < 3600_000 -> "${diff / 60_000}分钟前"
                diff < 86400_000 -> "${diff / 3600_000}小时前"
                diff < 172800_000 -> "昨天"
                else -> {
                    val sdf = java.text.SimpleDateFormat("MM/dd", java.util.Locale.getDefault())
                    sdf.format(java.util.Date(ts))
                }
            }
        }
    }
}
