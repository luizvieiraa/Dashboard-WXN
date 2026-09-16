package com.chatbot.whatsapp.dto.response;

import java.time.Instant;

/**
 * Resposta simples do health check da aplicacao (endpoint de negocio,
 * distinto do /actuator/health que tambem fica disponivel para uso por
 * ferramentas de infraestrutura).
 */
public record HealthResponse(String status, String service, Instant timestamp) {

    public static HealthResponse up(String service) {
        return new HealthResponse("UP", service, Instant.now());
    }
}
