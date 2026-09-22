package com.chatbot.whatsapp.service;

import com.chatbot.whatsapp.entity.Conversation;
import com.chatbot.whatsapp.entity.Message;
import com.chatbot.whatsapp.entity.enums.MessageDirection;
import com.chatbot.whatsapp.entity.enums.MessageStatus;
import com.chatbot.whatsapp.repository.MessageRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MessageService {

    private final MessageRepository messageRepository;

    public MessageService(MessageRepository messageRepository) {
        this.messageRepository = messageRepository;
    }

    @Transactional
    public Message recordInbound(Conversation conversation, String content) {
        return recordInbound(conversation, content, null);
    }

    /**
     * Grava uma mensagem recebida guardando tambem o id dela no provedor
     * externo, quando existir (ex.: o {@code wamid} da Meta). Esse id e a chave
     * de idempotencia contra reentregas do webhook.
     *
     * @param externalId id no provedor, ou {@code null} para origens que nao
     *                   possuem um (ex.: o simulador local).
     */
    @Transactional
    public Message recordInbound(Conversation conversation, String content, String externalId) {
        return messageRepository.save(
                Message.builder()
                        .conversation(conversation)
                        .direction(MessageDirection.INBOUND)
                        .content(content)
                        .status(MessageStatus.RECEIVED)
                        .externalId(externalId)
                        .build());
    }

    /** @see MessageRepository#existsByExternalId(String) */
    @Transactional(readOnly = true)
    public boolean alreadyProcessed(String externalId) {
        return externalId != null && messageRepository.existsByExternalId(externalId);
    }

    @Transactional
    public Message recordOutbound(Conversation conversation, String content, MessageStatus status) {
        return messageRepository.save(
                Message.builder()
                        .conversation(conversation)
                        .direction(MessageDirection.OUTBOUND)
                        .content(content)
                        .status(status)
                        .build());
    }

    @Transactional
    public void updateStatus(Message message, MessageStatus status) {
        message.setStatus(status);
        messageRepository.save(message);
    }

    @Transactional(readOnly = true)
    public List<Message> listByConversation(Long conversationId) {
        return messageRepository.findByConversationIdOrderByCreatedAtAscIdAsc(conversationId);
    }
}
