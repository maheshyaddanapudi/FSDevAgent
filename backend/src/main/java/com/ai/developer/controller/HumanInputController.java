// Backend API endpoint for handling human input responses
package com.ai.developer.controller;

import com.ai.developer.model.HumanInputRequest;
import com.ai.developer.model.HumanInputResponse;
import com.ai.developer.service.ChatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/chat")
public class HumanInputController {

    private static final Logger log = LoggerFactory.getLogger(HumanInputController.class);
    
    @Autowired
    private ChatService chatService;
    
    @GetMapping(value = "/stream/human-input", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamHumanInputResponse(
            @RequestParam String aiDeveloperAgentSessionId,
            @RequestParam String response) {
        
        log.info("Received human input response for session: {}", aiDeveloperAgentSessionId);
        
        SseEmitter emitter = new SseEmitter(300000L); // 5 minute timeout
        
        HumanInputResponse humanInputResponse = new HumanInputResponse();
        humanInputResponse.setAiDeveloperAgentSessionId(aiDeveloperAgentSessionId);
        humanInputResponse.setResponse(response);
        
        chatService.processHumanInputResponse(humanInputResponse, emitter);
        
        return emitter;
    }
}
