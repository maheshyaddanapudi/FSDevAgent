package com.ai.developer.tools.impl;

import com.ai.developer.config.ToolOutputWebSocketHandler;
import com.ai.developer.tools.ParameterInfo;
import com.ai.developer.tools.Tool;
import com.ai.developer.tools.ToolOutput;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Tool for data visualization capabilities
 */
@Slf4j
@Component
public class DataVisualizationTool implements Tool {

    private final ToolOutputWebSocketHandler webSocketHandler;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DataVisualizationTool(ToolOutputWebSocketHandler webSocketHandler) {
        this.webSocketHandler = webSocketHandler;
    }

    @Override
    public String getName() {
        return "data_visualization";
    }

    @Override
    public String getDescription() {
        return "Creates data visualizations from provided data";
    }

    @Override
    public Map<String, ParameterInfo> getParameters() {
        Map<String, ParameterInfo> parameters = new HashMap<>();
        
        parameters.put("type", ParameterInfo.builder()
                .name("type")
                .description("Type of visualization (bar, line, pie, scatter, etc.)")
                .type("string")
                .required(true)
                .build());
                
        parameters.put("data", ParameterInfo.builder()
                .name("data")
                .description("Data to visualize in JSON format")
                .type("object")
                .required(true)
                .build());
                
        parameters.put("title", ParameterInfo.builder()
                .name("title")
                .description("Title of the visualization")
                .type("string")
                .required(false)
                .build());
                
        parameters.put("options", ParameterInfo.builder()
                .name("options")
                .description("Additional options for the visualization")
                .type("object")
                .required(false)
                .build());
                
        return parameters;
    }

    @Override
    public Flux<ToolOutput> execute(Map<String, Object> parameters) {
        String type = (String) parameters.get("type");
        Object data = parameters.get("data");
        String title = (String) parameters.getOrDefault("title", "Data Visualization");
        Map<String, Object> options = (Map<String, Object>) parameters.getOrDefault("options", new HashMap<>());
        
        log.info("Creating {} visualization with title: {}", type, title);
        
        try {
            // Create visualization configuration
            Map<String, Object> visualization = new HashMap<>();
            visualization.put("type", type);
            visualization.put("data", data);
            visualization.put("title", title);
            visualization.put("options", options);
            visualization.put("id", UUID.randomUUID().toString());
            
            // Convert visualization map to JSON string before sending to WebSocket
            String visualizationJson = objectMapper.writeValueAsString(visualization);
            
            // Send visualization to WebSocket
            webSocketHandler.sendToolOutput("data_visualization", visualizationJson);
            
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("visualData", visualization);
            
            return Flux.just(ToolOutput.builder()
                    .type("visualization")
                    .content("Visualization created successfully")
                    .metadata(metadata)
                    .build());
                    
        } catch (Exception e) {
            log.error("Error creating visualization", e);
            return Flux.just(ToolOutput.builder()
                    .type("error")
                    .content("Error creating visualization: " + e.getMessage())
                    .build());
        }
    }
}
