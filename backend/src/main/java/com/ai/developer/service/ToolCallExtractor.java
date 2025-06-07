package com.ai.developer.service;

import com.ai.developer.llm.ToolCall;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class to extract tool calls from LLM responses in different formats
 * Enhanced with improved logging and error handling
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ToolCallExtractor {
    
    // Pattern for Anthropic's function_calls format
    private static final Pattern FUNCTION_CALLS_PATTERN = Pattern.compile(
        "<function_calls>\\s*" +
        "<invoke\\s+name=\"([^\"]+)\">\\s*" +
        "((?:<parameter\\s+name=\"[^\"]+\">[^<]*</parameter>\\s*)*)" +
        "</invoke>\\s*" +
        "</function_calls>",
        Pattern.DOTALL
    );
    
    // Pattern for tool_use JSON format
    private static final Pattern TOOL_USE_PATTERN = Pattern.compile(
        "<tool_use>\\s*" +
        "(\\{[^}]*\\})" +
        "\\s*</tool_use>",
        Pattern.DOTALL
    );
    
    // Pattern for EVENT:toolCall JSON format
    private static final Pattern EVENT_TOOL_CALL_PATTERN = Pattern.compile(
        "EVENT:toolCall:(\\{.*?\\})",
        Pattern.DOTALL
    );
    
    // Pattern to extract individual parameters from function_calls format
    private static final Pattern PARAMETER_PATTERN = Pattern.compile(
        "<parameter\\s+name=\"([^\"]+)\">([^<]*)</parameter>",
        Pattern.DOTALL
    );
    
    private final ObjectMapper objectMapper;
    
    /**
     * Extract tool calls from LLM response in any supported format
     */
    public List<ToolCall> extractToolCalls(String response) {
        List<ToolCall> toolCalls = new ArrayList<>();
        
        // Log the response for debugging
        log.debug("[TOOL_EXTRACT] Processing response for tool calls: {}", response);
        
        // Try function_calls format (Anthropic)
        Matcher functionMatcher = FUNCTION_CALLS_PATTERN.matcher(response);
        while (functionMatcher.find()) {
            String toolName = functionMatcher.group(1);
            String parametersBlock = functionMatcher.group(2);
            
            log.info("[TOOL_EXTRACT] Found Anthropic function_calls format tool: {}", toolName);
            log.debug("[TOOL_EXTRACT] Parameters block: {}", parametersBlock);
            
            Map<String, Object> arguments = new HashMap<>();
            
            // Extract parameters
            Matcher paramMatcher = PARAMETER_PATTERN.matcher(parametersBlock);
            while (paramMatcher.find()) {
                String paramName = paramMatcher.group(1);
                String paramValue = paramMatcher.group(2).trim();
                arguments.put(paramName, paramValue);
                log.debug("[TOOL_EXTRACT] Extracted parameter: {} = {}", paramName, paramValue);
            }
            
            toolCalls.add(ToolCall.builder()
                    .name(toolName)
                    .arguments(arguments)
                    .build());
            
            log.info("[TOOL_EXTRACT] Successfully extracted Anthropic tool call: {} with arguments: {}", toolName, arguments);
        }
        
        // Try tool_use format (OpenAI-style)
        Matcher toolUseMatcher = TOOL_USE_PATTERN.matcher(response);
        while (toolUseMatcher.find()) {
            String jsonContent = toolUseMatcher.group(1);
            log.info("[TOOL_EXTRACT] Found tool_use format JSON: {}", jsonContent);
            
            try {
                // Parse JSON content
                Map<String, Object> toolUseMap = objectMapper.readValue(jsonContent, Map.class);
                String toolName = (String) toolUseMap.get("name");
                
                // Handle different argument formats
                Map<String, Object> args;
                if (toolUseMap.containsKey("args")) {
                    args = (Map<String, Object>) toolUseMap.get("args");
                } else if (toolUseMap.containsKey("arguments")) {
                    args = (Map<String, Object>) toolUseMap.get("arguments");
                } else if (toolUseMap.containsKey("input")) {
                    args = (Map<String, Object>) toolUseMap.get("input");
                } else {
                    args = new HashMap<>();
                }
                
                if (args == null) {
                    args = new HashMap<>();
                }
                
                toolCalls.add(ToolCall.builder()
                        .name(toolName)
                        .arguments(args)
                        .build());
                
                log.info("[TOOL_EXTRACT] Successfully extracted tool_use tool call: {} with arguments: {}", toolName, args);
            } catch (Exception e) {
                log.error("[TOOL_EXTRACT] Error parsing tool_use JSON: {}", e.getMessage(), e);
            }
        }
        
        // Try EVENT:toolCall format
        Matcher eventToolCallMatcher = EVENT_TOOL_CALL_PATTERN.matcher(response);
        while (eventToolCallMatcher.find()) {
            String jsonContent = eventToolCallMatcher.group(1);
            log.info("[TOOL_EXTRACT] Found EVENT:toolCall format JSON: {}", jsonContent);
            
            try {
                // Parse JSON content
                Map<String, Object> toolCallMap = objectMapper.readValue(jsonContent, Map.class);
                String toolName = (String) toolCallMap.get("name");
                
                // Handle different argument formats
                Map<String, Object> args;
                if (toolCallMap.containsKey("arguments")) {
                    args = (Map<String, Object>) toolCallMap.get("arguments");
                } else if (toolCallMap.containsKey("args")) {
                    args = (Map<String, Object>) toolCallMap.get("args");
                } else {
                    args = new HashMap<>();
                }
                
                if (args == null) {
                    args = new HashMap<>();
                }
                
                toolCalls.add(ToolCall.builder()
                        .name(toolName)
                        .arguments(args)
                        .build());
                
                log.info("[TOOL_EXTRACT] Successfully extracted EVENT:toolCall tool call: {} with arguments: {}", toolName, args);
            } catch (Exception e) {
                log.error("[TOOL_EXTRACT] Error parsing EVENT:toolCall JSON: {}", e.getMessage(), e);
            }
        }
        
        return toolCalls;
    }
}
