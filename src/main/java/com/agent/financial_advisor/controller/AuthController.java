package com.agent.financial_advisor.controller;

import com.agent.financial_advisor.model.User;
import com.agent.financial_advisor.services.SyncService;
import com.agent.financial_advisor.services.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.view.RedirectView;

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
        // Check if user is authenticated
        if (oauth2User == null) {
            return "redirect:/login?error=not_authenticated";
        }

        try {
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

            // Auto-sync Gmail in background when user logs in
            CompletableFuture.runAsync(() -> {
                try {
                    syncService.syncGmail(user);
                } catch (Exception e) {
                    System.err.println("Error syncing Gmail: " + e.getMessage());
                }
            });

            // Auto-sync HubSpot in background when user logs in
            CompletableFuture.runAsync(() -> {
                try {
                    syncService.syncHubspot(user);
                } catch (Exception e) {
                    System.err.println("Error syncing HubSpot: " + e.getMessage());
                }
            });

            return "chat";
        } catch (Exception e) {
            System.err.println("Error in chat-page: " + e.getMessage());
            e.printStackTrace();
            return "redirect:/login?error=initialization_failed";
        }
    }

    @GetMapping("/logout")
    public RedirectView logout(HttpServletRequest request, HttpServletResponse response) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth != null) {
            new SecurityContextLogoutHandler().logout(request, response, auth);
        }

        return new RedirectView("/login?logout=true");
    }
}