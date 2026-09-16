package com.chatbot.whatsapp.controller;

import com.chatbot.whatsapp.dto.response.HealthResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Health check simples da aplicacao, pensado para ser usado por scripts de
 * teste manual, pela plataforma de deploy e pela pipeline de CI.
 *
 * <p>Complementa (nao substitui) o {@code /actuator/health} padrao do Spring
 * Boot, que tambem esta habilitado.</p>
 */
@RestController
@RequestMapping("/api/v1/health")
@Tag(name = "Health", description = "Verificacao de disponibilidade da API")
public class HealthController {

    @GetMapping
    @Operation(summary = "Verifica se a API esta no ar",
            description = "Retorna 200 com status UP quando a aplicacao esta rodando.")
    public ResponseEntity<HealthResponse> health() {
        return ResponseEntity.ok(HealthResponse.up("whatsapp-chatbot-api"));
    }
}
