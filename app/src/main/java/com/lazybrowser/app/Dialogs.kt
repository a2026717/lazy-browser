package com.lazybrowser.app

import android.app.AlertDialog
import android.content.Context
import com.lazybrowser.app.data.Bookmark
import com.lazybrowser.app.data.HistoryEntry
import java.text.SimpleDateFormat
import java.util.*

object BookmarkDialog {
    fun show(
        context: Context,
        bookmarks: List<Bookmark>,
        onOpen: (Bookmark) -> Unit,
        onDelete: (Bookmark) -> Unit
    ) {
        if (bookmarks.isEmpty()) {
            AlertDialog.Builder(context)
                .setTitle("书签")
                .setMessage("还没有书签哦~")
                .setPositiveButton("确定", null)
                .show()
            return
        }

        val items = bookmarks.map { "📌 ${it.title}\n${it.url}" }.toTypedArray()
        AlertDialog.Builder(context)
            .setTitle("书签 (${bookmarks.size})")
            .setItems(items) { _, which -> onOpen(bookmarks[which]) }
            .setNegativeButton("关闭", null)
            .show()
    }
}

object HistoryDialog {
    fun show(
        context: Context,
        entries: List<HistoryEntry>,
        onOpen: (HistoryEntry) -> Unit,
        onDelete: (HistoryEntry) -> Unit
    ) {
        if (entries.isEmpty()) {
            AlertDialog.Builder(context)
                .setTitle("历史记录")
                .setMessage("还没有浏览记录哦~")
                .setPositiveButton("确定", null)
                .show()
            return
        }

        val dateFormat = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
        val items = entries.map { entry ->
            val time = dateFormat.format(Date(entry.visitedAt))
            "🕐 $time  ${entry.title}\n${entry.url}"
        }.toTypedArray()

        AlertDialog.Builder(context)
            .setTitle("历史记录 (${entries.size})")
            .setItems(items) { _, which -> onOpen(entries[which]) }
            .setNeutralButton("清除全部") { _, _ ->
                AlertDialog.Builder(context)
                    .setTitle("确认清除")
                    .setMessage("确定要清除所有历史记录吗？")
                    .setPositiveButton("确定") { _, _ ->
                        entries.forEach { onDelete(it) }
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
            .setNegativeButton("关闭", null)
            .show()
    }
}
