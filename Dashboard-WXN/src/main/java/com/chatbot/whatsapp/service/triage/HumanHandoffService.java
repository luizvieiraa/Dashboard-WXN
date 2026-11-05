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

/**
 * Encaminha uma conversa automatizada para a fila de atendimento humano.
 *
 * <p>Usa os estados que ja existem: a conversa vai para {@code WAITING_HUMAN}
 * (o bot para de responder somente nela) e a triagem passa a exigir humano.
 * Reclamacoes continuam com o tratamento do {@link ComplaintEscalationService};
 * os demais motivos preservam a categoria e o resumo ja coletados e acrescentam
 * o motivo do encaminhamento.</p>
 */
@Service
public class HumanHandoffService {

    public static final String HANDOFF_REPLY =
            "Entendi. Vou encaminhar sua conversa para uma pessoa da nossa equipe, que vai continuar o "
                    + "atendimento por aqui. Se quiser, você já pode enviar mais detalhes.";

    private static final int MAX_SUBJECT_LENGTH = 255;

    private final TriageRepository triageRepository;
    private final ConversationService conversationService;
    private final ComplaintEscalationService complaintEscalationService;

    public HumanHandoffService(TriageRepository triageRepository,
                               ConversationService conversationService,
                               ComplaintEscalationService complaintEscalationService) {
        this.triageRepository = triageRepository;
        this.conversationService = conversationService;
        this.complaintEscalationService = complaintEscalationService;
    }

    /** @return a mensagem que o bot envia ao cliente ao encaminhar a conversa. */
    @Transactional
    public String handoff(Conversation conversation, HandoffReason reason, String customerMessage) {
        if (reason == HandoffReason.COMPLAINT) {
            complaintEscalationService.escalate(conversation, customerMessage);
            return ComplaintEscalationService.HANDOFF_REPLY;
        }

        String message = customerMessage.trim();
        Triage triage = triageRepository.findByConversationId(conversation.getId())
                .orElseGet(() -> Triage.builder()
                        .conversation(conversation)
                        .category(TriageCategory.OTHER)
                        .customerNeed(message)
                        .build());

        triage.setRequiresHuman(true);
        if (reason == HandoffReason.SENSITIVE_TOPIC) {
            triage.setPriority(TriagePriority.HIGH);
        }
        if (triage.getSubject() == null || triage.getSubject().isBlank()) {
            triage.setSubject(ComplaintEscalationService.truncate(message, MAX_SUBJECT_LENGTH));
        }
        String note = "Encaminhada para atendimento humano (" + reason.description() + ").";
        triage.setSummary(triage.getSummary() == null || triage.getSummary().isBlank()
                ? note + " Última mensagem do cliente: " + message
                : triage.getSummary() + " " + note);
        // A coleta guiada para aqui: o atendente completa o que faltar.
        triage.getMissingInformation().clear();

        conversationService.changeStatus(conversation, ConversationStatus.WAITING_HUMAN);
        triageRepository.save(triage);
        return HANDOFF_REPLY;
    }
}
