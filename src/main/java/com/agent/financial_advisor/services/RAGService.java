package com.agent.financial_advisor.services;

import com.agent.financial_advisor.model.EmailDocument;
import com.agent.financial_advisor.model.HubspotDocument;
import com.agent.financial_advisor.model.User;
import com.agent.financial_advisor.repository.EmailDocumentRepository;
import com.agent.financial_advisor.repository.HubspotDocumentRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePartHeader;
import com.pgvector.PGvector;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Service
public class RAGService {

    private final EmailDocumentRepository emailDocumentRepository;
    private final HubspotDocumentRepository hubspotDocumentRepository;
    private final OpenAIService openAIService;
    private final GmailService gmailService;

    // Conservative limits for token handling
    private static final int CHARS_PER_TOKEN = 4;
    private static final int MAX_TOKENS = 7000; // Leave buffer below 8192 limit
    private static final int MAX_CHARS = MAX_TOKENS * CHARS_PER_TOKEN;

    public RAGService(EmailDocumentRepository emailDocumentRepository,
                      HubspotDocumentRepository hubspotDocumentRepository,
                      OpenAIService openAIService,
                      GmailService gmailService) {
        this.emailDocumentRepository = emailDocumentRepository;
        this.hubspotDocumentRepository = hubspotDocumentRepository;
        this.openAIService = openAIService;
        this.gmailService = gmailService;
    }

