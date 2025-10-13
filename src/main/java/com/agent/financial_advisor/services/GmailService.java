package com.agent.financial_advisor.services;
import com.agent.financial_advisor.model.User;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.*;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.*;
@Service
@RequiredArgsConstructor
public class GmailService {

    private Gmail getGmailService(User user) throws GeneralSecurityException, IOException {
        NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        GsonFactory jsonFactory = GsonFactory.getDefaultInstance();

        GoogleCredentials credentials = GoogleCredentials.create(
                new AccessToken(user.getGoogleAccessToken(), null)
        );

        return new Gmail.Builder(httpTransport, jsonFactory, new HttpCredentialsAdapter(credentials))
                .setApplicationName("Financial Advisor AI Agent")
                .build();
    }

    public List<Message> listMessages(User user, int maxResults) throws Exception {
        Gmail service = getGmailService(user);
        ListMessagesResponse response = service.users().messages()
                .list("me")
                .setMaxResults((long) maxResults)
                .setQ("in:inbox OR in:sent")
                .execute();

        List<Message> messages = new ArrayList<>();
        if (response.getMessages() != null) {
            for (Message message : response.getMessages()) {
                Message fullMessage = service.users().messages()
                        .get("me", message.getId())
                        .setFormat("full")
                        .execute();
                messages.add(fullMessage);
            }
        }
        return messages;
    }

    public Message getMessage(User user, String messageId) throws Exception {
        Gmail service = getGmailService(user);
        return service.users().messages()
                .get("me", messageId)
                .setFormat("full")
                .execute();
    }

    public Message sendEmail(User user, String to, String subject, String body) throws Exception {
        Gmail service = getGmailService(user);

        String raw = createEmail(to, user.getEmail(), subject, body);
        Message message = new Message();
        message.setRaw(raw);

        return service.users().messages().send("me", message).execute();
    }

    public Message replyToEmail(User user, String messageId, String threadId, String body) throws Exception {
        Gmail service = getGmailService(user);

        Message originalMessage = service.users().messages().get("me", messageId).execute();
        String to = extractEmail(originalMessage, "From");
        String subject = extractSubject(originalMessage);

        if (!subject.toLowerCase().startsWith("re:")) {
            subject = "Re: " + subject;
        }

        String raw = createEmail(to, user.getEmail(), subject, body);
        Message message = new Message();
        message.setRaw(raw);
        message.setThreadId(threadId);

        return service.users().messages().send("me", message).execute();
    }

    private String createEmail(String to, String from, String subject, String body) {
        String email = "From: " + from + "\r\n" +
                "To: " + to + "\r\n" +
                "Subject: " + subject + "\r\n\r\n" +
                body;
        return Base64.getUrlEncoder().encodeToString(email.getBytes());
    }

    private String extractEmail(Message message, String headerName) {
        for (MessagePartHeader header : message.getPayload().getHeaders()) {
            if (header.getName().equalsIgnoreCase(headerName)) {
                String value = header.getValue();
                if (value.contains("<")) {
                    return value.substring(value.indexOf("<") + 1, value.indexOf(">"));
                }
                return value;
            }
        }
        return "";
    }

    private String extractSubject(Message message) {
        for (MessagePartHeader header : message.getPayload().getHeaders()) {
            if (header.getName().equalsIgnoreCase("Subject")) {
                return header.getValue();
            }
        }
        return "";
    }

    public String extractBody(Message message) {
        if (message.getPayload().getBody().getData() != null) {
            return new String(Base64.getUrlDecoder().decode(message.getPayload().getBody().getData()));
        }

        if (message.getPayload().getParts() != null) {
            for (MessagePart part : message.getPayload().getParts()) {
                if (part.getMimeType().equals("text/plain") && part.getBody().getData() != null) {
                    return new String(Base64.getUrlDecoder().decode(part.getBody().getData()));
                }
            }
            for (MessagePart part : message.getPayload().getParts()) {
                if (part.getMimeType().equals("text/html") && part.getBody().getData() != null) {
                    return new String(Base64.getUrlDecoder().decode(part.getBody().getData()));
                }
            }
        }
        return "";
    }
}
