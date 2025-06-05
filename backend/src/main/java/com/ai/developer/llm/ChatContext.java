package com.ai.developer.llm;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a chat context containing messages and other information.
 * This class is used to store the conversation history and context for LLM interactions.
 * Enhanced to support multi-turn conversations with better context management.
 * Added flexible workspace directory support for session isolation with dynamic subdirectories.
 */
public class ChatContext {
    private final List<Message> messages = new ArrayList<>();
    private String sessionId;
    private String systemPrompt;
    private Map<String, Object> metadata = new HashMap<>();
    private int maxContextSize = 20; // Default max number of messages to keep
    private Instant lastUpdated = Instant.now();
    private String workspaceDirectory;
    private Map<String, String> taskDirectories = new HashMap<>();
    
    public ChatContext() {
    }
    
    /**
     * Constructor with sessionId
     * @param sessionId Session ID to initialize with
     */
    public ChatContext(String sessionId) {
        this.sessionId = sessionId;
        this.workspaceDirectory = "/tmp/ai-developer-agent/" + sessionId + "/";
        // Create workspace directory
        createWorkspaceDirectory();
    }
    
    /**
     * Create the workspace directory if it doesn't exist
     */
    private void createWorkspaceDirectory() {
        if (workspaceDirectory != null) {
            File workspace = new File(workspaceDirectory);
            if (!workspace.exists()) {
                workspace.mkdirs();
            }
        }
    }
    
    /**
     * Create a task subdirectory within the workspace
     * @param taskName Name of the task
     * @return Path to the task directory
     */
    public String createTaskDirectory(String taskName) {
        if (workspaceDirectory == null || taskName == null || taskName.isEmpty()) {
            return null;
        }
        
        // Sanitize task name for directory use
        String sanitizedTaskName = taskName.replaceAll("[^a-zA-Z0-9-_]", "_");
        
        // Create task directory path
        String taskDir = workspaceDirectory + sanitizedTaskName + "/";
        File taskDirectory = new File(taskDir);
        if (!taskDirectory.exists()) {
            taskDirectory.mkdirs();
        }
        
        // Store task directory mapping
        taskDirectories.put(taskName, taskDir);
        
        return taskDir;
    }
    
    /**
     * Get a task directory path
     * @param taskName Name of the task
     * @return Path to the task directory or null if not found
     */
    public String getTaskDirectory(String taskName) {
        return taskDirectories.get(taskName);
    }
    
    /**
     * Get all task directories
     * @return Map of task names to directory paths
     */
    public Map<String, String> getTaskDirectories() {
        return new HashMap<>(taskDirectories);
    }
    
    /**
     * Resolve a path relative to the workspace directory
     * @param relativePath Path relative to workspace directory
     * @return Absolute path
     */
    public String resolvePath(String relativePath) {
        if (workspaceDirectory == null || relativePath == null) {
            return relativePath;
        }
        
        // If already absolute, return as is
        if (new File(relativePath).isAbsolute()) {
            return relativePath;
        }
        
        // Remove leading slash if present
        if (relativePath.startsWith("/")) {
            relativePath = relativePath.substring(1);
        }
        
        // Combine with workspace directory
        Path resolvedPath = Paths.get(workspaceDirectory, relativePath);
        return resolvedPath.toString();
    }
    
    /**
     * Get the workspace directory
     * @return Workspace directory path
     */
    public String getWorkspaceDirectory() {
        return workspaceDirectory;
    }
    
    /**
     * Set the workspace directory
     * @param workspaceDirectory Workspace directory path to set
     */
    public void setWorkspaceDirectory(String workspaceDirectory) {
        this.workspaceDirectory = workspaceDirectory;
        createWorkspaceDirectory();
    }
    
    /**
     * Get all messages in the context
     * @return List of messages
     */
    public List<Message> getMessages() {
        return messages;
    }
    
    /**
     * Set messages in the context
     * @param messages List of messages to set
     */
    public void setMessages(List<Message> messages) {
        this.messages.clear();
        if (messages != null) {
            this.messages.addAll(messages);
        }
        this.lastUpdated = Instant.now();
    }
    
    /**
     * Add a single message to the context
     * @param message Message to add
     */
    public void addMessage(Message message) {
        if (message != null) {
            this.messages.add(message);
            this.lastUpdated = Instant.now();
            
            // Trim context if it exceeds max size
            trimContextIfNeeded();
        }
    }
    
