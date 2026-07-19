package com.lazybrowser.app.tab

import android.content.Context
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.FrameLayout

/**
 * 标签页管理器 - 管理多个 WebView 实例
 */
class TabManager(
    private val context: Context,
    private val container: FrameLayout
) {
    data class Tab(
        val id: String,
        val webView: WebView,
        var title: String = "",
        var url: String = ""
    )

    private val tabs = mutableListOf<Tab>()
    private var currentTab: Tab? = null

    fun openInNewTab(url: String) {
        val webView = createWebView()
        val tab = Tab(
            id = "tab_${System.currentTimeMillis()}",
            webView = webView,
            url = url
        )
        tabs.add(tab)
        switchToTab(tab)
        webView.loadUrl(url)
    }

    fun switchToTab(tab: Tab) {
        // Remove current WebView from container
        currentTab?.webView?.let {
            container.removeView(it)
        }

        // Add new WebView
        currentTab = tab
        container.addView(tab.webView, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
    }

    fun closeTab(tab: Tab) {
        tabs.remove(tab)
        tab.webView.destroy()
        if (currentTab == tab) {
            currentTab = tabs.lastOrNull()
            currentTab?.webView?.let {
                container.addView(it, FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                ))
            }
        }
    }

    fun showTabSwitcher() {
        // TODO: Show tab switcher UI (grid view of tabs)
    }

    fun getCurrentWebView(): WebView? = currentTab?.webView

    fun getTabCount(): Int = tabs.size

    private fun createWebView(): WebView {
        return WebView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            // WebView settings will be configured by MainActivity
        }
    }
}
