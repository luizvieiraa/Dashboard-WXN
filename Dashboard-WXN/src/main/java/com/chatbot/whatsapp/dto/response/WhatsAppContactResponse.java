package com.chatbot.whatsapp.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Link publico para iniciar uma conversa com o chatbot no WhatsApp")
public record WhatsAppContactResponse(boolean available, String url) {
}
