package com.ai.developer.tools.impl;

import com.ai.developer.tools.*;
import com.microsoft.playwright.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@Slf4j
@Component
public class BrowserAutomationTool implements Tool {
    
    // Default workspace path for tools
    private static final String DEFAULT_WORKSPACE_PATH = "/tmp/ai-developer-agent";
    
    private Playwright playwright;
    private Browser browser;
    
    @PostConstruct
    public void init() {
        this.playwright = Playwright.create();
        this.browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                .setHeadless(true)
                .setArgs(Arrays.asList("--no-sandbox", "--disable-setuid-sandbox")));
    }
    
    @PreDestroy
    public void cleanup() {
        if (browser != null) browser.close();
        if (playwright != null) playwright.close();
    }
    
    @Override
    public String getName() {
        return "browser_automation";
    }
    
    @Override
    public String getDescription() {
        return "Automate browser interactions and capture screenshots within session-specific workspaces";
    }
    
    @Override
    public Map<String, ParameterInfo> getParameters() {
        Map<String, ParameterInfo> params = new HashMap<>();
        
        params.put("action", ParameterInfo.builder()
            .name("action")
            .type("string")
            .description("Action: navigate, screenshot, click, type, wait")
            .required(true)
            .build());
            
        params.put("url", ParameterInfo.builder()
            .name("url")
            .type("string")
            .description("URL to navigate to")
            .required(false)
            .build());
            
        params.put("selector", ParameterInfo.builder()
            .name("selector")
            .type("string")
            .description("CSS selector for element interaction")
            .required(false)
            .build());
            
        params.put("text", ParameterInfo.builder()
            .name("text")
            .type("string")
            .description("Text to type or wait for")
            .required(false)
            .build());
            
        params.put("sessionId", ParameterInfo.builder()
            .name("sessionId")
            .type("string")
            .description("Chat session ID for workspace management")
            .required(false)
            .build());
            
        params.put("taskDir", ParameterInfo.builder()
            .name("taskDir")
            .type("string")
            .description("Task-specific subdirectory within the session workspace")
            .required(false)
            .build());
            
        params.put("screenshotPath", ParameterInfo.builder()
            .name("screenshotPath")
            .type("string")
            .description("Path to save screenshot (relative to session workspace)")
            .required(false)
            .build());
            
        return params;
    }
    
    @Override
    public Flux<ToolOutput> execute(Map<String, Object> arguments) {
        // Enhanced logging for debugging argument structure
        log.info("BrowserAutomationTool executing with arguments: {}", arguments);
        
        // Extract action with alternative key checking
        String actionParam = (String) arguments.get("action");
        if (actionParam == null) {
            // Check for alternative keys that might contain action
            if (arguments.containsKey("operation")) {
                actionParam = (String) arguments.get("operation");
                log.warn("Using 'operation' instead of 'action' for BrowserAutomationTool");
            } else if (arguments.containsKey("command")) {
                actionParam = (String) arguments.get("command");
                log.warn("Using 'command' instead of 'action' for BrowserAutomationTool");
            } else if (arguments.containsKey("task")) {
                actionParam = (String) arguments.get("task");
                log.warn("Using 'task' instead of 'action' for BrowserAutomationTool");
            } else {
                log.error("Action parameter is null. Available keys: {}", arguments.keySet());
                return Flux.error(new IllegalArgumentException("Action parameter is required"));
            }
        }
        
        // Create final copies of variables for use in lambda
        final String action = actionParam;
        
        String sessionId = (String) arguments.getOrDefault("sessionId", UUID.randomUUID().toString());
        String taskDir = (String) arguments.getOrDefault("taskDir", "");
        String screenshotPath = (String) arguments.getOrDefault("screenshotPath", "screenshots");
        
        // Resolve screenshot path within session workspace
        String resolvedScreenshotPath = resolveScreenshotPath(screenshotPath, sessionId, taskDir);
        
        // Log the final parameters being used
        log.info("BrowserAutomationTool executing with action: {}, screenshotPath: {}", action, resolvedScreenshotPath);
        
        // Create final copies of all variables used in lambda
        final Map<String, Object> finalArguments = arguments;
        final String finalSessionId = sessionId;
        final String finalTaskDir = taskDir;
        final String finalResolvedScreenshotPath = resolvedScreenshotPath;
        
        return Mono.fromCallable(() -> {
            BrowserContext context = browser.newContext();
            Page page = context.newPage();
            
            try {
                if (action == null) {
                    throw new IllegalArgumentException("Action parameter is required");
                }
                
                switch (action.toLowerCase()) {
                    case "navigate":
                        return navigateTo(page, (String) finalArguments.get("url"), finalResolvedScreenshotPath, finalSessionId, finalTaskDir);
                    case "screenshot":
                        return captureScreenshot(page, finalResolvedScreenshotPath, finalSessionId, finalTaskDir);
                    case "click":
                        return clickElement(page, (String) finalArguments.get("selector"), finalResolvedScreenshotPath, finalSessionId, finalTaskDir);
                    case "type":
                        return typeText(page, (String) finalArguments.get("selector"), 
                                        (String) finalArguments.get("text"), finalResolvedScreenshotPath, finalSessionId, finalTaskDir);
                    case "wait":
                        return waitForElement(page, (String) finalArguments.get("selector"), finalResolvedScreenshotPath, finalSessionId, finalTaskDir);
                    default:
                        throw new IllegalArgumentException("Unknown action: " + action);
                }
            } finally {
                context.close();
            }
        }).flux();
    }
    
    /**
     * Resolve screenshot path within session workspace
     * Creates necessary directories if they don't exist
     */
    private String resolveScreenshotPath(String screenshotPath, String sessionId, String taskDir) {
        // Create session workspace directory
        String sessionWorkspace = DEFAULT_WORKSPACE_PATH + "/" + sessionId;
        
        // If task directory is specified, include it in the path
        if (taskDir != null && !taskDir.isEmpty()) {
            sessionWorkspace = sessionWorkspace + "/" + taskDir;
        }
        
        // Add screenshot directory
        String fullPath = sessionWorkspace + "/" + screenshotPath;
        
        try {
            Files.createDirectories(Path.of(fullPath));
        } catch (Exception e) {
            log.error("Error creating screenshot directory: {}", fullPath, e);
        }
        
        return fullPath;
    }
    
    /**
     * Get the full workspace path including session ID and optional task directory
     */
    private String getWorkspacePath(String sessionId, String taskDir) {
        String workspacePath = DEFAULT_WORKSPACE_PATH + "/" + sessionId;
        if (taskDir != null && !taskDir.isEmpty()) {
            workspacePath = workspacePath + "/" + taskDir;
        }
        return workspacePath;
    }
    
    private ToolOutput navigateTo(Page page, String url, String screenshotPath, String sessionId, String taskDir) {
        page.navigate(url);
        page.waitForLoadState();  // Default is 'load' event
        
        // Generate screenshot filename
        String filename = screenshotPath + "/navigation_" + System.currentTimeMillis() + ".png";
        
        // Capture screenshot for visualization
        byte[] screenshot = page.screenshot(new Page.ScreenshotOptions()
                .setFullPage(true));
        
        // Save screenshot to file
        try {
            Files.write(Path.of(filename), screenshot);
        } catch (Exception e) {
            log.error("Error saving screenshot: {}", filename, e);
        }
        
        return ToolOutput.builder()
                .type("navigation")
                .content("Navigated to: " + url)
                .metadata(Map.of(
                    "url", url,
                    "title", page.title(),
                    "status", "success",
                    "screenshot", Base64.getEncoder().encodeToString(screenshot),
                    "screenshotPath", filename,
                    "html", page.content(),
                    "sessionId", sessionId,
                    "workspacePath", getWorkspacePath(sessionId, taskDir)
                ))
                .build();
    }
    
    private ToolOutput captureScreenshot(Page page, String screenshotPath, String sessionId, String taskDir) {
        String filename = screenshotPath + "/screenshot_" + System.currentTimeMillis() + ".png";
        byte[] screenshot = page.screenshot(new Page.ScreenshotOptions()
                .setFullPage(true));
        
        // Save screenshot to file
        try {
            Files.write(Path.of(filename), screenshot);
        } catch (Exception e) {
            log.error("Error saving screenshot: {}", filename, e);
        }
        
        return ToolOutput.builder()
                .type("screenshot")
                .content(Base64.getEncoder().encodeToString(screenshot))
                .metadata(Map.of(
                    "filename", filename,
                    "format", "png",
                    "encoding", "base64",
                    "html", page.content(),
                    "sessionId", sessionId,
                    "workspacePath", getWorkspacePath(sessionId, taskDir)
                ))
                .build();
    }
    
    private ToolOutput clickElement(Page page, String selector, String screenshotPath, String sessionId, String taskDir) {
        // Generate screenshot filenames
        String beforeFilename = screenshotPath + "/click_before_" + System.currentTimeMillis() + ".png";
        String afterFilename = screenshotPath + "/click_after_" + System.currentTimeMillis() + ".png";
        
        // Capture before state
        byte[] beforeScreenshot = page.screenshot(new Page.ScreenshotOptions()
                .setFullPage(false));
        
        // Save before screenshot
        try {
            Files.write(Path.of(beforeFilename), beforeScreenshot);
        } catch (Exception e) {
            log.error("Error saving before screenshot: {}", beforeFilename, e);
        }
        
        // Perform click
        page.click(selector);
        
        // Wait for any navigation or network activity to complete
        page.waitForLoadState();
        
        // Capture after state
        byte[] afterScreenshot = page.screenshot(new Page.ScreenshotOptions()
                .setFullPage(false));
        
        // Save after screenshot
        try {
            Files.write(Path.of(afterFilename), afterScreenshot);
        } catch (Exception e) {
            log.error("Error saving after screenshot: {}", afterFilename, e);
        }
        
        return ToolOutput.builder()
                .type("click")
                .content("Clicked element: " + selector)
                .metadata(Map.of(
                    "selector", selector,
                    "before_screenshot", Base64.getEncoder().encodeToString(beforeScreenshot),
                    "after_screenshot", Base64.getEncoder().encodeToString(afterScreenshot),
                    "before_screenshot_path", beforeFilename,
                    "after_screenshot_path", afterFilename,
                    "html", page.content(),
                    "sessionId", sessionId,
                    "workspacePath", getWorkspacePath(sessionId, taskDir)
                ))
                .build();
    }
    
    private ToolOutput typeText(Page page, String selector, String text, String screenshotPath, String sessionId, String taskDir) {
        // Generate screenshot filenames
        String beforeFilename = screenshotPath + "/type_before_" + System.currentTimeMillis() + ".png";
        String afterFilename = screenshotPath + "/type_after_" + System.currentTimeMillis() + ".png";
        
        // Capture before state
        byte[] beforeScreenshot = page.screenshot(new Page.ScreenshotOptions()
                .setFullPage(false));
        
        // Save before screenshot
        try {
            Files.write(Path.of(beforeFilename), beforeScreenshot);
        } catch (Exception e) {
            log.error("Error saving before screenshot: {}", beforeFilename, e);
        }
        
        // Perform type
        page.fill(selector, text);
        
        // Capture after state
        byte[] afterScreenshot = page.screenshot(new Page.ScreenshotOptions()
                .setFullPage(false));
        
        // Save after screenshot
        try {
            Files.write(Path.of(afterFilename), afterScreenshot);
        } catch (Exception e) {
            log.error("Error saving after screenshot: {}", afterFilename, e);
        }
        
        return ToolOutput.builder()
                .type("type")
                .content("Typed text into: " + selector)
                .metadata(Map.of(
                    "selector", selector,
                    "text", text,
                    "before_screenshot", Base64.getEncoder().encodeToString(beforeScreenshot),
                    "after_screenshot", Base64.getEncoder().encodeToString(afterScreenshot),
                    "before_screenshot_path", beforeFilename,
                    "after_screenshot_path", afterFilename,
                    "sessionId", sessionId,
                    "workspacePath", getWorkspacePath(sessionId, taskDir)
                ))
                .build();
    }
    
    private ToolOutput waitForElement(Page page, String selector, String screenshotPath, String sessionId, String taskDir) {
        page.waitForSelector(selector);
        
        // Generate screenshot filename
        String filename = screenshotPath + "/wait_" + System.currentTimeMillis() + ".png";
        
        // Capture screenshot showing the element
        byte[] screenshot = page.screenshot(new Page.ScreenshotOptions()
                .setFullPage(false));
        
        // Save screenshot
        try {
            Files.write(Path.of(filename), screenshot);
        } catch (Exception e) {
            log.error("Error saving screenshot: {}", filename, e);
        }
        
        return ToolOutput.builder()
                .type("wait")
                .content("Element appeared: " + selector)
                .metadata(Map.of(
                    "selector", selector,
                    "screenshot", Base64.getEncoder().encodeToString(screenshot),
                    "screenshot_path", filename,
                    "sessionId", sessionId,
                    "workspacePath", getWorkspacePath(sessionId, taskDir)
                ))
                .build();
    }
}
