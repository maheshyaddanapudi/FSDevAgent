package com.ai.developer.tools;

import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import jakarta.annotation.PostConstruct;
import org.springframework.context.ApplicationContext;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Service
public class ToolRegistry {
    
    private final Map<String, Tool> tools = new ConcurrentHashMap<>();
    private final ApplicationContext context;
    
    public ToolRegistry(ApplicationContext context) {
        this.context = context;
    }
    
    @PostConstruct
    public void registerTools() {
        Map<String, Tool> toolBeans = context.getBeansOfType(Tool.class);
        toolBeans.forEach((name, tool) -> {
            tools.put(tool.getName(), tool);
            log.info("Registered tool: {}", tool.getName());
        });
    }
    
    public Tool getTool(String name) {
        return tools.get(name);
    }
    
    public List<Tool> getAllTools() {
        return new ArrayList<>(tools.values());
    }
    
    public Flux<ToolOutput> executeTool(String name, Map<String, Object> arguments) {
        Tool tool = tools.get(name);
        if (tool == null) {
            return Flux.error(new IllegalArgumentException("Tool not found: " + name));
        }
        
        return tool.execute(arguments)
                .doOnSubscribe(s -> log.info("Executing tool: {} with arguments: {}", name, arguments))
                .doOnComplete(() -> log.info("Tool execution completed: {}", name))
                .doOnError(e -> log.error("Error executing tool: {}", name, e));
    }
    
    public Mono<List<ToolInfo>> getAvailableToolsInfo() {
        return Mono.just(
            tools.values().stream()
                .map(tool -> new ToolInfo(
                    tool.getName(),
                    tool.getDescription(),
                    tool.getParameters()
                ))
                .toList()
        );
    }
}
