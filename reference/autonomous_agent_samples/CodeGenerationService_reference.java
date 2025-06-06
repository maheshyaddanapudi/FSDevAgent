package com.ai.developer.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Service for generating sophisticated code based on templates and patterns.
 * This service provides framework-aware code generation capabilities.
 */
@Slf4j
@Service
public class CodeGenerationService {
    
    private final Map<String, CodeTemplate> templates = new HashMap<>();
    
    public CodeGenerationService() {
        initializeTemplates();
    }
    
    /**
     * Generate code based on type and context
     */
    public String generateCode(String codeType, Map<String, Object> context) {
        CodeTemplate template = templates.get(codeType);
        if (template != null) {
            return template.generate(context);
        }
        
        // Default generation for unknown types
        log.warn("No template found for code type: {}", codeType);
        return generateDefaultCode(codeType, context);
    }
    
    /**
     * Initialize code templates
     */
    private void initializeTemplates() {
        // React Component Templates
        templates.put("react-form", new ReactFormTemplate());
        templates.put("react-list", new ReactListTemplate());
        templates.put("react-detail", new ReactDetailTemplate());
        templates.put("react-hook", new ReactHookTemplate());
        
        // Spring Boot Templates
        templates.put("spring-entity", new SpringEntityTemplate());
        templates.put("spring-repository", new SpringRepositoryTemplate());
        templates.put("spring-service", new SpringServiceTemplate());
        templates.put("spring-controller", new SpringControllerTemplate());
        templates.put("spring-dto", new SpringDTOTemplate());
        
        // Database Templates
        templates.put("sql-table", new SQLTableTemplate());
        templates.put("sql-migration", new SQLMigrationTemplate());
        
        // API Templates
        templates.put("rest-endpoint", new RestEndpointTemplate());
        templates.put("graphql-schema", new GraphQLSchemaTemplate());
        
        // Test Templates
        templates.put("unit-test", new UnitTestTemplate());
        templates.put("integration-test", new IntegrationTestTemplate());
    }
    
    /**
     * Generate default code when no specific template exists
     */
    private String generateDefaultCode(String codeType, Map<String, Object> context) {
        return String.format("""
            // Generated code for type: %s
            // TODO: Implement %s
            // Context: %s
            """, codeType, codeType, context);
    }
    
    /**
     * Base interface for code templates
     */
    interface CodeTemplate {
        String generate(Map<String, Object> context);
    }
    
    /**
     * React Form Component Template
     */
    static class ReactFormTemplate implements CodeTemplate {
        @Override
        public String generate(Map<String, Object> context) {
            String componentName = (String) context.getOrDefault("name", "Form");
            String entityName = (String) context.getOrDefault("entity", "Item");
            Map<String, String> fields = (Map<String, String>) context.getOrDefault("fields", 
                Map.of("name", "string", "description", "string"));
            
            StringBuilder formFields = new StringBuilder();
            StringBuilder stateFields = new StringBuilder();
            StringBuilder validationRules = new StringBuilder();
            
            fields.forEach((fieldName, fieldType) -> {
                // Generate form fields
                formFields.append(generateFormField(fieldName, fieldType));
                
                // Generate state
                stateFields.append(String.format("  %s: '',\n", fieldName));
                
                // Generate validation
                validationRules.append(String.format("""
                    if (!formData.%s.trim()) {
                      errors.%s = '%s is required';
                    }
                    """, fieldName, fieldName, capitalize(fieldName)));
            });
            
            return String.format("""
                import React, { useState } from 'react';
                import './`$s.css';
                
                interface %sData {
                %s}
                
                interface %sErrors {
                %s}
                
                interface %sProps {
                  onSubmit: (data: %sData) => void;
                  initialData?: Partial<%sData>;
                }
                
                const %s: React.FC<%sProps> = ({ onSubmit, initialData = {} }) => {
                  const [formData, setFormData] = useState<%sData>({
                %s  });
                  
                  const [errors, setErrors] = useState<%sErrors>({});
                  const [submitting, setSubmitting] = useState(false);
                  
                  const handleChange = (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => {
                    const { name, value } = e.target;
                    setFormData(prev => ({ ...prev, [name]: value }));
                    
                    // Clear error when user types
                    if (errors[name as keyof %sErrors]) {
                      setErrors(prev => ({ ...prev, [name]: undefined }));
                    }
                  };
                  
                  const validate = (): boolean => {
                    const errors: %sErrors = {};
                    
                %s
                    
                    setErrors(errors);
                    return Object.keys(errors).length === 0;
                  };
                  
                  const handleSubmit = async (e: React.FormEvent) => {
                    e.preventDefault();
                    
                    if (!validate()) {
                      return;
                    }
                    
                    setSubmitting(true);
                    try {
                      await onSubmit(formData);
                    } catch (error) {
                      console.error('Form submission error:', error);
                    } finally {
                      setSubmitting(false);
                    }
                  };
                  
                  return (
                    <form className="%s-form" onSubmit={handleSubmit}>
                      <h2>{initialData.id ? 'Edit' : 'Create'} %s</h2>
                      
                %s
                      
                      <div className="form-actions">
                        <button type="submit" disabled={submitting}>
                          {submitting ? 'Saving...' : 'Save'}
                        </button>
                        <button type="button" onClick={() => window.history.back()}>
                          Cancel
                        </button>
                      </div>
                    </form>
                  );
                };
                
                export default %s;
                """, 
                componentName,
                entityName,
                generateTypeFields(fields),
                entityName,
                generateTypeFields(fields),
                componentName,
                entityName,
                entityName,
                componentName, componentName,
                entityName,
                stateFields,
                entityName,
                entityName,
                entityName,
                validationRules,
                componentName.toLowerCase(),
                entityName,
                formFields,
                componentName
            );
        }
        
