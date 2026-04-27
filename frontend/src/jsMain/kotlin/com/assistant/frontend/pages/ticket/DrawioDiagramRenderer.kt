package com.assistant.frontend.pages.ticket

import com.assistant.ai.deepanalysis.models.DiagramData
import com.assistant.frontend.pages.ticket.drawio.DrawioDownloadHelper
import com.assistant.frontend.pages.ticket.drawio.DrawioTemplateEngine
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLElement

/**
 * Renders draw.io diagrams by merging metadata into XML templates
 * and displaying via the draw.io viewer CDN.
 * Requirements: 5.1, 5.2, 5.3, 5.4, 5.5, 5.6, 5.7
 */
internal object DrawioDiagramRenderer {

    private var viewerLoaded = false
    private const val VIEWER_CDN =
        "/js/viewer-static.min.js"

    fun renderInCard(card: HTMLElement, diagram: DiagramData) {
        val metadata = diagram.drawioMetadata ?: return
        val holder = createHolder(card)
        DrawioTemplateEngine.merge(metadata) { xml ->
            ensureViewerLoaded(
                onReady = { renderWithViewer(holder, xml) },
                onFail = { DrawioDownloadHelper.showFallback(holder, xml, diagram.title) }
            )
        }
    }

    private fun createHolder(card: HTMLElement): HTMLElement {
        val holder = document.createElement("div") as HTMLElement
        holder.style.cssText = "opacity:0.4;font-size:12px;"
        holder.textContent = "Loading diagram..."
        card.appendChild(holder)
        return holder
    }

    private fun renderWithViewer(holder: HTMLElement, xml: String) {
        holder.style.opacity = "1"
        holder.style.fontSize = ""
        holder.textContent = ""
        holder.setAttribute("data-drawio-xml", xml)
        val helper = window.asDynamic().__drawioRenderOne
        if (helper != null && helper != undefined) {
            try { helper(holder) } catch (e: dynamic) {
                console.log("[DrawioDiagramRenderer] render error: $e")
                DrawioDownloadHelper.showFallback(holder, xml, "diagram")
            }
        } else {
            DrawioDownloadHelper.showFallback(holder, xml, "diagram")
        }
    }

    internal fun ensureViewerLoaded(onReady: () -> Unit, onFail: () -> Unit) {
        if (viewerLoaded) { onReady(); return }
        if (js("typeof GraphViewer !== 'undefined'") as Boolean) {
            viewerLoaded = true; initViewer(); onReady(); return
        }
        loadViewerScript(onReady, onFail)
    }

    private fun loadViewerScript(onReady: () -> Unit, onFail: () -> Unit) {
        val script = document.createElement("script") as HTMLElement
        script.setAttribute("src", VIEWER_CDN)
        script.addEventListener("load", {
            viewerLoaded = true; initViewer(); onReady()
        })
        script.addEventListener("error", {
            console.log("[DrawioDiagramRenderer] Failed to load viewer CDN")
            onFail()
        })
        document.head?.appendChild(script)
    }

    @Suppress("unused")
    private fun initViewer() {
        js("""(function(){
            window.__drawioRenderOne=function(el){
                var xml=el.getAttribute('data-drawio-xml');
                if(!xml)return;
                el.innerHTML='';
                var container=document.createElement('div');
                container.style.background='transparent';
                el.appendChild(container);
                var config=JSON.stringify({highlight:'#0000ff',nav:false,resize:true,toolbar:null,xml:xml});
                container.setAttribute('data-mxgraph',config);
                if(typeof GraphViewer!=='undefined'){GraphViewer.createViewerForElement(container);}
            };
        })()""")
    }
}
