package com.agent.financial_advisor.services;

import com.agent.financial_advisor.model.User;
import com.agent.financial_advisor.repository.UserRepository;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePartHeader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@Slf4j
public class ProactiveMonitoringService {

    private final UserRepository userRepository;
    private final GmailService gmailService;
    private final CalendarService calendarService;
    private final AIAgentService aiAgentService;

    public ProactiveMonitoringService(UserRepository userRepository, GmailService gmailService, CalendarService calendarService, AIAgentService aiAgentService) {
        this.userRepository = userRepository;
        this.gmailService = gmailService;
        this.calendarService = calendarService;
        this.aiAgentService = aiAgentService;
    }

    private final Map<Long, Set<String>> processedEmails = new HashMap<>();
    private final Map<Long, Set<String>> processedEvents = new HashMap<>();

    @Scheduled(fixedDelay = 60000) // 1 minute
    public void monitorNewEmails() {
        List<User> users = userRepository.findAll();

        for (User user : users) {
            if (user.getGoogleAccessToken() == null) continue;

            try {
                List<Message> messages = gmailService.listMessages(user, 5);
                Set<String> userProcessed = processedEmails.computeIfAbsent(user.getId(), k -> new HashSet<>());

                for (Message message : messages) {
                    if (!userProcessed.contains(message.getId())) {
                        // ✅ FIX: Only process RECEIVED emails, not SENT emails
                        if (isReceivedEmail(message, user.getEmail())) {
                            log.info("📧 New email detected for user: {}", user.getEmail());
                            aiAgentService.processIncomingEmail(user, message);
                        }
                        userProcessed.add(message.getId());
                    }
                }

                if (userProcessed.size() > 100) {
                    List<String> sorted = new ArrayList<>(userProcessed);
                    userProcessed.clear();
                    userProcessed.addAll(sorted.subList(sorted.size() - 100, sorted.size()));
                }

            } catch (Exception e) {
                log.error("❌ Error monitoring emails for user {}: ", user.getEmail(), e);
            }
        }
    }

    /**
     * Check if the email was received (not sent by the user)
     */
    private boolean isReceivedEmail(Message message, String userEmail) {
        for (MessagePartHeader header : message.getPayload().getHeaders()) {
            if (header.getName().equalsIgnoreCase("From")) {
                String fromEmail = extractEmail(header.getValue());
                // If the "From" email is the user's email, it's a SENT email
                return !fromEmail.equalsIgnoreCase(userEmail);
            }
        }
        return true; // Default to processing if we can't determine
    }

    /**
     * Extract email address from "From" header
     */
    private String extractEmail(String from) {
        if (from.contains("<")) {
            return from.substring(from.indexOf("<") + 1, from.indexOf(">"));
        }
        return from.trim();
    }

    @Scheduled(fixedDelay = 120000) // 2 minutes
    public void monitorNewCalendarEvents() {
        List<User> users = userRepository.findAll();

        for (User user : users) {
            if (user.getGoogleAccessToken() == null) continue;

            try {
                List<Event> events = calendarService.getUpcomingEvents(user, 10);
                Set<String> userProcessed = processedEvents.computeIfAbsent(user.getId(), k -> new HashSet<>());

                for (Event event : events) {
                    if (!userProcessed.contains(event.getId())) {
                        log.info("📅 New calendar event detected for user: {}", user.getEmail());
                        aiAgentService.processNewCalendarEvent(user, event);
                        userProcessed.add(event.getId());
                    }
                }

                if (userProcessed.size() > 100) {
                    List<String> sorted = new ArrayList<>(userProcessed);
                    userProcessed.clear();
                    userProcessed.addAll(sorted.subList(sorted.size() - 100, sorted.size()));
                }

            } catch (Exception e) {
                log.error("❌ Error monitoring calendar for user {}: ", user.getEmail(), e);
            }
        }
    }
}