package com.assistant.frontend.pages.graph

/**
 * Dark theme style configuration for Cytoscape.js.
 * Extracted to keep CytoscapeRenderer under 200 lines.
 */
internal object CytoscapeStyles {

    private const val BG_COLOR = "rgba(13,17,23,1)"
    private const val LABEL_COLOR = "#ffffff"
    private const val LABEL_OUTLINE = "#0d1117"
    private const val CYAN_EDGE = "rgba(45, 254, 207, 0.35)"
    private const val PURPLE_EDGE = "rgba(190, 157, 255, 0.35)"
    private const val HIGHLIGHT_CLASS = "highlighted"
    private const val DIM_CLASS = "dimmed"
    private const val FOCUSED_CLASS = "focused"

    /** Build the full Cytoscape options object with styles. */
    fun buildOptions(): dynamic {
        val opts = js("({})")
        opts.style = buildStylesheet()
        opts.layout = js("({name:'preset'})")
        opts.minZoom = 0.1
        opts.maxZoom = 10.0
        opts.wheelSensitivity = 0.3
        return opts
    }

    /** Background color for the container element. */
    fun containerBackground(): String = BG_COLOR

    /** Cyan edge color for structural edges. */
    fun cyanEdge(): String = CYAN_EDGE

    /** Purple edge color for semantic edges. */
    fun purpleEdge(): String = PURPLE_EDGE

    /** CSS class name for highlighted state. */
    fun highlightClass(): String = HIGHLIGHT_CLASS

    /** CSS class name for focused node. */
    fun focusedClass(): String = FOCUSED_CLASS

    /** CSS class name for dimmed state. */
    fun dimClass(): String = DIM_CLASS

    private fun buildStylesheet(): dynamic = js("""[
        { selector: 'node', style: {
            'background-color': 'data(color)',
            'label': 'data(label)',
            'color': '${LABEL_COLOR}',
            'text-outline-color': '${LABEL_OUTLINE}',
            'text-outline-width': 2,
            'font-size': 11,
            'font-weight': 700,
            'font-family': 'Be Vietnam Pro, sans-serif',
            'width': 16, 'height': 16,
            'text-valign': 'bottom', 'text-halign': 'center',
            'text-margin-y': 6,
            'min-zoomed-font-size': 8
        }},
        { selector: 'edge', style: {
            'line-color': 'data(color)',
            'width': 1.5,
            'curve-style': 'bezier',
            'opacity': 0.6
        }},
        { selector: 'node.${HIGHLIGHT_CLASS}', style: {
            'border-width': 3,
            'border-color': '#2dfecf',
            'width': 24, 'height': 24,
            'font-size': 14,
            'z-index': 999
        }},
        { selector: 'node.${DIM_CLASS}', style: {
            'opacity': 0.15,
            'font-size': 0
        }},
        { selector: 'edge.${DIM_CLASS}', style: {
            'opacity': 0.05
        }},
        { selector: 'node.${FOCUSED_CLASS}', style: {
            'border-width': 4,
            'border-color': '#f9d423',
            'background-color': '#f9d423',
            'width': 28, 'height': 28,
            'font-size': 15,
            'font-weight': 900,
            'color': '#f9d423',
            'text-outline-color': '#0d1117',
            'text-outline-width': 3,
            'z-index': 9999
        }}
    ]""")
}
