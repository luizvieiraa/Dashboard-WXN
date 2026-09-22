package com.chatbot.whatsapp.service;

import com.chatbot.whatsapp.entity.Conversation;
import com.chatbot.whatsapp.entity.enums.ConversationStatus;
import com.chatbot.whatsapp.entity.enums.MessageStatus;
import com.chatbot.whatsapp.exception.InvalidConversationStateException;
import com.chatbot.whatsapp.integration.whatsapp.WhatsAppClient;
import com.chatbot.whatsapp.repository.ConversationRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Opera o ciclo de atendimento humano iniciado por uma triagem. */
@Service
public class HumanAttendanceService {

    private final ConversationService conversationService;
    private final ConversationRepository conversationRepository;
    private final MessageService messageService;
    private final WhatsAppClient whatsAppClient;

    public HumanAttendanceService(ConversationService conversationService,
                                  ConversationRepository conversationRepository,
                                  MessageService messageService,
                                  WhatsAppClient whatsAppClient) {
        this.conversationService = conversationService;
        this.conversationRepository = conversationRepository;
        this.messageService = messageService;
        this.whatsAppClient = whatsAppClient;
    }

    @Transactional
    public Conversation claim(Long conversationId, String attendant) {
        Conversation conversation = conversationService.findById(conversationId);
        requireStatus(conversation, ConversationStatus.WAITING_HUMAN, "assumida");

        conversation.setStatus(ConversationStatus.HUMAN_ACTIVE);
        conversation.setAssignedTo(attendant.trim());
        conversation.setAssignedAt(Instant.now());
        conversation.setContext("HUMAN_ATTENDANCE_STARTED");
        conversation.setLastInteractionAt(Instant.now());
        return conversationRepository.save(conversation);
    }

    @Transactional
    public Conversation reply(Long conversationId, String text) {
        Conversation conversation = conversationService.findById(conversationId);
        requireStatus(conversation, ConversationStatus.HUMAN_ACTIVE, "respondida");

        String message = text.trim();
        messageService.recordOutbound(conversation, message, MessageStatus.SENT);
        conversation.setContext("HUMAN_REPLY");
        conversation.setLastInteractionAt(Instant.now());
        Conversation saved = conversationRepository.save(conversation);
        whatsAppClient.sendMessage(conversation.getCustomer().getPhoneNumber(), message);
        return saved;
    }

    @Transactional
    public Conversation close(Long conversationId) {
        Conversation conversation = conversationService.findById(conversationId);
        if (conversation.getStatus() == ConversationStatus.CLOSED) {
            throw new InvalidConversationStateException("A conversa ja esta encerrada");
        }

        conversation.setStatus(ConversationStatus.CLOSED);
        conversation.setClosedAt(Instant.now());
        conversation.setContext("CONVERSATION_CLOSED");
        conversation.setLastInteractionAt(Instant.now());
        return conversationRepository.save(conversation);
    }

    private void requireStatus(Conversation conversation,
                               ConversationStatus expected,
                               String operation) {
        if (conversation.getStatus() != expected) {
            throw new InvalidConversationStateException(
                    "A conversa nao pode ser %s enquanto estiver em %s"
                            .formatted(operation, conversation.getStatus())
            );
        }
    }
}
