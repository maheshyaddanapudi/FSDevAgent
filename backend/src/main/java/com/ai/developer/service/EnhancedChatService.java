package com.ai.developer.service;

import com.ai.developer.config.EnhancedToolOutputWebSocketHandler;
import com.ai.developer.config.ToolOutputWebSocketHandler;
import com.ai.developer.llm.*;
import com.ai.developer.model.*;
import com.ai.developer.tools.Tool;
import com.ai.developer.tools.ToolOutput;
import com.ai.developer.tools.ToolRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Enhanced Chat Service with multi-turn conversation support and autonomous agent capabilities.
 * This service extends the original ChatService functionality while preserving the working
 * Claude tool call communication.
 * 
 * Enhanced with true autonomous execution capabilities for continuous operation without user intervention.
 */
@Service
@Primary
@Slf4j
public class EnhancedChatService {
    
    private final LLMProvider llmProvider;
    private final ToolRegistry toolRegistry;
    private final ObjectMapper objectMapper;
    private final EnhancedToolOutputWebSocketHandler webSocketHandler;
    private final AgentPromptService agentPromptService;
    
    private final ConcurrentHashMap<String, ChatContext> sessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AgentState> agentStates = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Sinks.Many<ChatResponse>> sessionSinks = new ConcurrentHashMap<>();
    
    // Default workspace path for tools
    private static final String DEFAULT_WORKSPACE_PATH = "/tmp/ai-developer-agent";
    
    // Session workspace root folder parameter name for workspace management
    private static final String SESSION_WORKSPACE_ROOT_FOLDER_PARAM = "sessionWorkspaceRootFolder";
    
    // Patterns for parsing responses
    private static final Pattern TOOL_USE_PATTERN = Pattern.compile("<tool_use>(.*?)</tool_use>|\\{\"type\":\"content_block_start\".*?\"type\":\"tool_use\".*?\\}", Pattern.DOTALL);
    private static final Pattern COMPLETION_PATTERN = Pattern.compile("(?i)(task complete|objectives? (?:met|achieved|completed)|all (?:done|finished)|nothing (?:more|else) to do)");
    private static final Pattern QUESTION_PATTERN = Pattern.compile("(?i)(\\?|would you like|should i|do you want|can you clarify|need more information|what about)");
    private static final Pattern EVENT_PATTERN = Pattern.compile("EVENT:([^:]+):(.+)");
    
    // Maximum iterations to prevent infinite loops
    private static final int MAX_AUTONOMOUS_ITERATIONS = 100;
    
    public EnhancedChatService(LLMProvider llmProvider, ToolRegistry toolRegistry, ObjectMapper objectMapper, 
                      EnhancedToolOutputWebSocketHandler webSocketHandler, AgentPromptService agentPromptService) {
        this.llmProvider = llmProvider;
        this.toolRegistry = toolRegistry;
        this.objectMapper = objectMapper;
        this.webSocketHandler = webSocketHandler;
        this.agentPromptService = agentPromptService;
        
        log.info("EnhancedChatService initialized with TRUE autonomous agent capabilities and multi-turn support");
    }
    
    /**
     * Create a new session with workspace initialization and agent state
     */
    public Mono<SessionResponse> createSession() {
        String sessionId = UUID.randomUUID().toString();
        log.info("Created new session: {}", sessionId);
        
        // Use sessionId as sessionWorkspaceRootFolder for workspace management
        String sessionWorkspaceRootFolder = sessionId;
        
        // Create session workspace directory
        String workspacePath = DEFAULT_WORKSPACE_PATH + "/" + sessionWorkspaceRootFolder;
        try {
            Files.createDirectories(Path.of(workspacePath));
            log.info("Created workspace directory for session {}: {}", sessionId, workspacePath);
        } catch (Exception e) {
            log.error("Error creating workspace directory for session {}: {}", sessionId, e.getMessage(), e);
        }
        
        // Create chat context with autonomous agent prompt
        ChatContext context = new ChatContext();
        ProjectContext projectContext = new ProjectContext();
        projectContext.setProjectPath(workspacePath);
        
        String systemPrompt = agentPromptService.generateSystemPrompt(projectContext);
        context.setSystemPrompt(systemPrompt);
        context.setMessages(new ArrayList<>());
        
        // Add metadata with session ID, sessionWorkspaceRootFolder, and workspace path
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("sessionId", sessionId);
        metadata.put(SESSION_WORKSPACE_ROOT_FOLDER_PARAM, sessionWorkspaceRootFolder);
        metadata.put("workspacePath", workspacePath);
        context.setMetadata(metadata);
        
        // Create agent state
        AgentState agentState = new AgentState();
        agentState.setAiDeveloperAgentSessionId(sessionId);
        agentState.setProjectContext(projectContext);
        agentState.setMode(ConversationMode.AUTONOMOUS); // Start in autonomous mode for true autonomy
        
        // Set canonical workspace path
        agentState.setCanonicalWorkspacePath(workspacePath);
        
        // Initialize workspace context
        refreshWorkspaceContext(sessionId, workspacePath);
        
        // Store context and state
        sessions.put(sessionId, context);
        agentStates.put(sessionId, agentState);
        
        return Mono.just(SessionResponse.builder()
                .aiDeveloperAgentSessionId(sessionId)
                .createdAt(Instant.now())
                .workspacePath(workspacePath)
                .build());
    }
    
