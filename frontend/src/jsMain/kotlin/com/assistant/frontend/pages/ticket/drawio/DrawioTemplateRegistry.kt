package com.assistant.frontend.pages.ticket.drawio

import kotlinx.browser.window
import org.w3c.fetch.Response
import kotlin.js.Promise

/**
 * Loads and caches draw.io XML templates from resource files.
 * Fallback to "component" template for unknown names.
 * Requirements: 2.3, 2.4
 */
internal object DrawioTemplateRegistry {

    private val cache = mutableMapOf<String, String>()
    private val validNames = setOf("flow", "deployment", "component", "dependency", "bpmn")

    private const val FALLBACK = "component"
    private const val BASE_PATH = "/templates/drawio"

    /** Resolves a template name: returns the name if valid, otherwise FALLBACK. */
    internal fun resolveTemplateName(name: String): String =
        if (name in validNames) name else FALLBACK

    fun load(name: String, onReady: (String) -> Unit) {
        val resolved = resolveTemplateName(name)
        val cached = cache[resolved]
        if (cached != null) { onReady(cached); return }
        fetchTemplate(resolved, onReady)
    }

    private fun fetchTemplate(name: String, onReady: (String) -> Unit) {
        val url = "$BASE_PATH/$name.xml"
        window.fetch(url).then { resp: Response ->
            if (resp.ok) {
                resp.text().then { xml: String ->
                    cache[name] = xml
                    onReady(xml)
                    null
                }
            } else {
                console.log("[DrawioTemplateRegistry] Failed to load $url, using inline fallback")
                val fallback = inlineFallback()
                cache[name] = fallback
                onReady(fallback)
                null
            }
            null
        }.catch {
            console.log("[DrawioTemplateRegistry] Fetch error for $url")
            val fallback = inlineFallback()
            cache[name] = fallback
            onReady(fallback)
            null
        }
    }

    private fun inlineFallback(): String =
        "<mxGraphModel><root><mxCell id=\"0\"/>" +
            "<mxCell id=\"1\" parent=\"0\"/></root></mxGraphModel>"
}
