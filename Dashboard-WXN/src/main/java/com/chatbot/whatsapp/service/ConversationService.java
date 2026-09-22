package com.chatbot.whatsapp.service;

import com.chatbot.whatsapp.entity.Conversation;
import com.chatbot.whatsapp.entity.Customer;
import com.chatbot.whatsapp.entity.enums.ChannelType;
import com.chatbot.whatsapp.entity.enums.ConversationStatus;
import com.chatbot.whatsapp.exception.ResourceNotFoundException;
import com.chatbot.whatsapp.repository.ConversationRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConversationService {

    private final ConversationRepository conversationRepository;

    public ConversationService(ConversationRepository conversationRepository) {
        this.conversationRepository = conversationRepository;
    }

    /**
     * Retorna a conversa ativa mais recente do cliente, ou cria uma nova
     * caso todas estejam encerradas. Isso evita criar uma conversa nova
     * a cada mensagem, mantendo o historico agrupado enquanto o cliente
     * estiver "no meio" de um atendimento.
     *
     * <p>A regra de quando uma conversa deve ser encerrada automaticamente
     * (ex.: apos X horas de inatividade) ainda depende de definicao de
     * negocio - PENDENTE DE DEFINICAO COM O CLIENTE.</p>
     */
    @Transactional
    public Conversation getOrCreateActiveConversation(Customer customer) {
        return conversationRepository
                .findFirstByCustomerAndStatusNotOrderByStartedAtDesc(customer, ConversationStatus.CLOSED)
                .orElseGet(() -> conversationRepository.save(
                        Conversation.builder()
                                .customer(customer)
                                .status(ConversationStatus.BOT_ACTIVE)
                                .channel(ChannelType.WHATSAPP)
                                .lastInteractionAt(Instant.now())
                                .build()));
    }

    @Transactional(readOnly = true)
    public Conversation findById(Long id) {
        return conversationRepository.findOneById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Conversa nao encontrada: id=" + id));
    }

    @Transactional
    public void touch(Conversation conversation, String lastContext) {
        conversation.setLastInteractionAt(Instant.now());
        conversation.setContext(lastContext);
        conversationRepository.save(conversation);
    }

    @Transactional
    public void changeStatus(Conversation conversation, ConversationStatus status) {
        conversation.setStatus(status);
        conversationRepository.save(conversation);
    }
}
