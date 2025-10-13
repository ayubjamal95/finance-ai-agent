package com.agent.financial_advisor.services;
import com.theokanning.openai.completion.chat.*;
import com.theokanning.openai.embedding.EmbeddingRequest;
import com.theokanning.openai.service.OpenAiService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.List;

@Service
public class OpenAIService {

    @Value("${app.openai.api-key}")
    private String apiKey;

    private OpenAiService openAiService;

    @PostConstruct
    public void init() {
        this.openAiService = new OpenAiService(apiKey, Duration.ofSeconds(60));
    }

    public ChatCompletionResult chat(List<ChatMessage> messages, List<ChatFunction> functions) {
        ChatCompletionRequest.ChatCompletionRequestBuilder builder = ChatCompletionRequest.builder()
                .model("gpt-3.5-turbo")
                .messages(messages)
                .temperature(0.7);

        if (functions != null && !functions.isEmpty()) {
            builder.functions(functions);
            builder.functionCall(ChatCompletionRequest.ChatCompletionRequestFunctionCall.of("auto"));
        }

        return openAiService.createChatCompletion(builder.build());
    }

    public List<Double> createEmbedding(String text) {
        EmbeddingRequest request = EmbeddingRequest.builder()
                .model("text-embedding-ada-002")
                .input(List.of(text))
                .build();

        return openAiService.createEmbeddings(request)
                .getData().get(0).getEmbedding();
    }
}
