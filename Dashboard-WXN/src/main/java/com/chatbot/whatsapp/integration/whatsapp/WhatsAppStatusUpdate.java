package com.chatbot.whatsapp.integration.whatsapp;

import java.time.Instant;

/**
 * Status de entrega informado pela Meta para uma mensagem enviada pelo backend,
 * ja livre do formato do provedor.
 *
 * @param messageId  {@code wamid} da mensagem enviada (gravado em {@code messages.external_id}).
 * @param status     valor informado pela Meta, em minusculas: {@code sent}, {@code delivered},
 *                   {@code read} ou {@code failed}. Valores desconhecidos sao ignorados pelo servico.
 * @param timestamp  momento do evento no provedor.
 * @param errorCode  codigo do erro da Meta quando {@code status=failed}; nulo nos demais casos.
 * @param errorTitle descricao curta do erro da Meta, sem dados do cliente.
 */
public record WhatsAppStatusUpdate(
        String messageId,
        String status,
        Instant timestamp,
        Integer errorCode,
        String errorTitle
) {

    public static final String SENT = "sent";
    public static final String DELIVERED = "delivered";
    public static final String READ = "read";
    public static final String FAILED = "failed";
}
