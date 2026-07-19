package com.lazybrowser.app.settings

import android.content.Context
import android.content.SharedPreferences
import com.lazybrowser.app.R

data class SearchEngine(
    val name: String,
    val url: String,
    val iconRes: Int
)

class SettingsManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("lazy_browser_prefs", Context.MODE_PRIVATE)

    val searchEngines = listOf(
        SearchEngine("Google", "https://www.google.com/search?q=", R.drawable.ic_google),
        SearchEngine("Bing", "https://www.bing.com/search?q=", R.drawable.ic_bing),
        SearchEngine("百度", "https://www.baidu.com/s?wd=", R.drawable.ic_baidu),
        SearchEngine("DuckDuckGo", "https://duckduckgo.com/?q=", R.drawable.ic_ddg),
        SearchEngine("搜狗", "https://www.sogou.com/web?query=", R.drawable.ic_sogou)
    )

    var searchEngine: SearchEngine
        get() = searchEngines.getOrElse(prefs.getInt("search_engine", 0)) { searchEngines[0] }
        set(value) { prefs.edit().putInt("search_engine", searchEngines.indexOf(value)).apply() }

    var homepage: String
        get() = prefs.getString("homepage", "https://www.google.com") ?: "https://www.google.com"
        set(value) = prefs.edit().putString("homepage", value).apply()

    var darkMode: Boolean
        get() = prefs.getBoolean("dark_mode", false)
        set(value) = prefs.edit().putBoolean("dark_mode", value).apply()

    var javaScriptEnabled: Boolean
        get() = prefs.getBoolean("js_enabled", true)
        set(value) = prefs.edit().putBoolean("js_enabled", value).apply()

    var offlineMode: Boolean
        get() = prefs.getBoolean("offline_mode", false)
        set(value) = prefs.edit().putBoolean("offline_mode", value).apply()

    var userAgent: String
        get() = prefs.getString("user_agent", DEFAULT_UA) ?: DEFAULT_UA
        set(value) = prefs.edit().putString("user_agent", value).apply()

    var adBlockEnabled: Boolean
        get() = prefs.getBoolean("ad_block", true)
        set(value) = prefs.edit().putBoolean("ad_block", value).apply()

    fun setSearchEngine(index: Int) {
        if (index in searchEngines.indices) {
            prefs.edit().putInt("search_engine", index).apply()
        }
    }

    companion object {
        const val DEFAULT_UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        const val DESKTOP_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

        val USER_AGENTS = mapOf(
            "默认" to DEFAULT_UA,
            "桌面模式" to DESKTOP_UA,
            "iPhone" to "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1",
            "iPad" to "Mozilla/5.0 (iPad; CPU OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1"
        )
    }
}
