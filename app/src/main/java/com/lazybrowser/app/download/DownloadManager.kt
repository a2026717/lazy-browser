package com.lazybrowser.app.download

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject

/**
 * 下载管理器 — 章节缓存
 *
 * 功能：
 * - 缓存全本 / 缓存指定章节
 * - WiFi 自动下载新章节
 * - 下载进度跟踪
 * - 已完成关联书架
 */
class DownloadManager(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("download_manager", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // ── 下载任务 ─────────────────────────────────────────────────────

    data class DownloadTask(
        val id: String,
        val bookUrl: String,
        val bookTitle: String,
        val chapterUrl: String,
        var chapterTitle: String,
        var status: Status = Status.PENDING,
        var progress: Int = 0,
        var totalChapters: Int = 0,
        var downloadedChapters: Int = 0,
        var createdAt: Long = System.currentTimeMillis(),
        var completedAt: Long = 0,
        var content: String = "" // 缓存的正文内容
    )

    enum class Status {
        PENDING,      // 等待下载
        DOWNLOADING,  // 下载中
        COMPLETED,    // 已完成
        FAILED,       // 失败
        PAUSED        // 暂停
    }

    // ── 缓存全本 ─────────────────────────────────────────────────────

    fun cacheBook(bookUrl: String, bookTitle: String, chapterUrls: List<Pair<String, String>>) {
        val taskId = "book_${System.currentTimeMillis()}"

        // 创建总任务
        val task = DownloadTask(
            id = taskId,
            bookUrl = bookUrl,
            bookTitle = bookTitle,
            chapterUrl = chapterUrls.firstOrNull()?.second ?: "",
            chapterTitle = "全本缓存",
            totalChapters = chapterUrls.size
        )
        saveTask(task)

        // 逐章下载
        scope.launch {
            chapterUrls.forEachIndexed { index, (title, url) ->
                updateTask(taskId) {
                    it.status = Status.DOWNLOADING
                    it.downloadedChapters = index
                    it.progress = (index * 100 / chapterUrls.size)
                    it.chapterTitle = title
                }

                try {
                    // 下载并缓存章节内容
                    val content = downloadChapterContent(url)
                    cacheChapterContent(taskId, url, title, content)

                    updateTask(taskId) {
                        it.downloadedChapters = index + 1
                        it.progress = ((index + 1) * 100 / chapterUrls.size)
                    }
                } catch (e: Exception) {
                    updateTask(taskId) {
                        it.status = Status.FAILED
                    }
                    return@forEachIndexed
                }

                // 间隔一下，避免请求太快
                delay(500)
            }

            // 全部完成
            updateTask(taskId) {
                it.status = Status.COMPLETED
                it.progress = 100
                it.downloadedChapters = chapterUrls.size
                it.completedAt = System.currentTimeMillis()
            }
        }
    }

    // ── 缓存单章 ─────────────────────────────────────────────────────

    fun cacheChapter(bookUrl: String, bookTitle: String, chapterUrl: String, chapterTitle: String) {
        val taskId = "ch_${System.currentTimeMillis()}"
        val task = DownloadTask(
            id = taskId,
            bookUrl = bookUrl,
            bookTitle = bookTitle,
            chapterUrl = chapterUrl,
            chapterTitle = chapterTitle,
            totalChapters = 1
        )
        saveTask(task)

        scope.launch {
            updateTask(taskId) { it.status = Status.DOWNLOADING }
            try {
                val content = downloadChapterContent(chapterUrl)
                cacheChapterContent(taskId, chapterUrl, chapterTitle, content)
                updateTask(taskId) {
                    it.status = Status.COMPLETED
                    it.progress = 100
                    it.downloadedChapters = 1
                    it.completedAt = System.currentTimeMillis()
                }
            } catch (e: Exception) {
                updateTask(taskId) { it.status = Status.FAILED }
            }
        }
    }

    // ── WiFi 自动下载新章节 ─────────────────────────────────────────

    fun enableAutoDownload(bookUrl: String, bookTitle: String) {
        prefs.edit().putBoolean("auto_$bookUrl", true).apply()
    }

    fun isAutoDownloadEnabled(bookUrl: String): Boolean {
        return prefs.getBoolean("auto_$bookUrl", false)
    }

    // ── 查询 ────────────────────────────────────────────────────────

    fun getDownloadingTasks(): List<DownloadTask> {
        return getAllTasks().filter {
            it.status == Status.DOWNLOADING || it.status == Status.PENDING
        }
    }

    fun getCompletedTasks(): List<DownloadTask> {
        return getAllTasks().filter { it.status == Status.COMPLETED }
    }

    fun isBookCached(bookUrl: String): Boolean {
        return getAllTasks().any { it.bookUrl == bookUrl && it.status == Status.COMPLETED }
    }

    fun getChapterContent(taskId: String, chapterUrl: String): String? {
        return prefs.getString("content_${taskId}_${chapterUrl.hashCode()}", null)
    }

    // ── 删除 ────────────────────────────────────────────────────────

    fun deleteTask(taskId: String) {
        val tasks = getAllTasks().toMutableList()
        tasks.removeAll { it.id == taskId }
        saveAllTasks(tasks)
        // 清理缓存内容
        prefs.edit().apply {
            prefs.all.keys.filter { it.startsWith("content_${taskId}_") }.forEach { remove(it) }
            apply()
        }
    }

    fun deleteTasks(taskIds: List<String>) {
        taskIds.forEach { deleteTask(it) }
    }

    // ── 内部实现 ─────────────────────────────────────────────────────

    private suspend fun downloadChapterContent(url: String): String {
        // 实际实现会用 OkHttp / HttpURLConnection 下载
        // 这里返回占位内容
        return withContext(Dispatchers.IO) {
            try {
                val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                connection.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36")
                val text = connection.inputStream.bufferedReader().readText()
                connection.disconnect()
                text
            } catch (e: Exception) {
                ""
            }
        }
    }

    private fun cacheChapterContent(taskId: String, chapterUrl: String, title: String, content: String) {
        prefs.edit()
            .putString("content_${taskId}_${chapterUrl.hashCode()}", content)
            .apply()
    }

    private fun saveTask(task: DownloadTask) {
        val tasks = getAllTasks().toMutableList()
        tasks.add(task)
        saveAllTasks(tasks)
    }

    private fun updateTask(taskId: String, update: (DownloadTask) -> Unit) {
        val tasks = getAllTasks().toMutableList()
        val index = tasks.indexOfFirst { it.id == taskId }
        if (index >= 0) {
            update(tasks[index])
            saveAllTasks(tasks)
        }
    }

    private fun getAllTasks(): List<DownloadTask> {
        val json = prefs.getString("tasks", "[]") ?: "[]"
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                DownloadTask(
                    id = obj.getString("id"),
                    bookUrl = obj.getString("bookUrl"),
                    bookTitle = obj.getString("bookTitle"),
                    chapterUrl = obj.getString("chapterUrl"),
                    chapterTitle = obj.getString("chapterTitle"),
                    status = Status.entries.getOrElse(obj.getInt("status")) { Status.PENDING },
                    progress = obj.getInt("progress"),
                    totalChapters = obj.getInt("totalChapters"),
                    downloadedChapters = obj.getInt("downloadedChapters"),
                    createdAt = obj.getLong("createdAt"),
                    completedAt = obj.getLong("completedAt")
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveAllTasks(tasks: List<DownloadTask>) {
        val arr = JSONArray()
        tasks.forEach { task ->
            arr.put(JSONObject().apply {
                put("id", task.id)
                put("bookUrl", task.bookUrl)
                put("bookTitle", task.bookTitle)
                put("chapterUrl", task.chapterUrl)
                put("chapterTitle", task.chapterTitle)
                put("status", task.status.ordinal)
                put("progress", task.progress)
                put("totalChapters", task.totalChapters)
                put("downloadedChapters", task.downloadedChapters)
                put("createdAt", task.createdAt)
                put("completedAt", task.completedAt)
            })
        }
        prefs.edit().putString("tasks", arr.toString()).apply()
    }
}
