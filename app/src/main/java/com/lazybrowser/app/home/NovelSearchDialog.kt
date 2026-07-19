package com.lazybrowser.app.home

import android.app.AlertDialog
import android.content.Context

/**
 * 小说一键搜 — 输入书名，同时搜多个小说站
 * 懒人不用记哪个站有哪本书
 */
object NovelSearchDialog {

    data class NovelSite(
        val name: String,
        val icon: String,
        val searchUrl: String, // {query} 会被替换为搜索词
        val description: String
    )

    private val sites = listOf(
        NovelSite(
            "起点中文网", "📖",
            "https://m.qidian.com/search?kw={query}",
            "正版，更新快，需付费"
        ),
        NovelSite(
            "笔趣阁", "📕",
            "https://m.biquge.com/search.php?keyword={query}",
            "免费，资源多"
        ),
        NovelSite(
            "番茄小说", "🍅",
            "https://fanqienovel.com/search?keyword={query}",
            "免费，正版授权"
        ),
        NovelSite(
            "搜狗小说", "🔎",
            "https://www.sogou.com/web?query={query}+小说",
            "聚合搜索"
        ),
        NovelSite(
            "百度小说", "🔍",
            "https://m.baidu.com/s?word={query}+小说+在线阅读",
            "百度搜索"
        ),
        NovelSite(
            "晋江文学", "✏️",
            "https://m.jjwxc.net/search/?kw={query}",
            "女频为主"
        ),
        NovelSite(
            "纵横中文", "📗",
            "https://m.zongheng.com/s?keyword={query}",
            "免费资源多"
        ),
        NovelSite(
            "17K小说", "📘",
            "https://search.17k.com/search.xhtml?c.st=0&c.q={query}",
            "老牌站点"
        )
    )

    fun show(context: Context, query: String, onNavigate: (String) -> Unit) {
        val items = sites.map { site ->
            "${site.icon} ${site.name}\n${site.description}"
        }.toTypedArray()

        AlertDialog.Builder(context)
            .setTitle("🔍 搜索「$query」")
            .setItems(items) { _, which ->
                val site = sites[which]
                val url = site.searchUrl.replace("{query}", java.net.URLEncoder.encode(query, "UTF-8"))
                onNavigate(url)
            }
            .setNegativeButton("取消", null)
            .show()
    }
}
