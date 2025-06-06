package com.ai.developer.model;

/**
 * Represents user intents in conversations with the autonomous agent.
 * Extracted from AgentPromptService to be used across services.
 */
public enum UserIntent {
    NEW_TASK,
    PAUSE_EXECUTION,
    CONTINUE_EXECUTION,
    REQUEST_EXPLANATION,
    MODIFY_APPROACH,
    ANSWER_QUESTION,
    CHECK_STATUS,
    GENERAL_CONVERSATION
}
