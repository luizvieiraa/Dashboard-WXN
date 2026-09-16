package com.chatbot.whatsapp.dto.response;

import com.chatbot.whatsapp.entity.Message;
import com.chatbot.whatsapp.entity.enums.MessageDirection;
import com.chatbot.whatsapp.entity.enums.MessageStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "Uma mensagem dentro de uma conversa")
public record MessageResponse(
        Long id,
        MessageDirection direction,
        String content,
        MessageStatus status,
        Instant createdAt
) {
    public static MessageResponse fromEntity(Message message) {
        return new MessageResponse(
                message.getId(),
                message.getDirection(),
                message.getContent(),
                message.getStatus(),
                message.getCreatedAt()
        );
    }
}
