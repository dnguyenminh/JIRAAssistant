package com.assistant.frontend.pages.ticket

import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLElement
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/** Matches raw <mxGraphModel> blocks not already inside code fences. */
private val MX_GRAPH_RAW = Regex(
    """<mxGraphModel>[\s\S]*?</mxGraphModel>"""
)

/**
 * Lazy-loads marked.js from CDN and renders Markdown → sanitized HTML.
 * Fallback to raw text wrapped in <pre> on CDN failure.
 * Requirements: 7.1 (Markdown rendering in Document Preview)
 */
internal object MarkdownRenderer {

    private var markedLoaded = false
    private var loadFailed = false

    private const val MARKED_CDN =
        "https://cdn.jsdelivr.net/npm/marked/marked.min.js"

    /**
     * Render a Markdown string to sanitized HTML.
     * Returns raw text in <pre> if CDN fails.
     */
    suspend fun render(markdown: String): String {
        if (markdown.isBlank()) return ""
        if (loadFailed) return wrapRawText(markdown)

        if (!markedLoaded) {
            val loaded = loadMarkedJs()
            if (!loaded) return wrapRawText(markdown)
        }

        return try {
            val prepared = wrapRawXmlInCodeFences(markdown)
            val html = parseMarkdown(prepared)
            sanitizeHtml(html)
        } catch (e: dynamic) {
            console.log("[MarkdownRenderer] parse error: $e")
            wrapRawText(markdown)
        }
    }

    /**
     * Wrap raw <mxGraphModel>...</mxGraphModel> blocks in ```xml
     * code fences so marked.js preserves them as <code> elements
     * instead of stripping them as HTML tags.
     */
    private fun wrapRawXmlInCodeFences(md: String): String {
        return md.replace(MX_GRAPH_RAW) { match ->
            "\n```xml\n${match.value.trim()}\n```\n"
        }
    }

    private suspend fun loadMarkedJs(): Boolean {
        if (markedLoaded) return true
        if (js("typeof marked !== 'undefined'") as Boolean) {
            markedLoaded = true
            configureMarked()
            return true
        }
        return suspendCoroutine { cont ->
            val script = document.createElement("script") as HTMLElement
            script.setAttribute("src", MARKED_CDN)
            script.addEventListener("load", {
                markedLoaded = true
                configureMarked()
                cont.resume(true)
            })
            script.addEventListener("error", {
                console.log("[MarkdownRenderer] Failed to load marked.js CDN")
                loadFailed = true
                cont.resume(false)
            })
            document.head?.appendChild(script)
        }
    }

    @Suppress("unused")
    private fun configureMarked() {
        js("""(function(){
            if(typeof marked==='undefined')return;
            marked.setOptions({
                breaks:true,
                gfm:true
            });
        })()""")
    }

    private fun parseMarkdown(markdown: String): String {
        val result = js("marked.parse(markdown)")
        return result as? String ?: ""
    }

    /**
     * Basic XSS sanitization: strip script tags, event handlers,
     * and javascript: protocol from rendered HTML.
     */
    private fun sanitizeHtml(html: String): String {
        var clean = html
        // Remove <script> blocks
        clean = clean.replace(
            Regex("<script[^>]*>[\\s\\S]*?</script>", RegexOption.IGNORE_CASE),
            ""
        )
        // Remove on* event attributes
        clean = clean.replace(
            Regex("\\s+on\\w+\\s*=\\s*(['\"])[^'\"]*\\1", RegexOption.IGNORE_CASE),
            ""
        )
        // Remove javascript: protocol in hrefs/src
        clean = clean.replace(
            Regex("(href|src)\\s*=\\s*(['\"])\\s*javascript:", RegexOption.IGNORE_CASE),
            "$1=$2"
        )
        return clean
    }

    private fun wrapRawText(markdown: String): String {
        val escaped = escapeHtml(markdown)
        return "<pre style=\"white-space:pre-wrap;word-break:break-word;" +
            "font-size:13px;line-height:1.6;\">$escaped</pre>"
    }

    private fun escapeHtml(text: String): String {
        val div = document.createElement("div")
        div.appendChild(document.createTextNode(text))
        return div.innerHTML
    }
}
