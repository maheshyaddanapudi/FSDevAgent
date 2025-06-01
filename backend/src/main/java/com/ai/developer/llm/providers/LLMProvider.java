package com.ai.developer.llm.providers;

import com.ai.developer.llm.ChatContext;
import com.ai.developer.llm.ToolCall;
import com.ai.developer.llm.ToolUseBlock;

import java.util.List;
import java.util.function.Consumer;

/**
 * Interface for LLM providers.
 * This interface defines the contract for different LLM implementations.
 */
public interface LLMProvider {
    
    /**
     * Initialize the LLM provider with any necessary configuration.
     */
    void init();
    
    /**
     * Get a completion from the LLM.
     * 
     * @param context The chat context containing messages and other information
     * @return The completion text
     */
    String getCompletion(ChatContext context);
    
    /**
     * Get a streaming completion from the LLM.
     * 
     * @param context The chat context containing messages and other information
     * @param onPartialResponse Consumer for handling partial responses
     * @return The final completion text
     */
    String streamingCompletion(ChatContext context, Consumer<String> onPartialResponse);
    
    /**
     * Extract tool calls from LLM response.
     * 
     * @param response The LLM response text
     * @return List of extracted tool calls
     */
    List<ToolCall> extractToolCalls(String response);
    
    /**
     * Extract tool use blocks from LLM response.
     * 
     * @param response The LLM response text
     * @return List of extracted tool use blocks
     */
    List<ToolUseBlock> extractToolUseBlocks(String response);
}