        private String generateFormField(String fieldName, String fieldType) {
            String inputType = "text";
            String inputElement = "input";
            
            if ("number".equals(fieldType)) {
                inputType = "number";
            } else if ("date".equals(fieldType)) {
                inputType = "date";
            } else if ("boolean".equals(fieldType)) {
                inputType = "checkbox";
            } else if (fieldName.contains("description") || fieldName.contains("content")) {
                inputElement = "textarea";
            }
            
            if ("textarea".equals(inputElement)) {
                return String.format("""
                      <div className="form-group">
                        <label htmlFor="%s">%s</label>
                        <textarea
                          id="%s"
                          name="%s"
                          value={formData.%s}
                          onChange={handleChange}
                          className={errors.%s ? 'error' : ''}
                          rows={4}
                        />
                        {errors.%s && <span className="error-message">{errors.%s}</span>}
                      </div>
                      
                """, fieldName, capitalize(fieldName), fieldName, fieldName, fieldName, 
                fieldName, fieldName, fieldName);
            }
            
            return String.format("""
                      <div className="form-group">
                        <label htmlFor="%s">%s</label>
                        <input
                          type="%s"
                          id="%s"
                          name="%s"
                          value={formData.%s}
                          onChange={handleChange}
                          className={errors.%s ? 'error' : ''}
                        />
                        {errors.%s && <span className="error-message">{errors.%s}</span>}
                      </div>
                      
            """, fieldName, capitalize(fieldName), inputType, fieldName, fieldName, 
            fieldName, fieldName, fieldName, fieldName);
        }
        
        private String generateTypeFields(Map<String, String> fields) {
            StringBuilder result = new StringBuilder();
            fields.forEach((name, type) -> {
                result.append(String.format("  %s: %s;\n", name, mapToTypeScriptType(type)));
            });
            return result.toString();
        }
        
        private String mapToTypeScriptType(String javaType) {
            return switch (javaType.toLowerCase()) {
                case "string", "text" -> "string";
                case "number", "int", "integer", "long", "double", "float" -> "number";
                case "boolean", "bool" -> "boolean";
                case "date", "datetime", "timestamp" -> "string";
                default -> "any";
            };
        }
        
        private String capitalize(String str) {
            return str.substring(0, 1).toUpperCase() + str.substring(1);
        }
    }
    
    /**
     * React List Component Template
     */
    static class ReactListTemplate implements CodeTemplate {
        @Override
        public String generate(Map<String, Object> context) {
            String componentName = (String) context.getOrDefault("name", "List");
            String entityName = (String) context.getOrDefault("entity", "Item");
            String apiEndpoint = (String) context.getOrDefault("endpoint", "/api/items");
            
            return String.format("""
                import React, { useState, useEffect } from 'react';
                import { Link } from 'react-router-dom';
                import './`$s.css';
                
                interface %s {
                  id: number;
                  name: string;
                  description?: string;
                  createdAt?: string;
                }
                
                const %s: React.FC = () => {
                  const [items, setItems] = useState<%s[]>([]);
                  const [loading, setLoading] = useState(true);
                  const [error, setError] = useState<string | null>(null);
                  const [searchTerm, setSearchTerm] = useState('');
                  
                  useEffect(() => {
                    fetchItems();
                  }, []);
                  
                  const fetchItems = async () => {
                    try {
                      const response = await fetch('%s');
                      if (!response.ok) throw new Error('Failed to fetch items');
                      const data = await response.json();
                      setItems(data);
                    } catch (err) {
                      setError(err instanceof Error ? err.message : 'An error occurred');
                    } finally {
                      setLoading(false);
                    }
                  };
                  
                  const handleDelete = async (id: number) => {
                    if (!confirm('Are you sure you want to delete this item?')) return;
                    
                    try {
                      const response = await fetch(`%s/${id}`, { method: 'DELETE' });
                      if (!response.ok) throw new Error('Failed to delete item');
                      setItems(items.filter(item => item.id !== id));
                    } catch (err) {
                      alert('Failed to delete item');
                    }
                  };
                  
                  const filteredItems = items.filter(item =>
                    item.name.toLowerCase().includes(searchTerm.toLowerCase())
                  );
                  
                  if (loading) return <div className="loading">Loading...</div>;
                  if (error) return <div className="error">Error: {error}</div>;
                  
                  return (
                    <div className="%s-list">
                      <div className="list-header">
                        <h1>%s</h1>
                        <Link to="/create" className="btn btn-primary">Create New</Link>
                      </div>
                      
                      <div className="search-bar">
                        <input
                          type="text"
                          placeholder="Search..."
                          value={searchTerm}
                          onChange={(e) => setSearchTerm(e.target.value)}
                        />
                      </div>
                      
                      {filteredItems.length === 0 ? (
                        <p className="no-items">No items found</p>
                      ) : (
                        <div className="items-grid">
                          {filteredItems.map(item => (
                            <div key={item.id} className="item-card">
                              <h3>{item.name}</h3>
                              {item.description && <p>{item.description}</p>}
                              <div className="item-actions">
                                <Link to={`/view/${item.id}`} className="btn btn-sm">View</Link>
                                <Link to={`/edit/${item.id}`} className="btn btn-sm">Edit</Link>
                                <button 
                                  onClick={() => handleDelete(item.id)} 
                                  className="btn btn-sm btn-danger"
                                >
                                  Delete
                                </button>
                              </div>
                            </div>
                          ))}
                        </div>
                      )}
                    </div>
                  );
                };
                
                export default %s;
                """,
                componentName,
                entityName,
                componentName,
                entityName,
                apiEndpoint,
                apiEndpoint,
                componentName.toLowerCase(),
                entityName + "s",
                componentName
            );
        }
    }
    
