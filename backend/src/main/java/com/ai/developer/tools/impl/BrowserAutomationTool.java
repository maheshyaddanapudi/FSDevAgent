package com.ai.developer.tools.impl;

import com.ai.developer.tools.*;
import com.microsoft.playwright.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.nio.file.Paths;
import java.util.*;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@Slf4j
@Component
public class BrowserAutomationTool implements Tool {
    
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
        return "Automate browser interactions and capture screenshots";
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
            
        return params;
    }
    
    @Override
    public Flux<ToolOutput> execute(Map<String, Object> arguments) {
        String action = (String) arguments.get("action");
        
        return Mono.fromCallable(() -> {
            BrowserContext context = browser.newContext();
            Page page = context.newPage();
            
            try {
                switch (action.toLowerCase()) {
                    case "navigate":
                        return navigateTo(page, (String) arguments.get("url"));
                    case "screenshot":
                        return captureScreenshot(page);
                    case "click":
                        return clickElement(page, (String) arguments.get("selector"));
                    case "type":
                        return typeText(page, (String) arguments.get("selector"), 
                                        (String) arguments.get("text"));
                    case "wait":
                        return waitForElement(page, (String) arguments.get("selector"));
                    default:
                        throw new IllegalArgumentException("Unknown action: " + action);
                }
            } finally {
                context.close();
            }
        }).flux();
    }
    
    private ToolOutput navigateTo(Page page, String url) {
        page.navigate(url);
        page.waitForLoadState();  // Default is 'load' event
        
        // Capture screenshot for visualization
        byte[] screenshot = page.screenshot(new Page.ScreenshotOptions()
                .setFullPage(true));
        
        return ToolOutput.builder()
                .type("navigation")
                .content("Navigated to: " + url)
                .metadata(Map.of(
                    "url", url,
                    "title", page.title(),
                    "status", "success",
                    "screenshot", Base64.getEncoder().encodeToString(screenshot),
                    "html", page.content()
                ))
                .build();
    }
    
    private ToolOutput captureScreenshot(Page page) {
        String filename = "screenshot_" + System.currentTimeMillis() + ".png";
        byte[] screenshot = page.screenshot(new Page.ScreenshotOptions()
                .setFullPage(true));
        
        return ToolOutput.builder()
                .type("screenshot")
                .content(Base64.getEncoder().encodeToString(screenshot))
                .metadata(Map.of(
                    "filename", filename,
                    "format", "png",
                    "encoding", "base64",
                    "html", page.content()
                ))
                .build();
    }
    
    private ToolOutput clickElement(Page page, String selector) {
        // Capture before state
        byte[] beforeScreenshot = page.screenshot(new Page.ScreenshotOptions()
                .setFullPage(false));
        
        // Perform click
        page.click(selector);
        
        // Wait for any navigation or network activity to complete
        page.waitForLoadState();
        
        // Capture after state
        byte[] afterScreenshot = page.screenshot(new Page.ScreenshotOptions()
                .setFullPage(false));
        
        return ToolOutput.builder()
                .type("click")
                .content("Clicked element: " + selector)
                .metadata(Map.of(
                    "selector", selector,
                    "before_screenshot", Base64.getEncoder().encodeToString(beforeScreenshot),
                    "after_screenshot", Base64.getEncoder().encodeToString(afterScreenshot),
                    "html", page.content()
                ))
                .build();
    }
    
    private ToolOutput typeText(Page page, String selector, String text) {
        // Capture before state
        byte[] beforeScreenshot = page.screenshot(new Page.ScreenshotOptions()
                .setFullPage(false));
        
        // Perform type
        page.fill(selector, text);
        
        // Capture after state
        byte[] afterScreenshot = page.screenshot(new Page.ScreenshotOptions()
                .setFullPage(false));
        
        return ToolOutput.builder()
                .type("type")
                .content("Typed text into: " + selector)
                .metadata(Map.of(
                    "selector", selector,
                    "text", text,
                    "before_screenshot", Base64.getEncoder().encodeToString(beforeScreenshot),
                    "after_screenshot", Base64.getEncoder().encodeToString(afterScreenshot)
                ))
                .build();
    }
    
    private ToolOutput waitForElement(Page page, String selector) {
        page.waitForSelector(selector);
        
        // Capture screenshot showing the element
        byte[] screenshot = page.screenshot(new Page.ScreenshotOptions()
                .setFullPage(false));
        
        return ToolOutput.builder()
                .type("wait")
                .content("Element appeared: " + selector)
                .metadata(Map.of(
                    "selector", selector,
                    "screenshot", Base64.getEncoder().encodeToString(screenshot)
                ))
                .build();
    }
}
