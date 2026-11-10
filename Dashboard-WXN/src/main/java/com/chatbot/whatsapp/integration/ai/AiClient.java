package com.chatbot.whatsapp.integration.ai;

import java.util.Optional;

/** Porta para um provedor externo de geracao de texto. */
public interface AiClient {

    Optional<String> generateReply(String customerMessage, String customerContext);

    /** {@code true} quando o provedor esta habilitado e com configuracao completa. */
    default boolean isAvailable() {
        return false;
    }

    /**
     * Classifica a mensagem do cliente e sugere a proxima acao do atendimento.
     *
     * @return vazio quando o provedor esta desligado, falhou ou respondeu fora do
     *         formato esperado; nesses casos o chamador usa o fluxo local.
     */
    default Optional<AiTriageDecision> decide(AiTriageRequest request) {
        return Optional.empty();
    }
}