    /**
     * Refresh workspace context after tool execution
     * This method scans the workspace directory and updates the agent state
     */
    private void refreshWorkspaceContext(String sessionId, String workspacePath) {
        try {
            AgentState agentState = agentStates.get(sessionId);
            if (agentState == null) {
                log.warn("Agent state not found for session: {}", sessionId);
                return;
            }
            
            // Scan workspace directory
            Map<String, Object> workspaceState = scanWorkspaceDirectory(workspacePath);
            
            // Update agent state
            agentState.updateWorkspaceState(workspacePath, workspaceState);
            
            // Update project context
            ProjectContext projectContext = agentState.getProjectContext();
            if (projectContext != null) {
                projectContext.setWorkspaceState(workspaceState);
            }
            
            log.info("Refreshed workspace context for session {}", sessionId);
        } catch (Exception e) {
            log.error("Error refreshing workspace context: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Scan workspace directory and build state map
     */
    private Map<String, Object> scanWorkspaceDirectory(String path) {
        Map<String, Object> state = new HashMap<>();
        try {
            Path dirPath = Path.of(path);
            if (!Files.exists(dirPath)) {
                return state;
            }
            
            List<Map<String, Object>> entries = new ArrayList<>();
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dirPath)) {
                for (Path entry : stream) {
                    boolean isDirectory = Files.isDirectory(entry);
                    String entryName = entry.getFileName().toString();
                    
                    Map<String, Object> entryMap = new HashMap<>();
                    entryMap.put("name", entryName);
                    entryMap.put("path", entry.toString());
                    entryMap.put("isDirectory", isDirectory);
                    
                    if (isDirectory) {
                        // Recursively scan subdirectories (limit depth to prevent excessive scanning)
                        entryMap.put("contents", scanDirectoryWithDepthLimit(entry.toString(), 3));
                    } else {
                        // Add file metadata
                        entryMap.put("size", Files.size(entry));
                        entryMap.put("lastModified", Files.getLastModifiedTime(entry).toMillis());
                        
                        // For small text files, include content preview
                        if (Files.size(entry) < 10240 && isTextFile(entry)) {
                            try {
                                String content = Files.readString(entry);
                                if (content.length() > 500) {
                                    content = content.substring(0, 500) + "... (truncated)";
                                }
                                entryMap.put("preview", content);
                            } catch (Exception e) {
                                log.debug("Could not read file content: {}", e.getMessage());
                            }
                        }
                    }
                    
                    entries.add(entryMap);
                }
            }
            
            state.put("entries", entries);
            state.put("path", path);
            
            return state;
        } catch (Exception e) {
            log.error("Error scanning workspace directory: {}", e.getMessage(), e);
            return state;
        }
    }
    
    /**
     * Scan directory with depth limit to prevent excessive recursion
     */
    private Map<String, Object> scanDirectoryWithDepthLimit(String path, int maxDepth) {
        Map<String, Object> state = new HashMap<>();
        if (maxDepth <= 0) {
            state.put("path", path);
            state.put("entries", List.of(Map.of("name", "...", "path", path, "isDirectory", false)));
            return state;
        }
        
        try {
            Path dirPath = Path.of(path);
            if (!Files.exists(dirPath)) {
                return state;
            }
            
            List<Map<String, Object>> entries = new ArrayList<>();
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(dirPath)) {
                for (Path entry : stream) {
                    boolean isDirectory = Files.isDirectory(entry);
                    String entryName = entry.getFileName().toString();
                    
                    Map<String, Object> entryMap = new HashMap<>();
                    entryMap.put("name", entryName);
                    entryMap.put("path", entry.toString());
                    entryMap.put("isDirectory", isDirectory);
                    
                    if (isDirectory) {
                        // Recursively scan subdirectories with reduced depth
                        entryMap.put("contents", scanDirectoryWithDepthLimit(entry.toString(), maxDepth - 1));
                    } else {
                        // Add file metadata
                        entryMap.put("size", Files.size(entry));
                    }
                    
                    entries.add(entryMap);
                }
            }
            
            state.put("entries", entries);
            state.put("path", path);
            
            return state;
        } catch (Exception e) {
            log.error("Error scanning directory with depth limit: {}", e.getMessage(), e);
            return state;
        }
    }
    
    /**
     * Check if a file is a text file
     */
    private boolean isTextFile(Path path) {
        String fileName = path.getFileName().toString().toLowerCase();
        return fileName.endsWith(".txt") || fileName.endsWith(".md") || 
               fileName.endsWith(".java") || fileName.endsWith(".py") || 
               fileName.endsWith(".js") || fileName.endsWith(".html") || 
               fileName.endsWith(".css") || fileName.endsWith(".json") || 
               fileName.endsWith(".xml") || fileName.endsWith(".yml") || 
               fileName.endsWith(".yaml") || fileName.endsWith(".properties") || 
               fileName.endsWith(".sh") || fileName.endsWith(".bat") || 
               fileName.endsWith(".cmd") || fileName.endsWith(".sql");
    }
    
