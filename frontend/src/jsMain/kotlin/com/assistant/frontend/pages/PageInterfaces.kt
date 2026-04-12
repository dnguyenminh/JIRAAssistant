package com.assistant.frontend.pages

import org.w3c.dom.Element

/** Marks a component that can render into a container. */
interface Renderable {
    fun render(container: Element)
}

/** Marks a component that needs cleanup on navigation away. */
interface Cleanable {
    fun cleanup()
}

/** Marks a component that supports start/stop polling. */
interface Pollable {
    fun startPolling()
    fun stopPolling()
}
