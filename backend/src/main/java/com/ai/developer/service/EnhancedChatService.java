package com.ai.developer.service;

import com.ai.developer.service.AgentControlService;
import com.ai.developer.model.*;
import com.ai.developer.llm.LLMProvider;
import com.ai.developer.llm.ChatContext;
import com.ai.developer.llm.Message;
import com.ai.developer.llm.ToolCall;
import com.ai.developer.llm.ProjectContext;
import com.ai.developer.tools.Tool;
import com.ai.developer.tools.ToolOutput;
import com.ai.developer.tools.ToolRegistry;
import com.ai.developer.exception.ClaudeApiException;
import com.ai.developer.exception.ClaudeRateLimitException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.io.File;
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
    private final AgentPromptService agentPromptService;
    
    private final ConcurrentHashMap<String, ChatContext> sessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AgentState> agentStates = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Sinks.Many<ChatResponse>> sessionSinks = new ConcurrentHashMap<>();
    
    // Default workspace path for tools
    private static final String DEFAULT_WORKSPACE_PATH = "/tmp/ai-developer-agent";
    
    // Session workspace root folder parameter name for workspace management
    private static final String SESSION_WORKSPACE_ROOT_FOLDER_PARAM = "sessionWorkspaceRootFolder";
    
    // Patterns for parsing responses
    // Patterns for parsing responses
    private static final Pattern TOOL_USE_PATTERN = Pattern.compile("<tool_use>(.*?)</tool_use>|\\{\"type\":\"content_block_start\".*?\"type\":\"tool_use\".*?\\}", Pattern.DOTALL);
    private static final Pattern COMPLETION_PATTERN = Pattern.compile("(?i)(task complete|objectives? (?:met|achieved|completed)|all (?:done|finished)|nothing (?:more|else) to do)");
    private static final Pattern QUESTION_PATTERN = Pattern.compile("(?i)(\\?|would you like|should i|do you want|can you clarify|need more information|what about)");
    private static final Pattern EVENT_PATTERN = Pattern.compile("EVENT:([^:]+):(.+)");
    
    // Maximum iterations to prevent infinite loops
    private static final int MAX_AUTONOMOUS_ITERATIONS = 100;
    
    public EnhancedChatService(LLMProvider llmProvider, ToolRegistry toolRegistry, ObjectMapper objectMapper, 
                      AgentPromptService agentPromptService) {
        this.llmProvider = llmProvider;
        this.toolRegistry = toolRegistry;
        this.objectMapper = objectMapper;
        this.agentPromptService = agentPromptService;
        
        log.info("EnhancedChatService initialized with simplified tool output handling");
    }
    
    /**
     * Create a new chat session with proper workspace management
     */
    public Mono<SessionResponse> createSession() {
        String sessionId = UUID.randomUUID().toString();
        log.info("Created new session: {}", sessionId);
        
        // Fix: Proper workspace management - use aiDeveloperAgentSessionId for workspace paths
        String sessionWorkspacePath = DEFAULT_WORKSPACE_PATH + "/" + sessionId;
        
        // Create session workspace directory
        try {
            Files.createDirectories(Path.of(sessionWorkspacePath));
            log.info("Created workspace directory for session {}: {}", sessionId, sessionWorkspacePath);
        } catch (Exception e) {
            log.error("Error creating workspace directory for session {}: {}", sessionId, e.getMessage(), e);
        }
        
        // Create chat context with autonomous agent prompt
        ChatContext context = new ChatContext();
        ProjectContext projectContext = new ProjectContext();
        projectContext.setProjectPath(sessionWorkspacePath);
        
        String systemPrompt = agentPromptService.generateSystemPrompt(projectContext);
        context.setSystemPrompt(systemPrompt);
        context.setMessages(new ArrayList<>());
        
        // Add metadata with session ID and workspace path
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("sessionId", sessionId);
        metadata.put(SESSION_WORKSPACE_ROOT_FOLDER_PARAM, DEFAULT_WORKSPACE_PATH);
        metadata.put("workspacePath", sessionWorkspacePath);
        context.setMetadata(metadata);
        
        // Create agent state
        AgentState agentState = new AgentState();
        agentState.setAiDeveloperAgentSessionId(sessionId);
        agentState.setProjectContext(projectContext);
        agentState.setMode(ConversationMode.AUTONOMOUS); // Start in autonomous mode for true autonomy
        
        // Set canonical workspace path
        agentState.setCanonicalWorkspacePath(sessionWorkspacePath);
        
        // Initialize workspace context
        refreshWorkspaceContext(sessionId, sessionWorkspacePath);
        
        // Store context and state
        sessions.put(sessionId, context);
        agentStates.put(sessionId, agentState);
        
        return Mono.just(SessionResponse.builder()
                .aiDeveloperAgentSessionId(sessionId)
                .createdAt(Instant.now())
                .workspacePath(sessionWorkspacePath)
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
        String sessionWorkspacePath = DEFAULT_WORKSPACE_PATH + "/" + sessionId;
        try {
            Files.createDirectories(Path.of(sessionWorkspacePath));
            log.info("Created workspace directory for existing session {}: {}", sessionId, sessionWorkspacePath);
        } catch (Exception e) {
            log.error("Error creating workspace directory for existing session {}: {}", sessionId, e.getMessage(), e);
        }
        
        // Create chat context with autonomous agent prompt
        ChatContext context = new ChatContext();
        ProjectContext projectContext = new ProjectContext();
        projectContext.setProjectPath(sessionWorkspacePath);
        
        String systemPrompt = agentPromptService.generateSystemPrompt(projectContext);
        context.setSystemPrompt(systemPrompt);
        context.setMessages(new ArrayList<>());
        
        // Add metadata with session ID and workspace path
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("sessionId", sessionId);
        metadata.put("workspacePath", sessionWorkspacePath);
        context.setMetadata(metadata);
        
        // Create agent state
        AgentState agentState = new AgentState();
        agentState.setAiDeveloperAgentSessionId(sessionId);
        agentState.setProjectContext(projectContext);
        agentState.setMode(ConversationMode.AUTONOMOUS); // Start in autonomous mode for true autonomy
        
        // Set canonical workspace path
        agentState.setCanonicalWorkspacePath(sessionWorkspacePath);
        
        // Initialize workspace context
        refreshWorkspaceContext(sessionId, sessionWorkspacePath);
        
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
        
        // Send stream-start event
        sink.tryEmitNext(ChatResponse.builder()
                .aiDeveloperAgentSessionId(sessionId)
                .role("assistant")
                .messageType("stream-start")
                .message("")
                .timestamp(Instant.now())
                .build());
        
        // StringBuilder to accumulate streaming content
        StringBuilder accumulatedContent = new StringBuilder();
        
        // Stream LLM response
        llmProvider.streamResponse(message, context)
            .doOnNext(chunk -> {
                // Accumulate the chunk
                accumulatedContent.append(chunk);
                
                // Stream accumulated content to user
                sink.tryEmitNext(ChatResponse.builder()
                        .aiDeveloperAgentSessionId(sessionId)
                        .role("assistant")
                        .messageType("message")
                        .message(accumulatedContent.toString())  // Send accumulated content
                        .timestamp(Instant.now())
                        .build());
                
                // Small delay to throttle SSE streaming and prevent browser overwhelm
                try {
                    Thread.sleep(200);  // 200ms delay between SSE chunks
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            })
            .doOnComplete(() -> {
                log.info("Completed LLM response for session {}", sessionId);
                
                // Send stream-end event
                sink.tryEmitNext(ChatResponse.builder()
                        .aiDeveloperAgentSessionId(sessionId)
                        .role("assistant")
                        .messageType("stream-end")
                        .message("")
                        .timestamp(Instant.now())
                        .build());
                
                sink.tryEmitComplete();
            })
            .doOnError(error -> {
                log.error("Error in LLM response for session {}: {}", sessionId, error.getMessage(), error);
                
                // Emit error to user
                sink.tryEmitNext(ChatResponse.builder()
                        .aiDeveloperAgentSessionId(sessionId)
                        .role("assistant")
                        .messageType("message")
                        .message("I encountered an error: " + error.getMessage())
                        .timestamp(Instant.now())
                        .build());
                
                // Send stream-end event even on error
                sink.tryEmitNext(ChatResponse.builder()
                        .aiDeveloperAgentSessionId(sessionId)
                        .role("assistant")
                        .messageType("stream-end")
                        .message("")
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
                            // Stream all content chunks to user in real-time (including thinking, analysis, etc.)
                            sink.tryEmitNext(ChatResponse.builder()
                                    .aiDeveloperAgentSessionId(sessionId)
                                    .role("assistant")
                                    .message(chunk)
                                    .messageType(determineMessageType(chunk))
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
                        
                        // CRITICAL FIX: Process events FIRST before other completion checks
                        processEvents(sessionId, agentState, completeResponse);
                        
                        // Check if events set shouldContinue to false (e.g., TASK_COMPLETE)
                        if (!agentState.isShouldContinue()) {
                            log.info("Task completion detected via events for session {}", sessionId);
                            return;
                        }
                        
                        // Process the response for tool calls
                        processAutonomousResponse(sessionId, context, agentState, completeResponse, sink);
                        
                        // Check if events processing in tool execution set shouldContinue to false
                        if (!agentState.isShouldContinue()) {
                            log.info("Task completion detected during tool processing for session {}", sessionId);
                            return;
                        }
                        
                        // Check if task is complete using pattern matching
                        if (isTaskComplete(completeResponse)) {
                            log.info("Task complete detected via pattern matching for session {}", sessionId);
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
                            log.debug("Continuing autonomous execution for session {} - iteration {}", sessionId, iterationCount.get());
                            // Small delay to prevent tight loops
                            try {
                                Thread.sleep(1000);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                        } else {
                            log.info("Autonomous execution stopping for session {} after iteration {}", sessionId, iterationCount.get());
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
            
            // NOTE: Event processing is now handled in doOnComplete() before this method
            // No need to process events here to avoid duplication
            
            return;
        }
        
        // Process tool calls
        for (String toolCallJson : toolCalls) {
            try {
                // Normalize JSON keys: change "args" and "input" to "arguments"
                String normalizedJson = toolCallJson
                    .replaceAll("\"args\"\\s*:", "\"arguments\":")
                    .replaceAll("\"input\"\\s*:", "\"arguments\":");
                
                // Parse tool call with normalized JSON
                ToolCall toolCall = objectMapper.readValue(normalizedJson, ToolCall.class);
                
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
                log.info("TOOL_EXECUTION: Starting execution of tool '{}' for session '{}' with arguments: {}", 
                    toolCall.getName(), sessionId, toolCall.getArguments());
                
                // Get workspace path from agent state
                String workspacePath = agentState.getCanonicalWorkspacePath();
                if (workspacePath == null || workspacePath.isEmpty()) {
                    workspacePath = DEFAULT_WORKSPACE_PATH + "/" + sessionId;
                    log.warn("TOOL_EXECUTION: Canonical workspace path not set, using default: {}", workspacePath);
                    agentState.setCanonicalWorkspacePath(workspacePath);
                }
                
                log.info("TOOL_EXECUTION: Using workspace path: {}", workspacePath);
                
                // Inject session ID and workspace path into tool arguments
                Map<String, Object> enhancedArguments = new HashMap<>(toolCall.getArguments());
                enhancedArguments.put("aiDeveloperAgentSessionId", sessionId);
                enhancedArguments.put("sessionWorkspaceRootFolder", sessionId);
                enhancedArguments.put("sessionId", sessionId);
                
                log.info("TOOL_EXECUTION: Enhanced arguments with session ID: {}", enhancedArguments);
                
                // Set last action
                agentState.setLastAction("Executing tool: " + toolCall.getName());
                
                // Execute tool with enhanced arguments
                Flux<ToolOutput> toolOutputFlux = tool.execute(enhancedArguments);
                
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
                        
                        log.info("TOOL_RESULT: Tool '{}' completed successfully. Output length: {} characters", 
                            toolCall.getName(), combinedOutput.length());
                        log.debug("TOOL_RESULT: Full output for '{}': {}", toolCall.getName(), combinedOutput);
                        
                        // Verify workspace files if this was a file-writing tool
                        if (toolCall.getName().equals("planning_tool") || toolCall.getName().equals("file_system")) {
                            verifyWorkspaceFiles(finalWorkspacePath, toolCall.getName());
                        }
                        
                        // Tool results are now handled via chat SSE stream
                        // No separate tool event broadcasting needed
                        
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
                        
                        // ✅ NEW: Emit tool output via SSE for emulator
                        sink.tryEmitNext(ChatResponse.builder()
                                .aiDeveloperAgentSessionId(sessionId)
                                .role("tool")
                                .message(combinedOutput)
                                .toolCallId(toolCall.getId())
                                .messageType("tool_result")
                                .timestamp(Instant.now())
                                .build());
                        
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
        
        log.debug("TOOL_EXTRACTION: Analyzing response for tool calls: {}", llmResponse.substring(0, Math.min(200, llmResponse.length())));
        
        Matcher matcher = TOOL_USE_PATTERN.matcher(llmResponse);
        while (matcher.find()) {
            String toolCallJson = matcher.group(1);
            if (toolCallJson != null) {
                log.info("TOOL_CALL: Found XML format tool call: {}", toolCallJson);
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
                        log.info("TOOL_CALL: Found Claude 3 format tool call: {}", jsonBlock);
                        toolCalls.add(jsonBlock);
                    }
                }
            }
        }
        
        if (toolCalls.isEmpty()) {
            log.warn("TOOL_EXTRACTION: No tool calls found in response. Response contains: thinking={}, tool_use={}, analysis={}", 
                llmResponse.contains("<thinking>"), 
                llmResponse.contains("<tool_use>"), 
                llmResponse.contains("<analysis>"));
        } else {
            log.info("TOOL_EXTRACTION: Successfully extracted {} tool calls", toolCalls.size());
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
                    log.info("TASK_COMPLETE event: Setting shouldContinue to false for session {}", sessionId);
                    agentState.setShouldContinue(false);
                    agentState.setLastAction("Task completed: " + eventData);
                    break;
                    
                case "PROGRESS":
                    log.info("PROGRESS event: Updating progress for session {}: {}", sessionId, eventData);
                    agentState.getMemory().put("currentProgress", eventData);
                    agentState.setLastAction("Progress update: " + eventData);
                    break;
                    
                case "PHASE_TRANSITION":
                    log.info("PHASE_TRANSITION event: Phase transition for session {}: {}", sessionId, eventData);
                    agentState.getMemory().put("currentPhase", eventData);
                    agentState.setLastAction("Phase transition: " + eventData);
                    
                    // Check if phase transition indicates completion
                    if ("COMPLETED".equalsIgnoreCase(eventData) || "COMPLETE".equalsIgnoreCase(eventData)) {
                        log.info("PHASE_TRANSITION indicates completion: Setting shouldContinue to false for session {}", sessionId);
                        agentState.setShouldContinue(false);
                    }
                    break;
                    
                case "SET_GOAL":
                    log.info("SET_GOAL event: Setting goal for session {}: {}", sessionId, eventData);
                    agentState.setCurrentObjective(eventData);
                    agentState.setLastAction("Goal set: " + eventData);
                    break;
                    
                case "ADD_MEMORY":
                    String[] memoryParts = eventData.split(":", 2);
                    if (memoryParts.length == 2) {
                        String key = memoryParts[0].trim();
                        String value = memoryParts[1].trim();
                        log.info("ADD_MEMORY event: Adding memory for session {}: {} = {}", sessionId, key, value);
                        agentState.getMemory().put(key, value);
                        agentState.setLastAction("Memory added: " + key + " = " + value);
                    } else {
                        log.warn("ADD_MEMORY event: Invalid format for session {}: {}", sessionId, eventData);
                    }
                    break;
                    
                case "ERROR":
                    log.warn("ERROR event: Error reported for session {}: {}", sessionId, eventData);
                    agentState.getMemory().put("lastError", eventData);
                    agentState.setLastAction("Error: " + eventData);
                    break;
                    
                case "PAUSE":
                    log.info("PAUSE event: Pausing execution for session {}: {}", sessionId, eventData);
                    agentState.setShouldContinue(false);
                    agentState.setWaitingForUserInput(true);
                    agentState.setLastAction("Paused: " + eventData);
                    break;
                    
                case "RESUME":
                    log.info("RESUME event: Resuming execution for session {}: {}", sessionId, eventData);
                    agentState.setShouldContinue(true);
                    agentState.setWaitingForUserInput(false);
                    agentState.setLastAction("Resumed: " + eventData);
                    break;
                    
                case "STEP_COMPLETE":
                    log.info("STEP_COMPLETE event: Step completed for session {}: {}", sessionId, eventData);
                    agentState.getMemory().put("lastCompletedStep", eventData);
                    agentState.setLastAction("Step completed: " + eventData);
                    break;
                    
                case "OBJECTIVE_UPDATE":
                    log.info("OBJECTIVE_UPDATE event: Updating objective for session {}: {}", sessionId, eventData);
                    agentState.setCurrentObjective(eventData);
                    agentState.setLastAction("Objective updated: " + eventData);
                    break;
                    
                default:
                    log.warn("Unknown event type: {} with data: {} for session {}", eventType, eventData, sessionId);
                    // Store unknown events in memory for debugging
                    List<String> unknownEvents = (List<String>) agentState.getMemory()
                            .computeIfAbsent("unknownEvents", k -> new ArrayList<String>());
                    unknownEvents.add(eventType + ":" + eventData);
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

    /**
     * Process human input response for human-in-the-loop workflow
     */
    public void processHumanInputResponse(HumanInputResponse humanInputResponse, org.springframework.web.servlet.mvc.method.annotation.SseEmitter emitter) {
        String sessionId = humanInputResponse.getAiDeveloperAgentSessionId();
        String response = humanInputResponse.getResponse();
        
        log.info("Processing human input response for session: {}, response: {}", sessionId, response);
        
        try {
            // Send the human response back to the chat flow
            emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event()
                    .name("human-input-response")
                    .data(response));
            
            emitter.complete();
        } catch (Exception e) {
            log.error("Error processing human input response for session: {}", sessionId, e);
            emitter.completeWithError(e);
        }
    }
    
    /**
     * Verify workspace files after tool execution
     */
    private void verifyWorkspaceFiles(String workspacePath, String toolName) {
        try {
            File workspaceDir = new File(workspacePath);
            if (!workspaceDir.exists()) {
                log.warn("WORKSPACE_VERIFY: Workspace directory does not exist: {}", workspacePath);
                return;
            }
            
            File[] files = workspaceDir.listFiles();
            if (files == null || files.length == 0) {
                log.warn("WORKSPACE_VERIFY: No files found in workspace after {} execution: {}", toolName, workspacePath);
                return;
            }
            
            log.info("WORKSPACE_VERIFY: Found {} files in workspace after {} execution:", files.length, toolName);
            for (File file : files) {
                if (file.isFile()) {
                    log.info("WORKSPACE_VERIFY: - {} (size: {} bytes)", file.getName(), file.length());
                    
                    // Special logging for todo.md from planning_tool
                    if (file.getName().equals("todo.md") && toolName.equals("planning_tool")) {
                        try {
                            String content = java.nio.file.Files.readString(file.toPath());
                            log.info("WORKSPACE_VERIFY: todo.md content preview: {}", 
                                content.substring(0, Math.min(200, content.length())));
                        } catch (Exception e) {
                            log.warn("WORKSPACE_VERIFY: Could not read todo.md content: {}", e.getMessage());
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("WORKSPACE_VERIFY: Error verifying workspace files: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Determine message type based on content for UI rendering
     */
    private String determineMessageType(String content) {
        if (content.contains("<thinking>") || content.contains("</thinking>")) {
            return "thinking";
        } else if (content.contains("<analysis>") || content.contains("</analysis>")) {
            return "analysis";
        } else if (content.contains("<reflection>") || content.contains("</reflection>")) {
            return "reflection";
        } else if (content.contains("<planning>") || content.contains("</planning>")) {
            return "planning";
        } else if (content.contains("<answer>") || content.contains("</answer>")) {
            return "answer";
        } else if (content.contains("<tool_result>") || content.contains("</tool_result>")) {
            return "tool_result";
        } else {
            return "text";
        }
    }
    
    /**
     * Enhanced error handler for Claude API exceptions.
     * This method provides automatic pause functionality and user-friendly error messages.
     * 
     * @param sessionId The session ID
     * @param exception The Claude API exception
     * @param sink The response sink for sending error messages to the UI
     */
    public void handleClaudeApiException(String sessionId, ClaudeApiException exception, Sinks.Many<ChatResponse> sink) {
        log.error("Handling Claude API exception for session {}: {}", sessionId, exception.getMessage());
        
        // Get user-friendly and technical error messages
        String userMessage = getUserFriendlyErrorMessage(exception);
        String technicalDetails = getTechnicalErrorDetails(exception);
        
        // Send user-friendly error message to UI
        sink.tryEmitNext(ChatResponse.builder()
                .aiDeveloperAgentSessionId(sessionId)
                .role("assistant")
                .messageType("error")
                .message(userMessage)
                .timestamp(Instant.now())
                .build());
        
        // If the error requires agent pause, pause the agent and notify user
        if (exception.shouldPauseAgent()) {
            pauseAgentForError(sessionId, exception, sink);
        }
        
        // Send technical details as a separate message for debugging
        sink.tryEmitNext(ChatResponse.builder()
                .aiDeveloperAgentSessionId(sessionId)
                .role("system")
                .messageType("error_details")
                .message("Technical Details:\n" + technicalDetails)
                .timestamp(Instant.now())
                .build());
    }
    
    /**
     * Pause the agent execution due to a Claude API error.
     * This method updates the agent state and provides user guidance for resuming.
     * 
     * @param sessionId The session ID
     * @param exception The Claude API exception that triggered the pause
     * @param sink The response sink for sending messages to the UI
     */
    private void pauseAgentForError(String sessionId, ClaudeApiException exception, Sinks.Many<ChatResponse> sink) {
        log.info("Pausing agent for session {} due to Claude API error: {}", sessionId, exception.getMessage());
        
        // Update agent state to paused
        AgentState agentState = agentStates.get(sessionId);
        if (agentState != null) {
            agentState.setCurrentPhase(DevelopmentPhase.PAUSED);
            agentState.setLastError(exception.getMessage());
            agentState.setErrorTimestamp(Instant.now());
        }
        
        // Send pause notification to UI
        sink.tryEmitNext(ChatResponse.builder()
                .aiDeveloperAgentSessionId(sessionId)
                .role("system")
                .messageType("agent_paused")
                .message("🛑 Agent execution has been automatically paused due to an API error.")
                .timestamp(Instant.now())
                .build());
        
        // Provide specific guidance based on error type
        String resumeGuidance = getResumeGuidance(exception);
        sink.tryEmitNext(ChatResponse.builder()
                .aiDeveloperAgentSessionId(sessionId)
                .role("system")
                .messageType("resume_guidance")
                .message(resumeGuidance)
                .timestamp(Instant.now())
                .build());
    }
    
    /**
     * Get user-friendly error message for a Claude API exception.
     * This method delegates to the ClaudeLLMProvider for consistent messaging.
     * 
     * @param exception The Claude API exception
     * @return User-friendly error message
     */
    private String getUserFriendlyErrorMessage(ClaudeApiException exception) {
        // If we have access to the LLMProvider and it's a ClaudeLLMProvider, use its method
        if (llmProvider instanceof com.ai.developer.llm.providers.ClaudeLLMProvider) {
            com.ai.developer.llm.providers.ClaudeLLMProvider claudeProvider = 
                (com.ai.developer.llm.providers.ClaudeLLMProvider) llmProvider;
            return claudeProvider.getUserFriendlyErrorMessage(exception);
        }
        
        // Fallback to basic error message
        return "An error occurred with the AI service: " + exception.getMessage();
    }
    
    /**
     * Get technical details for a Claude API exception.
     * This method delegates to the ClaudeLLMProvider for consistent formatting.
     * 
     * @param exception The Claude API exception
     * @return Technical error details
     */
    private String getTechnicalErrorDetails(ClaudeApiException exception) {
        // If we have access to the LLMProvider and it's a ClaudeLLMProvider, use its method
        if (llmProvider instanceof com.ai.developer.llm.providers.ClaudeLLMProvider) {
            com.ai.developer.llm.providers.ClaudeLLMProvider claudeProvider = 
                (com.ai.developer.llm.providers.ClaudeLLMProvider) llmProvider;
            return claudeProvider.getTechnicalErrorDetails(exception);
        }
        
        // Fallback to basic technical details
        return String.format("Status Code: %d\nError Type: %s\nMessage: %s", 
                exception.getStatusCode(), exception.getErrorType(), exception.getErrorMessage());
    }
    
    /**
     * Get guidance for resuming agent execution after an error.
     * 
     * @param exception The Claude API exception
     * @return Resume guidance message
     */
    private String getResumeGuidance(ClaudeApiException exception) {
        switch (exception.getStatusCode()) {
            case 429:
                if (exception instanceof ClaudeRateLimitException) {
                    ClaudeRateLimitException rateLimitEx = (ClaudeRateLimitException) exception;
                    return String.format("⏱️ Rate limit reached. The agent will automatically retry in %d seconds. " +
                            "You can also wait and then send a new message to resume manually.", 
                            rateLimitEx.getRetryAfterSeconds());
                }
                return "⏱️ Rate limit reached. Please wait a moment before sending a new message to resume.";
                
            case 401:
                return "🔑 Please check your Claude API key configuration and send a new message to resume.";
                
            case 500:
            case 502:
            case 503:
            case 504:
                return "🔧 The AI service is temporarily unavailable. Please wait a few minutes and send a new message to resume.";
                
            default:
                return "🔄 To resume the agent, please resolve any configuration issues and send a new message.";
        }
    }
    
    /**
     * Check if the agent is currently paused due to an error.
     * 
     * @param sessionId The session ID
     * @return true if the agent is paused due to an error
     */
    public boolean isAgentPausedForError(String sessionId) {
        AgentState agentState = agentStates.get(sessionId);
        return agentState != null && 
               agentState.getCurrentPhase() == DevelopmentPhase.PAUSED && 
               agentState.getLastError() != null;
    }
    
    /**
     * Resume agent execution after an error has been resolved.
     * 
     * @param sessionId The session ID
     */
    public void resumeAgentAfterError(String sessionId) {
        log.info("Resuming agent for session {} after error resolution", sessionId);
        
        AgentState agentState = agentStates.get(sessionId);
        if (agentState != null && agentState.getCurrentPhase() == DevelopmentPhase.PAUSED) {
            agentState.setCurrentPhase(DevelopmentPhase.PLANNING); // Resume with planning phase
            agentState.setLastError(null);
            agentState.setErrorTimestamp(null);
            
            // Send resume notification to UI if there's an active sink
            Sinks.Many<ChatResponse> sink = sessionSinks.get(sessionId);
            if (sink != null) {
                sink.tryEmitNext(ChatResponse.builder()
                        .aiDeveloperAgentSessionId(sessionId)
                        .role("system")
                        .messageType("agent_resumed")
                        .message("✅ Agent execution has been resumed.")
                        .timestamp(Instant.now())
                        .build());
            }
        }
    }
}
