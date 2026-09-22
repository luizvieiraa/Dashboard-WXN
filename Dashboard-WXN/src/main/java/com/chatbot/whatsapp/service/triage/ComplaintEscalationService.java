package com.chatbot.whatsapp.service.triage;

import com.chatbot.whatsapp.entity.Conversation;
import com.chatbot.whatsapp.entity.Triage;
import com.chatbot.whatsapp.entity.enums.ConversationStatus;
import com.chatbot.whatsapp.entity.enums.TriageCategory;
import com.chatbot.whatsapp.entity.enums.TriagePriority;
import com.chatbot.whatsapp.repository.TriageRepository;
import com.chatbot.whatsapp.service.ConversationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Registra uma reclamacao e transfere a conversa para a fila humana. */
@Service
public class ComplaintEscalationService {

    public static final String HANDOFF_REPLY =
            "Sinto muito pelo ocorrido. Encaminhei sua conversa para um atendente humano, que continuará o atendimento.";

    private final TriageRepository triageRepository;
    private final ConversationService conversationService;

    public ComplaintEscalationService(TriageRepository triageRepository,
                                      ConversationService conversationService) {
        this.triageRepository = triageRepository;
        this.conversationService = conversationService;
    }

    @Transactional
    public Triage escalate(Conversation conversation, String complaint) {
        String normalizedComplaint = complaint.trim();
        Triage triage = triageRepository.findByConversationId(conversation.getId())
                .orElseGet(() -> Triage.builder()
                        .conversation(conversation)
                        .customerNeed(normalizedComplaint)
                        .build());

        triage.setCategory(TriageCategory.COMPLAINT);
        triage.setPriority(TriagePriority.HIGH);
        triage.setRequiresHuman(true);
        triage.setSubject(normalizedComplaint);
        triage.setSummary("Reclamação encaminhada para atendimento humano: " + normalizedComplaint);
        triage.getMissingInformation().clear();

        conversationService.changeStatus(conversation, ConversationStatus.WAITING_HUMAN);
        return triageRepository.save(triage);
    }
}
