package com.chatbot.whatsapp.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Mensagem enviada por um atendente humano")
public record HumanReplyRequest(
        @NotBlank(message = "message e obrigatoria")
        @Size(max = 4096, message = "message deve ter no maximo 4096 caracteres")
        String message
) {
}
