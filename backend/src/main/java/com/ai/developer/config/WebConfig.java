package com.ai.developer.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }
    
    /**
     * Fix for HttpMessageNotWritableException with event-stream content type
     * Adds support for converting Map objects to text/event-stream format
     */
    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        // Find the Jackson converter
        for (HttpMessageConverter<?> converter : converters) {
            if (converter instanceof MappingJackson2HttpMessageConverter) {
                MappingJackson2HttpMessageConverter jacksonConverter = (MappingJackson2HttpMessageConverter) converter;
                
                // Add text/event-stream to supported media types
                List<MediaType> supportedMediaTypes = new ArrayList<>(jacksonConverter.getSupportedMediaTypes());
                supportedMediaTypes.add(MediaType.TEXT_EVENT_STREAM);
                jacksonConverter.setSupportedMediaTypes(supportedMediaTypes);
                
                break;
            }
        }
    }
}
