package com.agent.financial_advisor.controller;

import com.agent.financial_advisor.model.User;
import com.agent.financial_advisor.services.AIAgentService;
import com.agent.financial_advisor.services.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
public class ChatController {
    @Autowired
    AIAgentService aiAgentService;
    @Autowired
    UserService userService;


    @MessageMapping("/chat")
    @SendTo("/topic/messages")
    public ChatResponse handleChatMessage(
            ChatRequest request, Principal principal) {
        String email ="anonymous";



        if (principal instanceof Authentication auth) {
            Object userPrincipal = auth.getPrincipal();

            if (userPrincipal instanceof DefaultOAuth2User oauth2User) {
                email = oauth2User.getAttribute("email");
            }
        }

        User user = userService.findByEmail(email);

        if (user == null) {
            // It's cleaner to return an error response, though in a production app,
            // you might throw an exception or handle authentication failure upstream.
            return new ChatResponse("Error: User not found!!!");
        }

        String response = aiAgentService.processMessage(user, request.getMessage());
        return new ChatResponse(response);
    }

    // --- Inner Classes (DTOs) ---


    public static class ChatRequest {
        private String message;

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }


     // <-- This generates the public ChatResponse(String message) constructor
    public static class ChatResponse {
        public ChatResponse(final String message) {
            this.message = message;
        }

        // We can make this field final for immutability in the response DTO
        private String message;

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }
}