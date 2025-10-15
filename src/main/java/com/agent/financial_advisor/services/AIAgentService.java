package com.agent.financial_advisor.services;

import com.agent.financial_advisor.model.*;
import com.agent.financial_advisor.repository.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventAttendee;
import com.theokanning.openai.completion.chat.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@Slf4j
public class AIAgentService {
    private final OpenAIService openAIService;
    private final RAGService ragService;
    private final GmailService gmailService;
    private final CalendarService calendarService;
    private final HubspotService hubspotService;
    private final MessageRepository messageRepository;
    private final TaskRepository taskRepository;
    private final OngoingInstructionRepository instructionRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AIAgentService(OpenAIService openAIService, RAGService ragService, GmailService gmailService,
                          CalendarService calendarService, HubspotService hubspotService,
                          MessageRepository messageRepository, TaskRepository taskRepository,
                          OngoingInstructionRepository instructionRepository) {
        this.openAIService = openAIService;
        this.ragService = ragService;
        this.gmailService = gmailService;
        this.calendarService = calendarService;
        this.hubspotService = hubspotService;
        this.messageRepository = messageRepository;
        this.taskRepository = taskRepository;
        this.instructionRepository = instructionRepository;
    }
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AIAgentService.class);


    // User context for function execution
    private User currentUser;

    // ========== Parameter Classes ==========

    public static class SearchParams {
        private String query;
        public String getQuery() { return query; }
        public void setQuery(String query) { this.query = query; }
    }

    public static class EmailParams {
        private String to;
        private String subject;
        private String body;
        public String getTo() { return to; }
        public void setTo(String to) { this.to = to; }
        public String getSubject() { return subject; }
        public void setSubject(String subject) { this.subject = subject; }
        public String getBody() { return body; }
        public void setBody(String body) { this.body = body; }
    }

    public static class ReplyParams {
        private String message_id;
        private String thread_id;
        private String body;
        public String getMessage_id() { return message_id; }
        public void setMessage_id(String message_id) { this.message_id = message_id; }
        public String getThread_id() { return thread_id; }
        public void setThread_id(String thread_id) { this.thread_id = thread_id; }
        public String getBody() { return body; }
        public void setBody(String body) { this.body = body; }
    }

    public static class FetchEmailsParams {
        private Integer max_results;
        public Integer getMax_results() { return max_results; }
        public void setMax_results(Integer max_results) { this.max_results = max_results; }
    }

    public static class GetEventsParams {
        private Integer max_results;
        public Integer getMax_results() { return max_results; }
        public void setMax_results(Integer max_results) { this.max_results = max_results; }
    }

    public static class AvailabilityParams {
        private String start_date;
        private String end_date;
        public String getStart_date() { return start_date; }
        public void setStart_date(String start_date) { this.start_date = start_date; }
        public String getEnd_date() { return end_date; }
        public void setEnd_date(String end_date) { this.end_date = end_date; }
    }

    public static class CalendarEventParams {
        private String title;
        private String description;
        private String start_time;
        private String end_time;
        private List<String> attendees;
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getStart_time() { return start_time; }
        public void setStart_time(String start_time) { this.start_time = start_time; }
        public String getEnd_time() { return end_time; }
        public void setEnd_time(String end_time) { this.end_time = end_time; }
        public List<String> getAttendees() { return attendees; }
        public void setAttendees(List<String> attendees) { this.attendees = attendees; }
    }

    public static class SearchContactParams {
        private String email;
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
    }

    public static class CreateContactParams {
        private String email;
        private String firstname;
        private String lastname;
        private String phone;
        private String company;
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getFirstname() { return firstname; }
        public void setFirstname(String firstname) { this.firstname = firstname; }
        public String getLastname() { return lastname; }
        public void setLastname(String lastname) { this.lastname = lastname; }
        public String getPhone() { return phone; }
        public void setPhone(String phone) { this.phone = phone; }
        public String getCompany() { return company; }
        public void setCompany(String company) { this.company = company; }
    }

    public static class AddNoteParams {
        private String contact_id;
        private String note;
        public String getContact_id() { return contact_id; }
        public void setContact_id(String contact_id) { this.contact_id = contact_id; }
        public String getNote() { return note; }
        public void setNote(String note) { this.note = note; }
    }

    public static class CreateTaskParams {
        private String type;
        private String description;
        private String context;
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getContext() { return context; }
        public void setContext(String context) { this.context = context; }
    }

    // ========== Available Functions ==========

    private List<ChatFunction> getAvailableFunctions() {
        List<ChatFunction> functions = new ArrayList<>();

        // Search indexed knowledge base
        functions.add(ChatFunction.builder()
                .name("search_knowledge_base")
                .description("Search through indexed emails and HubSpot contacts for historical information")
                .executor(SearchParams.class, params -> {
                    SearchParams p = (SearchParams) params;
                    return executeSearchKnowledgeBase(p.getQuery());
                })
                .build());

        // Fetch recent emails from Gmail API
        functions.add(ChatFunction.builder()
                .name("fetch_recent_emails")
                .description("Fetch recent emails directly from Gmail. Use when user asks about recent/latest emails.")
                .executor(FetchEmailsParams.class, params -> {
                    FetchEmailsParams p = (FetchEmailsParams) params;
                    int maxResults = p.getMax_results() != null ? p.getMax_results() : 10;
                    return executeFetchRecentEmails(maxResults);
                })
                .build());

        // Get upcoming calendar events
        functions.add(ChatFunction.builder()
                .name("get_upcoming_events")
                .description("Get upcoming calendar events. Use when user asks about meetings or schedule.")
                .executor(GetEventsParams.class, params -> {
                    GetEventsParams p = (GetEventsParams) params;
                    int maxResults = p.getMax_results() != null ? p.getMax_results() : 10;
                    return executeGetUpcomingEvents(maxResults);
                })
                .build());

        // Send email
        functions.add(ChatFunction.builder()
                .name("send_email")
                .description("Send an email to a recipient")
                .executor(EmailParams.class, params -> {
                    EmailParams p = (EmailParams) params;
                    return executeSendEmail(p.getTo(), p.getSubject(), p.getBody());
                })
                .build());

        // Reply to email
        functions.add(ChatFunction.builder()
                .name("reply_to_email")
                .description("Reply to an existing email thread")
                .executor(ReplyParams.class, params -> {
                    ReplyParams p = (ReplyParams) params;
                    return executeReplyToEmail(p.getMessage_id(), p.getThread_id(), p.getBody());
                })
                .build());

        // Get calendar availability
        functions.add(ChatFunction.builder()
                .name("get_available_times")
                .description("Get available time slots from the calendar")
                .executor(AvailabilityParams.class, params -> {
                    AvailabilityParams p = (AvailabilityParams) params;
                    return executeGetAvailableTimes(p.getStart_date(), p.getEnd_date());
                })
                .build());

        // Create calendar event
        functions.add(ChatFunction.builder()
                .name("create_calendar_event")
                .description("Create a new calendar event")
                .executor(CalendarEventParams.class, params -> {
                    CalendarEventParams p = (CalendarEventParams) params;
                    return executeCreateCalendarEvent(p.getTitle(), p.getDescription(),
                            p.getStart_time(), p.getEnd_time(), p.getAttendees());
                })
                .build());

        // Search HubSpot contact
        functions.add(ChatFunction.builder()
                .name("search_hubspot_contact")
                .description("Search for a contact in HubSpot by email")
                .executor(SearchContactParams.class, params -> {
                    SearchContactParams p = (SearchContactParams) params;
                    return executeSearchHubspotContact(p.getEmail());
                })
                .build());

        // Create HubSpot contact
        functions.add(ChatFunction.builder()
                .name("create_hubspot_contact")
                .description("Create a new contact in HubSpot")
                .executor(CreateContactParams.class, params -> {
                    CreateContactParams p = (CreateContactParams) params;
                    return executeCreateHubspotContact(p);
                })
                .build());

        // Add note to HubSpot contact
        functions.add(ChatFunction.builder()
                .name("add_hubspot_note")
                .description("Add a note to a HubSpot contact")
                .executor(AddNoteParams.class, params -> {
                    AddNoteParams p = (AddNoteParams) params;
                    return executeAddHubspotNote(p.getContact_id(), p.getNote());
                })
                .build());

        // Create task
        functions.add(ChatFunction.builder()
                .name("create_task")
                .description("Create a task that requires waiting for external input")
                .executor(CreateTaskParams.class, params -> {
                    CreateTaskParams p = (CreateTaskParams) params;
                    return executeCreateTask(p.getType(), p.getDescription(), p.getContext());
                })
                .build());

        return functions;
    }

    // ========== Execution Methods ==========

    private String executeSearchKnowledgeBase(String query) {
        try {
            String context = ragService.search(currentUser, query, 5);
            return context.isEmpty() ? "No relevant information found in indexed data." : context;
        } catch (Exception e) {
            return "Error searching: " + e.getMessage();
        }
    }

    private String executeFetchRecentEmails(int maxResults) {
        try {
            List<com.google.api.services.gmail.model.Message> messages =
                    gmailService.listMessages(currentUser, maxResults);

            if (messages.isEmpty()) {
                return "No emails found.";
            }

            StringBuilder result = new StringBuilder();
            result.append(String.format("Found %d recent emails:\n\n", messages.size()));

            for (int i = 0; i < messages.size(); i++) {
                com.google.api.services.gmail.model.Message msg = messages.get(i);

                String from = "", subject = "", date = "", messageId = "", threadId = "";

                for (com.google.api.services.gmail.model.MessagePartHeader header :
                        msg.getPayload().getHeaders()) {
                    if (header.getName().equalsIgnoreCase("From")) from = header.getValue();
                    if (header.getName().equalsIgnoreCase("Subject")) subject = header.getValue();
                    if (header.getName().equalsIgnoreCase("Date")) date = header.getValue();
                }

                messageId = msg.getId();
                threadId = msg.getThreadId();

                String body = gmailService.extractBody(msg);
                String snippet = body.length() > 200 ? body.substring(0, 200) + "..." : body;

                result.append(String.format("%d. From: %s\n", i + 1, from));
                result.append(String.format("   Subject: %s\n", subject));
                result.append(String.format("   Date: %s\n", date));
                result.append(String.format("   Message ID: %s\n", messageId));
                result.append(String.format("   Thread ID: %s\n", threadId));
                result.append(String.format("   Content: %s\n\n", snippet));
            }

            return result.toString();

        } catch (Exception e) {
            return "Error fetching emails: " + e.getMessage();
        }
    }

    private String executeGetUpcomingEvents(int maxResults) {
        try {
            List<Event> events = calendarService.getUpcomingEvents(currentUser, maxResults);

            if (events.isEmpty()) {
                return "No upcoming events found.";
            }

            StringBuilder result = new StringBuilder();
            result.append(String.format("Found %d upcoming events:\n\n", events.size()));

            for (int i = 0; i < events.size(); i++) {
                Event event = events.get(i);
                String start = event.getStart().getDateTime() != null ?
                        event.getStart().getDateTime().toString() :
                        event.getStart().getDate().toString();
                String end = event.getEnd().getDateTime() != null ?
                        event.getEnd().getDateTime().toString() :
                        event.getEnd().getDate().toString();

                result.append(String.format("%d. %s\n", i + 1, event.getSummary()));
                result.append(String.format("   Start: %s\n", start));
                result.append(String.format("   End: %s\n", end));
                result.append(String.format("   Event ID: %s\n", event.getId()));

                if (event.getAttendees() != null && !event.getAttendees().isEmpty()) {
                    result.append("   Attendees: ");
                    for (EventAttendee attendee : event.getAttendees()) {
                        result.append(attendee.getEmail()).append(", ");
                    }
                    result.append("\n");
                }
                result.append("\n");
            }

            return result.toString();

        } catch (Exception e) {
            return "Error fetching events: " + e.getMessage();
        }
    }

    private String executeSendEmail(String to, String subject, String body) {
        try {
            gmailService.sendEmail(currentUser, to, subject, body);
            log.info("✉️ Email sent to: {}", to);
            return "Email sent successfully to " + to;
        } catch (Exception e) {
            return "Error sending email: " + e.getMessage();
        }
    }

    private String executeReplyToEmail(String messageId, String threadId, String body) {
        try {
            gmailService.replyToEmail(currentUser, messageId, threadId, body);
            log.info("↩️ Reply sent to thread: {}", threadId);
            return "Reply sent successfully";
        } catch (Exception e) {
            return "Error replying: " + e.getMessage();
        }
    }

    private String executeGetAvailableTimes(String startDate, String endDate) {
        try {
            LocalDateTime start = LocalDateTime.parse(startDate);
            LocalDateTime end = LocalDateTime.parse(endDate);
            List<String> availableSlots = calendarService.getAvailableSlots(currentUser, start, end);
            return "Available times:\n" + String.join("\n", availableSlots);
        } catch (Exception e) {
            return "Error getting availability: " + e.getMessage();
        }
    }

    private String executeCreateCalendarEvent(String title, String description,
                                              String startTime, String endTime,
                                              List<String> attendees) {
        try {
            LocalDateTime start = LocalDateTime.parse(startTime);
            LocalDateTime end = LocalDateTime.parse(endTime);
            Event event = calendarService.createEvent(currentUser, title, description,
                    start, end, attendees);
            log.info("📅 Calendar event created: {}", title);
            return "Calendar event created: " + event.getHtmlLink();
        } catch (Exception e) {
            return "Error creating event: " + e.getMessage();
        }
    }

    private String executeSearchHubspotContact(String email) {
        try {
            JsonNode contact = hubspotService.searchContactByEmail(currentUser, email);
            if (contact == null) {
                return "Contact not found for email: " + email;
            }
            return "Contact found: " + contact.toString();
        } catch (Exception e) {
            return "Error searching contact: " + e.getMessage();
        }
    }

