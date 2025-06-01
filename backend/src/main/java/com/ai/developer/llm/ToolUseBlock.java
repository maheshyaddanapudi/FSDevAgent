package com.ai.developer.llm;

import java.util.Map;

/**
 * Represents a tool use block in LLM responses.
 * This class is used to parse and handle tool calls from LLM responses.
 */
public class ToolUseBlock {
    private String name;
    private Map<String, Object> args;
    private String blockId;
    private String content;
    private String id; // Added for getId() method
    private Object input; // Changed to Object type to support both String and Map inputs

    public ToolUseBlock() {
    }

    public ToolUseBlock(String name, Map<String, Object> args, String blockId, String content) {
        this.name = name;
        this.args = args;
        this.blockId = blockId;
        this.content = content;
        this.id = blockId; // Initialize id with blockId for compatibility
        this.input = content; // Initialize input with content for compatibility
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Map<String, Object> getArgs() {
        return args;
    }

    public void setArgs(Map<String, Object> args) {
        this.args = args;
    }

    public String getBlockId() {
        return blockId;
    }

    public void setBlockId(String blockId) {
        this.blockId = blockId;
        this.id = blockId; // Keep id and blockId in sync
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    // Added method to resolve compilation error
    public String getId() {
        return id != null ? id : blockId;
    }

    // Added method to resolve compilation error
    public void setId(String id) {
        this.id = id;
    }

    // Added method to resolve compilation error - changed return type to Object
    public Object getInput() {
        return input != null ? input : content;
    }

    // Added method to resolve compilation error - changed parameter type to Object
    public void setInput(Object input) {
        this.input = input;
    }
    
    // Added builder pattern support
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private ToolUseBlock block = new ToolUseBlock();
        
        public Builder name(String name) {
            block.setName(name);
            return this;
        }
        
        public Builder id(String id) {
            block.setId(id);
            return this;
        }
        
        public Builder input(Object input) {
            block.setInput(input);
            return this;
        }
        
        public Builder args(Map<String, Object> args) {
            block.setArgs(args);
            return this;
        }
        
        public Builder blockId(String blockId) {
            block.setBlockId(blockId);
            return this;
        }
        
        public Builder content(String content) {
            block.setContent(content);
            return this;
        }
        
        public ToolUseBlock build() {
            return block;
        }
    }

    @Override
    public String toString() {
        return "ToolUseBlock{" +
                "name='" + name + '\'' +
                ", args=" + args +
                ", blockId='" + blockId + '\'' +
                ", content='" + content + '\'' +
                ", id='" + id + '\'' +
                ", input='" + input + '\'' +
                '}';
    }
}