    @Transactional
    public void indexEmail(User user, Message message) {
        try {
            String messageId = message.getId();
            if (emailDocumentRepository.existsByGmailMessageId(messageId)) {
                return;
            }

            // Extract email metadata
            EmailDocument doc = new EmailDocument();
            doc.setUser(user);
            doc.setGmailMessageId(messageId);

            for (MessagePartHeader header : message.getPayload().getHeaders()) {
                if (header.getName().equalsIgnoreCase("From")) {
                    String from = header.getValue();
                    if (from.contains("<")) {
                        doc.setFromEmail(from.substring(from.indexOf("<") + 1, from.indexOf(">")));
                        doc.setFromName(from.substring(0, from.indexOf("<")).trim());
                    } else {
                        doc.setFromEmail(from);
                        doc.setFromName(from);
                    }
                }
                if (header.getName().equalsIgnoreCase("Subject")) {
                    doc.setSubject(header.getValue());
                }
                if (header.getName().equalsIgnoreCase("Date")) {
                    try {
                        doc.setEmailDate(LocalDateTime.ofInstant(
                                Instant.ofEpochMilli(message.getInternalDate()),
                                ZoneId.systemDefault()
                        ));
                    } catch (Exception e) {
                        doc.setEmailDate(LocalDateTime.now());
                    }
                }
            }

            String body = gmailService.extractBody(message);
            doc.setBody(body);

            // Create the text to embed
            String textToEmbed = String.format("From: %s (%s)\nSubject: %s\nBody: %s",
                    doc.getFromName(), doc.getFromEmail(), doc.getSubject(), body);

            // Check if text is too long
            if (textToEmbed.length() > MAX_CHARS) {
                // Split into chunks and save multiple documents
                List<String> chunks = chunkText(textToEmbed, MAX_CHARS);

                for (int i = 0; i < chunks.size(); i++) {
                    EmailDocument chunkDoc = createChunkDocument(doc, chunks.get(i), i);
                    List<Double> embedding = openAIService.createEmbedding(chunks.get(i));
                    chunkDoc.setEmbedding(new PGvector(convertToFloatArray(embedding)));
                    emailDocumentRepository.save(chunkDoc);
                }
            } else {
                // Text is within limits, process normally
                List<Double> embedding = openAIService.createEmbedding(textToEmbed);
                doc.setEmbedding(new PGvector(convertToFloatArray(embedding)));
                emailDocumentRepository.save(doc);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Transactional
    public void indexHubspotContact(User user, JsonNode contact) {
        try {
            String contactId = contact.get("id").asText();
            JsonNode properties = contact.get("properties");
            JsonNode notes = contact.get("notes");

            HubspotDocument doc = hubspotDocumentRepository
                    .findByUserAndHubspotContactId(user, contactId)
                    .orElse(new HubspotDocument());

            doc.setUser(user);
            doc.setHubspotContactId(contactId);
            doc.setFirstName(properties.has("firstname") ? properties.get("firstname").asText() : "");
            doc.setLastName(properties.has("lastname") ? properties.get("lastname").asText() : "");
            doc.setEmail(properties.has("email") ? properties.get("email").asText() : "");
            // join all note texts into one string, or store as JSON
            if (notes != null && notes.isArray()) {
                String allNotes = StreamSupport.stream(notes.spliterator(), false)
                        .map(n -> n.get("hs_note_body").asText())
                        .collect(Collectors.joining("\n---\n"));
                doc.setNotes(allNotes);
            } else {
                doc.setNotes("");
            }
            doc.setAllProperties(properties.toString());

            if (properties.has("lastmodifieddate")) {
                doc.setLastModified(LocalDateTime.ofInstant(
                        Instant.parse(properties.get("lastmodifieddate").asText()),
                        ZoneId.systemDefault()
                ));
            }

            String textToEmbed = String.format("Contact: %s %s\nEmail: %s\nNotes: %s\nAll Properties: %s",
                    doc.getFirstName(), doc.getLastName(), doc.getEmail(), doc.getNotes(), doc.getAllProperties());

            // Check if text is too long for HubSpot contacts too
            if (textToEmbed.length() > MAX_CHARS) {
                // Truncate or summarize the properties field
                String truncatedProperties = doc.getAllProperties().substring(0,
                        Math.min(doc.getAllProperties().length(), MAX_CHARS / 2));
                textToEmbed = String.format("Contact: %s %s\nEmail: %s\nNotes: %s\nProperties: %s",
                        doc.getFirstName(), doc.getLastName(), doc.getEmail(), doc.getNotes(), truncatedProperties);
            }

            List<Double> embedding = openAIService.createEmbedding(textToEmbed);
            doc.setEmbedding(new PGvector(convertToFloatArray(embedding)));
            hubspotDocumentRepository.save(doc);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public String search(User user, String query, int limit) {
        try {
            List<Double> queryEmbedding = openAIService.createEmbedding(query);
            String embeddingStr = "[" + queryEmbedding.stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining(",")) + "]";

            List<EmailDocument> emails = emailDocumentRepository.findSimilar(
                    user.getId(), embeddingStr, limit
            );

            List<HubspotDocument> contacts = hubspotDocumentRepository.findSimilar(
                    user.getId(), embeddingStr, limit
            );

            StringBuilder context = new StringBuilder();
            context.append("=== Relevant Emails ===\n");
            for (EmailDocument email : emails) {
                context.append(String.format("From: %s (%s)\nSubject: %s\nDate: %s\nBody: %s\n\n",
                        email.getFromName(), email.getFromEmail(), email.getSubject(),
                        email.getEmailDate(), email.getBody()));
            }

            context.append("\n=== Relevant Contacts ===\n");
            for (HubspotDocument contact : contacts) {
                context.append(String.format("Contact: %s %s\nEmail: %s\nNotes: %s\n\n",
                        contact.getFirstName(), contact.getLastName(),
                        contact.getEmail(), contact.getNotes()));
            }

            return context.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    /**
     * Splits text into chunks at sentence boundaries
     */
    private List<String> chunkText(String text, int maxChunkSize) {
        List<String> chunks = new ArrayList<>();

        if (text == null || text.isEmpty()) {
            return chunks;
        }

        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + maxChunkSize, text.length());

            // Try to break at sentence boundary if not at the end
            if (end < text.length()) {
                int lastPeriod = text.lastIndexOf(". ", end);
                int lastExclamation = text.lastIndexOf("! ", end);
                int lastQuestion = text.lastIndexOf("? ", end);
                int lastNewline = text.lastIndexOf("\n", end);

                int breakPoint = Math.max(
                        Math.max(lastPeriod, lastExclamation),
                        Math.max(lastQuestion, lastNewline)
                );

                // Use break point if it's reasonable (at least 70% of max size)
                if (breakPoint > start && breakPoint > start + (maxChunkSize * 0.7)) {
                    end = breakPoint + 1;
                }
            }

            chunks.add(text.substring(start, end).trim());
            start = end;
        }

        return chunks;
    }

    /**
     * Creates a chunk document with metadata from the original
     */
    private EmailDocument createChunkDocument(EmailDocument original, String chunkText, int chunkIndex) {
        EmailDocument chunk = new EmailDocument();
        chunk.setUser(original.getUser());
        // Make gmailMessageId unique for each chunk
        chunk.setGmailMessageId(original.getGmailMessageId() + "_chunk_" + chunkIndex);
        chunk.setFromEmail(original.getFromEmail());
        chunk.setFromName(original.getFromName());
        chunk.setSubject(original.getSubject() + " [Part " + (chunkIndex + 1) + "]");
        chunk.setBody(chunkText); // Store the full chunk including metadata
        chunk.setEmailDate(original.getEmailDate());
        return chunk;
    }

    /**
     * Converts List<Double> to float[]
     */
    private float[] convertToFloatArray(List<Double> embedding) {
        float[] embeddingArray = new float[embedding.size()];
        for (int i = 0; i < embedding.size(); i++) {
            embeddingArray[i] = embedding.get(i).floatValue();
        }
        return embeddingArray;
    }
}