    /**
     * React Detail Component Template
     */
    static class ReactDetailTemplate implements CodeTemplate {
        @Override
        public String generate(Map<String, Object> context) {
            String componentName = (String) context.getOrDefault("name", "Detail");
            String entityName = (String) context.getOrDefault("entity", "Item");
            String apiEndpoint = (String) context.getOrDefault("endpoint", "/api/items");
            
            return String.format("""
                import React, { useState, useEffect } from 'react';
                import { useParams, Link, useNavigate } from 'react-router-dom';
                import './`$s.css';
                
                interface %s {
                  id: number;
                  name: string;
                  description?: string;
                  createdAt?: string;
                  updatedAt?: string;
                }
                
                const %s: React.FC = () => {
                  const { id } = useParams<{ id: string }>();
                  const navigate = useNavigate();
                  const [item, setItem] = useState<%s | null>(null);
                  const [loading, setLoading] = useState(true);
                  const [error, setError] = useState<string | null>(null);
                  
                  useEffect(() => {
                    fetchItem();
                  }, [id]);
                  
                  const fetchItem = async () => {
                    try {
                      const response = await fetch(`%s/${id}`);
                      if (!response.ok) throw new Error('Failed to fetch item');
                      const data = await response.json();
                      setItem(data);
                    } catch (err) {
                      setError(err instanceof Error ? err.message : 'An error occurred');
                    } finally {
                      setLoading(false);
                    }
                  };
                  
                  const handleDelete = async () => {
                    if (!confirm('Are you sure you want to delete this item?')) return;
                    
                    try {
                      const response = await fetch(`%s/${id}`, { method: 'DELETE' });
                      if (!response.ok) throw new Error('Failed to delete item');
                      navigate('/');
                    } catch (err) {
                      alert('Failed to delete item');
                    }
                  };
                  
                  if (loading) return <div className="loading">Loading...</div>;
                  if (error) return <div className="error">Error: {error}</div>;
                  if (!item) return <div className="not-found">Item not found</div>;
                  
                  return (
                    <div className="%s-detail">
                      <div className="detail-header">
                        <h1>{item.name}</h1>
                        <div className="detail-actions">
                          <Link to={`/edit/${item.id}`} className="btn btn-primary">Edit</Link>
                          <button onClick={handleDelete} className="btn btn-danger">Delete</button>
                        </div>
                      </div>
                      
                      <div className="detail-content">
                        {item.description && (
                          <div className="detail-section">
                            <h3>Description</h3>
                            <p>{item.description}</p>
                          </div>
                        )}
                        
                        <div className="detail-metadata">
                          <p><strong>ID:</strong> {item.id}</p>
                          {item.createdAt && (
                            <p><strong>Created:</strong> {new Date(item.createdAt).toLocaleString()}</p>
                          )}
                          {item.updatedAt && (
                            <p><strong>Updated:</strong> {new Date(item.updatedAt).toLocaleString()}</p>
                          )}
                        </div>
                      </div>
                      
                      <div className="detail-footer">
                        <Link to="/" className="btn">Back to List</Link>
                      </div>
                    </div>
                  );
                };
                
                export default %s;
                """,
                componentName,
                entityName,
                componentName,
                entityName,
                apiEndpoint,
                apiEndpoint,
                componentName.toLowerCase(),
                componentName
            );
        }
    }
    
    /**
     * React Custom Hook Template
     */
    static class ReactHookTemplate implements CodeTemplate {
        @Override
        public String generate(Map<String, Object> context) {
            String hookName = (String) context.getOrDefault("name", "useApi");
            String purpose = (String) context.getOrDefault("purpose", "data fetching");
            
            return String.format("""
                import { useState, useEffect, useCallback } from 'react';
                
                interface %sOptions {
                  initialData?: any;
                  onSuccess?: (data: any) => void;
                  onError?: (error: Error) => void;
                }
                
                interface %sResult {
                  data: any;
                  loading: boolean;
                  error: Error | null;
                  refetch: () => Promise<void>;
                }
                
                export const %s = (url: string, options: %sOptions = {}): %sResult => {
                  const [data, setData] = useState(options.initialData || null);
                  const [loading, setLoading] = useState(false);
                  const [error, setError] = useState<Error | null>(null);
                  
                  const fetchData = useCallback(async () => {
                    setLoading(true);
                    setError(null);
                    
                    try {
                      const response = await fetch(url);
                      if (!response.ok) {
                        throw new Error(`HTTP error! status: ${response.status}`);
                      }
                      
                      const result = await response.json();
                      setData(result);
                      
                      if (options.onSuccess) {
                        options.onSuccess(result);
                      }
                    } catch (err) {
                      const error = err instanceof Error ? err : new Error('Unknown error');
                      setError(error);
                      
                      if (options.onError) {
                        options.onError(error);
                      }
                    } finally {
                      setLoading(false);
                    }
                  }, [url, options.onSuccess, options.onError]);
                  
                  useEffect(() => {
                    fetchData();
                  }, [fetchData]);
                  
                  return {
                    data,
                    loading,
                    error,
                    refetch: fetchData
                  };
                };
                
                // Example usage:
                // const { data, loading, error, refetch } = %s('/api/users');
                """,
                capitalize(hookName),
                capitalize(hookName),
                hookName,
                capitalize(hookName),
                capitalize(hookName),
                hookName
            );
        }
        
        private String capitalize(String str) {
            return str.substring(0, 1).toUpperCase() + str.substring(1);
        }
    }
    
    /**
     * Spring Entity Template
     */
    static class SpringEntityTemplate implements CodeTemplate {
        @Override
        public String generate(Map<String, Object> context) {
            String entityName = (String) context.getOrDefault("name", "Entity");
            String tableName = (String) context.getOrDefault("table", entityName.toLowerCase() + "s");
            Map<String, String> fields = (Map<String, String>) context.getOrDefault("fields",
                Map.of("name", "String", "description", "String"));
            
            StringBuilder fieldDeclarations = new StringBuilder();
            StringBuilder gettersSetters = new StringBuilder();
            
            fields.forEach((fieldName, fieldType) -> {
                // Skip id field as it's already included
                if (!"id".equals(fieldName)) {
                    fieldDeclarations.append(generateField(fieldName, fieldType));
                    gettersSetters.append(generateGetterSetter(fieldName, fieldType));
                }
            });
            
            return String.format("""
                package com.example.model;
                
                import jakarta.persistence.*;
                import lombok.Data;
                import lombok.NoArgsConstructor;
                import lombok.AllArgsConstructor;
                import lombok.Builder;
                
                import java.time.LocalDateTime;
                
                @Entity
                @Table(name = "%s")
                @Data
                @NoArgsConstructor
                @AllArgsConstructor
                @Builder
                public class %s {
                    
                    @Id
                    @GeneratedValue(strategy = GenerationType.IDENTITY)
                    private Long id;
                    
                %s
                    
                    @Column(name = "created_at", nullable = false, updatable = false)
                    @Builder.Default
                    private LocalDateTime createdAt = LocalDateTime.now();
                    
                    @Column(name = "updated_at")
                    private LocalDateTime updatedAt;
                    
                    @PrePersist
                    protected void onCreate() {
                        if (createdAt == null) {
                            createdAt = LocalDateTime.now();
                        }
                        updatedAt = LocalDateTime.now();
                    }
                    
                    @PreUpdate
                    protected void onUpdate() {
                        updatedAt = LocalDateTime.now();
                    }
                }
                """,
                tableName,
                entityName,
                fieldDeclarations
            );
        }
        
        private String generateField(String fieldName, String fieldType) {
            String columnName = camelToSnake(fieldName);
            boolean isRequired = !fieldName.contains("description") && !fieldName.contains("optional");
            
            if ("String".equals(fieldType) && fieldName.contains("description")) {
                return String.format("""
                    @Column(name = "%s", columnDefinition = "TEXT")
                    private %s %s;
                    
                """, columnName, fieldType, fieldName);
            }
            
            return String.format("""
                    @Column(name = "%s"%s)
                    private %s %s;
                    
            """, columnName, isRequired ? ", nullable = false" : "", fieldType, fieldName);
        }
        
