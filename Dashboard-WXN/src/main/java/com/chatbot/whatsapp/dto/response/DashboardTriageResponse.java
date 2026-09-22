package com.chatbot.whatsapp.dto.response;

import com.chatbot.whatsapp.entity.Triage;
import com.chatbot.whatsapp.entity.enums.ConversationStatus;
import com.chatbot.whatsapp.entity.enums.TriageCategory;
import com.chatbot.whatsapp.entity.enums.TriagePriority;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "Triagem pronta para exibição no dashboard")
public record DashboardTriageResponse(
        Long triageId,
        Long conversationId,
        String customerName,
        String customerPhone,
        String companyName,
        TriageCategory category,
        TriagePriority priority,
        String subject,
        String customerNeed,
        String summary,
        boolean requiresHuman,
        ConversationStatus conversationStatus,
        String assignedTo,
        Instant lastInteractionAt,
        Instant updatedAt
) {
    public static DashboardTriageResponse fromEntity(Triage triage) {
        var conversation = triage.getConversation();
        var customer = conversation.getCustomer();
        return new DashboardTriageResponse(
                triage.getId(),
                conversation.getId(),
                customer.getName(),
                customer.getPhoneNumber(),
                customer.getCompanyName(),
                triage.getCategory(),
                triage.getPriority(),
                triage.getSubject(),
                triage.getCustomerNeed(),
                triage.getSummary(),
                triage.isRequiresHuman(),
                conversation.getStatus(),
                conversation.getAssignedTo(),
                conversation.getLastInteractionAt(),
                triage.getUpdatedAt()
        );
    }
}
