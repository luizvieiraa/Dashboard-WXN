package com.chatbot.whatsapp.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "Indicadores consolidados para o dashboard operacional")
public record DashboardSummaryResponse(
        long totalCustomers,
        long totalConversations,
        long totalMessages,
        long botActive,
        long collectingInformation,
        long qualified,
        long waitingHuman,
        long humanActive,
        long closed,
        long informationTriages,
        long complaints,
        long requiringHuman,
        Instant generatedAt
) {
}
