package com.lazybrowser.app.download

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.lazybrowser.app.R

/**
 * 下载管理界面
 *
 * - 顶部：「已完成 / 正在下载」两个标签
 * - 下载中：进度条 + 剩余时间
 * - 已完成：关联书架，点击打开阅读
 * - 长按批量操作（删除/导出）
 */
class DownloadFragment(
    private val context: Context,
    private val downloadManager: DownloadManager,
    private val onTaskClick: (DownloadManager.DownloadTask) -> Unit
) {

    private var recyclerView: RecyclerView? = null
    private var tabDownloading: TextView? = null
    private var tabCompleted: TextView? = null
    private var emptyView: View? = null
    private var batchBar: View? = null
    private var isShowingCompleted = false
    private val selectedTasks = mutableSetOf<String>()
    private var isBatchMode = false

    fun createView(): View {
        val view = LayoutInflater.from(context).inflate(R.layout.fragment_download, null)

        recyclerView = view.findViewById(R.id.downloadRecycler)
        tabDownloading = view.findViewById(R.id.tabDownloading)
        tabCompleted = view.findViewById(R.id.tabCompleted)
        emptyView = view.findViewById(R.id.downloadEmpty)
        batchBar = view.findViewById(R.id.batchBar)

        setupTabs(view)
        setupBatchActions(view)
        showDownloading()

        return view
    }

    // ── 标签切换 ────────────────────────────────────────────────────

    private fun setupTabs(view: View) {
        tabDownloading?.setOnClickListener { showDownloading() }
        tabCompleted?.setOnClickListener { showCompleted() }
    }

    private fun showDownloading() {
        isShowingCompleted = false
        tabDownloading?.setTextColor(0xFF1A73E8.toInt())
        tabDownloading?.setTypeface(null, android.graphics.Typeface.BOLD)
        tabCompleted?.setTextColor(0xFF999999.toInt())
        tabCompleted?.setTypeface(null, android.graphics.Typeface.NORMAL)

        val tasks = downloadManager.getDownloadingTasks()
        recyclerView?.layoutManager = LinearLayoutManager(context)
        recyclerView?.adapter = DownloadingAdapter(tasks)
        emptyView?.visibility = if (tasks.isEmpty()) View.VISIBLE else View.GONE
        exitBatchMode()
    }

    private fun showCompleted() {
        isShowingCompleted = true
        tabCompleted?.setTextColor(0xFF1A73E8.toInt())
        tabCompleted?.setTypeface(null, android.graphics.Typeface.BOLD)
        tabDownloading?.setTextColor(0xFF999999.toInt())
        tabDownloading?.setTypeface(null, android.graphics.Typeface.NORMAL)

        val tasks = downloadManager.getCompletedTasks()
        recyclerView?.layoutManager = LinearLayoutManager(context)
        recyclerView?.adapter = CompletedAdapter(tasks)
        emptyView?.visibility = if (tasks.isEmpty()) View.VISIBLE else View.GONE
        exitBatchMode()
    }

    // ── 批量操作 ────────────────────────────────────────────────────

    private fun setupBatchActions(view: View) {
        view.findViewById<View>(R.id.btnBatchDelete).setOnClickListener {
            downloadManager.deleteTasks(selectedTasks.toList())
            exitBatchMode()
            refresh()
        }

        view.findViewById<View>(R.id.btnBatchExport).setOnClickListener {
            // TODO: 导出缓存内容
            Toast.makeText(context, "导出功能开发中", Toast.LENGTH_SHORT).show()
        }

        view.findViewById<View>(R.id.btnCancelBatch).setOnClickListener {
            exitBatchMode()
        }
    }

    private fun enterBatchMode(taskId: String) {
        isBatchMode = true
        selectedTasks.clear()
        selectedTasks.add(taskId)
        batchBar?.visibility = View.VISIBLE
        updateBatchCount()
    }

    private fun toggleBatchSelection(taskId: String) {
        if (selectedTasks.contains(taskId)) {
            selectedTasks.remove(taskId)
        } else {
            selectedTasks.add(taskId)
        }
        if (selectedTasks.isEmpty()) {
            exitBatchMode()
        } else {
            updateBatchCount()
        }
    }

    private fun exitBatchMode() {
        isBatchMode = false
        selectedTasks.clear()
        batchBar?.visibility = View.GONE
    }

    private fun updateBatchCount() {
        batchBar?.findViewById<TextView>(R.id.batchCount)?.text = "已选 ${selectedTasks.size} 项"
    }

    private fun refresh() {
        if (isShowingCompleted) showCompleted() else showDownloading()
    }

    // ── 下载中适配器 ────────────────────────────────────────────────

    inner class DownloadingAdapter(private val tasks: List<DownloadManager.DownloadTask>) :
        RecyclerView.Adapter<DownloadingAdapter.VH>() {

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val icon: TextView = view.findViewById(R.id.taskIcon)
            val title: TextView = view.findViewById(R.id.taskTitle)
            val chapter: TextView = view.findViewById(R.id.taskChapter)
            val progressBar: ProgressBar = view.findViewById(R.id.taskProgress)
            val progressText: TextView = view.findViewById(R.id.taskProgressText)
            val statusText: TextView = view.findViewById(R.id.taskStatus)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(context)
                .inflate(R.layout.item_download_task, parent, false)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val task = tasks[position]
            holder.icon.text = "⬇️"
            holder.title.text = task.bookTitle
            holder.chapter.text = task.chapterTitle
            holder.progressBar.max = 100
            holder.progressBar.progress = task.progress
            holder.progressText.text = "${task.progress}%"

            holder.statusText.text = when (task.status) {
                DownloadManager.Status.PENDING -> "等待中"
                DownloadManager.Status.DOWNLOADING -> "${task.downloadedChapters}/${task.totalChapters}章"
                DownloadManager.Status.FAILED -> "失败"
                else -> ""
            }

            holder.itemView.setOnClickListener {
                if (isBatchMode) {
                    toggleBatchSelection(task.id)
                    holder.itemView.alpha = if (selectedTasks.contains(task.id)) 0.5f else 1f
                }
            }

            holder.itemView.setOnLongClickListener {
                enterBatchMode(task.id)
                holder.itemView.alpha = 0.5f
                true
            }
        }

        override fun getItemCount() = tasks.size
    }

    // ── 已完成适配器 ────────────────────────────────────────────────

    inner class CompletedAdapter(private val tasks: List<DownloadManager.DownloadTask>) :
        RecyclerView.Adapter<CompletedAdapter.VH>() {

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val icon: TextView = view.findViewById(R.id.taskIcon)
            val title: TextView = view.findViewById(R.id.taskTitle)
            val chapter: TextView = view.findViewById(R.id.taskChapter)
            val progressBar: ProgressBar = view.findViewById(R.id.taskProgress)
            val progressText: TextView = view.findViewById(R.id.taskProgressText)
            val statusText: TextView = view.findViewById(R.id.taskStatus)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(context)
                .inflate(R.layout.item_download_task, parent, false)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val task = tasks[position]
            holder.icon.text = "✅"
            holder.title.text = task.bookTitle
            holder.chapter.text = "${task.totalChapters}章已缓存"
            holder.progressBar.progress = 100
            holder.progressText.text = "100%"
            holder.statusText.text = "离线可用"

            holder.itemView.setOnClickListener {
                if (isBatchMode) {
                    toggleBatchSelection(task.id)
                    holder.itemView.alpha = if (selectedTasks.contains(task.id)) 0.5f else 1f
                } else {
                    onTaskClick(task)
                }
            }

            holder.itemView.setOnLongClickListener {
                enterBatchMode(task.id)
                holder.itemView.alpha = 0.5f
                true
            }
        }

        override fun getItemCount() = tasks.size
    }
}
