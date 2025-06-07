package com.ai.developer.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request model for agent control operations
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentControlRequest {
    private String objective;
    private String mode;
    private Integer maxIterations;
}