        private String generateGetterSetter(String fieldName, String fieldType) {
            // Lombok @Data handles this, so return empty
            return "";
        }
        
        private String camelToSnake(String camel) {
            return camel.replaceAll("([A-Z])", "_$1").toLowerCase().replaceFirst("^_", "");
        }
    }
    
    /**
     * Spring Repository Template
     */
    static class SpringRepositoryTemplate implements CodeTemplate {
        @Override
        public String generate(Map<String, Object> context) {
            String entityName = (String) context.getOrDefault("entity", "Entity");
            String searchField = (String) context.getOrDefault("searchField", "name");
            
            return String.format("""
                package com.example.repository;
                
                import com.example.model.%s;
                import org.springframework.data.jpa.repository.JpaRepository;
                import org.springframework.data.jpa.repository.Query;
                import org.springframework.data.repository.query.Param;
                import org.springframework.stereotype.Repository;
                
                import java.util.List;
                import java.util.Optional;
                
                @Repository
                public interface %sRepository extends JpaRepository<%s, Long> {
                    
                    // Find by name
                    Optional<%s> findBy%s(String %s);
                    
                    // Find all by name containing (case-insensitive)
                    List<%s> findBy%sContainingIgnoreCase(String %s);
                    
                    // Custom query example
                    @Query("SELECT e FROM %s e WHERE LOWER(e.%s) LIKE LOWER(CONCAT('%%', :searchTerm, '%%'))")
                    List<%s> search(@Param("searchTerm") String searchTerm);
                    
                    // Check if exists by name
                    boolean existsBy%s(String %s);
                    
                    // Delete by name
                    void deleteBy%s(String %s);
                    
                    // Find all ordered by creation date
                    List<%s> findAllByOrderByCreatedAtDesc();
                }
                """,
                entityName,
                entityName,
                entityName,
                entityName,
                capitalize(searchField),
                searchField,
                entityName,
                capitalize(searchField),
                searchField,
                entityName,
                searchField,
                entityName,
                capitalize(searchField),
                searchField,
                capitalize(searchField),
                searchField,
                entityName
            );
        }
        
        private String capitalize(String str) {
            return str.substring(0, 1).toUpperCase() + str.substring(1);
        }
    }
    
    /**
     * Spring Service Template
     */
    static class SpringServiceTemplate implements CodeTemplate {
        @Override
        public String generate(Map<String, Object> context) {
            String entityName = (String) context.getOrDefault("entity", "Entity");
            String serviceName = entityName + "Service";
            
            return String.format("""
                package com.example.service;
                
                import com.example.model.%s;
                import com.example.repository.%sRepository;
                import com.example.dto.%sDTO;
                import com.example.dto.Create%sDTO;
                import com.example.dto.Update%sDTO;
                import com.example.exception.ResourceNotFoundException;
                import lombok.RequiredArgsConstructor;
                import lombok.extern.slf4j.Slf4j;
                import org.springframework.data.domain.Page;
                import org.springframework.data.domain.Pageable;
                import org.springframework.stereotype.Service;
                import org.springframework.transaction.annotation.Transactional;
                
                import java.util.List;
                import java.util.stream.Collectors;
                
                @Service
                @Transactional
                @RequiredArgsConstructor
                @Slf4j
                public class %s {
                    
                    private final %sRepository repository;
                    
                    public List<%sDTO> findAll() {
                        log.debug("Finding all %s entities");
                        return repository.findAll().stream()
                                .map(this::toDTO)
                                .collect(Collectors.toList());
                    }
                    
                    public Page<%sDTO> findAll(Pageable pageable) {
                        log.debug("Finding all %s entities with pagination");
                        return repository.findAll(pageable)
                                .map(this::toDTO);
                    }
                    
                    public %sDTO findById(Long id) {
                        log.debug("Finding %s by id: {}", id);
                        return repository.findById(id)
                                .map(this::toDTO)
                                .orElseThrow(() -> new ResourceNotFoundException("%s not found with id: " + id));
                    }
                    
                    public %sDTO create(Create%sDTO createDTO) {
                        log.debug("Creating new %s: {}", createDTO);
                        
                        %s entity = %s.builder()
                                .name(createDTO.getName())
                                .description(createDTO.getDescription())
                                .build();
                        
                        %s saved = repository.save(entity);
                        log.info("Created new %s with id: {}", saved.getId());
                        
                        return toDTO(saved);
                    }
                    
                    public %sDTO update(Long id, Update%sDTO updateDTO) {
                        log.debug("Updating %s with id: {}", id);
                        
                        %s entity = repository.findById(id)
                                .orElseThrow(() -> new ResourceNotFoundException("%s not found with id: " + id));
                        
                        entity.setName(updateDTO.getName());
                        entity.setDescription(updateDTO.getDescription());
                        
                        %s updated = repository.save(entity);
                        log.info("Updated %s with id: {}", updated.getId());
                        
                        return toDTO(updated);
                    }
                    
                    public void delete(Long id) {
                        log.debug("Deleting %s with id: {}", id);
                        
                        if (!repository.existsById(id)) {
                            throw new ResourceNotFoundException("%s not found with id: " + id);
                        }
                        
                        repository.deleteById(id);
                        log.info("Deleted %s with id: {}", id);
                    }
                    
                    public List<%sDTO> search(String searchTerm) {
                        log.debug("Searching %s entities with term: {}", searchTerm);
                        return repository.search(searchTerm).stream()
                                .map(this::toDTO)
                                .collect(Collectors.toList());
                    }
                    
                    private %sDTO toDTO(%s entity) {
                        return %sDTO.builder()
                                .id(entity.getId())
                                .name(entity.getName())
                                .description(entity.getDescription())
                                .createdAt(entity.getCreatedAt())
                                .updatedAt(entity.getUpdatedAt())
                                .build();
                    }
                }
                """,
                entityName,
                entityName,
                entityName,
                entityName,
                entityName,
                serviceName,
                entityName,
                entityName,
                entityName.toLowerCase(),
                entityName,
                entityName.toLowerCase(),
                entityName,
                entityName.toLowerCase(),
                entityName,
                entityName,
                entityName,
                entityName.toLowerCase(),
                entityName,
                entityName,
                entityName,
                entityName.toLowerCase(),
                entityName,
                entityName,
                entityName.toLowerCase(),
                entityName,
                entityName,
                entityName,
                entityName.toLowerCase(),
                entityName,
                entityName.toLowerCase(),
                entityName,
                entityName.toLowerCase(),
                entityName,
                entityName.toLowerCase(),
                entityName,
                entityName.toLowerCase(),
                entityName,
                entityName,
                entityName
            );
        }
    }
    
