package com.chatbot.whatsapp.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * Resposta devolvida apos o processamento de uma mensagem recebida via
 * webhook: confirma o que foi recebido e traz a resposta que o chatbot
 * geraria para o cliente.
 */
@Schema(description = "Resultado do processamento de uma mensagem recebida via webhook")
public record WhatsAppWebhookResponse(

        @Schema(description = "Id da conversa a qual a mensagem pertence")
        Long conversationId,

        @Schema(description = "Numero de telefone do cliente")
        String customerPhone,

        @Schema(description = "Mensagem recebida do cliente")
        String receivedMessage,

        @Schema(description = "Resposta gerada pelo chatbot para o cliente")
        String botReply,

        @Schema(description = "Momento em que a mensagem foi processada")
        Instant processedAt
) {
}
