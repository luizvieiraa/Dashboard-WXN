package com.chatbot.whatsapp.entity.enums;

/**
 * Situacao de uma conversa/sessao entre o cliente e o chatbot.
 */
public enum ConversationStatus {
    /** Conversa em andamento; novas mensagens do mesmo cliente sao anexadas a ela. */
    OPEN,
    /** Conversa encerrada. Uma nova mensagem do cliente inicia uma nova conversa. */
    CLOSED
}
