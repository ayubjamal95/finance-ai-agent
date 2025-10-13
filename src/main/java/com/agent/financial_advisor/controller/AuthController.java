package com.agent.financial_advisor.controller;

import com.agent.financial_advisor.model.User;
import com.agent.financial_advisor.services.SyncService;
import com.agent.financial_advisor.services.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.concurrent.CompletableFuture;

@Controller
@RequiredArgsConstructor
public class AuthController {

    @Autowired
    UserService userService;
    @Autowired
    SyncService syncService;

    @GetMapping("/")
    public String index() {
        return "redirect:/chat-page";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/chat-page")
    public String chat(
            @AuthenticationPrincipal OAuth2User oauth2User,
            @RegisteredOAuth2AuthorizedClient("google") OAuth2AuthorizedClient googleClient
    ) {

        String googleAccessToken = googleClient != null ? googleClient.getAccessToken().getTokenValue() : null;
        String googleRefreshToken = (googleClient != null && googleClient.getRefreshToken() != null)
                ? googleClient.getRefreshToken().getTokenValue()
                : null;

        // Save or update user info with both tokens
        User user = userService.getOrCreateUser(
                oauth2User,
                googleAccessToken,
                googleRefreshToken
        );

        // ✅ Auto-sync Gmail in background when user logs in
        CompletableFuture.runAsync(() -> {
            syncService.syncGmail(user);

        });
        // ✅ Auto-sync Gmail in background when user logs in
        CompletableFuture.runAsync(() -> {
            syncService.syncHubspot(user);

        });
        return "chat";
    }
}
