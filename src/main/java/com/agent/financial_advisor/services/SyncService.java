package com.agent.financial_advisor.services;

import com.google.api.services.gmail.model.Message;
import com.agent.financial_advisor.model.User;
import com.agent.financial_advisor.repository.UserRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

@Service
public class SyncService {
    private final UserRepository userRepository;
    private final GmailService gmailService;
    private final HubspotService hubspotService;
    private final RAGService ragService;

    public SyncService(UserRepository userRepository, GmailService gmailService, HubspotService hubspotService, RAGService ragService) {
        this.userRepository = userRepository;
        this.gmailService = gmailService;
        this.hubspotService = hubspotService;
        this.ragService = ragService;
    }

    @Scheduled(fixedDelay = 60000)
    public void syncAllUsers() {
        List<User> users = userRepository.findAll();
        for (User user : users) {
            if (user.getGoogleAccessToken() != null) {
                syncGmail(user);
            }
            if (user.getHubspotAccessToken() != null) {
                syncHubspot(user);
            }
        }
    }

    public void syncGmail(User user) {
        try {
            List<Message> messages = gmailService.listMessages(user, 50);
            for (Message message : messages) {
                ragService.indexEmail(user, message);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void syncHubspot(User user) {
        try {
            List<JsonNode> contacts = hubspotService.getAllContacts(user);
            for (JsonNode contact : contacts) {
                ragService.indexHubspotContact(user, contact);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
