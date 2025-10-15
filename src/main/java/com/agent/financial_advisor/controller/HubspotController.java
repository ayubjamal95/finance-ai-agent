package com.agent.financial_advisor.controller;

import com.agent.financial_advisor.model.User;
import com.agent.financial_advisor.services.UserService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.view.RedirectView;

import java.io.IOException;

@Controller
public class HubspotController {

    private static final Logger log = LoggerFactory.getLogger(HubspotController.class);

    private final UserService userService;
    private final OkHttpClient httpClient = new OkHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public HubspotController(UserService userService) {
        this.userService = userService;
    }

    @Value("${app.hubspot.client-id}")
    private String clientId;

    @Value("${app.hubspot.client-secret}")
    private String clientSecret;

    @Value("${app.hubspot.redirect-uri}")
    private String redirectUri;

    // Connect endpoint — redirects user to HubSpot OAuth
    @GetMapping("/api/hubspot/connect")
    public RedirectView connectHubspot() {
        String[] scopes = {
                "oauth",
                "crm.objects.owners.read",
                "crm.objects.contacts.write",
                "crm.objects.appointments.read",
                "crm.objects.appointments.write",
                "crm.objects.companies.write",
                "crm.objects.companies.read",
                "crm.objects.deals.read",
                "crm.schemas.contacts.read",
                "crm.objects.deals.write",
                "crm.objects.contacts.read"
        };

        String scopeString = String.join(" ", scopes);

        String hubspotAuthUrl = String.format(
                "https://app-na2.hubspot.com/oauth/authorize?client_id=%s&redirect_uri=%s&scope=%s",
                clientId,
                redirectUri,  // This should be http://localhost:8080/api/hubspot/callback
                scopeString
        );

        log.info("Redirecting user to HubSpot OAuth URL: {}", hubspotAuthUrl);

        return new RedirectView(hubspotAuthUrl);
    }

    @GetMapping("/hubspot/callback")
    public RedirectView hubspotCallback(
            @RequestParam("code") String code,
            @AuthenticationPrincipal OAuth2User oauth2User
    ) throws IOException {

        log.info("=== HubSpot Callback Started ===");
        log.info("Received code: {}", code);
        log.info("Client ID: {}", clientId);
        log.info("Redirect URI: {}", redirectUri);

        if (oauth2User == null) {
            log.warn("OAuth2User is null in callback");
            return new RedirectView("/chat-page?error=oauth2user_null");
        }

        String email = oauth2User.getAttribute("email");
        log.info("Authenticated user email: {}", email);

        // Prepare request to exchange code for access token
        RequestBody formBody = new FormBody.Builder()
                .add("grant_type", "authorization_code")
                .add("client_id", clientId)
                .add("client_secret", clientSecret)
                .add("redirect_uri", redirectUri)
                .add("code", code)
                .build();

        Request request = new Request.Builder()
                .url("https://api.hubapi.com/oauth/v1/token")
                .post(formBody)
                .addHeader("Content-Type", "application/x-www-form-urlencoded")  // ✅ Add header
                .build();

        log.info("Exchanging code for tokens at {}", request.url());

        try (Response response = httpClient.newCall(request).execute()) {

            log.info("HubSpot token response code: {}", response.code());

            // ✅ FIX: Read response body ONCE and store it
            String responseBody = response.body() != null ? response.body().string() : "empty body";
            log.info("HubSpot response body: {}", responseBody);

            if (!response.isSuccessful()) {
                log.error("Failed to get tokens from HubSpot: {}", responseBody);

                // Try to parse error details
                try {
                    JsonNode errorJson = objectMapper.readTree(responseBody);
                    log.error("Error details: {}", errorJson.toPrettyString());
                } catch (Exception e) {
                    log.error("Could not parse error response");
                }

                return new RedirectView("/chat-page?error=hubspot_auth_failed");
            }

            // ✅ FIX: Use the already-read responseBody variable
            JsonNode tokenResponse = objectMapper.readTree(responseBody);
            log.info("HubSpot token response: {}", tokenResponse.toPrettyString());

            // ✅ Add null checks
            if (!tokenResponse.has("access_token")) {
                log.error("No access_token in response!");
                return new RedirectView("/chat-page?error=no_access_token");
            }

            String accessToken = tokenResponse.get("access_token").asText();
            String refreshToken = tokenResponse.has("refresh_token") ?
                    tokenResponse.get("refresh_token").asText() : null;

            log.info("Access token received: {}...", accessToken.substring(0, Math.min(20, accessToken.length())));
            log.info("Refresh token: {}", refreshToken != null ? "Present" : "Not present");

            User user = userService.findByEmail(email);
            if (user != null) {
                log.info("Updating HubSpot tokens for user: {}", email);
                userService.updateHubspotTokens(user, accessToken, refreshToken);
                log.info("✅ HubSpot tokens saved successfully!");
            } else {
                log.warn("User not found with email: {}", email);
                return new RedirectView("/chat-page?error=user_not_found");
            }

        } catch (Exception e) {
            log.error("Exception during HubSpot OAuth: ", e);
            return new RedirectView("/chat-page?error=exception");
        }

        log.info("Redirecting user to /chat after successful HubSpot OAuth");
        return new RedirectView("/chat-page?hubspot=connected");
    }
}
