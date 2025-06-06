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
                import './%s.css';
                
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
                import './%s.css';
                
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
                entityName,
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
                import { useParams, Link } from 'react-router-dom';
                import './%s.css';
                
                interface %s {
                  id: number;
                  name: string;
                  description?: string;
                  createdAt?: string;
                  [key: string]: any;
                }
                
                const %s: React.FC = () => {
                  const { id } = useParams<{ id: string }>();
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
                  
                  if (loading) return <div className="loading">Loading...</div>;
                  if (error) return <div className="error">Error: {error}</div>;
                  if (!item) return <div className="not-found">Item not found</div>;
                  
                  return (
                    <div className="%s-detail">
                      <div className="detail-header">
                        <h1>{item.name}</h1>
                        <div className="detail-actions">
                          <Link to={`/edit/${item.id}`} className="btn btn-primary">Edit</Link>
                          <Link to="/" className="btn">Back to List</Link>
                        </div>
                      </div>
                      
                      <div className="detail-content">
                        {Object.entries(item).map(([key, value]) => {
                          if (key === 'id') return null;
                          return (
                            <div key={key} className="detail-item">
                              <h3>{key.charAt(0).toUpperCase() + key.slice(1)}</h3>
                              <p>{typeof value === 'object' ? JSON.stringify(value) : String(value)}</p>
                            </div>
                          );
                        })}
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
                componentName.toLowerCase(),
                componentName
            );
        }
    }
    
    /**
     * React Hook Template
     */
    static class ReactHookTemplate implements CodeTemplate {
        @Override
        public String generate(Map<String, Object> context) {
            String hookName = (String) context.getOrDefault("name", "useData");
            String entityName = (String) context.getOrDefault("entity", "Item");
            String apiEndpoint = (String) context.getOrDefault("endpoint", "/api/items");
            
            return String.format("""
                import { useState, useEffect, useCallback } from 'react';
                
                interface %s {
                  id: number;
                  name: string;
                  [key: string]: any;
                }
                
                interface Use%sResult {
                  items: %s[];
                  loading: boolean;
                  error: string | null;
                  fetchItems: () => Promise<void>;
                  fetchItem: (id: number) => Promise<%s | null>;
                  createItem: (item: Omit<%s, 'id'>) => Promise<%s | null>;
                  updateItem: (id: number, item: Partial<%s>) => Promise<boolean>;
                  deleteItem: (id: number) => Promise<boolean>;
                }
                
                export const %s = (): Use%sResult => {
                  const [items, setItems] = useState<%s[]>([]);
                  const [loading, setLoading] = useState(false);
                  const [error, setError] = useState<string | null>(null);
                  
                  const fetchItems = useCallback(async () => {
                    setLoading(true);
                    setError(null);
                    
                    try {
                      const response = await fetch('%s');
                      if (!response.ok) throw new Error('Failed to fetch items');
                      const data = await response.json();
                      setItems(data);
                      return data;
                    } catch (err) {
                      setError(err instanceof Error ? err.message : 'An error occurred');
                      return [];
                    } finally {
                      setLoading(false);
                    }
                  }, []);
                  
                  const fetchItem = useCallback(async (id: number): Promise<%s | null> => {
                    setLoading(true);
                    setError(null);
                    
                    try {
                      const response = await fetch(`%s/${id}`);
                      if (!response.ok) throw new Error('Failed to fetch item');
                      const data = await response.json();
                      return data;
                    } catch (err) {
                      setError(err instanceof Error ? err.message : 'An error occurred');
                      return null;
                    } finally {
                      setLoading(false);
                    }
                  }, []);
                  
                  const createItem = useCallback(async (item: Omit<%s, 'id'>): Promise<%s | null> => {
                    setLoading(true);
                    setError(null);
                    
                    try {
                      const response = await fetch('%s', {
                        method: 'POST',
                        headers: {
                          'Content-Type': 'application/json',
                        },
                        body: JSON.stringify(item),
                      });
                      
                      if (!response.ok) throw new Error('Failed to create item');
                      const newItem = await response.json();
                      setItems(prev => [...prev, newItem]);
                      return newItem;
                    } catch (err) {
                      setError(err instanceof Error ? err.message : 'An error occurred');
                      return null;
                    } finally {
                      setLoading(false);
                    }
                  }, []);
                  
                  const updateItem = useCallback(async (id: number, item: Partial<%s>): Promise<boolean> => {
                    setLoading(true);
                    setError(null);
                    
                    try {
                      const response = await fetch(`%s/${id}`, {
                        method: 'PUT',
                        headers: {
                          'Content-Type': 'application/json',
                        },
                        body: JSON.stringify(item),
                      });
                      
                      if (!response.ok) throw new Error('Failed to update item');
                      const updatedItem = await response.json();
                      
                      setItems(prev => 
                        prev.map(i => i.id === id ? { ...i, ...updatedItem } : i)
                      );
                      
                      return true;
                    } catch (err) {
                      setError(err instanceof Error ? err.message : 'An error occurred');
                      return false;
                    } finally {
                      setLoading(false);
                    }
                  }, []);
                  
                  const deleteItem = useCallback(async (id: number): Promise<boolean> => {
                    setLoading(true);
                    setError(null);
                    
                    try {
                      const response = await fetch(`%s/${id}`, {
                        method: 'DELETE',
                      });
                      
                      if (!response.ok) throw new Error('Failed to delete item');
                      setItems(prev => prev.filter(item => item.id !== id));
                      return true;
                    } catch (err) {
                      setError(err instanceof Error ? err.message : 'An error occurred');
                      return false;
                    } finally {
                      setLoading(false);
                    }
                  }, []);
                  
                  useEffect(() => {
                    fetchItems();
                  }, [fetchItems]);
                  
                  return {
                    items,
                    loading,
                    error,
                    fetchItems,
                    fetchItem,
                    createItem,
                    updateItem,
                    deleteItem,
                  };
                };
                """,
                entityName,
                entityName,
                entityName,
                entityName,
                entityName,
                entityName,
                entityName,
                hookName,
                entityName,
                entityName,
                apiEndpoint,
                entityName,
                apiEndpoint,
                entityName,
                entityName,
                apiEndpoint,
                entityName,
                apiEndpoint,
                apiEndpoint
            );
        }
    }
    
    /**
     * Spring Entity Template
     */
    static class SpringEntityTemplate implements CodeTemplate {
        @Override
        public String generate(Map<String, Object> context) {
            String packageName = (String) context.getOrDefault("package", "com.example.model");
            String entityName = (String) context.getOrDefault("name", "Entity");
            String tableName = (String) context.getOrDefault("table", entityName.toLowerCase());
            Map<String, String> fields = (Map<String, String>) context.getOrDefault("fields", 
                Map.of("name", "String", "description", "String"));
            
            StringBuilder fieldDeclarations = new StringBuilder();
            
            fields.forEach((fieldName, fieldType) -> {
                String columnAnnotation = "";
                if (!"id".equals(fieldName)) {
                    columnAnnotation = String.format("    @Column(name = \"%s\")\n", fieldName);
                }
                
                fieldDeclarations.append(String.format("""
                %s    private %s %s;
                    
                """, columnAnnotation, fieldType, fieldName));
            });
            
            return String.format("""
                package %s;
                
                import lombok.AllArgsConstructor;
                import lombok.Builder;
                import lombok.Data;
                import lombok.NoArgsConstructor;
                
                import javax.persistence.*;
                import java.time.LocalDateTime;
                
                @Entity
                @Table(name = "%s")
                @Data
                @Builder
                @NoArgsConstructor
                @AllArgsConstructor
                public class %s {
                    
                    @Id
                    @GeneratedValue(strategy = GenerationType.IDENTITY)
                    private Long id;
                    
                %s
                    @Column(name = "created_at")
                    private LocalDateTime createdAt;
                    
                    @Column(name = "updated_at")
                    private LocalDateTime updatedAt;
                    
                    @PrePersist
                    protected void onCreate() {
                        createdAt = LocalDateTime.now();
                        updatedAt = LocalDateTime.now();
                    }
                    
                    @PreUpdate
                    protected void onUpdate() {
                        updatedAt = LocalDateTime.now();
                    }
                }
                """,
                packageName,
                tableName,
                entityName,
                fieldDeclarations
            );
        }
    }
    
    /**
     * Spring Repository Template
     */
    static class SpringRepositoryTemplate implements CodeTemplate {
        @Override
        public String generate(Map<String, Object> context) {
            String packageName = (String) context.getOrDefault("package", "com.example.repository");
            String entityName = (String) context.getOrDefault("entity", "Entity");
            String entityPackage = (String) context.getOrDefault("entityPackage", "com.example.model");
            
            return String.format("""
                package %s;
                
                import %s.%s;
                import org.springframework.data.jpa.repository.JpaRepository;
                import org.springframework.stereotype.Repository;
                
                import java.util.List;
                import java.util.Optional;
                
                @Repository
                public interface %sRepository extends JpaRepository<%s, Long> {
                    
                    List<%s> findByNameContainingIgnoreCase(String name);
                    
                    Optional<%s> findByName(String name);
                    
                }
                """,
                packageName,
                entityPackage,
                entityName,
                entityName,
                entityName,
                entityName,
                entityName
            );
        }
    }
    
    /**
     * Spring Service Template
     */
    static class SpringServiceTemplate implements CodeTemplate {
        @Override
        public String generate(Map<String, Object> context) {
            String packageName = (String) context.getOrDefault("package", "com.example.service");
            String entityName = (String) context.getOrDefault("entity", "Entity");
            String entityPackage = (String) context.getOrDefault("entityPackage", "com.example.model");
            String repositoryPackage = (String) context.getOrDefault("repositoryPackage", "com.example.repository");
            
            return String.format("""
                package %s;
                
                import %s.%s;
                import %s.%sRepository;
                import lombok.RequiredArgsConstructor;
                import org.springframework.stereotype.Service;
                import org.springframework.transaction.annotation.Transactional;
                
                import java.util.List;
                import java.util.Optional;
                
                @Service
                @RequiredArgsConstructor
                public class %sService {
                    
                    private final %sRepository repository;
                    
                    public List<%s> findAll() {
                        return repository.findAll();
                    }
                    
                    public Optional<%s> findById(Long id) {
                        return repository.findById(id);
                    }
                    
                    public List<%s> findByName(String name) {
                        return repository.findByNameContainingIgnoreCase(name);
                    }
                    
                    @Transactional
                    public %s save(%s entity) {
                        return repository.save(entity);
                    }
                    
                    @Transactional
                    public %s update(Long id, %s entity) {
                        return repository.findById(id)
                                .map(existingEntity -> {
                                    entity.setId(id);
                                    return repository.save(entity);
                                })
                                .orElseThrow(() -> new RuntimeException("%s not found with id: " + id));
                    }
                    
                    @Transactional
                    public void delete(Long id) {
                        repository.deleteById(id);
                    }
                    
                    public boolean exists(Long id) {
                        return repository.existsById(id);
                    }
                }
                """,
                packageName,
                entityPackage,
                entityName,
                repositoryPackage,
                entityName,
                entityName,
                entityName,
                entityName,
                entityName,
                entityName,
                entityName,
                entityName,
                entityName,
                entityName,
                entityName
            );
        }
    }
    
    /**
     * Spring Controller Template
     */
    static class SpringControllerTemplate implements CodeTemplate {
        @Override
        public String generate(Map<String, Object> context) {
            String packageName = (String) context.getOrDefault("package", "com.example.controller");
            String entityName = (String) context.getOrDefault("entity", "Entity");
            String entityPackage = (String) context.getOrDefault("entityPackage", "com.example.model");
            String servicePackage = (String) context.getOrDefault("servicePackage", "com.example.service");
            String basePath = (String) context.getOrDefault("basePath", "/api/" + entityName.toLowerCase() + "s");
            
            return String.format("""
                package %s;
                
                import %s.%s;
                import %s.%sService;
                import lombok.RequiredArgsConstructor;
                import org.springframework.http.HttpStatus;
                import org.springframework.http.ResponseEntity;
                import org.springframework.web.bind.annotation.*;
                
                import java.util.List;
                
                @RestController
                @RequestMapping("%s")
                @RequiredArgsConstructor
                public class %sController {
                    
                    private final %sService service;
                    
                    @GetMapping
                    public List<%s> findAll() {
                        return service.findAll();
                    }
                    
                    @GetMapping("/{id}")
                    public ResponseEntity<%s> findById(@PathVariable Long id) {
                        return service.findById(id)
                                .map(ResponseEntity::ok)
                                .orElse(ResponseEntity.notFound().build());
                    }
                    
                    @GetMapping("/search")
                    public List<%s> search(@RequestParam String name) {
                        return service.findByName(name);
                    }
                    
                    @PostMapping
                    @ResponseStatus(HttpStatus.CREATED)
                    public %s create(@RequestBody %s entity) {
                        return service.save(entity);
                    }
                    
                    @PutMapping("/{id}")
                    public ResponseEntity<%s> update(@PathVariable Long id, @RequestBody %s entity) {
                        if (!service.exists(id)) {
                            return ResponseEntity.notFound().build();
                        }
                        return ResponseEntity.ok(service.update(id, entity));
                    }
                    
                    @DeleteMapping("/{id}")
                    public ResponseEntity<Void> delete(@PathVariable Long id) {
                        if (!service.exists(id)) {
                            return ResponseEntity.notFound().build();
                        }
                        service.delete(id);
                        return ResponseEntity.noContent().build();
                    }
                }
                """,
                packageName,
                entityPackage,
                entityName,
                servicePackage,
                entityName,
                basePath,
                entityName,
                entityName,
                entityName,
                entityName,
                entityName,
                entityName,
                entityName,
                entityName,
                entityName
            );
        }
    }
    
    /**
     * Spring DTO Template
     */
    static class SpringDTOTemplate implements CodeTemplate {
        @Override
        public String generate(Map<String, Object> context) {
            String packageName = (String) context.getOrDefault("package", "com.example.dto");
            String entityName = (String) context.getOrDefault("entity", "Entity");
            Map<String, String> fields = (Map<String, String>) context.getOrDefault("fields", 
                Map.of("name", "String", "description", "String"));
            
            StringBuilder fieldDeclarations = new StringBuilder();
            
            fields.forEach((fieldName, fieldType) -> {
                fieldDeclarations.append(String.format("    private %s %s;\n    \n", fieldType, fieldName));
            });
            
            return String.format("""
                package %s;
                
                import lombok.AllArgsConstructor;
                import lombok.Builder;
                import lombok.Data;
                import lombok.NoArgsConstructor;
                
                @Data
                @Builder
                @NoArgsConstructor
                @AllArgsConstructor
                public class %sDTO {
                    
                    private Long id;
                    
                %s}
                """,
                packageName,
                entityName,
                fieldDeclarations
            );
        }
    }
    
    /**
     * SQL Table Template
     */
    static class SQLTableTemplate implements CodeTemplate {
        @Override
        public String generate(Map<String, Object> context) {
            String tableName = (String) context.getOrDefault("table", "entity");
            Map<String, String> fields = (Map<String, String>) context.getOrDefault("fields", 
                Map.of("name", "VARCHAR(255)", "description", "TEXT"));
            
            StringBuilder fieldDefinitions = new StringBuilder();
            
            fields.forEach((fieldName, fieldType) -> {
                fieldDefinitions.append(String.format("    %s %s NOT NULL,\n", fieldName, fieldType));
            });
            
            return String.format("""
                CREATE TABLE %s (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                %s    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                );
                """,
                tableName,
                fieldDefinitions
            );
        }
    }
    
    /**
     * SQL Migration Template
     */
    static class SQLMigrationTemplate implements CodeTemplate {
        @Override
        public String generate(Map<String, Object> context) {
            String version = (String) context.getOrDefault("version", "V1");
            String description = (String) context.getOrDefault("description", "Create_initial_tables");
            String tableName = (String) context.getOrDefault("table", "entity");
            Map<String, String> fields = (Map<String, String>) context.getOrDefault("fields", 
                Map.of("name", "VARCHAR(255)", "description", "TEXT"));
            
            StringBuilder fieldDefinitions = new StringBuilder();
            
            fields.forEach((fieldName, fieldType) -> {
                fieldDefinitions.append(String.format("    %s %s NOT NULL,\n", fieldName, fieldType));
            });
            
            return String.format("""
                -- %s__%s.sql
                
                CREATE TABLE %s (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                %s    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                );
                
                -- Add indexes
                CREATE INDEX idx_%s_name ON %s(name);
                """,
                version,
                description,
                tableName,
                fieldDefinitions,
                tableName,
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
            String packageName = (String) context.getOrDefault("package", "com.example.controller");
            String endpointName = (String) context.getOrDefault("name", "Resource");
            String path = (String) context.getOrDefault("path", "/api/resources");
            String returnType = (String) context.getOrDefault("returnType", "Object");
            
            return String.format("""
                package %s;
                
                import lombok.RequiredArgsConstructor;
                import org.springframework.http.ResponseEntity;
                import org.springframework.web.bind.annotation.*;
                
                import java.util.List;
                import java.util.Map;
                
                @RestController
                @RequestMapping("%s")
                @RequiredArgsConstructor
                public class %sController {
                    
                    @GetMapping
                    public ResponseEntity<List<%s>> getAll() {
                        // TODO: Implement getAll
                        return ResponseEntity.ok(List.of());
                    }
                    
                    @GetMapping("/{id}")
                    public ResponseEntity<%s> getById(@PathVariable String id) {
                        // TODO: Implement getById
                        return ResponseEntity.notFound().build();
                    }
                    
                    @PostMapping
                    public ResponseEntity<%s> create(@RequestBody Map<String, Object> request) {
                        // TODO: Implement create
                        return ResponseEntity.ok(null);
                    }
                    
                    @PutMapping("/{id}")
                    public ResponseEntity<%s> update(@PathVariable String id, @RequestBody Map<String, Object> request) {
                        // TODO: Implement update
                        return ResponseEntity.notFound().build();
                    }
                    
                    @DeleteMapping("/{id}")
                    public ResponseEntity<Void> delete(@PathVariable String id) {
                        // TODO: Implement delete
                        return ResponseEntity.noContent().build();
                    }
                }
                """,
                packageName,
                path,
                endpointName,
                returnType,
                returnType,
                returnType,
                returnType
            );
        }
    }
    
    /**
     * GraphQL Schema Template
     */
    static class GraphQLSchemaTemplate implements CodeTemplate {
        @Override
        public String generate(Map<String, Object> context) {
            String typeName = (String) context.getOrDefault("type", "Item");
            Map<String, String> fields = (Map<String, String>) context.getOrDefault("fields", 
                Map.of("name", "String", "description", "String"));
            
            StringBuilder fieldDefinitions = new StringBuilder();
            StringBuilder inputFieldDefinitions = new StringBuilder();
            
            fields.forEach((fieldName, fieldType) -> {
                String graphqlType = mapToGraphQLType(fieldType);
                fieldDefinitions.append(String.format("  %s: %s\n", fieldName, graphqlType));
                inputFieldDefinitions.append(String.format("  %s: %s\n", fieldName, graphqlType));
            });
            
            return String.format("""
                type %s {
                  id: ID!
                %s  createdAt: String
                  updatedAt: String
                }
                
                input %sInput {
                %s}
                
                input %sUpdateInput {
                  id: ID!
                %s}
                
                type Query {
                  get%s(id: ID!): %s
                  getAll%ss: [%s]
                  search%ss(query: String): [%s]
                }
                
                type Mutation {
                  create%s(input: %sInput!): %s
                  update%s(input: %sUpdateInput!): %s
                  delete%s(id: ID!): Boolean
                }
                """,
                typeName,
                fieldDefinitions,
                typeName,
                inputFieldDefinitions,
                typeName,
                inputFieldDefinitions,
                typeName, typeName,
                typeName, typeName,
                typeName, typeName,
                typeName, typeName, typeName,
                typeName, typeName, typeName,
                typeName
            );
        }
        
        private String mapToGraphQLType(String javaType) {
            return switch (javaType.toLowerCase()) {
                case "string", "text" -> "String!";
                case "integer", "int", "long" -> "Int!";
                case "float", "double" -> "Float!";
                case "boolean", "bool" -> "Boolean!";
                case "date", "datetime", "timestamp" -> "String!";
                default -> "String!";
            };
        }
    }
    
    /**
     * Unit Test Template
     */
    static class UnitTestTemplate implements CodeTemplate {
        @Override
        public String generate(Map<String, Object> context) {
            String packageName = (String) context.getOrDefault("package", "com.example.service");
            String className = (String) context.getOrDefault("class", "Service");
            String testName = (String) context.getOrDefault("name", className + "Test");
            
            return String.format("""
                package %s;
                
                import org.junit.jupiter.api.BeforeEach;
                import org.junit.jupiter.api.Test;
                import org.mockito.InjectMocks;
                import org.mockito.Mock;
                import org.mockito.MockitoAnnotations;
                
                import static org.junit.jupiter.api.Assertions.*;
                import static org.mockito.Mockito.*;
                
                class %s {
                    
                    @InjectMocks
                    private %s service;
                    
                    // TODO: Add mocks
                    // @Mock
                    // private Repository repository;
                    
                    @BeforeEach
                    void setUp() {
                        MockitoAnnotations.openMocks(this);
                    }
                    
                    @Test
                    void testMethodName_scenario_expectedBehavior() {
                        // Arrange
                        // TODO: Setup test data and expectations
                        
                        // Act
                        // TODO: Call the method under test
                        
                        // Assert
                        // TODO: Verify the results
                        fail("Test not implemented");
                    }
                    
                    // TODO: Add more test methods
                }
                """,
                packageName,
                testName,
                className
            );
        }
    }
    
    /**
     * Integration Test Template
     */
    static class IntegrationTestTemplate implements CodeTemplate {
        @Override
        public String generate(Map<String, Object> context) {
            String packageName = (String) context.getOrDefault("package", "com.example.controller");
            String className = (String) context.getOrDefault("class", "Controller");
            String testName = (String) context.getOrDefault("name", className + "IntegrationTest");
            String basePath = (String) context.getOrDefault("basePath", "/api/resources");
            
            return String.format("""
                package %s;
                
                import org.junit.jupiter.api.Test;
                import org.springframework.beans.factory.annotation.Autowired;
                import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
                import org.springframework.boot.test.context.SpringBootTest;
                import org.springframework.http.MediaType;
                import org.springframework.test.web.servlet.MockMvc;
                
                import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
                import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
                
                @SpringBootTest
                @AutoConfigureMockMvc
                class %s {
                    
                    @Autowired
                    private MockMvc mockMvc;
                    
                    @Test
                    void testGetAll_shouldReturnOk() throws Exception {
                        mockMvc.perform(get("%s"))
                                .andExpect(status().isOk())
                                .andExpect(content().contentType(MediaType.APPLICATION_JSON));
                    }
                    
                    @Test
                    void testGetById_withValidId_shouldReturnOk() throws Exception {
                        // TODO: Replace with valid ID
                        String id = "1";
                        
                        mockMvc.perform(get("%s/{id}", id))
                                .andExpect(status().isOk())
                                .andExpect(content().contentType(MediaType.APPLICATION_JSON));
                    }
                    
                    @Test
                    void testGetById_withInvalidId_shouldReturnNotFound() throws Exception {
                        String id = "999";
                        
                        mockMvc.perform(get("%s/{id}", id))
                                .andExpect(status().isNotFound());
                    }
                    
                    @Test
                    void testCreate_withValidData_shouldReturnCreated() throws Exception {
                        String requestBody = "{\\"name\\": \\"Test\\", \\"description\\": \\"Test Description\\"}";
                        
                        mockMvc.perform(post("%s")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody))
                                .andExpect(status().isOk())
                                .andExpect(content().contentType(MediaType.APPLICATION_JSON));
                    }
                    
                    // TODO: Add more test methods
                }
                """,
                packageName,
                testName,
                basePath,
                basePath,
                basePath,
                basePath
            );
        }
    }
}