// Add this method to AIAgentService.java

    private String executeCreateHubspotContact(CreateContactParams params) {
        try {
            // ✅ FIX: Prevent creating contact for the user themselves
            if (params.getEmail() != null && params.getEmail().equalsIgnoreCase(currentUser.getEmail())) {
                return "Cannot create a HubSpot contact for yourself.";
            }

            Map<String, String> properties = new HashMap<>();
            if (params.getEmail() != null) properties.put("email", params.getEmail());
            if (params.getFirstname() != null) properties.put("firstname", params.getFirstname());
            if (params.getLastname() != null) properties.put("lastname", params.getLastname());
            if (params.getPhone() != null) properties.put("phone", params.getPhone());
            if (params.getCompany() != null) properties.put("company", params.getCompany());

            JsonNode newContact = hubspotService.createContact(currentUser, properties);
            log.info("👤 HubSpot contact created: {}", params.getEmail());

            // ✅ After creating contact, send thank you email
            String contactEmail = params.getEmail();
            String contactName = (params.getFirstname() != null ? params.getFirstname() : "");

            try {
                String emailBody = String.format("Dear %s,\n\nThank you for being a valued client! We appreciate your business and look forward to working with you.\n\nBest regards,\n%s",
                        contactName.isEmpty() ? "Client" : contactName,
                        currentUser.getName() != null ? currentUser.getName() : "Your Financial Advisor");

                gmailService.sendEmail(currentUser, contactEmail, "Thank You for Being a Client", emailBody);
                log.info("✉️ Thank you email sent to: {}", contactEmail);
            } catch (Exception emailError) {
                log.error("Failed to send thank you email: ", emailError);
                // Don't fail contact creation if email fails
            }

            return "Contact created with ID: " + newContact.get("id").asText() + ". Thank you email sent.";
        } catch (Exception e) {
            return "Error creating contact: " + e.getMessage();
        }
    }

    private String executeAddHubspotNote(String contactId, String note) {
        try {
            hubspotService.addNoteToContact(currentUser, contactId, note);
            log.info("📝 Note added to contact: {}", contactId);
            return "Note added successfully";
        } catch (Exception e) {
            return "Error adding note: " + e.getMessage();
        }
    }

    private String executeCreateTask(String type, String description, String context) {
        try {
            Task task = new Task();
            task.setUser(currentUser);
            task.setType(type);
            task.setDescription(description);
            task.setContext(context);
            task.setStatus("pending");
            taskRepository.save(task);
            return "Task created with ID: " + task.getId();
        } catch (Exception e) {
            return "Error creating task: " + e.getMessage();
        }
    }

    // ========== Main Processing Methods ==========

    @Transactional
    public String processMessage(User user, String userMessage) {
        this.currentUser = user;

        try {
            // Save user message
            Message message = new Message();
            message.setUser(user);
            message.setRole("user");
            message.setContent(userMessage);
            messageRepository.save(message);

            // Check for ongoing instructions
            if (userMessage.toLowerCase().contains("ongoing") ||
                    userMessage.toLowerCase().contains("always") ||
                    userMessage.toLowerCase().contains("whenever")) {
                OngoingInstruction instruction = new OngoingInstruction();
                instruction.setUser(user);
                instruction.setInstruction(userMessage);
                instructionRepository.save(instruction);
                log.info("💾 Ongoing instruction saved for user: {}", user.getEmail());
            }

            // Get conversation history
            List<Message> history = messageRepository.findTop20ByUserOrderByCreatedAtDesc(user);
            Collections.reverse(history);

            // Get ongoing instructions
            List<OngoingInstruction> instructions = instructionRepository.findByUserAndActiveTrue(user);

            // Build enhanced system message
            StringBuilder systemMessage = new StringBuilder();
            systemMessage.append("You are an AI assistant for a financial advisor. You have access to their emails, calendar, and HubSpot CRM.\n\n");
            systemMessage.append("IMPORTANT INSTRUCTIONS:\n");
            systemMessage.append("- When user asks about recent/latest emails, use fetch_recent_emails function\n");
            systemMessage.append("- When user asks about upcoming meetings/schedule, use get_upcoming_events function\n");
            systemMessage.append("- When user asks about client information in notes, use search_knowledge_base first\n");
            systemMessage.append("- When scheduling meetings, first get_available_times, then create_calendar_event\n");
            systemMessage.append("- Always search_hubspot_contact before creating new contacts to avoid duplicates\n");
            systemMessage.append("- When replying to emails, you need the message_id and thread_id from fetch_recent_emails\n\n");
            systemMessage.append("Current date and time: ").append(LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)).append("\n\n");

            if (!instructions.isEmpty()) {
                systemMessage.append("ONGOING INSTRUCTIONS:\n");
                for (OngoingInstruction inst : instructions) {
                    systemMessage.append("- ").append(inst.getInstruction()).append("\n");
                }
                systemMessage.append("\n");
            }

            systemMessage.append("Always be helpful, proactive, and professional.");

            // Build chat messages
            List<ChatMessage> chatMessages = new ArrayList<>();
            chatMessages.add(new ChatMessage(ChatMessageRole.SYSTEM.value(), systemMessage.toString()));

            for (Message msg : history) {
                chatMessages.add(new ChatMessage(msg.getRole(), msg.getContent()));
            }

            // Process with function calling
            String response = processChatWithFunctions(chatMessages, 0);

            // Save assistant response
            Message assistantMessage = new Message();
            assistantMessage.setUser(user);
            assistantMessage.setRole("assistant");
            assistantMessage.setContent(response);
            messageRepository.save(assistantMessage);

            return response;

        } catch (Exception e) {
            log.error("Error processing message: ", e);
            return "I encountered an error processing your request: " + e.getMessage();
        } finally {
            this.currentUser = null;
        }
    }

    @Transactional
    public void processIncomingEmail(User user, com.google.api.services.gmail.model.Message gmailMessage) {
        this.currentUser = user;

        try {
            ragService.indexEmail(user, gmailMessage);

            List<OngoingInstruction> instructions = instructionRepository.findByUserAndActiveTrue(user);
            if (instructions.isEmpty()) {
                return;
            }

            String emailBody = gmailService.extractBody(gmailMessage);
            String from = "", subject = "";

            for (com.google.api.services.gmail.model.MessagePartHeader header : gmailMessage.getPayload().getHeaders()) {
                if (header.getName().equalsIgnoreCase("From")) from = header.getValue();
                if (header.getName().equalsIgnoreCase("Subject")) subject = header.getValue();
            }

            StringBuilder prompt = new StringBuilder();
            prompt.append("📧 NEW EMAIL RECEIVED:\n");
            prompt.append("From: ").append(from).append("\n");
            prompt.append("Subject: ").append(subject).append("\n");
            prompt.append("Body: ").append(emailBody).append("\n");
            prompt.append("Message ID: ").append(gmailMessage.getId()).append("\n");
            prompt.append("Thread ID: ").append(gmailMessage.getThreadId()).append("\n\n");
            prompt.append("ONGOING INSTRUCTIONS:\n");
            for (OngoingInstruction inst : instructions) {
                prompt.append("- ").append(inst.getInstruction()).append("\n");
            }
            prompt.append("\nBased on these instructions, should you take any action? If yes, execute the appropriate functions.");

            List<ChatMessage> messages = new ArrayList<>();
            messages.add(new ChatMessage(ChatMessageRole.SYSTEM.value(),
                    "You are a proactive AI assistant. Analyze the email against ongoing instructions and take appropriate action using available functions."));
            messages.add(new ChatMessage(ChatMessageRole.USER.value(), prompt.toString()));

            String result = processChatWithFunctions(messages, 0);
            log.info("🤖 Proactive action taken for email: {}", result);

        } catch (Exception e) {
            log.error("Error processing incoming email: ", e);
        } finally {
            this.currentUser = null;
        }
    }

    @Transactional
    public void processNewCalendarEvent(User user, Event event) {
        this.currentUser = user;

        try {
            List<OngoingInstruction> instructions = instructionRepository.findByUserAndActiveTrue(user);
            if (instructions.isEmpty()) {
                return;
            }

            String eventSummary = event.getSummary();
            String startTime = event.getStart().getDateTime() != null ?
                    event.getStart().getDateTime().toString() : "";

            List<String> attendees = new ArrayList<>();
            if (event.getAttendees() != null) {
                for (EventAttendee attendee : event.getAttendees()) {
                    attendees.add(attendee.getEmail());
                }
            }

            StringBuilder prompt = new StringBuilder();
            prompt.append("📅 NEW CALENDAR EVENT CREATED:\n");
            prompt.append("Title: ").append(eventSummary).append("\n");
            prompt.append("Start: ").append(startTime).append("\n");
            prompt.append("Attendees: ").append(String.join(", ", attendees)).append("\n");
            prompt.append("Event ID: ").append(event.getId()).append("\n\n");
            prompt.append("ONGOING INSTRUCTIONS:\n");
            for (OngoingInstruction inst : instructions) {
                prompt.append("- ").append(inst.getInstruction()).append("\n");
            }
            prompt.append("\nShould you take any action based on these instructions? If yes, execute appropriate functions.");

            List<ChatMessage> messages = new ArrayList<>();
            messages.add(new ChatMessage(ChatMessageRole.SYSTEM.value(),
                    "You are a proactive AI assistant. Analyze the calendar event and ongoing instructions, then take action."));
            messages.add(new ChatMessage(ChatMessageRole.USER.value(), prompt.toString()));

            String result = processChatWithFunctions(messages, 0);
            log.info("🤖 Proactive action taken for calendar event: {}", result);

        } catch (Exception e) {
            log.error("Error processing calendar event: ", e);
        } finally {
            this.currentUser = null;
        }
    }

    private String processChatWithFunctions(List<ChatMessage> messages, int depth) {
        if (depth > 10) {
            return "I've reached the maximum number of actions for this request. Please try breaking it into smaller tasks.";
        }

        try {
            ChatCompletionResult result = openAIService.chat(messages, getAvailableFunctions());
            ChatCompletionChoice choice = result.getChoices().get(0);
            ChatMessage responseMessage = choice.getMessage();

            if (responseMessage.getFunctionCall() != null) {
                ChatFunctionCall functionCall = responseMessage.getFunctionCall();
                String functionName = functionCall.getName();
                String argumentsJson = functionCall.getArguments().toString();

                log.info("🔧 Function called: {} with args: {}", functionName, argumentsJson);

                String functionResult = executeFunctionByName(functionName, argumentsJson);

                messages.add(responseMessage);

                ChatMessage functionMessage = new ChatMessage(ChatMessageRole.FUNCTION.value(), functionResult);
                functionMessage.setName(functionName);
                messages.add(functionMessage);

                return processChatWithFunctions(messages, depth + 1);
            } else {
                return responseMessage.getContent() != null ?
                        responseMessage.getContent() :
                        "I've completed that action for you.";
            }

        } catch (Exception e) {
            log.error("Error in function calling loop: ", e);
            return "I encountered an error: " + e.getMessage();
        }
    }

    private String executeFunctionByName(String functionName, String argumentsJson) {
        try {
            JsonNode arguments = objectMapper.readTree(argumentsJson);

            switch (functionName) {
                case "search_knowledge_base":
                    return executeSearchKnowledgeBase(arguments.get("query").asText());

                case "fetch_recent_emails":
                    int maxEmails = arguments.has("max_results") ?
                            arguments.get("max_results").asInt() : 10;
                    return executeFetchRecentEmails(maxEmails);

                case "get_upcoming_events":
                    int maxEvents = arguments.has("max_results") ?
                            arguments.get("max_results").asInt() : 10;
                    return executeGetUpcomingEvents(maxEvents);

                case "send_email":
                    return executeSendEmail(
                            arguments.get("to").asText(),
                            arguments.get("subject").asText(),
                            arguments.get("body").asText()
                    );

                case "reply_to_email":
                    return executeReplyToEmail(
                            arguments.get("message_id").asText(),
                            arguments.get("thread_id").asText(),
                            arguments.get("body").asText()
                    );

                case "get_available_times":
                    return executeGetAvailableTimes(
                            arguments.get("start_date").asText(),
                            arguments.get("end_date").asText()
                    );

                case "create_calendar_event":
                    List<String> attendees = new ArrayList<>();
                    if (arguments.has("attendees") && arguments.get("attendees").isArray()) {
                        arguments.get("attendees").forEach(node -> attendees.add(node.asText()));
                    }
                    return executeCreateCalendarEvent(
                            arguments.get("title").asText(),
                            arguments.has("description") ? arguments.get("description").asText() : "",
                            arguments.get("start_time").asText(),
                            arguments.get("end_time").asText(),
                            attendees
                    );

                case "search_hubspot_contact":
                    return executeSearchHubspotContact(arguments.get("email").asText());

                case "create_hubspot_contact":
                    CreateContactParams contactParams = new CreateContactParams();
                    contactParams.setEmail(arguments.get("email").asText());
                    if (arguments.has("firstname")) contactParams.setFirstname(arguments.get("firstname").asText());
                    if (arguments.has("lastname")) contactParams.setLastname(arguments.get("lastname").asText());
                    if (arguments.has("phone")) contactParams.setPhone(arguments.get("phone").asText());
                    if (arguments.has("company")) contactParams.setCompany(arguments.get("company").asText());
                    return executeCreateHubspotContact(contactParams);

                case "add_hubspot_note":
                    return executeAddHubspotNote(
                            arguments.get("contact_id").asText(),
                            arguments.get("note").asText()
                    );

                case "create_task":
                    return executeCreateTask(
                            arguments.get("type").asText(),
                            arguments.get("description").asText(),
                            arguments.get("context").asText()
                    );

                default:
                    return "Unknown function: " + functionName;
            }
        } catch (Exception e) {
            log.error("Error executing function {}: ", functionName, e);
            return "Error executing function " + functionName + ": " + e.getMessage();
        }
    }
}