package com.assistant.frontend.components.chat

import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLButtonElement
import org.w3c.dom.HTMLTextAreaElement

/**
 * Web Speech API integration for voice-to-text input.
 * Requirements: 19.27, 19.28, 19.29
 */
object VoiceInput {

    private var recognition: dynamic = null
    private var isRecording = false

    fun init(inputEl: HTMLTextAreaElement, btnEl: HTMLButtonElement) {
        if (!isSupported()) {
            btnEl.style.display = "none"
            return
        }
        btnEl.addEventListener("click", { toggle(inputEl, btnEl) })
    }

    fun isSupported(): Boolean {
        val sr = window.asDynamic().SpeechRecognition
        val wsr = window.asDynamic().webkitSpeechRecognition
        return sr != null || sr != undefined || wsr != null || wsr != undefined
    }

    private fun toggle(inputEl: HTMLTextAreaElement, btnEl: HTMLButtonElement) {
        if (isRecording) stop(btnEl) else start(inputEl, btnEl)
    }

    private fun start(inputEl: HTMLTextAreaElement, btnEl: HTMLButtonElement) {
        val SpeechRecognition = window.asDynamic().SpeechRecognition ?: window.asDynamic().webkitSpeechRecognition
        if (SpeechRecognition == null) return
        recognition = js("new SpeechRecognition()")
        recognition.continuous = false
        recognition.interimResults = true
        recognition.lang = "vi-VN"
        recognition.onresult = { event: dynamic ->
            val transcript = event.results[0][0].transcript as? String ?: ""
            inputEl.value = inputEl.value + transcript
        }
        recognition.onend = { stop(btnEl) }
        recognition.start()
        isRecording = true
        btnEl.classList.add("recording")
    }

    private fun stop(btnEl: HTMLButtonElement) {
        recognition?.stop()
        isRecording = false
        btnEl.classList.remove("recording")
    }
}