    /**
     * Spring REST Controller Template
     */
    static class SpringControllerTemplate implements CodeTemplate {
        @Override
        public String generate(Map<String, Object> context) {
            String entityName = (String) context.getOrDefault("entity", "Entity");
            String basePath = (String) context.getOrDefault("basePath", "/api/" + entityName.toLowerCase() + "s");
            
            return String.format("""
                package com.example.controller;
                
                import com.example.dto.%sDTO;
                import com.example.dto.Create%sDTO;
                import com.example.dto.Update%sDTO;
                import com.example.service.%sService;
                import io.swagger.v3.oas.annotations.Operation;
                import io.swagger.v3.oas.annotations.Parameter;
                import io.swagger.v3.oas.annotations.responses.ApiResponse;
                import io.swagger.v3.oas.annotations.tags.Tag;
                import jakarta.validation.Valid;
                import lombok.RequiredArgsConstructor;
                import lombok.extern.slf4j.Slf4j;
                import org.springframework.data.domain.Page;
                import org.springframework.data.domain.Pageable;
                import org.springframework.data.web.PageableDefault;
                import org.springframework.http.HttpStatus;
                import org.springframework.http.ResponseEntity;
                import org.springframework.web.bind.annotation.*;
                
                import java.util.List;
                
                @RestController
                @RequestMapping("%s")
                @RequiredArgsConstructor
                @Slf4j
                @Tag(name = "%s", description = "%s management API")
                @CrossOrigin(origins = "*")
                public class %sController {
                    
                    private final %sService service;
                    
                    @GetMapping
                    @Operation(summary = "Get all %s")
                    public ResponseEntity<List<%sDTO>> getAll(
                            @RequestParam(required = false) String search) {
                        
                        if (search != null && !search.trim().isEmpty()) {
                            return ResponseEntity.ok(service.search(search));
                        }
                        
                        return ResponseEntity.ok(service.findAll());
                    }
                    
                    @GetMapping("/paged")
                    @Operation(summary = "Get all %s with pagination")
                    public ResponseEntity<Page<%sDTO>> getAllPaged(
                            @PageableDefault(size = 20) Pageable pageable) {
                        return ResponseEntity.ok(service.findAll(pageable));
                    }
                    
                    @GetMapping("/{id}")
                    @Operation(summary = "Get %s by ID")
                    @ApiResponse(responseCode = "200", description = "Found the %s")
                    @ApiResponse(responseCode = "404", description = "%s not found")
                    public ResponseEntity<%sDTO> getById(
                            @Parameter(description = "ID of %s to retrieve")
                            @PathVariable Long id) {
                        return ResponseEntity.ok(service.findById(id));
                    }
                    
                    @PostMapping
                    @Operation(summary = "Create a new %s")
                    @ApiResponse(responseCode = "201", description = "%s created")
                    @ApiResponse(responseCode = "400", description = "Invalid input")
                    public ResponseEntity<%sDTO> create(
                            @Valid @RequestBody Create%sDTO createDTO) {
                        log.info("Creating new %s: {}", createDTO);
                        %sDTO created = service.create(createDTO);
                        return ResponseEntity.status(HttpStatus.CREATED).body(created);
                    }
                    
                    @PutMapping("/{id}")
                    @Operation(summary = "Update an existing %s")
                    @ApiResponse(responseCode = "200", description = "%s updated")
                    @ApiResponse(responseCode = "404", description = "%s not found")
                    public ResponseEntity<%sDTO> update(
                            @Parameter(description = "ID of %s to update")
                            @PathVariable Long id,
                            @Valid @RequestBody Update%sDTO updateDTO) {
                        log.info("Updating %s with id {}: {}", id, updateDTO);
                        return ResponseEntity.ok(service.update(id, updateDTO));
                    }
                    
                    @DeleteMapping("/{id}")
                    @Operation(summary = "Delete a %s")
                    @ApiResponse(responseCode = "204", description = "%s deleted")
                    @ApiResponse(responseCode = "404", description = "%s not found")
                    public ResponseEntity<Void> delete(
                            @Parameter(description = "ID of %s to delete")
                            @PathVariable Long id) {
                        log.info("Deleting %s with id: {}", id);
                        service.delete(id);
                        return ResponseEntity.noContent().build();
                    }
                }
                """,
                entityName,
                entityName,
                entityName,
                entityName,
                basePath,
                entityName,
                entityName,
                entityName,
                entityName,
                entityName.toLowerCase(),
                entityName,
                entityName.toLowerCase(),
                entityName,
                entityName.toLowerCase(),
                entityName.toLowerCase(),
                entityName,
                entityName,
                entityName.toLowerCase(),
                entityName.toLowerCase(),
                entityName,
                entityName,
                entityName.toLowerCase(),
                entityName,
                entityName.toLowerCase(),
                entityName.toLowerCase(),
                entityName,
                entityName.toLowerCase(),
                entityName,
                entityName.toLowerCase(),
                entityName.toLowerCase(),
                entityName.toLowerCase(),
                entityName.toLowerCase(),
                entityName.toLowerCase(),
                entityName.toLowerCase()
            );
        }
    }
    
