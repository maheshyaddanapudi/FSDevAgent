package com.ai.developer.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * Response model for tool events sent via Server-Sent Events
 * Replaces WebSocket event structure with SSE-compatible format
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ToolEventResponse {
    
    /**
     * Event type (tool_execution, tool_result, phase_transition, etc.)
     */
    private String type;
    
    /**
     * Session ID this event belongs to
     */
    private String sessionId;
    
    /**
     * Tool name (for tool-related events)
     */
    private String toolName;
    
    /**
     * Event data payload
     */
    private Map<String, Object> data;
    
    /**
     * Event timestamp
     */
    private Instant timestamp;
    
    /**
     * Optional event ID for deduplication
     */
    private String eventId;
    
    /**
     * Optional sequence number for ordering
     */
    private Long sequence;
}

