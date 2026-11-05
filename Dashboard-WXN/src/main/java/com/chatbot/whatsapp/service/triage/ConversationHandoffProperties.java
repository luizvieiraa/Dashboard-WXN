package com.chatbot.whatsapp.service.triage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Regras de encaminhamento automatico para atendimento humano.
 *
 * @param maxUnresolvedAttempts respostas automaticas seguidas sem solucao (intencao
 *                              nao reconhecida ou pedido de mais informacoes) que levam
 *                              a conversa para um atendente. Vale com ou sem assistente
 *                              virtual.
 */
@ConfigurationProperties(prefix = "conversation.handoff")
public record ConversationHandoffProperties(int maxUnresolvedAttempts) {

    private static final int DEFAULT_MAX_UNRESOLVED_ATTEMPTS = 3;

    public int effectiveMaxUnresolvedAttempts() {
        return maxUnresolvedAttempts > 0 ? maxUnresolvedAttempts : DEFAULT_MAX_UNRESOLVED_ATTEMPTS;
    }
}
