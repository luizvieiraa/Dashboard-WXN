package com.chatbot.whatsapp.integration.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

/**
 * Configuracao da geracao opcional de respostas por IA.
 *
 * @param enabled         liga o assistente virtual; desligado, o chatbot usa somente o fluxo local.
 * @param apiUrl          endpoint compativel com o formato OpenAI Responses.
 * @param apiKey          chave do provedor, mantida somente no ambiente.
 * @param model           modelo configurado no provedor.
 * @param timeoutSeconds  limite de espera pela resposta antes do fallback.
 * @param maxOutputTokens limite de tokens gerados por resposta (controle de custo).
 * @param historyMessages quantidade maxima de mensagens recentes da conversa enviadas como contexto.
 * @param maxInputChars   tamanho maximo da mensagem do cliente enviada ao provedor.
 * @param minConfidence   confianca minima (0 a 1) para o bot responder sozinho; abaixo dela a
 *                        conversa vai para um atendente.
 * @param knowledgeBase   local da base de conhecimento ({@code classpath:} ou {@code file:}).
 */
@ConfigurationProperties(prefix = "ai")
public record AiProperties(
        boolean enabled,
        String apiUrl,
        String apiKey,
        String model,
        int timeoutSeconds,
        int maxOutputTokens,
        int historyMessages,
        int maxInputChars,
        double minConfidence,
        String knowledgeBase
) {

    static final String DEFAULT_KNOWLEDGE_BASE = "classpath:assistant/knowledge-base.md";
    private static final int DEFAULT_HISTORY_MESSAGES = 10;
    private static final int MAX_HISTORY_MESSAGES = 30;
    private static final int DEFAULT_MAX_INPUT_CHARS = 1000;
    private static final int MAX_INPUT_CHARS = 4096;
    private static final double DEFAULT_MIN_CONFIDENCE = 0.6;

    @ConstructorBinding
    public AiProperties {
    }

    /** Formato anterior, sem os limites da triagem; os valores padrao sao aplicados. */
    public AiProperties(boolean enabled, String apiUrl, String apiKey, String model,
                        int timeoutSeconds, int maxOutputTokens) {
        this(enabled, apiUrl, apiKey, model, timeoutSeconds, maxOutputTokens, 0, 0, 0, null);
    }

    public int effectiveHistoryMessages() {
        return historyMessages > 0 ? Math.min(historyMessages, MAX_HISTORY_MESSAGES) : DEFAULT_HISTORY_MESSAGES;
    }

    public int effectiveMaxInputChars() {
        return maxInputChars > 0 ? Math.min(maxInputChars, MAX_INPUT_CHARS) : DEFAULT_MAX_INPUT_CHARS;
    }

    public double effectiveMinConfidence() {
        return minConfidence > 0 && minConfidence <= 1 ? minConfidence : DEFAULT_MIN_CONFIDENCE;
    }

    public String effectiveKnowledgeBase() {
        return knowledgeBase == null || knowledgeBase.isBlank() ? DEFAULT_KNOWLEDGE_BASE : knowledgeBase.trim();
    }
}
