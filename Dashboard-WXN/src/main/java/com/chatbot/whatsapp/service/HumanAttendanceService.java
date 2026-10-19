package com.chatbot.whatsapp.service;

import com.chatbot.whatsapp.entity.Conversation;
import com.chatbot.whatsapp.entity.Attendant;
import com.chatbot.whatsapp.entity.enums.MessageStatus;
import com.chatbot.whatsapp.integration.whatsapp.WhatsAppClient;
import com.chatbot.whatsapp.integration.whatsapp.WhatsAppSendResult;
import com.chatbot.whatsapp.repository.ConversationRepository;
import com.chatbot.whatsapp.repository.AttendantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Opera o ciclo de atendimento humano iniciado por uma triagem. */
@Service
public class HumanAttendanceService {

    private final ConversationService conversationService;
    private final ConversationRepository conversationRepository;
    private final MessageService messageService;
    private final WhatsAppClient whatsAppClient;
    private final AttendantRepository attendantRepository;

    public HumanAttendanceService(ConversationService conversationService,
                                  ConversationRepository conversationRepository,
                                  MessageService messageService,
                                  WhatsAppClient whatsAppClient,
                                  AttendantRepository attendantRepository) {
        this.conversationService = conversationService;
        this.conversationRepository = conversationRepository;
        this.messageService = messageService;
        this.whatsAppClient = whatsAppClient;
        this.attendantRepository = attendantRepository;
    }

    @Transactional
    public Conversation claim(Long conversationId, String attendant) {
        Conversation conversation = conversationService.findById(conversationId);
        Attendant candidate = new Attendant(attendant);
        var operator = attendantRepository.findFirstByName(candidate.getName())
                .orElseGet(() -> attendantRepository.save(candidate));
        conversation.assume(operator);
        return conversationRepository.save(conversation);
    }

    @Transactional
    public Conversation reply(Long conversationId, String text) {
        Conversation conversation = conversationService.findById(conversationId);
        conversation.requireHumanAttendance();

        String message = text.trim();
        WhatsAppSendResult sendResult = whatsAppClient.sendMessage(
                conversation.getCustomer().getPhoneNumber(), message);
        messageService.recordOutbound(
                conversation,
                message,
                sendResult.sent() ? MessageStatus.SENT : MessageStatus.FAILED,
                sendResult.providerMessageId()
        );
        conversation.recordInteraction("HUMAN_REPLY");
        Conversation saved = conversationRepository.save(conversation);
        return saved;
    }

    @Transactional
    public Conversation close(Long conversationId) {
        Conversation conversation = conversationService.findById(conversationId);
        conversation.close();
        return conversationRepository.save(conversation);
    }

}