    /**
     * Register an existing session created by another service
     * This ensures session state consistency across services
     */
    public void registerExistingSession(String sessionId) {
        log.info("Registering existing session in EnhancedChatService: {}", sessionId);
        
        if (sessions.containsKey(sessionId) || agentStates.containsKey(sessionId)) {
            log.info("Session already registered: {}", sessionId);
            return;
        }
        
        // Create session workspace directory
        String workspacePath = DEFAULT_WORKSPACE_PATH + "/" + sessionId;
        try {
            Files.createDirectories(Path.of(workspacePath));
            log.info("Created workspace directory for existing session {}: {}", workspacePath, sessionId);
        } catch (Exception e) {
            log.error("Error creating workspace directory for existing session {}: {}", sessionId, e.getMessage(), e);
        }
        
        // Create chat context with autonomous agent prompt
        ChatContext context = new ChatContext();
        ProjectContext projectContext = new ProjectContext();
        projectContext.setProjectPath(workspacePath);
        
        String systemPrompt = agentPromptService.generateSystemPrompt(projectContext);
        context.setSystemPrompt(systemPrompt);
        context.setMessages(new ArrayList<>());
        
        // Add metadata with session ID and workspace path
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("sessionId", sessionId);
        metadata.put("workspacePath", workspacePath);
        context.setMetadata(metadata);
        
        // Create agent state
        AgentState agentState = new AgentState();
        agentState.setAiDeveloperAgentSessionId(sessionId);
        agentState.setProjectContext(projectContext);
        agentState.setMode(ConversationMode.AUTONOMOUS); // Start in autonomous mode for true autonomy
        
        // Set canonical workspace path
        agentState.setCanonicalWorkspacePath(workspacePath);
        
        // Initialize workspace context
        refreshWorkspaceContext(sessionId, workspacePath);
        
        // Store context and state
        sessions.put(sessionId, context);
        agentStates.put(sessionId, agentState);
        
        log.info("Successfully registered existing session: {}", sessionId);
    }
    
    /**
     * Get all sessions
     */
    public List<String> getSessions() {
        return new ArrayList<>(sessions.keySet());
    }
    
    /**
     * Get a specific session
     */
    public ChatContext getSession(String sessionId) {
        ChatContext context = sessions.get(sessionId);
        if (context == null) {
            log.warn("Session not found: {}", sessionId);
            return null;
        }
        
        // Return a defensive copy to prevent modification
        return createDefensiveCopy(context);
    }
    
    /**
     * Create a defensive copy of a chat context
     */
    private ChatContext createDefensiveCopy(ChatContext context) {
        ChatContext copy = new ChatContext();
        copy.setSystemPrompt(context.getSystemPrompt());
        
        // Copy metadata if present
        if (context.getMetadata() != null) {
            copy.setMetadata(new HashMap<>(context.getMetadata()));
        }
        
        List<Message> messagesCopy = new ArrayList<>();
        for (Message message : context.getMessages()) {
            // Ensure no 'tool' role is used in the copy - map to 'assistant' instead
            String role = "tool".equals(message.getRole()) ? "assistant" : message.getRole();
            
            messagesCopy.add(Message.builder()
                    .role(role)
                    .content(message.getContent())
                    .toolCallId(message.getToolCallId())
                    .toolCall(message.getToolCall())
                    .timestamp(message.getTimestamp())
                    .build());
        }
        
        copy.setMessages(messagesCopy);
        return copy;
    }
    
    /**
     * Get session history
     */
    public Mono<List<ChatResponse>> getSessionHistory(String sessionId) {
        log.info("Getting history for session: {}", sessionId);
        ChatContext context = sessions.get(sessionId);
        if (context == null) {
            log.warn("Session not found: {}", sessionId);
            return Mono.error(new IllegalArgumentException("Session not found: " + sessionId));
        }
        
        List<ChatResponse> history = new ArrayList<>();
        for (Message message : context.getMessages()) {
            history.add(ChatResponse.builder()
                    .aiDeveloperAgentSessionId(sessionId)
                    .role(message.getRole())
                    .message(message.getContent())
                    .timestamp(message.getTimestamp())
                    .build());
        }
        
        return Mono.just(history);
    }
    
    /**
     * Delete a session
     */
    public boolean deleteSession(String sessionId) {
        ChatContext removed = sessions.remove(sessionId);
        AgentState removedState = agentStates.remove(sessionId);
        Sinks.Many<ChatResponse> removedSink = sessionSinks.remove(sessionId);
        
        if (removed != null) {
            log.info("Deleted session: {}", sessionId);
            return true;
        }
        
        log.warn("Session not found for deletion: {}", sessionId);
        return false;
    }
    
    /**
     * Process a user message with support for both autonomous and conversational modes
     */
    public Flux<ChatResponse> processMessage(ChatRequest request) {
        String sessionId = request.getAiDeveloperAgentSessionId();
        String message = request.getMessage();
        
        log.info("Processing message for session {}: {}", sessionId, message);
        
        ChatContext context = sessions.get(sessionId);
        AgentState agentState = agentStates.get(sessionId);
        
        if (context == null || agentState == null) {
            log.warn("Session not found: {}", sessionId);
            return Flux.error(new IllegalArgumentException("Session not found: " + sessionId));
        }
        
        // Add user message to context
        context.getMessages().add(Message.builder()
                .role("user")
                .content(message)
                .timestamp(Instant.now())
                .build());
        
        // Track conversation in agent state memory
        List<String> conversationHistory = (List<String>) agentState.getMemory()
                .computeIfAbsent("conversationHistory", k -> new ArrayList<String>());
        conversationHistory.add("User: " + message);
        
        // Analyze user intent
        UserIntent intent = analyzeUserIntent(message, agentState);
        
        // For new tasks, set objective and start autonomous execution
        if (intent == UserIntent.NEW_TASK) {
            agentState.setCurrentObjective(message);
            agentState.setShouldContinue(true);
            agentState.setIterationCount(0);
            agentState.setMode(ConversationMode.AUTONOMOUS);
            
            // Start the autonomous execution loop
            return executeAutonomousAgentLoop(sessionId, context, agentState);
        }
        
        return handleUserIntent(sessionId, context, agentState, intent, message);
    }
    
