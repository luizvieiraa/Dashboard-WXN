package com.chatbot.whatsapp.service;

import com.chatbot.whatsapp.dto.response.DashboardSummaryResponse;
import com.chatbot.whatsapp.dto.response.DashboardTriageResponse;
import com.chatbot.whatsapp.entity.Triage;
import com.chatbot.whatsapp.entity.enums.ConversationStatus;
import com.chatbot.whatsapp.entity.enums.TriageCategory;
import com.chatbot.whatsapp.repository.ConversationRepository;
import com.chatbot.whatsapp.repository.CustomerRepository;
import com.chatbot.whatsapp.repository.MessageRepository;
import com.chatbot.whatsapp.repository.TriageRepository;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {

    private final CustomerRepository customerRepository;
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final TriageRepository triageRepository;

    public DashboardService(CustomerRepository customerRepository,
                            ConversationRepository conversationRepository,
                            MessageRepository messageRepository,
                            TriageRepository triageRepository) {
        this.customerRepository = customerRepository;
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.triageRepository = triageRepository;
    }

    @Transactional(readOnly = true)
    public DashboardSummaryResponse summary() {
        return new DashboardSummaryResponse(
                customerRepository.count(),
                conversationRepository.count(),
                messageRepository.count(),
                conversationRepository.countByStatus(ConversationStatus.BOT_ACTIVE),
                conversationRepository.countByStatus(ConversationStatus.COLLECTING_INFORMATION),
                conversationRepository.countByStatus(ConversationStatus.QUALIFIED),
                conversationRepository.countByStatus(ConversationStatus.WAITING_HUMAN),
                conversationRepository.countByStatus(ConversationStatus.HUMAN_ACTIVE),
                conversationRepository.countByStatus(ConversationStatus.CLOSED),
                triageRepository.countByCategory(TriageCategory.INFORMATION),
                triageRepository.countByCategory(TriageCategory.COMPLAINT),
                triageRepository.countByRequiresHumanTrue(),
                Instant.now()
        );
    }

    @Transactional(readOnly = true)
    public List<DashboardTriageResponse> listTriages(
            TriageCategory category,
            ConversationStatus status,
            Boolean requiresHuman) {
        return triageRepository.findAllByOrderByUpdatedAtDesc().stream()
                .filter(triage -> category == null || triage.getCategory() == category)
                .filter(triage -> status == null || triage.getConversation().getStatus() == status)
                .filter(triage -> requiresHuman == null || triage.isRequiresHuman() == requiresHuman)
                .sorted(Comparator
                        .comparingInt((Triage triage) -> priorityWeight(triage)).reversed()
                        .thenComparing(triage -> triage.getConversation().getLastInteractionAt(),
                                Comparator.reverseOrder()))
                .map(DashboardTriageResponse::fromEntity)
                .toList();
    }

    private int priorityWeight(Triage triage) {
        return switch (triage.getPriority()) {
            case LOW -> 0;
            case MEDIUM -> 1;
            case HIGH -> 2;
            case URGENT -> 3;
        };
    }
}
