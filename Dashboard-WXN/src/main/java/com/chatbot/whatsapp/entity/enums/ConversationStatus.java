package com.chatbot.whatsapp.entity.enums;

/**
 * Situacao de uma conversa/sessao entre o cliente e o chatbot.
 */
public enum ConversationStatus {
    /** Bot ativo, antes do inicio ou entre etapas da coleta estruturada. */
    BOT_ACTIVE,
    /** Bot coletando os dados necessarios para a triagem. */
    COLLECTING_INFORMATION,
    /** Triagem concluida e pronta para consulta/acao comercial. */
    QUALIFIED,
    /** Automacao pausada enquanto a conversa aguarda um atendente. */
    WAITING_HUMAN,
    /** Conversa assumida por um atendente humano. */
    HUMAN_ACTIVE,
    /** Conversa encerrada. Uma nova mensagem do cliente inicia uma nova conversa. */
    CLOSED
}
