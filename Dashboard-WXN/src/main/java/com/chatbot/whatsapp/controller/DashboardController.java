package com.chatbot.whatsapp.controller;

import com.chatbot.whatsapp.dto.response.DashboardSummaryResponse;
import com.chatbot.whatsapp.dto.response.DashboardTriageResponse;
import com.chatbot.whatsapp.entity.enums.ConversationStatus;
import com.chatbot.whatsapp.entity.enums.TriageCategory;
import com.chatbot.whatsapp.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
@Tag(name = "Dashboard", description = "Indicadores e triagens para a operação da WXN")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/summary")
    @Operation(summary = "Retorna os principais indicadores operacionais")
    public ResponseEntity<DashboardSummaryResponse> summary() {
        return ResponseEntity.ok(dashboardService.summary());
    }

    @GetMapping("/triages")
    @Operation(summary = "Lista triagens com filtros opcionais")
    public ResponseEntity<List<DashboardTriageResponse>> triages(
            @RequestParam(required = false) TriageCategory category,
            @RequestParam(required = false) ConversationStatus status,
            @RequestParam(required = false) Boolean requiresHuman) {
        return ResponseEntity.ok(dashboardService.listTriages(category, status, requiresHuman));
    }
}
