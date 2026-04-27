package com.assistant.frontend.pages.ticket

import com.assistant.ai.deepanalysis.models.DiagramData
import com.assistant.frontend.services.HtmlUtils
import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLElement

/**
 * Renders Mermaid diagrams using Mermaid.js CDN.
 * Extracted from DiagramRenderer to support format routing.
 * Requirements: 28.3, 28.4, 28.5
 */
internal object MermaidDiagramRenderer {

    private var mermaidLoaded = false
    private var diagramCounter = 0

    fun renderInCard(card: HTMLElement, diagram: DiagramData) {
        val holder = document.createElement("div") as HTMLElement
        val code = sanitizeMermaidCode(diagram.mermaidCode)
        if (code.isBlank()) {
            holder.innerHTML = "<span style='opacity:0.4;font-size:12px;'>(empty diagram)</span>"
        } else {
            setupPendingHolder(holder, code)
            ensureMermaidLoaded { triggerRender(holder) }
        }
        card.appendChild(holder)
    }

    private fun setupPendingHolder(holder: HTMLElement, code: String) {
        holder.setAttribute("data-diagram-id", "mdiag${diagramCounter++}")
        holder.setAttribute("data-diagram-code", code)
        holder.className = "mermaid-pending"
        holder.style.cssText = "opacity:0.4;font-size:12px;"
        holder.textContent = "Loading diagram..."
    }

    private fun triggerRender(el: HTMLElement) {
        val helper = window.asDynamic().__mermaidRenderOne
        if (helper != null && helper != undefined) {
            try { helper(el) } catch (e: dynamic) {
                showCodeFallback(el, "render failed")
            }
        } else {
            showCodeFallback(el, "Mermaid not ready")
        }
    }

    /** Strip markdown fences from AI-generated code blocks. */
    private fun stripFences(code: String): String {
        var c = code.trim()
        if (!c.startsWith("```")) return c
        val nl = c.indexOf('\n')
        if (nl > 0) c = c.substring(nl + 1)
        if (c.endsWith("```")) c = c.dropLast(3)
        return c.trim()
    }

    /** Quote unquoted bracket/brace labels containing special chars. */
    private fun quoteLabels(code: String): String {
        return code.lines().joinToString("\n") { line ->
            if (line.trimStart().startsWith("style ") ||
                line.trimStart().startsWith("classDef ")) return@joinToString line
            var l = quoteBracketLabels(line)
            quoteBraceLabels(l)
        }
    }

    /** Quote unquoted [...] labels with special chars. */
    private fun quoteBracketLabels(line: String): String {
        return try {
            // Use indexOf-based approach to avoid JS regex issues with [ ]
            val result = StringBuilder()
            var i = 0
            while (i < line.length) {
                val openIdx = line.indexOf('[', i)
                if (openIdx < 0) { result.append(line, i, line.length); break }
                result.append(line, i, openIdx)
                // Check if already quoted: ["
                if (openIdx + 1 < line.length && line[openIdx + 1] == '"') {
                    val closeQuote = line.indexOf("\"]", openIdx + 2)
                    if (closeQuote >= 0) {
                        result.append(line, openIdx, closeQuote + 2)
                        i = closeQuote + 2; continue
                    }
                }
                val closeIdx = line.indexOf(']', openIdx + 1)
                if (closeIdx < 0) { result.append(line, openIdx, line.length); break }
                val inner = line.substring(openIdx + 1, closeIdx)
                if (needsQuoting(inner)) result.append("[\"$inner\"]")
                else result.append(line, openIdx, closeIdx + 1)
                i = closeIdx + 1
            }
            result.toString()
        } catch (_: Exception) { line }
    }

    /** Quote unquoted {...} labels with special chars. */
    private fun quoteBraceLabels(line: String): String {
        return try {
            val result = StringBuilder()
            var i = 0
            while (i < line.length) {
                val openIdx = line.indexOf('{', i)
                if (openIdx < 0) { result.append(line, i, line.length); break }
                result.append(line, i, openIdx)
                if (openIdx + 1 < line.length && line[openIdx + 1] == '"') {
                    val closeQuote = line.indexOf("\"}", openIdx + 2)
                    if (closeQuote >= 0) {
                        result.append(line, openIdx, closeQuote + 2)
                        i = closeQuote + 2; continue
                    }
                }
                val closeIdx = line.indexOf('}', openIdx + 1)
                if (closeIdx < 0) { result.append(line, openIdx, line.length); break }
                val inner = line.substring(openIdx + 1, closeIdx)
                if (needsQuoting(inner)) result.append("{\"$inner\"}")
                else result.append(line, openIdx, closeIdx + 1)
                i = closeIdx + 1
            }
            result.toString()
        } catch (_: Exception) { line }
    }

