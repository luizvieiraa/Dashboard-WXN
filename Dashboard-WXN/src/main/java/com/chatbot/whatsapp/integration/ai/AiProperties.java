package com.chatbot.whatsapp.integration.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuracao da geracao opcional de respostas por IA. */
@ConfigurationProperties(prefix = "ai")
public record AiProperties(
        boolean enabled,
        String apiUrl,
        String apiKey,
        String model,
        int timeoutSeconds,
        int maxOutputTokens
) {
}
