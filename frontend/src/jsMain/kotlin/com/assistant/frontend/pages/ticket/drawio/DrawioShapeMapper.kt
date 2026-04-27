package com.assistant.frontend.pages.ticket.drawio

/**
 * Maps draw.io node types to mxCell style strings and generates cell XML.
 * Requirements: 2.6, 4.2, 4.5
 */
internal object DrawioShapeMapper {

    private const val W = 120
    private const val H = 80

    private val styles = mapOf(
        "webapp" to "rounded=1;whiteSpace=wrap;html=1;fillColor=#dae8fc;strokeColor=#6c8ebf;",
        "database" to "shape=cylinder3;whiteSpace=wrap;html=1;size=15;fillColor=#d5e8d4;strokeColor=#82b366;",
        "external_api" to "shape=cloud;whiteSpace=wrap;html=1;fillColor=#fff2cc;strokeColor=#d6b656;",
        "server" to "shape=mxgraph.cisco.servers.standard_server;html=1;fillColor=#f5f5f5;strokeColor=#666666;",
        "mobile" to "shape=mxgraph.android.phone2;html=1;fillColor=#e1d5e7;strokeColor=#9673a6;",
        "cloud" to "shape=cloud;whiteSpace=wrap;html=1;fillColor=#f8cecc;strokeColor=#b85450;",
        "user" to "shape=mxgraph.basic.person;html=1;fillColor=#e1d5e7;strokeColor=#9673a6;",
        "service" to "shape=hexagon;perimeter=hexagonPerimeter2;whiteSpace=wrap;html=1;fillColor=#d5e8d4;strokeColor=#82b366;",
        "queue" to "shape=parallelogram;perimeter=parallelogramPerimeter;whiteSpace=wrap;html=1;fillColor=#fff2cc;strokeColor=#d6b656;",
        "cache" to "shape=diamond;whiteSpace=wrap;html=1;fillColor=#f8cecc;strokeColor=#b85450;"
    )

    private const val DEFAULT_STYLE =
        "rounded=1;whiteSpace=wrap;html=1;fillColor=#f5f5f5;strokeColor=#666666;"

    fun styleFor(type: String): String = styles[type] ?: DEFAULT_STYLE

    fun toCell(
        id: String, label: String, type: String,
        x: Int, y: Int, cellId: Int
    ): String {
        val style = styleFor(type)
        val escaped = escapeXml(label)
        return "<mxCell id=\"$cellId\" value=\"$escaped\" " +
            "style=\"$style\" vertex=\"1\" parent=\"1\">" +
            "<mxGeometry x=\"$x\" y=\"$y\" width=\"$W\" height=\"$H\" " +
            "as=\"geometry\"/></mxCell>"
    }

    private fun escapeXml(s: String): String = s
        .replace("&", "&amp;").replace("<", "&lt;")
        .replace(">", "&gt;").replace("\"", "&quot;")
}
