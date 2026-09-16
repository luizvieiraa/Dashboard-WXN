package com.chatbot.whatsapp.entity.enums;

/**
 * Status do ciclo de vida de uma mensagem.
 */
public enum MessageStatus {
    /** Mensagem inbound recebida e ainda nao processada. */
    RECEIVED,
    /** Mensagem inbound processada com sucesso pelo chatbot. */
    PROCESSED,
    /** Mensagem outbound enviada (ou simulada como enviada) ao cliente. */
    SENT,
    /** Falha ao processar ou enviar a mensagem. */
    FAILED
}
