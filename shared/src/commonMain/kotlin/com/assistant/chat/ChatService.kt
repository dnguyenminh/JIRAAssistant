package com.assistant.chat

/**
 * Service interface for AI Chat processing.
 * Constructs prompts with KB context + graph context + conversation history,
 * sends to AI provider via AIOrchestrator, and parses structured responses.
 *
 * Requirements: 19.5 (send message with context to AI),
 *               19.6 (use active AI provider with failover),
 *               19.9 (query KB for related ticket data as context),
 *               19.10 (query graph/network for relationship context)
 */
interface ChatService {
    /**
     * Process a chat message: build prompt with KB context + graph context + history,
     * send to AI provider, parse response into ChatResponse.
     *
     * @param message The user's chat message
     * @param context Current user context (project key, screen, role, userId)
     * @param conversationHistory Recent conversation messages for continuity
     * @return ChatResponse with reply, suggested actions, and references
     */
    suspend fun processChat(
        message: String,
        context: ChatContext,
        conversationHistory: List<ChatMessage>
    ): ChatResponse

    /**
     * Build the system prompt for AI chat based on current user context.
     * Includes project key, current screen, user role, and response format rules.
     *
     * @param context Current user context
     * @return Formatted system prompt string
     */
    fun buildSystemPrompt(context: ChatContext): String
}