    /**
     * Trim the context to the maximum allowed size
     * Keeps the system prompt and the most recent messages
     */
    private void trimContextIfNeeded() {
        if (messages.size() <= maxContextSize) {
            return;
        }
        
        // Keep system messages and the most recent messages
        List<Message> systemMessages = new ArrayList<>();
        List<Message> userAssistantMessages = new ArrayList<>();
        
        for (Message message : messages) {
            if ("system".equals(message.getRole())) {
                systemMessages.add(message);
            } else {
                userAssistantMessages.add(message);
            }
        }
        
        // If we have more user/assistant messages than allowed, trim the oldest ones
        int maxUserAssistantMessages = maxContextSize - systemMessages.size();
        if (userAssistantMessages.size() > maxUserAssistantMessages) {
            userAssistantMessages = userAssistantMessages.subList(
                userAssistantMessages.size() - maxUserAssistantMessages,
                userAssistantMessages.size()
            );
        }
        
        // Combine system messages and user/assistant messages
        messages.clear();
        messages.addAll(systemMessages);
        messages.addAll(userAssistantMessages);
    }
    
    /**
     * Get the session ID
     * @return Session ID
     */
    public String getSessionId() {
        return sessionId;
    }
    
    /**
     * Set the session ID
     * @param sessionId Session ID to set
     */
    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
        
        // Update workspace directory if session ID changes
        if (this.workspaceDirectory == null || !this.workspaceDirectory.contains(sessionId)) {
            this.workspaceDirectory = "/tmp/ai-developer-agent/" + sessionId + "/";
            createWorkspaceDirectory();
        }
    }
    
    /**
     * Get the system prompt
     * @return System prompt
     */
    public String getSystemPrompt() {
        return systemPrompt;
    }
    
    /**
     * Set the system prompt
     * @param systemPrompt System prompt to set
     */
    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }
    
    /**
     * Get the metadata
     * @return Metadata map
     */
    public Map<String, Object> getMetadata() {
        return metadata;
    }
    
    /**
     * Set the metadata
     * @param metadata Metadata map to set
     */
    public void setMetadata(Map<String, Object> metadata) {
        if (metadata != null) {
            this.metadata = metadata;
        } else {
            this.metadata = new HashMap<>();
        }
    }
    
    /**
     * Add a metadata entry
     * @param key Metadata key
     * @param value Metadata value
     */
    public void addMetadata(String key, Object value) {
        if (key != null) {
            this.metadata.put(key, value);
        }
    }
    
    /**
     * Get a metadata entry
     * @param key Metadata key
     * @return Metadata value or null if not found
     */
    public Object getMetadataValue(String key) {
        return this.metadata.get(key);
    }
    
    /**
     * Get the maximum context size
     * @return Maximum context size
     */
    public int getMaxContextSize() {
        return maxContextSize;
    }
    
    /**
     * Set the maximum context size
     * @param maxContextSize Maximum context size to set
     */
    public void setMaxContextSize(int maxContextSize) {
        if (maxContextSize > 0) {
            this.maxContextSize = maxContextSize;
            trimContextIfNeeded();
        }
    }
    
    /**
     * Get the last updated timestamp
     * @return Last updated timestamp
     */
    public Instant getLastUpdated() {
        return lastUpdated;
    }
    
    /**
     * Get the total token count estimate for this context
     * Very rough estimate based on 4 chars per token
     * @return Estimated token count
     */
    public int getEstimatedTokenCount() {
        int count = 0;
        
        // Count system prompt tokens
        if (systemPrompt != null) {
            count += systemPrompt.length() / 4;
        }
        
        // Count message tokens
        for (Message message : messages) {
            if (message.getContent() != null) {
                count += message.getContent().length() / 4;
            }
        }
        
        return count;
    }
    
    /**
     * Find references to previous messages or entities
     * @param query Text to search for
     * @return List of messages that match the query
     */
    public List<Message> findReferences(String query) {
        if (query == null || query.isEmpty()) {
            return new ArrayList<>();
        }
        
        List<Message> matches = new ArrayList<>();
        for (Message message : messages) {
            if (message.getContent() != null && 
                message.getContent().toLowerCase().contains(query.toLowerCase())) {
                matches.add(message);
            }
        }
        
        return matches;
    }
    
    /**
     * Create a builder for ChatContext
     * @return Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Builder class for ChatContext
     */
    public static class Builder {
        private final ChatContext context = new ChatContext();
        
        public Builder messages(List<Message> messages) {
            if (messages != null) {
                context.getMessages().addAll(messages);
            }
            return this;
        }
        
        public Builder sessionId(String sessionId) {
            context.setSessionId(sessionId);
            return this;
        }
        
        public Builder systemPrompt(String systemPrompt) {
            context.setSystemPrompt(systemPrompt);
            return this;
        }
        
        public Builder metadata(Map<String, Object> metadata) {
            context.setMetadata(metadata);
            return this;
        }
        
        public Builder maxContextSize(int maxContextSize) {
            context.setMaxContextSize(maxContextSize);
            return this;
        }
        
        public Builder workspaceDirectory(String workspaceDirectory) {
            context.setWorkspaceDirectory(workspaceDirectory);
            return this;
        }
        
        public ChatContext build() {
            return context;
        }
    }
}
