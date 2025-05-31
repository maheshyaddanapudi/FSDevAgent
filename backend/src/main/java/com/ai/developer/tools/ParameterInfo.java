package com.ai.developer.tools;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class ParameterInfo {
    private String name;
    private String type;
    private String description;
    private boolean required;
    private List<String> enumValues;
    
    public List<String> getEnumValues() {
        return enumValues;
    }
}
