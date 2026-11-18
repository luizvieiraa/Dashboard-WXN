package com.chatbot.whatsapp.controller;

import com.chatbot.whatsapp.dto.response.IntegrationStatusResponse;
import com.chatbot.whatsapp.integration.ProviderStatus;
import com.chatbot.whatsapp.integration.ai.AiClient;
import com.chatbot.whatsapp.integration.ai.AiProperties;
import com.chatbot.whatsapp.integration.whatsapp.WhatsAppClient;
import com.chatbot.whatsapp.integration.whatsapp.WhatsAppProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Mostra ao simulador se a IA e o WhatsApp estao ligados e como terminou a
 * chamada mais recente a cada um, para diagnosticar sem abrir os logs.
 */
@RestController
@RequestMapping("/api/v1/health/integrations")
@Tag(name = "Health", description = "Verificacao de disponibilidade da API")
public class IntegrationStatusController {

    static final String MODE_REAL = "META_CLOUD_API";
    static final String MODE_SIMULATED = "SIMULADO";

    private final AiClient aiClient;
    private final AiProperties aiProperties;
    private final WhatsAppClient whatsAppClient;
    private final WhatsAppProperties whatsAppProperties;

    public IntegrationStatusController(AiClient aiClient,
                                       AiProperties aiProperties,
                                       WhatsAppClient whatsAppClient,
                                       WhatsAppProperties whatsAppProperties) {
        this.aiClient = aiClient;
        this.aiProperties = aiProperties;
        this.whatsAppClient = whatsAppClient;
        this.whatsAppProperties = whatsAppProperties;
    }

    @GetMapping
    @Operation(summary = "Estado das integracoes com a IA e o WhatsApp",
            description = "Indica se cada integracao esta ligada e o resultado da chamada mais recente "
                    + "desde que a aplicacao iniciou. Nunca devolve tokens ou chaves.")
    public IntegrationStatusResponse status() {
        ProviderStatus.Snapshot whatsapp = whatsAppClient.status();
        ProviderStatus.Snapshot assistant = aiClient.status();
        return new IntegrationStatusResponse(
                new IntegrationStatusResponse.WhatsApp(
                        whatsAppProperties.enabled() ? MODE_REAL : MODE_SIMULATED,
                        whatsapp.failing(),
                        whatsapp.lastSuccessAt(),
                        whatsapp.lastFailureAt(),
                        whatsapp.lastFailure()),
                new IntegrationStatusResponse.Assistant(
                        aiProperties.enabled(),
                        aiClient.isAvailable(),
                        host(aiProperties.apiUrl()),
                        blankToNull(aiProperties.model()),
                        assistant.failing(),
                        assistant.lastSuccessAt(),
                        assistant.lastFailureAt(),
                        assistant.lastFailure()));
    }

    /** So o host: o caminho e a query do endpoint nao interessam ao painel. */
    private static String host(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            return URI.create(url.trim()).getHost();
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