    private fun needsQuoting(s: String): Boolean =
        s.contains("(") || s.contains(")") ||
            s.contains(",") || s.any { it.code > 127 }

    /** Sanitize AI-generated mermaid code for safe rendering. */
    private fun sanitizeMermaidCode(raw: String): String {
        val stripped = stripFences(raw)
        val noSemicolons = stripped.replace(Regex(""";[ \t]*$""", RegexOption.MULTILINE), "")
        val quoted = quoteLabels(noSemicolons)
        return quoted.replace(Regex("\n{3,}"), "\n\n")
    }

    private fun showCodeFallback(el: HTMLElement, reason: String) {
        val code = el.getAttribute("data-diagram-code") ?: ""
        el.style.opacity = "1"
        el.style.fontSize = ""
        el.className = ""
        val escaped = HtmlUtils.escapeHtml(code)
        el.innerHTML = "<div style=\"font-size:11px;font-weight:600;" +
            "letter-spacing:1px;opacity:0.4;margin-bottom:8px;\">" +
            "\u26A0 DIAGRAM SOURCE ($reason)</div>" +
            "<pre style=\"background:rgba(0,0,0,0.3);padding:12px;" +
            "border-radius:6px;overflow-x:auto;font-size:12px;" +
            "line-height:1.6;color:rgba(255,255,255,0.7);" +
            "white-space:pre-wrap;word-break:break-word;\">" +
            "<code>$escaped</code></pre>"
    }

    private fun ensureMermaidLoaded(onReady: () -> Unit) {
        if (mermaidLoaded) { onReady(); return }
        if (js("typeof mermaid !== 'undefined'") as Boolean) {
            mermaidLoaded = true; initMermaid(); onReady(); return
        }
        val script = document.createElement("script") as HTMLElement
        script.setAttribute("src",
            "https://cdn.jsdelivr.net/npm/mermaid@11/dist/mermaid.min.js")
        script.addEventListener("load", {
            mermaidLoaded = true; initMermaid(); onReady()
        })
        script.addEventListener("error", {
            console.log("[MermaidDiagramRenderer] Failed to load Mermaid CDN")
        })
        document.head?.appendChild(script)
    }

    @Suppress("unused")
    private fun initMermaid() {
        js("""(function(){
            if(typeof mermaid==='undefined')return;
            mermaid.initialize({
                startOnLoad:false,
                suppressErrorRendering:true,
                theme:'dark',
                themeVariables:{
                    primaryColor:'#2dfecf',
                    primaryTextColor:'#fff',
                    lineColor:'#3386ff',
                    secondaryColor:'#1a1c2e'
                }
            });
            window.__mermaidRenderOne=function(el){
                var id=el.getAttribute('data-diagram-id');
                var code=el.getAttribute('data-diagram-code');
                if(!id||!code)return;
                code=code.replace(/;[ \t]*$/gm,'');
                code=code.split('\n').map(function(line){
                    if(/^\s*(style|classDef)\s/.test(line))return line;
                    line=line.replace(/\[([^\]"]+)\]/g,function(m,inner){
                        if(/[(),]/.test(inner)||/[^\x00-\x7F]/.test(inner))return '["'+inner+'"]';
                        return m;
                    });
                    line=line.replace(/\{([^}"]+)\}/g,function(m,inner){
                        if(/[(),]/.test(inner)||/[^\x00-\x7F]/.test(inner))return '{"'+inner+'"}';
                        return m;
                    });
                    return line;
                }).join('\n');
                var esc=function(s){return s.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');};
                var fallback=function(reason){
                    el.style.opacity='1';el.style.fontSize='';el.className='';
                    el.innerHTML='<div style="font-size:11px;font-weight:600;letter-spacing:1px;opacity:0.4;margin-bottom:8px;">\u26A0 DIAGRAM SOURCE ('+reason+')</div><pre style="background:rgba(0,0,0,0.3);padding:12px;border-radius:6px;overflow-x:auto;font-size:12px;line-height:1.6;color:rgba(255,255,255,0.7);white-space:pre-wrap;word-break:break-word;"><code>'+esc(code)+'</code></pre>';
                };
                try{
                    mermaid.render(id,code).then(function(r){
                        el.style.opacity='1';el.style.fontSize='';el.className='';
                        el.innerHTML=r.svg;
                    }).catch(function(e){
                        console.log('[MermaidDiagramRenderer] render error '+id+':',e);
                        fallback('syntax error');
                    });
                }catch(e){
                    console.log('[MermaidDiagramRenderer] sync error '+id+':',e);
                    fallback('render failed');
                }
            };
        })()""")
    }
}
