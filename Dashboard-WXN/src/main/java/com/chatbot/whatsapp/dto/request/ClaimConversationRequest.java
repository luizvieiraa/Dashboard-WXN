package com.chatbot.whatsapp.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Identifica o atendente que assumirá a conversa")
public record ClaimConversationRequest(
        @NotBlank(message = "attendant e obrigatorio")
        @Size(max = 255, message = "attendant deve ter no maximo 255 caracteres")
        String attendant
) {
}
