package com.agent.financial_advisor.controller;

import com.agent.financial_advisor.model.User;
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

    public ApiController(UserService userService, SyncService syncService) {
        this.userService = userService;
        this.syncService = syncService;
    }

    @PostMapping("/sync")
    public ResponseEntity<?> syncData(@AuthenticationPrincipal OAuth2User oauth2User) {
        String email = oauth2User.getAttribute("email");
        User user = userService.findByEmail(email);

        if (user == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "User not found"));
        }

        // Trigger immediate sync
        new Thread(() -> {
            syncService.syncAllUsers();
        }).start();

        return ResponseEntity.ok(Map.of("message", "Sync started"));
    }

    @GetMapping("/user/status")
    public ResponseEntity<?> getUserStatus(@AuthenticationPrincipal OAuth2User oauth2User) {
        String email = oauth2User.getAttribute("email");
        User user = userService.findByEmail(email);

        if (user == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "User not found"));
        }

        return ResponseEntity.ok(Map.of(
                "googleConnected", user.getGoogleAccessToken() != null,
                "hubspotConnected", user.getHubspotAccessToken() != null,
                "email", user.getEmail(),
                "name", user.getName()
        ));
    }
}
