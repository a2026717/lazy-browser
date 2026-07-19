package com.lazybrowser.app.adblock

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * 轻量级广告拦截器
 * 基于域名黑名单 + EasyList 简化规则
 */
class AdBlocker(private val context: Context) {

    private val blockedDomains = mutableSetOf<String>()
    private val blockedPatterns = mutableListOf<Regex>()
    private var loaded = false

    fun loadRules() {
        if (loaded) return
        // Built-in domain blocklist (most common ad networks)
        blockedDomains.addAll(listOf(
            "doubleclick.net",
            "googleadservices.com",
            "googlesyndication.com",
            "google-analytics.com",
            "googletagmanager.com",
            "facebook.com/tr",
            "adservice.google.com",
            "pagead2.googlesyndication.com",
            "tpc.googlesyndication.com",
            "securepubads.g.doubleclick.net",
            "ad.doubleclick.net",
            "static.criteo.net",
            "bidder.criteo.com",
            "cas.criteo.com",
            "disqus.com/ads",
            "ads.yahoo.com",
            "adserver.yahoo.com",
            "media.net",
            "adnxs.com",
            "adsrvr.org",
            "amazon-adsystem.com",
            "serving-sys.com",
            "adcolony.com",
            "unity3d.com/ads",
            "chartboost.com",
            "mopub.com",
            "inmobi.com",
            "tapjoy.com",
            "vungle.com",
            "applovin.com",
            "ironsrc.com"
        ))

        // Common ad URL patterns
        val patterns = listOf(
            "/ads/",
            "/ad/",
            "/advert/",
            "/banner/",
            "/popup/",
            "/popunder/",
            "adsense",
            "adserver",
            "adchoices",
            "sponsored"
        )
        blockedPatterns.addAll(patterns.map { Regex(it, RegexOption.IGNORE_CASE) })

        // Load EasyList from assets (if available)
        try {
            val inputStream = context.assets.open("easylist.txt")
            val reader = BufferedReader(InputStreamReader(inputStream))
            reader.useLines { lines ->
                lines.forEach { line ->
                    if (line.isNotBlank() && !line.startsWith("!") && !line.startsWith("[")) {
                        if (line.startsWith("||")) {
                            // Domain rule: ||example.com^
                            val domain = line.removePrefix("||")
                                .removeSuffix("^")
                                .removeSuffix("*")
                                .trim()
                            if (domain.isNotEmpty()) blockedDomains.add(domain)
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // EasyList not bundled, use built-in list only
        }
        loaded = true
    }

    fun isAd(url: String): Boolean {
        if (!loaded) loadRules()
        val lowerUrl = url.lowercase()

        // Check domain blocklist
        for (domain in blockedDomains) {
            if (lowerUrl.contains(domain)) return true
        }

        // Check patterns
        for (pattern in blockedPatterns) {
            if (pattern.containsMatchIn(lowerUrl)) return true
        }

        return false
    }
}
