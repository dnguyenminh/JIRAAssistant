package com.assistant.frontend.pages

import com.assistant.frontend.api.ApiClient
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.w3c.dom.Element

/**
 * Template Method base class for all page controllers.
 * Provides common render flow: clear → cleanup → load template → bind → load.
 */
abstract class BasePage(private val templateName: String) {

    protected val scope = MainScope()

    protected val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun render(container: Element) {
        container.innerHTML = ""
        cleanup()
        scope.launch {
            val html = ApiClient.loadTemplate(templateName)
            container.innerHTML = html
            onBind()
            onLoad()
        }
    }

    open fun cleanup() {}
    protected abstract fun onBind()
    protected abstract fun onLoad()
}