    /**
     * Analyze user intent to determine response mode
     */
    private UserIntent analyzeUserIntent(String message, AgentState agentState) {
        String lowerMessage = message.toLowerCase();
        
        // Check for mode switches
        if (lowerMessage.contains("stop") || lowerMessage.contains("pause") || lowerMessage.contains("wait")) {
            return UserIntent.PAUSE_EXECUTION;
        }
        
        if (lowerMessage.contains("continue") || lowerMessage.contains("proceed") || lowerMessage.contains("go ahead")) {
            return UserIntent.CONTINUE_EXECUTION;
        }
        
        if (lowerMessage.contains("explain") || lowerMessage.contains("why") || lowerMessage.contains("how")) {
            return UserIntent.REQUEST_EXPLANATION;
        }
        
        if (lowerMessage.contains("change") || lowerMessage.contains("modify") || lowerMessage.contains("update")) {
            return UserIntent.MODIFY_APPROACH;
        }
        
        if (agentState.isWaitingForUserInput() && agentState.getMemory().containsKey("pendingQuestion")) {
            return UserIntent.ANSWER_QUESTION;
        }
        
        if (lowerMessage.contains("status") || lowerMessage.contains("progress")) {
            return UserIntent.CHECK_STATUS;
        }
        
        // Check if this is a new task or continuation
        if (agentState.getCurrentObjective() == null || agentState.getIterationCount() == 0) {
            return UserIntent.NEW_TASK;
        }
        
        return UserIntent.GENERAL_CONVERSATION;
    }
    
    /**
     * Handle different user intents appropriately
     */
    private Flux<ChatResponse> handleUserIntent(String sessionId, ChatContext context, AgentState agentState, 
                                               UserIntent intent, String message) {
        
        log.info("Handling user intent: {} for session {}", intent, sessionId);
        
        switch (intent) {
            case NEW_TASK:
                // Start autonomous execution for new task
                agentState.setCurrentObjective(message);
                agentState.setShouldContinue(true);
                agentState.setIterationCount(0);
                agentState.setMode(ConversationMode.AUTONOMOUS);
                return executeAutonomousAgentLoop(sessionId, context, agentState);
            
            case PAUSE_EXECUTION:
                // Pause autonomous execution
                agentState.setShouldContinue(false);
                agentState.setMode(ConversationMode.CONVERSATIONAL);
                return Flux.just(ChatResponse.builder()
                        .aiDeveloperAgentSessionId(sessionId)
                        .role("assistant")
                        .message("I've paused the execution. Here's what I've completed so far:\n\n" + 
                                generateProgressSummary(agentState) + 
                                "\n\nWhat would you like me to do next?")
                        .timestamp(Instant.now())
                        .build());
            
            case CONTINUE_EXECUTION:
                // Resume autonomous execution
                agentState.setShouldContinue(true);
                agentState.setMode(ConversationMode.AUTONOMOUS);
                agentState.setWaitingForUserInput(false);
                return executeAutonomousAgentLoop(sessionId, context, agentState);
            
            case REQUEST_EXPLANATION:
                // Provide explanation of current approach
                return Flux.just(ChatResponse.builder()
                        .aiDeveloperAgentSessionId(sessionId)
                        .role("assistant")
                        .message("Here's my current approach to solving your task:\n\n" + 
                                generateApproachExplanation(agentState) + 
                                "\n\nWould you like me to continue or modify my approach?")
                        .timestamp(Instant.now())
                        .build());
            
            case MODIFY_APPROACH:
                // Modify approach based on user feedback
                agentState.getMemory().put("userFeedback", message);
                return Flux.just(ChatResponse.builder()
                        .aiDeveloperAgentSessionId(sessionId)
                        .role("assistant")
                        .message("I'll adjust my approach based on your feedback. Here's my updated plan:\n\n" + 
                                generateUpdatedPlan(agentState, message) + 
                                "\n\nShould I proceed with this approach?")
                        .timestamp(Instant.now())
                        .build());
            
            case ANSWER_QUESTION:
                // Process answer to pending question
                agentState.getMemory().put("userAnswer", message);
                agentState.getMemory().remove("pendingQuestion");
                agentState.setWaitingForUserInput(false);
                agentState.setShouldContinue(true);
                agentState.setMode(ConversationMode.AUTONOMOUS);
                return executeAutonomousAgentLoop(sessionId, context, agentState);
            
            case CHECK_STATUS:
                // Provide status update
                return Flux.just(ChatResponse.builder()
                        .aiDeveloperAgentSessionId(sessionId)
                        .role("assistant")
                        .message("Here's the current status of your task:\n\n" + 
                                generateProgressSummary(agentState) + 
                                "\n\nWould you like me to continue?")
                        .timestamp(Instant.now())
                        .build());
            
            case GENERAL_CONVERSATION:
            default:
                // Handle general conversation
                if (agentState.getMode() == ConversationMode.AUTONOMOUS) {
                    // In autonomous mode, treat as feedback and continue
                    agentState.getMemory().put("userFeedback", message);
                    return executeAutonomousAgentLoop(sessionId, context, agentState);
                } else {
                    // In conversational mode, respond directly
                    return processConversationalMessage(sessionId, context, message);
                }
        }
    }
    