    /**
     * Spring DTO Template
     */
    static class SpringDTOTemplate implements CodeTemplate {
        @Override
        public String generate(Map<String, Object> context) {
            String entityName = (String) context.getOrDefault("entity", "Entity");
            Map<String, String> fields = (Map<String, String>) context.getOrDefault("fields",
                Map.of("name", "String", "description", "String"));
            
            // Generate main DTO
            StringBuilder mainDTO = new StringBuilder();
            mainDTO.append(String.format("""
                package com.example.dto;
                
                import lombok.AllArgsConstructor;
                import lombok.Builder;
                import lombok.Data;
                import lombok.NoArgsConstructor;
                
                import java.time.LocalDateTime;
                
                @Data
                @Builder
                @NoArgsConstructor
                @AllArgsConstructor
                public class %sDTO {
                    private Long id;
                """, entityName));
            
            fields.forEach((name, type) -> {
                mainDTO.append(String.format("    private %s %s;\n", type, name));
            });
            
            mainDTO.append("""
                    private LocalDateTime createdAt;
                    private LocalDateTime updatedAt;
                }
                """);
            
            // Generate CreateDTO
            StringBuilder createDTO = new StringBuilder();
            createDTO.append(String.format("""
                
                package com.example.dto;
                
                import jakarta.validation.constraints.NotBlank;
                import jakarta.validation.constraints.Size;
                import lombok.AllArgsConstructor;
                import lombok.Builder;
                import lombok.Data;
                import lombok.NoArgsConstructor;
                
                @Data
                @Builder
                @NoArgsConstructor
                @AllArgsConstructor
                public class Create%sDTO {
                """, entityName));
            
            fields.forEach((name, type) -> {
                if ("String".equals(type) && "name".equals(name)) {
                    createDTO.append("""
                        @NotBlank(message = "Name is required")
                        @Size(min = 1, max = 255, message = "Name must be between 1 and 255 characters")
                        private String name;
                    
                    """);
                } else {
                    createDTO.append(String.format("    private %s %s;\n", type, name));
                }
            });
            
            createDTO.append("}");
            
            // Generate UpdateDTO (similar to CreateDTO)
            String updateDTO = createDTO.toString().replace("Create" + entityName + "DTO", "Update" + entityName + "DTO");
            
            return mainDTO.toString() + "\n" + createDTO.toString() + "\n" + updateDTO;
        }
    }
    
    /**
     * SQL Table Template
     */
    static class SQLTableTemplate implements CodeTemplate {
        @Override
        public String generate(Map<String, Object> context) {
            String tableName = (String) context.getOrDefault("table", "entities");
            Map<String, String> columns = (Map<String, String>) context.getOrDefault("columns",
                Map.of("name", "VARCHAR(255)", "description", "TEXT"));
            
            StringBuilder sql = new StringBuilder();
            sql.append(String.format("CREATE TABLE IF NOT EXISTS %s (\n", tableName));
            sql.append("    id SERIAL PRIMARY KEY,\n");
            
            columns.forEach((name, type) -> {
                boolean isRequired = !name.contains("description") && !name.contains("optional");
                sql.append(String.format("    %s %s%s,\n", 
                    camelToSnake(name), 
                    mapToSQLType(type),
                    isRequired ? " NOT NULL" : ""));
            });
            
            sql.append("    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,\n");
            sql.append("    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP\n");
            sql.append(");\n\n");
            
            // Add indexes
            sql.append(String.format("CREATE INDEX idx_%s_created_at ON %s(created_at);\n", tableName, tableName));
            if (columns.containsKey("name")) {
                sql.append(String.format("CREATE INDEX idx_%s_name ON %s(name);\n", tableName, tableName));
            }
            
            // Add update trigger
            sql.append(String.format("""
                
                CREATE OR REPLACE FUNCTION update_%s_updated_at()
                RETURNS TRIGGER AS $$
                BEGIN
                    NEW.updated_at = CURRENT_TIMESTAMP;
                    RETURN NEW;
                END;
                $$ LANGUAGE plpgsql;
                
                CREATE TRIGGER trigger_update_%s_updated_at
                BEFORE UPDATE ON %s
                FOR EACH ROW
                EXECUTE FUNCTION update_%s_updated_at();
                """, tableName, tableName, tableName, tableName));
            
            return sql.toString();
        }
        
        private String camelToSnake(String camel) {
            return camel.replaceAll("([A-Z])", "_$1").toLowerCase().replaceFirst("^_", "");
        }
        
        private String mapToSQLType(String javaType) {
            return switch (javaType.toLowerCase()) {
                case "string" -> "VARCHAR(255)";
                case "text", "description" -> "TEXT";
                case "int", "integer" -> "INTEGER";
                case "long" -> "BIGINT";
                case "double", "float" -> "NUMERIC(10,2)";
                case "boolean", "bool" -> "BOOLEAN";
                case "date" -> "DATE";
                case "datetime", "timestamp" -> "TIMESTAMP";
                default -> "VARCHAR(255)";
            };
        }
    }
    
    /**
     * SQL Migration Template
     */
    static class SQLMigrationTemplate implements CodeTemplate {
        @Override
        public String generate(Map<String, Object> context) {
            String version = (String) context.getOrDefault("version", "V1");
            String description = (String) context.getOrDefault("description", "initial_schema");
            String tableName = (String) context.getOrDefault("table", "entities");
            
            return String.format("""
                -- %s__%s.sql
                -- Migration: %s
                -- Description: %s
                
                BEGIN;
                
                -- Create table
                %s
                
                -- Seed initial data (optional)
                -- INSERT INTO %s (name, description) VALUES
                -- ('Sample 1', 'Sample description 1'),
                -- ('Sample 2', 'Sample description 2');
                
                COMMIT;
                """,
                version,
                description,
                version,
                description,
                new SQLTableTemplate().generate(context),
                tableName
            );
        }
    }
    
    /**
     * REST Endpoint Template
     */
    static class RestEndpointTemplate implements CodeTemplate {
        @Override
        public String generate(Map<String, Object> context) {
            String method = (String) context.getOrDefault("method", "GET");
            String path = (String) context.getOrDefault("path", "/api/resource");
            String description = (String) context.getOrDefault("description", "API endpoint");
            
            return String.format("""
                /**
                 * %s
                 * Method: %s
                 * Path: %s
                 */
                @%sMapping("%s")
                @Operation(summary = "%s")
                public ResponseEntity<?> handleRequest(
                        @RequestBody(required = false) Map<String, Object> requestBody,
                        @RequestParam Map<String, String> queryParams,
                        @PathVariable Map<String, String> pathVars) {
                    
                    log.info("%s request to %s", requestBody, queryParams, pathVars);
                    
                    try {
                        // TODO: Implement business logic
                        Map<String, Object> response = new HashMap<>();
                        response.put("status", "success");
                        response.put("timestamp", LocalDateTime.now());
                        
                        return ResponseEntity.ok(response);
                    } catch (Exception e) {
                        log.error("Error processing request", e);
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body(Map.of("error", e.getMessage()));
                    }
                }
                """,
                description,
                method,
                path,
                capitalize(method.toLowerCase()),
                path,
                description,
                method,
                path
            );
        }
        
        private String capitalize(String str) {
            return str.substring(0, 1).toUpperCase() + str.substring(1);
        }
    }
    
