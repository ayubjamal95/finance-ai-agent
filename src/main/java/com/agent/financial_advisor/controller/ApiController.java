package com.agent.financial_advisor.controller;

import com.agent.financial_advisor.model.User;
import com.agent.financial_advisor.services.AIAgentService;
import com.agent.financial_advisor.services.SyncService;
import com.agent.financial_advisor.services.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final UserService userService;
    private final SyncService syncService;
    private final AIAgentService aiAgentService;

    public ApiController(UserService userService, SyncService syncService, AIAgentService aiAgentService) {
        this.userService = userService;
        this.syncService = syncService;
        this.aiAgentService = aiAgentService;
    }

    @PostMapping("/sync")
    public ResponseEntity<?> syncData(@AuthenticationPrincipal OAuth2User oauth2User) {
        // Check if user is authenticated
        if (oauth2User == null) {
            return ResponseEntity.status(401)
                    .body(Map.of("error", "Not authenticated. Please log in."));
        }

        String email = oauth2User.getAttribute("email");

        // Additional null check for email
        if (email == null || email.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Email not found in OAuth2 profile"));
        }

        User user = userService.findByEmail(email);

        if (user == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "User not found"));
        }

        // Trigger immediate sync
        new Thread(() -> {
            syncService.syncAllUsers();
        }).start();

        return ResponseEntity.ok(Map.of("message", "Sync started"));
    }

    @GetMapping("/user/status")
    public ResponseEntity<?> getUserStatus(@AuthenticationPrincipal OAuth2User oauth2User) {
        // Check if user is authenticated
        if (oauth2User == null) {
            return ResponseEntity.status(401)
                    .body(Map.of(
                            "error", "Not authenticated",
                            "googleConnected", false,
                            "hubspotConnected", false
                    ));
        }

        String email = oauth2User.getAttribute("email");

        if (email == null || email.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Email not found in OAuth2 profile"));
        }

        User user = userService.findByEmail(email);

        if (user == null) {
            return ResponseEntity.status(404)
                    .body(Map.of(
                            "error", "User not found",
                            "googleConnected", false,
                            "hubspotConnected", false
                    ));
        }

        return ResponseEntity.ok(Map.of(
                "googleConnected", user.getGoogleAccessToken() != null,
                "hubspotConnected", user.getHubspotAccessToken() != null,
                "email", user.getEmail(),
                "name", user.getName() != null ? user.getName() : ""
        ));
    }

    @PostMapping("/chat")
    public ResponseEntity<?> chat(
            @AuthenticationPrincipal OAuth2User oauth2User,
            @RequestBody Map<String, String> payload
    ) {
        // Check if user is authenticated
        if (oauth2User == null) {
            return ResponseEntity.status(401)
                    .body(Map.of("error", "Not authenticated"));
        }

        String email = oauth2User.getAttribute("email");

        if (email == null || email.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Email not found in OAuth2 profile"));
        }

        User user = userService.findByEmail(email);

        if (user == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "User not found"));
        }

        String message = payload.get("message");
        if (message == null || message.trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Message cannot be empty"));
        }

        try {
            // Process message through AI agent (you'll need to inject AIAgentService)
            String response = "This is a fallback response. Please use WebSocket for better experience.";

            return ResponseEntity.ok(Map.of(
                    "message", response,
                    "timestamp", System.currentTimeMillis()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(Map.of("error", "Error processing message: " + e.getMessage()));
        }
    }
}