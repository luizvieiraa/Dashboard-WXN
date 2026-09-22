package com.chatbot.whatsapp.service.chatbot;

import com.chatbot.whatsapp.entity.Customer;
import com.chatbot.whatsapp.integration.ai.AiClient;
import org.springframework.stereotype.Service;

/** Escolhe IA quando habilitada e preserva a resposta local como fallback. */
@Service
public class IntelligentResponseService {

    private final AiClient aiClient;
    private final ChatbotResponseService fallbackService;

    public IntelligentResponseService(AiClient aiClient, ChatbotResponseService fallbackService) {
        this.aiClient = aiClient;
        this.fallbackService = fallbackService;
    }

    public GeneratedReply generateReply(String message, ChatIntent intent, Customer customer) {
        String context = "nome=%s; empresa=%s"
                .formatted(orUnknown(customer.getName()), orUnknown(customer.getCompanyName()));

        return aiClient.generateReply(message, context)
                .map(text -> new GeneratedReply(text, "AI_RESPONSE"))
                .orElseGet(() -> new GeneratedReply(
                        fallbackService.generateReply(intent),
                        intent.name()
                ));
    }

    private String orUnknown(String value) {
        return value == null || value.isBlank() ? "nao informado" : value;
    }
}
