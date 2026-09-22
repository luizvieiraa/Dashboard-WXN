package com.chatbot.whatsapp.dto.response;

import com.chatbot.whatsapp.entity.Conversation;
import com.chatbot.whatsapp.entity.enums.ChannelType;
import com.chatbot.whatsapp.entity.enums.ConversationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

@Schema(description = "Dados de uma conversa entre um cliente e o chatbot")
public record ConversationResponse(
        Long id,
        String customerPhone,
        ConversationStatus status,
        ChannelType channel,
        String context,
        Instant startedAt,
        Instant lastInteractionAt,
        String assignedTo,
        Instant assignedAt,
        Instant closedAt,
        List<MessageResponse> messages
) {
    public static ConversationResponse fromEntity(Conversation conversation, List<MessageResponse> messages) {
        return new ConversationResponse(
                conversation.getId(),
                conversation.getCustomer().getPhoneNumber(),
                conversation.getStatus(),
                conversation.getChannel(),
                conversation.getContext(),
                conversation.getStartedAt(),
                conversation.getLastInteractionAt(),
                conversation.getAssignedTo(),
                conversation.getAssignedAt(),
                conversation.getClosedAt(),
                messages
        );
    }
}