    /**
     * Generate progress summary for the current task
     */
    private String generateProgressSummary(AgentState agentState) {
        StringBuilder summary = new StringBuilder();
        
        // Add objective
        summary.append("Objective: ").append(agentState.getCurrentObjective()).append("\n\n");
        
        // Add current phase
        summary.append("Current Phase: ").append(agentState.getCurrentPhase()).append("\n\n");
        
        // Add progress percentage
        summary.append("Progress: ").append(agentState.getProgress()).append("%\n\n");
        
        // Add completed tasks
        if (!agentState.getCompletedTasks().isEmpty()) {
            summary.append("Completed Tasks:\n");
            for (String task : agentState.getCompletedTasks()) {
                summary.append("- ").append(task).append("\n");
            }
            summary.append("\n");
        }
        
        // Add pending tasks
        if (!agentState.getPendingTasks().isEmpty()) {
            summary.append("Pending Tasks:\n");
            for (String task : agentState.getPendingTasks()) {
                summary.append("- ").append(task).append("\n");
            }
            summary.append("\n");
        }
        
        // Add last action
        if (agentState.getLastAction() != null && !agentState.getLastAction().isEmpty()) {
            summary.append("Last Action: ").append(agentState.getLastAction()).append("\n");
        }
        
        return summary.toString();
    }
    
    /**
     * Generate explanation of current approach
     */
    private String generateApproachExplanation(AgentState agentState) {
        StringBuilder explanation = new StringBuilder();
        
        // Add objective
        explanation.append("Objective: ").append(agentState.getCurrentObjective()).append("\n\n");
        
        // Add current phase
        explanation.append("Current Phase: ").append(agentState.getCurrentPhase()).append("\n\n");
        
        // Add approach explanation
        explanation.append("My approach is to break down this task into manageable steps:\n\n");
        
        // Add completed tasks with explanations
        if (!agentState.getCompletedTasks().isEmpty()) {
            explanation.append("Steps I've completed:\n");
            for (String task : agentState.getCompletedTasks()) {
                explanation.append("- ").append(task).append("\n");
            }
            explanation.append("\n");
        }
        
        // Add pending tasks with explanations
        if (!agentState.getPendingTasks().isEmpty()) {
            explanation.append("Steps I'm planning to take next:\n");
            for (String task : agentState.getPendingTasks()) {
                explanation.append("- ").append(task).append("\n");
            }
            explanation.append("\n");
        }
        
        // Add reasoning
        explanation.append("My reasoning is based on best practices for software development, including proper planning, modular design, and thorough testing. I'm using a step-by-step approach to ensure each component works correctly before moving on to the next.");
        
        return explanation.toString();
    }
    
    /**
     * Generate updated plan based on user feedback
     */
    private String generateUpdatedPlan(AgentState agentState, String feedback) {
        StringBuilder plan = new StringBuilder();
        
        // Add objective
        plan.append("Objective: ").append(agentState.getCurrentObjective()).append("\n\n");
        
        // Add feedback acknowledgment
        plan.append("Based on your feedback: \"").append(feedback).append("\"\n\n");
        
        // Add updated approach
        plan.append("I'll adjust my approach as follows:\n\n");
        
        // Modify pending tasks based on feedback
        List<String> pendingTasks = new ArrayList<>(agentState.getPendingTasks());
        
        // Simple heuristic to modify tasks based on feedback
        String lowerFeedback = feedback.toLowerCase();
        if (lowerFeedback.contains("add") || lowerFeedback.contains("include")) {
            // Add a new task based on feedback
            pendingTasks.add("Incorporate " + feedback.replaceAll("(?i)add|include", "").trim());
        } else if (lowerFeedback.contains("remove") || lowerFeedback.contains("skip")) {
            // Remove tasks that match the feedback
            pendingTasks.removeIf(task -> 
                    task.toLowerCase().contains(lowerFeedback.replaceAll("(?i)remove|skip", "").trim()));
        } else if (lowerFeedback.contains("change") || lowerFeedback.contains("modify")) {
            // Modify existing tasks
            for (int i = 0; i < pendingTasks.size(); i++) {
                String task = pendingTasks.get(i);
                if (task.toLowerCase().contains(lowerFeedback.replaceAll("(?i)change|modify", "").trim())) {
                    pendingTasks.set(i, "Modified: " + task + " (based on feedback)");
                }
            }
        }
        
        // Update agent state with modified tasks
        agentState.setPendingTasks(pendingTasks);
        
        // Add updated pending tasks
        if (!pendingTasks.isEmpty()) {
            plan.append("Updated plan:\n");
            for (String task : pendingTasks) {
                plan.append("- ").append(task).append("\n");
            }
            plan.append("\n");
        }
        
        return plan.toString();
    }
    
    /**
     * Process message in conversational mode
     */
    private Flux<ChatResponse> processConversationalMessage(String sessionId, ChatContext context, String message) {
        log.info("Processing conversational message for session {}: {}", sessionId, message);
        
        // Create sink for streaming response
        Sinks.Many<ChatResponse> sink = Sinks.many().multicast().onBackpressureBuffer();
        sessionSinks.put(sessionId, sink);
        
        // Stream LLM response
        llmProvider.streamResponse(message, context)
            .doOnNext(chunk -> {
                // Stream chunk to user
                sink.tryEmitNext(ChatResponse.builder()
                        .aiDeveloperAgentSessionId(sessionId)
                        .role("assistant")
                        .message(chunk)
                        .timestamp(Instant.now())
                        .build());
            })
            .doOnComplete(() -> {
                log.info("Completed LLM response for session {}", sessionId);
                sink.tryEmitComplete();
            })
            .doOnError(error -> {
                log.error("Error in LLM response for session {}: {}", sessionId, error.getMessage(), error);
                
                // Emit error to user
                sink.tryEmitNext(ChatResponse.builder()
                        .aiDeveloperAgentSessionId(sessionId)
                        .role("assistant")
                        .message("I encountered an error: " + error.getMessage())
                        .timestamp(Instant.now())
                        .build());
                
                sink.tryEmitComplete();
            })
            .subscribe();
        
        return sink.asFlux();
    }
    
