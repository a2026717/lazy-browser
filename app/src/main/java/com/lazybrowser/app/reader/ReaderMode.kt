package com.lazybrowser.app.reader

import android.webkit.WebView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 阅读模式 - 提取正文内容，去除广告和杂乱元素
 */
object ReaderMode {

    private const val READER_CSS = """
        <style>
            body {
                font-family: 'Noto Serif SC', Georgia, serif;
                font-size: 18px;
                line-height: 1.8;
                max-width: 720px;
                margin: 0 auto;
                padding: 20px;
                color: #333;
                background: #fafafa;
            }
            h1 {
                font-size: 28px;
                margin-bottom: 16px;
                color: #111;
            }
            img {
                max-width: 100%;
                height: auto;
                border-radius: 8px;
                margin: 16px 0;
            }
            p {
                margin-bottom: 16px;
            }
            blockquote {
                border-left: 3px solid #ddd;
                padding-left: 16px;
                color: #666;
                margin: 16px 0;
            }
            pre, code {
                background: #f0f0f0;
                border-radius: 4px;
                padding: 2px 6px;
                font-size: 14px;
            }
            a { color: #1a73e8; }
        </style>
    """

    private const val EXTRACT_JS = """
        (function() {
            // Remove unwanted elements
            var selectors = ['nav', 'header', 'footer', 'aside', '.ad', '.ads', '.sidebar',
                '.comment', '.comments', '.social', '.share', '.related', '.recommendation',
                'script', 'style', 'iframe', 'noscript'];
            selectors.forEach(function(sel) {
                document.querySelectorAll(sel).forEach(function(el) { el.remove(); });
            });

            // Try to find main content
            var content = document.querySelector('article') ||
                          document.querySelector('[role="main"]') ||
                          document.querySelector('.content') ||
                          document.querySelector('.post') ||
                          document.querySelector('.entry') ||
                          document.querySelector('main') ||
                          document.body;

            var title = document.title || '';
            var text = content ? content.innerHTML : document.body.innerHTML;

            return JSON.stringify({ title: title, content: text });
        })()
    """

    suspend fun extract(webView: WebView): String = withContext(Dispatchers.Main) {
        val result = webView.evaluateJavascriptAsync(EXTRACT_JS)
        val json = org.json.JSONObject(result ?: "{}")
        val title = json.optString("title", "")
        val content = json.optString("content", "")

        """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>$title</title>
            $READER_CSS
        </head>
        <body>
            <h1>$title</h1>
            $content
        </body>
        </html>
        """
    }

    private suspend fun WebView.evaluateJavascriptAsync(script: String): String? {
        return withContext(Dispatchers.Main) {
            kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
                evaluateJavascript(script) { result ->
                    continuation.resume(result) {}
                }
            }
        }
    }
}