    /**
     * GraphQL Schema Template
     */
    static class GraphQLSchemaTemplate implements CodeTemplate {
        @Override
        public String generate(Map<String, Object> context) {
            String typeName = (String) context.getOrDefault("type", "Entity");
            Map<String, String> fields = (Map<String, String>) context.getOrDefault("fields",
                Map.of("name", "String!", "description", "String"));
            
            StringBuilder schema = new StringBuilder();
            
            // Type definition
            schema.append(String.format("type %s {\n", typeName));
            schema.append("  id: ID!\n");
            fields.forEach((name, type) -> {
                schema.append(String.format("  %s: %s\n", name, mapToGraphQLType(type)));
            });
            schema.append("  createdAt: DateTime!\n");
            schema.append("  updatedAt: DateTime\n");
            schema.append("}\n\n");
            
            // Input types
            schema.append(String.format("input Create%sInput {\n", typeName));
            fields.forEach((name, type) -> {
                schema.append(String.format("  %s: %s\n", name, 
                    mapToGraphQLType(type).replace("!", "")));
            });
            schema.append("}\n\n");
            
            schema.append(String.format("input Update%sInput {\n", typeName));
            fields.forEach((name, type) -> {
                schema.append(String.format("  %s: %s\n", name, 
                    mapToGraphQLType(type).replace("!", "")));
            });
            schema.append("}\n\n");
            
            // Queries
            schema.append("type Query {\n");
            schema.append(String.format("  %s(id: ID!): %s\n", 
                typeName.toLowerCase(), typeName));
            schema.append(String.format("  %ss(limit: Int = 10, offset: Int = 0): [%s!]!\n", 
                typeName.toLowerCase(), typeName));
            schema.append(String.format("  search%ss(query: String!): [%s!]!\n", 
                typeName, typeName));
            schema.append("}\n\n");
            
            // Mutations
            schema.append("type Mutation {\n");
            schema.append(String.format("  create%s(input: Create%sInput!): %s!\n", 
                typeName, typeName, typeName));
            schema.append(String.format("  update%s(id: ID!, input: Update%sInput!): %s!\n", 
                typeName, typeName, typeName));
            schema.append(String.format("  delete%s(id: ID!): Boolean!\n", typeName));
            schema.append("}\n");
            
            return schema.toString();
        }
        
        private String mapToGraphQLType(String javaType) {
            return switch (javaType.toLowerCase()) {
                case "string", "text" -> "String";
                case "string!" -> "String!";
                case "int", "integer" -> "Int";
                case "long" -> "Int";
                case "double", "float" -> "Float";
                case "boolean", "bool" -> "Boolean";
                case "date", "datetime", "timestamp" -> "DateTime";
                default -> "String";
            };
        }
    }
    
    /**
     * Unit Test Template
     */
    static class UnitTestTemplate implements CodeTemplate {
        @Override
        public String generate(Map<String, Object> context) {
            String className = (String) context.getOrDefault("class", "Service");
            String testType = (String) context.getOrDefault("type", "service");
            
            if ("service".equals(testType)) {
                return generateServiceTest(className);
            } else if ("controller".equals(testType)) {
                return generateControllerTest(className);
            }
            
            return generateGenericTest(className);
        }
        
        private String generateServiceTest(String className) {
            return String.format("""
                package com.example.service;
                
                import com.example.model.Entity;
                import com.example.repository.EntityRepository;
                import com.example.dto.EntityDTO;
                import com.example.exception.ResourceNotFoundException;
                import org.junit.jupiter.api.BeforeEach;
                import org.junit.jupiter.api.Test;
                import org.junit.jupiter.api.extension.ExtendWith;
                import org.mockito.InjectMocks;
                import org.mockito.Mock;
                import org.mockito.junit.jupiter.MockitoExtension;
                
                import java.util.Arrays;
                import java.util.List;
                import java.util.Optional;
                
                import static org.assertj.core.api.Assertions.assertThat;
                import static org.assertj.core.api.Assertions.assertThatThrownBy;
                import static org.mockito.ArgumentMatchers.any;
                import static org.mockito.Mockito.*;
                
                @ExtendWith(MockitoExtension.class)
                class %sTest {
                    
                    @Mock
                    private EntityRepository repository;
                    
                    @InjectMocks
                    private %s service;
                    
                    private Entity testEntity;
                    
                    @BeforeEach
                    void setUp() {
                        testEntity = Entity.builder()
                                .id(1L)
                                .name("Test Entity")
                                .description("Test Description")
                                .build();
                    }
                    
                    @Test
                    void findAll_ShouldReturnListOfDTOs() {
                        // Given
                        when(repository.findAll()).thenReturn(Arrays.asList(testEntity));
                        
                        // When
                        List<EntityDTO> result = service.findAll();
                        
                        // Then
                        assertThat(result).hasSize(1);
                        assertThat(result.get(0).getName()).isEqualTo("Test Entity");
                        verify(repository, times(1)).findAll();
                    }
                    
                    @Test
                    void findById_WhenExists_ShouldReturnDTO() {
                        // Given
                        when(repository.findById(1L)).thenReturn(Optional.of(testEntity));
                        
                        // When
                        EntityDTO result = service.findById(1L);
                        
                        // Then
                        assertThat(result).isNotNull();
                        assertThat(result.getName()).isEqualTo("Test Entity");
                        verify(repository, times(1)).findById(1L);
                    }
                    
                    @Test
                    void findById_WhenNotExists_ShouldThrowException() {
                        // Given
                        when(repository.findById(999L)).thenReturn(Optional.empty());
                        
                        // When/Then
                        assertThatThrownBy(() -> service.findById(999L))
                                .isInstanceOf(ResourceNotFoundException.class)
                                .hasMessageContaining("not found");
                    }
                    
                    @Test
                    void create_ShouldSaveAndReturnDTO() {
                        // Given
                        when(repository.save(any(Entity.class))).thenReturn(testEntity);
                        
                        // When
                        EntityDTO result = service.create(new CreateEntityDTO("Test", "Description"));
                        
                        // Then
                        assertThat(result).isNotNull();
                        assertThat(result.getName()).isEqualTo("Test Entity");
                        verify(repository, times(1)).save(any(Entity.class));
                    }
                    
                    @Test
                    void delete_WhenExists_ShouldDeleteEntity() {
                        // Given
                        when(repository.existsById(1L)).thenReturn(true);
                        
                        // When
                        service.delete(1L);
                        
                        // Then
                        verify(repository, times(1)).deleteById(1L);
                    }
                    
                    @Test
                    void delete_WhenNotExists_ShouldThrowException() {
                        // Given
                        when(repository.existsById(999L)).thenReturn(false);
                        
                        // When/Then
                        assertThatThrownBy(() -> service.delete(999L))
                                .isInstanceOf(ResourceNotFoundException.class);
                    }
                }
                """, className, className);
        }
        