    /**
     * Execute autonomous agent loop with ReAct paradigm
     */
    private Flux<ChatResponse> executeAutonomousAgentLoop(String sessionId, ChatContext context, AgentState agentState) {
        log.info("Executing autonomous agent loop for session {}", sessionId);
        
        // Create sink for streaming response
        Sinks.Many<ChatResponse> sink = Sinks.many().multicast().onBackpressureBuffer();
        sessionSinks.put(sessionId, sink);
        
        // Create iteration counter
        AtomicInteger iterationCount = new AtomicInteger(0);
        
        // Start autonomous execution in a separate thread
        Thread autonomousThread = new Thread(() -> {
            executeAutonomousLoop(sessionId, context, agentState, sink, iterationCount);
        });
        autonomousThread.setName("autonomous-" + sessionId);
        autonomousThread.start();
        
        return sink.asFlux();
    }
    
    /**
     * Execute autonomous loop with ReAct paradigm
     */
    private void executeAutonomousLoop(String sessionId, ChatContext context, AgentState agentState, 
                                      Sinks.Many<ChatResponse> sink, AtomicInteger iterationCount) {
        log.info("Starting autonomous loop for session {}", sessionId);
        
        // Set should continue flag
        agentState.setShouldContinue(true);
        
        while (agentState.isShouldContinue() && iterationCount.get() < MAX_AUTONOMOUS_ITERATIONS) {
            // Check if execution is paused
            if (!agentState.isShouldContinue()) {
                log.info("Autonomous execution paused for session {}", sessionId);
                break;
            }
            
            // Update iteration count
            agentState.setIterationCount(iterationCount.incrementAndGet());
            log.info("Autonomous iteration {} for session {}", iterationCount.get(), sessionId);
            
            // Generate next step with ReAct paradigm
            try {
                // Create a copy of the context for the LLM request
                ChatContext requestContext = createDefensiveCopy(context);
                
                // Add ReAct prompt
                String reactPrompt = agentPromptService.generateReActPrompt(agentState);
                
                // Stream LLM response with real-time processing
                final StringBuilder responseBuilder = new StringBuilder();
                final AtomicBoolean hasToolUse = new AtomicBoolean(false);
                
                llmProvider.streamResponse(reactPrompt, requestContext)
                    .doOnNext(chunk -> {
                        // Append chunk to response builder
                        responseBuilder.append(chunk);
                        
                        // Check for tool use pattern
                        if (chunk.contains("<tool_use>") || chunk.contains("</tool_use>") || 
                            chunk.contains("\"type\":\"tool_use\"")) {
                            hasToolUse.set(true);
                        } else {
                            // Stream non-tool chunks to user in real-time
                            sink.tryEmitNext(ChatResponse.builder()
                                    .aiDeveloperAgentSessionId(sessionId)
                                    .role("assistant")
                                    .message(chunk)
                                    .timestamp(Instant.now())
                                    .build());
                        }
                    })
                    .doOnComplete(() -> {
                        String completeResponse = responseBuilder.toString();
                        log.info("Completed LLM response for iteration {}", iterationCount.get());
                        
                        // Add assistant message to context
                        context.getMessages().add(Message.builder()
                                .role("assistant")
                                .content(completeResponse)
                                .timestamp(Instant.now())
                                .build());
                        
                        // Track conversation in agent state memory
                        List<String> conversationHistory = (List<String>) agentState.getMemory()
                                .computeIfAbsent("conversationHistory", k -> new ArrayList<String>());
                        conversationHistory.add("Assistant: " + completeResponse);
                        
                        // Process the response
                        processAutonomousResponse(sessionId, context, agentState, completeResponse, sink);
                        
                        // Check if task is complete
                        if (isTaskComplete(completeResponse)) {
                            log.info("Task complete for session {}", sessionId);
                            agentState.setShouldContinue(false);
                            return;
                        }
                        
                        // Check if user input is needed
                        if (needsUserInput(completeResponse) && !hasToolUse.get()) {
                            log.info("User input needed for session {}", sessionId);
                            agentState.setWaitingForUserInput(true);
                            agentState.setShouldContinue(false);
                            
                            // Extract question for future reference
                            String question = extractQuestion(completeResponse);
                            agentState.getMemory().put("pendingQuestion", question);
                            
                            return;
                        }
                        
                        // Continue to next iteration if execution should continue
                        if (agentState.isShouldContinue()) {
                            // Small delay to prevent tight loops
                            try {
                                Thread.sleep(1000);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                        }
                    })
                    .doOnError(error -> {
                        log.error("Error in LLM response for session {}: {}", sessionId, error.getMessage(), error);
                        
                        // Emit error to user
                        sink.tryEmitNext(ChatResponse.builder()
                                .aiDeveloperAgentSessionId(sessionId)
                                .role("assistant")
                                .message("I encountered an error: " + error.getMessage() + "\nI'll try a different approach.")
                                .timestamp(Instant.now())
                                .build());
                        
                        // Continue to next iteration with error recovery
                        agentState.setLastAction("Recovered from error: " + error.getMessage());
                        
                        // Small delay to prevent tight loops
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    })
                    .blockLast(); // Use blockLast() instead of subscribe() to ensure the streaming completes before continuing
                
            } catch (Exception e) {
                log.error("Error in autonomous iteration for session {}: {}", sessionId, e.getMessage(), e);
                
                // Emit error to user
                sink.tryEmitNext(ChatResponse.builder()
                        .aiDeveloperAgentSessionId(sessionId)
                        .role("assistant")
                        .message("I encountered an error: " + e.getMessage() + "\nI'll try a different approach.")
                        .timestamp(Instant.now())
                        .build());
                
                // Continue to next iteration with error recovery
                agentState.setLastAction("Recovered from error: " + e.getMessage());
                
                // Small delay to prevent tight loops
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        
        // Check if max iterations reached
        if (iterationCount.get() >= MAX_AUTONOMOUS_ITERATIONS) {
            log.warn("Max iterations reached for session {}", sessionId);
            
            // Emit warning to user
            sink.tryEmitNext(ChatResponse.builder()
                    .aiDeveloperAgentSessionId(sessionId)
                    .role("assistant")
                    .message("I've reached the maximum number of iterations. Here's what I've accomplished so far:\n\n" + 
                            generateProgressSummary(agentState) + 
                            "\n\nWould you like me to continue?")
                    .timestamp(Instant.now())
                    .build());
            
            // Pause execution
            agentState.setShouldContinue(false);
        }
    }
    
    /**
     * Process autonomous response with ReAct paradigm
     */
    private void processAutonomousResponse(String sessionId, ChatContext context, AgentState agentState, 
                                          String llmResponse, Sinks.Many<ChatResponse> sink) {
        log.info("Processing autonomous response for session {}", sessionId);
        
        // Extract tool calls
        List<String> toolCalls = extractToolCalls(llmResponse);
        
        if (toolCalls.isEmpty()) {
            // No tool calls, treat as regular response
            log.info("No tool calls found in response for session {}", sessionId);
            
            // Check for events
            processEvents(sessionId, agentState, llmResponse);
            
            return;
        }
        
        // Process tool calls
        for (String toolCallJson : toolCalls) {
            try {
                // Parse tool call
                ToolCall toolCall = objectMapper.readValue(toolCallJson, ToolCall.class);
                
                // Get tool
                Tool tool = toolRegistry.getTool(toolCall.getName());
                if (tool == null) {
                    log.warn("Tool not found: {}", toolCall.getName());
                    
                    // Emit error to user
                    sink.tryEmitNext(ChatResponse.builder()
                            .aiDeveloperAgentSessionId(sessionId)
                            .role("assistant")
                            .message("I tried to use a tool that doesn't exist: " + toolCall.getName())
                            .timestamp(Instant.now())
                            .build());
                    
                    continue;
                }
                
                // Execute tool
                log.info("Executing tool {} for session {}", toolCall.getName(), sessionId);
                
                // Get workspace path from agent state
                String workspacePath = agentState.getCanonicalWorkspacePath();
                if (workspacePath == null || workspacePath.isEmpty()) {
                    workspacePath = DEFAULT_WORKSPACE_PATH + "/" + sessionId;
                    log.warn("Canonical workspace path not set, using default: {}", workspacePath);
                    agentState.setCanonicalWorkspacePath(workspacePath);
                }
                
                // Set last action
                agentState.setLastAction("Executing tool: " + toolCall.getName());
                
                // Execute tool
                Flux<ToolOutput> toolOutputFlux = tool.execute(toolCall.getArguments());
                
                // Process tool output
                // Capture the workspacePath as final to use in lambda
                final String finalWorkspacePath = workspacePath;
                
                toolOutputFlux.collectList().subscribe(toolOutputs -> {
                    try {
                        // Combine tool outputs
                        StringBuilder outputBuilder = new StringBuilder();
                        for (ToolOutput output : toolOutputs) {
                            outputBuilder.append(output.getContent()).append("\n");
                        }
                        String combinedOutput = outputBuilder.toString().trim();
                        
                        // Send tool output to websocket
                        webSocketHandler.broadcastToolOutput(Map.of(
                                "sessionId", sessionId,
                                "toolName", toolCall.getName(),
                                "arguments", toolCall.getArguments(),
                                "output", combinedOutput,
                                "timestamp", Instant.now().toString()
                        ));
                        
                        // Add tool call and output to context
                        context.getMessages().add(Message.builder()
                                .role("assistant")
                                .content("<tool_use>" + toolCallJson + "</tool_use>")
                                .timestamp(Instant.now())
                                .build());
                        
                        context.getMessages().add(Message.builder()
                                .role("tool")
                                .content(combinedOutput)
                                .timestamp(Instant.now())
                                .build());
                        
                        // Track tool use in agent state memory
                        List<Map<String, Object>> toolUses = (List<Map<String, Object>>) agentState.getMemory()
                                .computeIfAbsent("toolUses", k -> new ArrayList<Map<String, Object>>());
                        
                        Map<String, Object> toolUseRecord = new HashMap<>();
                        toolUseRecord.put("tool", toolCall.getName());
                        toolUseRecord.put("arguments", toolCall.getArguments());
                        toolUseRecord.put("output", combinedOutput);
                        toolUseRecord.put("timestamp", Instant.now().toString());
                        
                        toolUses.add(toolUseRecord);
                        
                        // Refresh workspace context after tool execution
                        refreshWorkspaceContext(sessionId, finalWorkspacePath);
                        
                    } catch (Exception e) {
                        log.error("Error processing tool output for session {}: {}", sessionId, e.getMessage(), e);
                    }
                });
                
            } catch (Exception e) {
                log.error("Error executing tool for session {}: {}", sessionId, e.getMessage(), e);
                
                // Emit error to user
                sink.tryEmitNext(ChatResponse.builder()
                        .aiDeveloperAgentSessionId(sessionId)
                        .role("assistant")
                        .message("I encountered an error executing a tool: " + e.getMessage())
                        .timestamp(Instant.now())
                        .build());
            }
        }
    }
    
    /**
     * Extract tool calls from LLM response
     */
    private List<String> extractToolCalls(String llmResponse) {
        List<String> toolCalls = new ArrayList<>();
        
        Matcher matcher = TOOL_USE_PATTERN.matcher(llmResponse);
        while (matcher.find()) {
            String toolCallJson = matcher.group(1);
            if (toolCallJson != null) {
                toolCalls.add(toolCallJson);
            } else {
                // Handle Claude 3 format
                String fullMatch = matcher.group(0);
                if (fullMatch.contains("\"type\":\"tool_use\"")) {
                    // Extract the JSON from the content block
                    int startIndex = fullMatch.indexOf("{");
                    int endIndex = fullMatch.lastIndexOf("}") + 1;
                    if (startIndex >= 0 && endIndex > startIndex) {
                        String jsonBlock = fullMatch.substring(startIndex, endIndex);
                        toolCalls.add(jsonBlock);
                    }
                }
            }
        }
        
        return toolCalls;
    }
    
    /**
     * Process events from LLM response
     */
    private void processEvents(String sessionId, AgentState agentState, String llmResponse) {
        Matcher matcher = EVENT_PATTERN.matcher(llmResponse);
        while (matcher.find()) {
            String eventType = matcher.group(1);
            String eventData = matcher.group(2);
            
            log.info("Processing event {} for session {}: {}", eventType, sessionId, eventData);
            
            switch (eventType) {
                case "TASK_COMPLETE":
                    agentState.setShouldContinue(false);
                    break;
                case "SET_GOAL":
                    agentState.setCurrentObjective(eventData);
                    break;
                case "ADD_MEMORY":
                    String[] memoryParts = eventData.split(":", 2);
                    if (memoryParts.length == 2) {
                        agentState.getMemory().put(memoryParts[0].trim(), memoryParts[1].trim());
                    }
                    break;
                default:
                    log.warn("Unknown event type: {}", eventType);
                    break;
            }
        }
    }
    
    /**
     * Check if task is complete based on response
     */
    private boolean isTaskComplete(String response) {
        return COMPLETION_PATTERN.matcher(response).find();
    }
    
    /**
     * Check if user input is needed based on response
     */
    private boolean needsUserInput(String response) {
        // More selective pattern to prevent false positives during autonomous execution
        
        // Split response into paragraphs
        String[] paragraphs = response.split("\n\n");
        
        // Check only the last paragraph for direct questions
        if (paragraphs.length > 0) {
            String lastParagraph = paragraphs[paragraphs.length - 1];
            
            // Check for explicit user input requests
            if (lastParagraph.contains("Please provide") || 
                lastParagraph.contains("I need your input") ||
                lastParagraph.contains("Please let me know") ||
                (lastParagraph.contains("?") && 
                 (lastParagraph.contains("you") || lastParagraph.contains("your")))) {
                
                return true;
            }
        }
        
        // Default to not needing user input
        return false;
    }
    
    /**
     * Extract question from response
     */
    private String extractQuestion(String response) {
        // Split response into paragraphs
        String[] paragraphs = response.split("\n\n");
        
        // Check paragraphs from end to beginning
        for (int i = paragraphs.length - 1; i >= 0; i--) {
            String paragraph = paragraphs[i];
            if (paragraph.contains("?")) {
                return paragraph;
            }
        }
        
        // Default to last paragraph if no question found
        if (paragraphs.length > 0) {
            return paragraphs[paragraphs.length - 1];
        }
        
        return response;
    }
    
    /**
     * Execute tool directly
     */
    public Flux<ToolOutput> executeTool(String sessionId, String toolName, Map<String, Object> arguments) {
        log.info("Executing tool {} for session {}", toolName, sessionId);
        
        // Get tool
        Tool tool = toolRegistry.getTool(toolName);
        if (tool == null) {
            log.warn("Tool not found: {}", toolName);
            return Flux.error(new IllegalArgumentException("Tool not found: " + toolName));
        }
        
        // Get workspace path from agent state
        AgentState agentState = agentStates.get(sessionId);
        final String workspacePath;
        
        if (agentState != null) {
            String path = agentState.getCanonicalWorkspacePath();
            if (path == null || path.isEmpty()) {
                path = DEFAULT_WORKSPACE_PATH + "/" + sessionId;
                log.warn("Canonical workspace path not set, using default: {}", path);
                agentState.setCanonicalWorkspacePath(path);
            }
            workspacePath = path;
        } else {
            workspacePath = DEFAULT_WORKSPACE_PATH + "/" + sessionId;
            log.warn("Agent state not found for session {}, using default workspace path: {}", sessionId, workspacePath);
        }
        
        // Execute tool and return as Flux
        final AgentState finalAgentState = agentState;
        return tool.execute(arguments)
                .doOnNext(output -> {
                    // Refresh workspace context after tool execution
                    if (finalAgentState != null) {
                        refreshWorkspaceContext(sessionId, workspacePath);
                    }
                });
    }
}
