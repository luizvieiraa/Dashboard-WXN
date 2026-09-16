package com.chatbot.whatsapp.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;

/**
 * Payload recebido pelo endpoint de webhook do WhatsApp.
 *
 * <p>Este e o formato utilizado para SIMULAR uma mensagem recebida via
 * WhatsApp, permitindo testar todo o fluxo (API -> processamento -> banco ->
 * resposta) antes de uma integracao real estar configurada. Quando a
 * integracao real com um provedor (Meta Cloud API, Twilio, etc.) for
 * definida, o formato exato do payload do provedor sera adaptado para este
 * DTO em uma camada de conversao dedicada, sem impactar o restante do
 * sistema.</p>
 */
@Schema(description = "Mensagem recebida via WhatsApp (ou simulada para testes)")
public record WhatsAppWebhookRequest(

        @Schema(description = "Numero de telefone do cliente, em formato E.164 sem '+' (ex.: 5511999999999)",
                example = "5511999999999")
        @NotBlank(message = "phone e obrigatorio")
        @Pattern(regexp = "\\d{8,15}", message = "phone deve conter apenas digitos (8 a 15 caracteres)")
        String phone,

        @Schema(description = "Conteudo textual da mensagem enviada pelo cliente", example = "Olá, gostaria de saber o preço de um produto")
        @NotBlank(message = "message e obrigatorio")
        String message,

        @Schema(description = "Data/hora em que a mensagem foi enviada. Se omitido, o servidor usa o horario atual.",
                example = "2026-09-09T12:00:00Z")
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Instant timestamp
) {
}
