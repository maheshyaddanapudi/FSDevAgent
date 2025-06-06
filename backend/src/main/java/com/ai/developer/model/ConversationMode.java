package com.ai.developer.model;

/**
 * Conversation modes for flexible interaction with the autonomous agent.
 * Extracted from AgentPromptService to be used across services.
 */
public enum ConversationMode {
    AUTONOMOUS,      // Full autonomous operation
    INTERACTIVE,     // Pauses for confirmation at key points
    COLLABORATIVE,   // Frequent interaction with user
    INSTRUCTIONAL,   // Explains actions in detail
    CONVERSATIONAL   // Standard conversational mode
}
