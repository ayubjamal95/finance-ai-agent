package com.agent.financial_advisor.services;

import com.agent.financial_advisor.model.User;

import com.agent.financial_advisor.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.stream.StreamSupport;

@Service
public class HubspotService {

    private final OkHttpClient httpClient = new OkHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final UserRepository userRepository;

    @Value("${app.hubspot.client-id}")
    private String clientId;

    @Value("${app.hubspot.client-secret}")
    private String clientSecret;

    private static final String HUBSPOT_API_BASE = "https://api.hubapi.com";
    private static final String TOKEN_REFRESH_URL = "https://api.hubapi.com/oauth/v1/token";

    public HubspotService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }
    public List<JsonNode> getAllContacts(User user) throws IOException {
        List<JsonNode> allContacts = new ArrayList<>();
        String after = null;
        boolean tokenRefreshed = false;

        do {
            String url = HUBSPOT_API_BASE + "/crm/v3/objects/contacts?limit=100" +
                    (after != null ? "&after=" + after : "") +
                    "&properties=firstname,lastname,email,phone,company,notes,createdate,lastmodifieddate";

            Request request = new Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer " + user.getHubspotAccessToken())
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                // If token expired, refresh and retry once
                if (response.code() == 401 && !tokenRefreshed) {
                    System.out.println("Access token expired. Refreshing...");
                    user = refreshAccessToken(user);
                    tokenRefreshed = true;

                    // Retry the request with new token
                    request = new Request.Builder()
                            .url(url)
                            .header("Authorization", "Bearer " + user.getHubspotAccessToken())
                            .build();

                    try (Response retryResponse = httpClient.newCall(request).execute()) {
                        if (!retryResponse.isSuccessful()) {
                            String errorBody = retryResponse.body() != null ? retryResponse.body().string() : "No error body";
                            throw new IOException("Failed after token refresh. Code: " + retryResponse.code() +
                                    ", Body: " + errorBody);
                        }
                        after = processResponse(retryResponse, allContacts);
                    }
                } else if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "No error body";
                    throw new IOException("HubSpot API error. Code: " + response.code() + ", Body: " + errorBody);
                } else {
                    after = processResponse(response, allContacts);
                    tokenRefreshed = false; // Reset for next iteration
                }
            }
        } while (after != null);

        return allContacts;
    }

    public JsonNode getContact(User user, String contactId) throws IOException {
        String url = HUBSPOT_API_BASE + "/crm/v3/objects/contacts/" + contactId;
        boolean tokenRefreshed = false;

        Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + user.getHubspotAccessToken())
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            // Handle expired token (401) — refresh once and retry
            if (response.code() == 401 && !tokenRefreshed) {
                System.out.println("Access token expired. Refreshing...");
                user = refreshAccessToken(user);
                tokenRefreshed = true;

                // Retry the request with new token
                request = new Request.Builder()
                        .url(url)
                        .header("Authorization", "Bearer " + user.getHubspotAccessToken())
                        .build();

                try (Response retryResponse = httpClient.newCall(request).execute()) {
                    if (!retryResponse.isSuccessful()) {
                        String errorBody = retryResponse.body() != null ? retryResponse.body().string() : "No error body";
                        throw new IOException("Failed after token refresh. Code: " + retryResponse.code() +
                                ", Body: " + errorBody);
                    }
                    return objectMapper.readTree(retryResponse.body().string());
                }
            }

            // Handle general errors
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "No error body";
                throw new IOException("HubSpot API error. Code: " + response.code() + ", Body: " + errorBody);
            }

            // Parse successful response
            return objectMapper.readTree(response.body().string());
        }
    }


    public JsonNode searchContactByEmail(User user, String email) throws IOException {
        final String url = HUBSPOT_API_BASE + "/crm/v3/objects/contacts/search";
        boolean tokenRefreshed = false;

        // ✅ build searchRequest manually for guaranteed JSON structure
        ObjectNode searchRequest = objectMapper.createObjectNode();
        ArrayNode filterGroups = objectMapper.createArrayNode();
        ArrayNode filters = objectMapper.createArrayNode();

        ObjectNode filter = objectMapper.createObjectNode();
        filter.put("propertyName", "email");
        filter.put("operator", "EQ"); // try "EQUALS" if error persists
        filter.put("value", email);
        filters.add(filter);

        ObjectNode filterGroup = objectMapper.createObjectNode();
        filterGroup.set("filters", filters);
        filterGroups.add(filterGroup);

        searchRequest.set("filterGroups", filterGroups);

        // (optional but recommended) specify which properties to return
        ArrayNode properties = objectMapper.createArrayNode();
        properties.add("firstname");
        properties.add("lastname");
        properties.add("email");
        properties.add("phone");
        searchRequest.set("properties", properties);

        String jsonBody = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(searchRequest);

        // 🪵 --- LOGGING SECTION ---
        System.out.println("========== HubSpot Search Request ==========");
        System.out.println("➡️  URL: " + url);
        System.out.println("➡️  Method: POST");
        System.out.println("➡️  Headers:");
        System.out.println("   Authorization: Bearer [REDACTED]");
        System.out.println("   Content-Type: application/json");
        System.out.println("➡️  Request Body:");
        System.out.println(jsonBody);
        System.out.println("============================================");
        // ---------------------------------------------

        RequestBody body = RequestBody.create(
                jsonBody,
                MediaType.parse("application/json; charset=utf-8")
        );

        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .header("Authorization", "Bearer " + user.getHubspotAccessToken())
                .header("Accept", "application/json")
                .header("Content-Type", "application/json; charset=utf-8")
                .build();

        try (Response response = httpClient.newCall(request).execute()) {

            String responseBody = response.body() != null ? response.body().string() : "No response body";

            // 🪵 log response
            System.out.println("========== HubSpot Search Response ==========");
            System.out.println("⬅️  Status Code: " + response.code());
            System.out.println("⬅️  Body: " + responseBody);
            System.out.println("=============================================");

            // 🔁 Handle expired token once
            if (response.code() == 401 && !tokenRefreshed) {
                System.out.println("Access token expired. Refreshing...");
                user = refreshAccessToken(user);
                tokenRefreshed = true;

                request = new Request.Builder()
                        .url(url)
                        .post(body)
                        .header("Authorization", "Bearer " + user.getHubspotAccessToken())
                        .header("Accept", "application/json")
                        .header("Content-Type", "application/json; charset=utf-8")
                        .build();

                try (Response retryResponse = httpClient.newCall(request).execute()) {
                    String retryBody = retryResponse.body() != null ? retryResponse.body().string() : "No body";
                    System.out.println("🔁 Retry Response Code: " + retryResponse.code());
                    System.out.println("🔁 Retry Response Body: " + retryBody);

                    if (!retryResponse.isSuccessful()) {
                        throw new IOException("Failed after token refresh. Code: " + retryResponse.code() +
                                ", Body: " + retryBody);
                    }
                    return extractFirstResult(retryResponse);
                }
            }

            if (!response.isSuccessful()) {
                throw new IOException("HubSpot API error. Code: " + response.code() + ", Body: " + responseBody);
            }

            return objectMapper.readTree(responseBody);
        }
    }


    /**
     * Helper method to parse the first contact from HubSpot search response.
     */
    private JsonNode extractFirstResult(Response response) throws IOException {
        JsonNode root = objectMapper.readTree(response.body().string());
        JsonNode results = root.get("results");
        return (results != null && results.size() > 0) ? results.get(0) : null;
    }

    public JsonNode createContact(User user, Map<String, String> properties) throws IOException {
        String url = HUBSPOT_API_BASE + "/crm/v3/objects/contacts";
        boolean tokenRefreshed = false;

        Map<String, Object> contactData = Map.of("properties", properties);

        RequestBody body = RequestBody.create(
                objectMapper.writeValueAsString(contactData),
                MediaType.parse("application/json")
        );

        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .header("Authorization", "Bearer " + user.getHubspotAccessToken())
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            // Handle expired token case (401)
            if (response.code() == 401 && !tokenRefreshed) {
                System.out.println("Access token expired. Refreshing...");
                user = refreshAccessToken(user);
                tokenRefreshed = true;

                // Retry with new token
                request = new Request.Builder()
                        .url(url)
                        .post(body)
                        .header("Authorization", "Bearer " + user.getHubspotAccessToken())
                        .build();

                try (Response retryResponse = httpClient.newCall(request).execute()) {
                    if (!retryResponse.isSuccessful()) {
                        String errorBody = retryResponse.body() != null ? retryResponse.body().string() : "No error body";
                        throw new IOException("Failed after token refresh. Code: " + retryResponse.code() +
                                ", Body: " + errorBody);
                    }
                    return objectMapper.readTree(retryResponse.body().string());
                }
            }

            // Handle other errors
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "No error body";
                throw new IOException("HubSpot API error. Code: " + response.code() + ", Body: " + errorBody);
            }

            // Success case
            return objectMapper.readTree(response.body().string());
        }
    }

    public JsonNode updateContact(User user, String contactId, Map<String, String> properties) throws IOException {
        String url = HUBSPOT_API_BASE + "/crm/v3/objects/contacts/" + contactId;

        // HubSpot expects "properties" as the root key
        Map<String, Object> contactData = Map.of("properties", properties);

        RequestBody body = RequestBody.create(
                objectMapper.writeValueAsString(contactData),
                MediaType.parse("application/json")
        );

        Request request = new Request.Builder()
                .url(url)
                .patch(body)
                .header("Authorization", "Bearer " + user.getHubspotAccessToken())
                .header("Content-Type", "application/json")
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";

            if (!response.isSuccessful()) {
                throw new IOException("Failed to update contact. HTTP " + response.code() + " → " + responseBody);
            }

            return objectMapper.readTree(responseBody);
        }
    }

    public void addNoteToContact(User user, String contactId, String noteText) throws IOException {
        String url = HUBSPOT_API_BASE + "/crm/v3/objects/notes";
        boolean tokenRefreshed = false;

        Map<String, Object> noteData = Map.of(
                "properties", Map.of(
                        "hs_note_body", noteText,
                        "hs_timestamp", Instant.now().toString() // use ISO 8601 format
                ),
                "associations", List.of(
                        Map.of(
                                "to", Map.of("id", contactId),
                                "types", List.of(
                                        Map.of(
                                                "associationCategory", "HUBSPOT_DEFINED",
                                                "associationTypeId", 202 // 202 = note-to-contact
                                        )
                                )
                        )
                )
        );

        RequestBody body = RequestBody.create(
                objectMapper.writeValueAsString(noteData),
                MediaType.parse("application/json")
        );

        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .header("Authorization", "Bearer " + user.getHubspotAccessToken())
                .header("Content-Type", "application/json")
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            // If token expired, refresh once and retry
            if (response.code() == 401 && !tokenRefreshed) {
                System.out.println("Access token expired. Refreshing...");

                user = refreshAccessToken(user);
                tokenRefreshed = true;

                // Retry with new token
                request = new Request.Builder()
                        .url(url)
                        .post(body)
                        .header("Authorization", "Bearer " + user.getHubspotAccessToken())
                        .header("Content-Type", "application/json")
                        .build();

                try (Response retryResponse = httpClient.newCall(request).execute()) {
                    if (!retryResponse.isSuccessful()) {
                        String errorBody = retryResponse.body() != null ? retryResponse.body().string() : "No error body";
                        throw new IOException("Failed after token refresh. Code: " + retryResponse.code() +
                                ", Body: " + errorBody);
                    }
                    return;
                }
            }

            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "No error body";
                throw new IOException("HubSpot API error. Code: " + response.code() + ", Body: " + errorBody);
            }
        }
    }

    public List<JsonNode> getTopContacts(User user, int limit) {
        String accessToken = user.getHubspotAccessToken();

        HttpUrl url = HttpUrl.parse("https://api.hubapi.com/crm/v3/objects/contacts")
                .newBuilder()
                .addQueryParameter("limit", String.valueOf(limit))
                .build();

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + accessToken)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new RuntimeException("Failed to fetch contacts: " + response.code());
            }

            String body = response.body().string();
            JsonNode json = objectMapper.readTree(body);
            return StreamSupport.stream(json.get("results").spliterator(), false).toList();
        } catch (Exception e) {
            throw new RuntimeException("Error fetching contacts", e);
        }
    }
    private String processResponse(Response response, List<JsonNode> allContacts) throws IOException {
        String responseBody = response.body().string();
        JsonNode root = objectMapper.readTree(responseBody);

        JsonNode results = root.get("results");
        if (results != null && results.isArray()) {
            for (JsonNode contact : results) {
                allContacts.add(contact);
            }
        }

        // Extract pagination cursor
        JsonNode paging = root.get("paging");
        if (paging != null && paging.has("next") && paging.get("next").has("after")) {
            return paging.get("next").get("after").asText();
        }

        return null;
    }

    /**
     * Refreshes the HubSpot access token using the refresh token
     */
    private User refreshAccessToken(User user) throws IOException {
        if (user.getHubspotRefreshToken() == null || user.getHubspotRefreshToken().isEmpty()) {
            throw new IOException("No refresh token available. User needs to re-authenticate with HubSpot.");
        }

        RequestBody formBody = new FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("client_id", clientId)
                .add("client_secret", clientSecret)
                .add("refresh_token", user.getHubspotRefreshToken())
                .build();

        Request request = new Request.Builder()
                .url(TOKEN_REFRESH_URL)
                .post(formBody)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "No error body";
                throw new IOException("Token refresh failed: " + response.code() + " - " + errorBody);
            }

            String responseBody = response.body().string();
            JsonNode tokenResponse = objectMapper.readTree(responseBody);

            String newAccessToken = tokenResponse.get("access_token").asText();
            String newRefreshToken = tokenResponse.has("refresh_token") ?
                    tokenResponse.get("refresh_token").asText() : user.getHubspotRefreshToken();

            // Update user with new tokens
            user.setHubspotAccessToken(newAccessToken);
            user.setHubspotRefreshToken(newRefreshToken);
            userRepository.save(user);

            System.out.println("Access token refreshed successfully");
            return user;
        }
    }
}