        private String generateControllerTest(String className) {
            return String.format("""
                package com.example.controller;
                
                import com.example.dto.EntityDTO;
                import com.example.service.EntityService;
                import com.fasterxml.jackson.databind.ObjectMapper;
                import org.junit.jupiter.api.BeforeEach;
                import org.junit.jupiter.api.Test;
                import org.springframework.beans.factory.annotation.Autowired;
                import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
                import org.springframework.boot.test.mock.mockito.MockBean;
                import org.springframework.http.MediaType;
                import org.springframework.test.web.servlet.MockMvc;
                
                import java.util.Arrays;
                
                import static org.mockito.ArgumentMatchers.any;
                import static org.mockito.Mockito.when;
                import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
                import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
                
                @WebMvcTest(%s.class)
                class %sTest {
                    
                    @Autowired
                    private MockMvc mockMvc;
                    
                    @Autowired
                    private ObjectMapper objectMapper;
                    
                    @MockBean
                    private EntityService service;
                    
                    private EntityDTO testDTO;
                    
                    @BeforeEach
                    void setUp() {
                        testDTO = EntityDTO.builder()
                                .id(1L)
                                .name("Test Entity")
                                .description("Test Description")
                                .build();
                    }
                    
                    @Test
                    void getAll_ShouldReturnListOfEntities() throws Exception {
                        // Given
                        when(service.findAll()).thenReturn(Arrays.asList(testDTO));
                        
                        // When/Then
                        mockMvc.perform(get("/api/entities"))
                                .andExpect(status().isOk())
                                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                                .andExpect(jsonPath("$[0].name").value("Test Entity"));
                    }
                    
                    @Test
                    void getById_WhenExists_ShouldReturnEntity() throws Exception {
                        // Given
                        when(service.findById(1L)).thenReturn(testDTO);
                        
                        // When/Then
                        mockMvc.perform(get("/api/entities/1"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.name").value("Test Entity"));
                    }
                    
                    @Test
                    void create_ShouldReturnCreatedEntity() throws Exception {
                        // Given
                        when(service.create(any())).thenReturn(testDTO);
                        
                        // When/Then
                        mockMvc.perform(post("/api/entities")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(testDTO)))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.name").value("Test Entity"));
                    }
                    
                    @Test
                    void delete_ShouldReturnNoContent() throws Exception {
                        // When/Then
                        mockMvc.perform(delete("/api/entities/1"))
                                .andExpect(status().isNoContent());
                    }
                }
                """, className, className);
        }
        
        private String generateGenericTest(String className) {
            return String.format("""
                package com.example;
                
                import org.junit.jupiter.api.BeforeEach;
                import org.junit.jupiter.api.Test;
                import org.junit.jupiter.api.DisplayName;
                
                import static org.assertj.core.api.Assertions.assertThat;
                
                class %sTest {
                    
                    private %s instance;
                    
                    @BeforeEach
                    void setUp() {
                        instance = new %s();
                    }
                    
                    @Test
                    @DisplayName("Should create instance successfully")
                    void shouldCreateInstance() {
                        assertThat(instance).isNotNull();
                    }
                    
                    // TODO: Add more test cases
                }
                """, className, className, className);
        }
    }
    
    /**
     * Integration Test Template
     */
    static class IntegrationTestTemplate implements CodeTemplate {
        @Override
        public String generate(Map<String, Object> context) {
            String feature = (String) context.getOrDefault("feature", "Entity");
            
            return String.format("""
                package com.example.integration;
                
                import org.junit.jupiter.api.Test;
                import org.springframework.beans.factory.annotation.Autowired;
                import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
                import org.springframework.boot.test.context.SpringBootTest;
                import org.springframework.http.MediaType;
                import org.springframework.test.context.ActiveProfiles;
                import org.springframework.test.web.servlet.MockMvc;
                import org.springframework.transaction.annotation.Transactional;
                
                import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
                import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
                
                @SpringBootTest
                @AutoConfigureMockMvc
                @ActiveProfiles("test")
                @Transactional
                class %sIntegrationTest {
                    
                    @Autowired
                    private MockMvc mockMvc;
                    
                    @Test
                    void shouldPerformCompleteWorkflow() throws Exception {
                        // Create
                        String createJson = "{\\"name\\": \\"Test %s\\", \\"description\\": \\"Test Description\\"}";
                        
                        String response = mockMvc.perform(post("/api/%ss")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(createJson))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.id").exists())
                                .andReturn()
                                .getResponse()
                                .getContentAsString();
                        
                        // Extract ID from response
                        Long id = 1L; // TODO: Parse from response
                        
                        // Read
                        mockMvc.perform(get("/api/%ss/" + id))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.name").value("Test %s"));
                        
                        // Update
                        String updateJson = "{\\"name\\": \\"Updated %s\\", \\"description\\": \\"Updated Description\\"}";
                        
                        mockMvc.perform(put("/api/%ss/" + id)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(updateJson))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.name").value("Updated %s"));
                        
                        // Delete
                        mockMvc.perform(delete("/api/%ss/" + id))
                                .andExpect(status().isNoContent());
                        
                        // Verify deletion
                        mockMvc.perform(get("/api/%ss/" + id))
                                .andExpect(status().isNotFound());
                    }
                    
                    @Test
                    void shouldHandleValidationErrors() throws Exception {
                        String invalidJson = "{\\"name\\": \\"\\", \\"description\\": \\"Test\\"}";
                        
                        mockMvc.perform(post("/api/%ss")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(invalidJson))
                                .andExpect(status().isBadRequest())
                                .andExpect(jsonPath("$.errors").exists());
                    }
                    
                    @Test
                    void shouldHandlePagination() throws Exception {
                        mockMvc.perform(get("/api/%ss/paged")
                                .param("page", "0")
                                .param("size", "10")
                                .param("sort", "name,asc"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.content").isArray())
                                .andExpect(jsonPath("$.pageable").exists());
                    }
                }
                """,
                feature,
                feature,
                feature.toLowerCase(),
                feature.toLowerCase(),
                feature,
                feature,
                feature.toLowerCase(),
                feature,
                feature.toLowerCase(),
                feature.toLowerCase(),
                feature.toLowerCase(),
                feature.toLowerCase()
            );
        }
    }
}