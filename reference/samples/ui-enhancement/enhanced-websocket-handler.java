// Enhanced WebSocket Configuration
@Configuration
@EnableWebSocket
public class EnhancedWebSocketConfig implements WebSocketConfigurer {
    
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(new EnhancedToolWebSocketHandler(), "/ws/tools")
                .addHandler(new ChatStreamWebSocketHandler(), "/ws/chat")
                .setAllowedOrigins("*")
                .withSockJS();
    }
}

@Component
public class EnhancedToolWebSocketHandler extends TextWebSocketHandler {
    
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, ToolExecutionContext> activeTools = new ConcurrentHashMap<>();
    
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.put(session.getId(), session);
        
        // Send initial state
        sendMessage(session, new WebSocketMessage(
            "connection_established",
            Map.of(
                "sessionId", session.getId(),
                "timestamp", Instant.now(),
                "capabilities", getToolCapabilities()
            )
        ));
    }
    
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        WebSocketRequest request = objectMapper.readValue(message.getPayload(), WebSocketRequest.class);
        
        switch (request.getType()) {
            case "execute_tool":
                handleToolExecution(session, request);
                break;
            case "cancel_tool":
                handleToolCancellation(session, request);
                break;
            case "get_tool_status":
                handleToolStatusRequest(session, request);
                break;
        }
    }
    
    private void handleToolExecution(WebSocketSession session, WebSocketRequest request) {
        String toolId = UUID.randomUUID().toString();
        ToolExecutionContext context = new ToolExecutionContext(toolId, session.getId());
        activeTools.put(toolId, context);
        
        // Send execution started
        sendMessage(session, new WebSocketMessage(
            "tool_execution_started",
            Map.of(
                "toolId", toolId,
                "toolName", request.getToolName(),
                "timestamp", Instant.now()
            )
        ));
        
        // Execute tool asynchronously with streaming
        CompletableFuture.runAsync(() -> {
            try {
                Tool tool = toolRegistry.getTool(request.getToolName());
                
                // Create streaming callback
                StreamingCallback callback = (chunk) -> {
                    sendMessage(session, new WebSocketMessage(
                        "tool_output_chunk",
                        Map.of(
                            "toolId", toolId,
                            "chunk", chunk,
                            "timestamp", Instant.now()
                        )
                    ));
                };
                
                // Execute with streaming
                ToolResult result = tool.executeWithStreaming(
                    request.getArguments(),
                    callback,
                    context
                );
                
                // Send completion
                sendMessage(session, new WebSocketMessage(
                    "tool_execution_completed",
                    Map.of(
                        "toolId", toolId,
                        "result", result,
                        "timestamp", Instant.now()
                    )
                ));
                
            } catch (Exception e) {
                sendMessage(session, new WebSocketMessage(
                    "tool_execution_error",
                    Map.of(
                        "toolId", toolId,
                        "error", e.getMessage(),
                        "timestamp", Instant.now()
                    )
                ));
            } finally {
                activeTools.remove(toolId);
            }
        });
    }
    
    private void sendMessage(WebSocketSession session, WebSocketMessage message) {
        try {
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(message)));
        } catch (Exception e) {
            log.error("Error sending WebSocket message", e);
        }
    }
}

// Enhanced Tool Execution with Rich Output
@Component
public class EnhancedToolExecutor {
    
    public interface ToolOutputHandler {
        void onOutput(ToolOutput output);
        void onProgress(int percentage, String status);
        void onError(String error);
        void onComplete(ToolResult result);
    }
    
    @Data
    @Builder
    public static class ToolOutput {
        private String type; // "text", "code", "image", "table", "chart"
        private String content;
        private Map<String, Object> metadata;
        private OutputFormat format;
        
        public enum OutputFormat {
            PLAIN_TEXT,
            ANSI_COLORED,
            JSON,
            HTML,
            MARKDOWN,
            BINARY
        }
    }
    
    public void executeWithRichOutput(
        String toolName, 
        Map<String, Object> args,
        ToolOutputHandler handler
    ) {
        try {
            Tool tool = toolRegistry.getTool(toolName);
            
            if (tool instanceof FileSystemTool) {
                executeFileSystemTool(tool, args, handler);
            } else if (tool instanceof GitTool) {
                executeGitTool(tool, args, handler);
            } else if (tool instanceof BuildTool) {
                executeBuildTool(tool, args, handler);
            } else {
                executeGenericTool(tool, args, handler);
            }
            
        } catch (Exception e) {
            handler.onError(e.getMessage());
        }
    }
    
    private void executeFileSystemTool(Tool tool, Map<String, Object> args, ToolOutputHandler handler) {
        String operation = (String) args.get("operation");
        
        switch (operation) {
            case "list":
                // Send directory tree structure
                handler.onOutput(ToolOutput.builder()
                    .type("table")
                    .content(generateFileListTable(args))
                    .metadata(Map.of(
                        "columns", List.of("Name", "Type", "Size", "Modified"),
                        "sortable", true
                    ))
                    .format(OutputFormat.JSON)
                    .build());
                break;
                
            case "read":
                // Send file content with syntax highlighting info
                String content = tool.execute(args);
                String fileExt = getFileExtension((String) args.get("path"));
                
                handler.onOutput(ToolOutput.builder()
                    .type("code")
                    .content(content)
                    .metadata(Map.of(
                        "language", getLanguageFromExtension(fileExt),
                        "filename", args.get("path")
                    ))
                    .format(OutputFormat.PLAIN_TEXT)
                    .build());
                break;
        }
    }
    
    private void executeBuildTool(Tool tool, Map<String, Object> args, ToolOutputHandler handler) {
        // Stream build output with ANSI color support
        ProcessBuilder pb = new ProcessBuilder(/* build command */);
        Process process = pb.start();
        
        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(process.getInputStream())
        )) {
            String line;
            int lineCount = 0;
            
            while ((line = reader.readLine()) != null) {
                // Parse build output for progress
                int progress = parseBuildProgress(line);
                if (progress > 0) {
                    handler.onProgress(progress, "Building...");
                }
                
                // Send colored output
                handler.onOutput(ToolOutput.builder()
                    .type("text")
                    .content(line)
                    .format(OutputFormat.ANSI_COLORED)
                    .metadata(Map.of(
                        "level", detectLogLevel(line),
                        "lineNumber", lineCount++
                    ))
                    .build());
            }
        }
    }
}