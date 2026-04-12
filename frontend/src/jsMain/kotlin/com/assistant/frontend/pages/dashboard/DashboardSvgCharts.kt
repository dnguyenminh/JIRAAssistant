package com.assistant.frontend.pages.dashboard

import com.assistant.frontend.models.VelocityPoint
import kotlinx.browser.document
import org.w3c.dom.Element

/**
 * SVG chart renderers for the Dashboard page.
 */
internal object DashboardSvgCharts {

    private const val SVG_NS = "http://www.w3.org/2000/svg"

    fun renderNetworkPreview() {
        val svg = document.getElementById("networkPreviewSvg") ?: return
        data class Node(val x: Int, val y: Int, val r: Int, val color: String)
        val nodes = listOf(
            Node(60, 35, 8, "#2dfecf"), Node(150, 25, 10, "#3386ff"),
            Node(240, 40, 7, "#be9dff"), Node(100, 80, 9, "#2dfecf"),
            Node(200, 90, 8, "#3386ff"), Node(50, 100, 6, "#be9dff"),
            Node(260, 100, 7, "#2dfecf")
        )
        val edges = listOf(0 to 1, 1 to 2, 0 to 3, 3 to 4, 4 to 2, 5 to 0, 5 to 3, 4 to 6, 2 to 6, 1 to 4)
        for ((from, to) in edges) {
            val line = document.createElementNS(SVG_NS, "line")
            line.setAttribute("x1", "${nodes[from].x}"); line.setAttribute("y1", "${nodes[from].y}")
            line.setAttribute("x2", "${nodes[to].x}"); line.setAttribute("y2", "${nodes[to].y}")
            line.setAttribute("stroke", "rgba(45, 254, 207, 0.15)"); line.setAttribute("stroke-width", "1")
            svg.appendChild(line)
        }
        for (node in nodes) {
            appendNodeCircles(svg, node.x, node.y, node.r, node.color)
        }
    }

    private fun appendNodeCircles(svg: Element, x: Int, y: Int, r: Int, color: String) {
        val glow = document.createElementNS(SVG_NS, "circle")
        glow.setAttribute("cx", "$x"); glow.setAttribute("cy", "$y")
        glow.setAttribute("r", "${r + 4}"); glow.setAttribute("fill", color); glow.setAttribute("opacity", "0.15")
        svg.appendChild(glow)
        val circle = document.createElementNS(SVG_NS, "circle")
        circle.setAttribute("cx", "$x"); circle.setAttribute("cy", "$y")
        circle.setAttribute("r", "$r"); circle.setAttribute("fill", color); circle.setAttribute("opacity", "0.8")
        svg.appendChild(circle)
    }

    fun renderDriftChart(velocityTrend: List<VelocityPoint>) {
        val svg = document.getElementById("driftChartSvg") ?: return
        svg.innerHTML = ""
        if (velocityTrend.isEmpty()) { renderEmptyDrift(svg); return }
        val driftData = velocityTrend.map { it.storyPoints }
        val maxVal = driftData.max().coerceAtLeast(1.0)
        val points = computePoints(driftData, maxVal)
        appendGradientDefs(svg)
        appendAreaFill(svg, points)
        appendPolyline(svg, points)
        appendDataPoints(svg, points, driftData)
    }

    private fun renderEmptyDrift(svg: Element) {
        val text = document.createElementNS(SVG_NS, "text")
        text.setAttribute("x", "150"); text.setAttribute("y", "65")
        text.setAttribute("text-anchor", "middle"); text.setAttribute("fill", "rgba(255,255,255,0.2)")
        text.setAttribute("font-size", "12"); text.setAttribute("font-family", "Be Vietnam Pro, sans-serif")
        text.textContent = "No drift data"
        svg.appendChild(text)
    }

    private data class Pt(val x: Double, val y: Double)

    private fun computePoints(data: List<Double>, maxVal: Double): List<Pt> {
        val w = 300.0; val h = 120.0; val padX = 10.0; val padY = 15.0
        val stepX = if (data.size > 1) (w - padX * 2) / (data.size - 1) else 0.0
        return data.mapIndexed { i, v ->
            Pt(padX + i * stepX, h - padY - (v / maxVal) * (h - padY * 2))
        }
    }

    private fun appendGradientDefs(svg: Element) {
        val defs = document.createElementNS(SVG_NS, "defs")
        val grad = document.createElementNS(SVG_NS, "linearGradient")
        grad.setAttribute("id", "driftGrad"); grad.setAttribute("x1", "0"); grad.setAttribute("y1", "0")
        grad.setAttribute("x2", "0"); grad.setAttribute("y2", "1")
        val stop1 = document.createElementNS(SVG_NS, "stop")
        stop1.setAttribute("offset", "0%"); stop1.setAttribute("stop-color", "#be9dff"); stop1.setAttribute("stop-opacity", "0.4")
        val stop2 = document.createElementNS(SVG_NS, "stop")
        stop2.setAttribute("offset", "100%"); stop2.setAttribute("stop-color", "#be9dff"); stop2.setAttribute("stop-opacity", "0.02")
        grad.appendChild(stop1); grad.appendChild(stop2); defs.appendChild(grad); svg.appendChild(defs)
    }

    private fun appendAreaFill(svg: Element, points: List<Pt>) {
        val h = 120.0; val padY = 15.0
        val areaD = points.mapIndexed { i, p -> "${if (i == 0) "M" else "L"}${p.x},${p.y}" }.joinToString(" ") +
            " L${points.last().x},${h - padY} L${points.first().x},${h - padY} Z"
        val area = document.createElementNS(SVG_NS, "path")
        area.setAttribute("d", areaD); area.setAttribute("fill", "url(#driftGrad)")
        svg.appendChild(area)
    }

    private fun appendPolyline(svg: Element, points: List<Pt>) {
        val line = document.createElementNS(SVG_NS, "polyline")
        line.setAttribute("points", points.joinToString(" ") { "${it.x},${it.y}" })
        line.setAttribute("fill", "none"); line.setAttribute("stroke", "#be9dff")
        line.setAttribute("stroke-width", "2"); line.setAttribute("stroke-linecap", "round")
        line.setAttribute("stroke-linejoin", "round")
        svg.appendChild(line)
    }

    private fun appendDataPoints(svg: Element, points: List<Pt>, data: List<Double>) {
        for (p in points) {
            val dot = document.createElementNS(SVG_NS, "circle")
            dot.setAttribute("cx", "${p.x}"); dot.setAttribute("cy", "${p.y}")
            dot.setAttribute("r", "3"); dot.setAttribute("fill", "#be9dff"); dot.setAttribute("opacity", "0.9")
            svg.appendChild(dot)
        }
        val peakIdx = data.indexOf(data.max())
        if (peakIdx >= 0) {
            val glow = document.createElementNS(SVG_NS, "circle")
            glow.setAttribute("cx", "${points[peakIdx].x}"); glow.setAttribute("cy", "${points[peakIdx].y}")
            glow.setAttribute("r", "8"); glow.setAttribute("fill", "#be9dff"); glow.setAttribute("opacity", "0.2")
            svg.appendChild(glow)
        }
    }
}
