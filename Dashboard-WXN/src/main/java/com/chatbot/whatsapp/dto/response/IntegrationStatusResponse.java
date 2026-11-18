package com.chatbot.whatsapp.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "Estado das integracoes externas. Nunca inclui credenciais.")
public record IntegrationStatusResponse(WhatsApp whatsapp, Assistant assistant) {

    @Schema(description = "Canal WhatsApp")
    public record WhatsApp(
            @Schema(description = "META_CLOUD_API quando o envio real esta ligado; SIMULADO caso contrario",
                    example = "SIMULADO")
            String mode,
            @Schema(description = "true quando o envio mais recente falhou")
            boolean failing,
            Instant lastSuccessAt,
            Instant lastFailureAt,
            @Schema(description = "Status HTTP, codigo da Meta e explicacao fixa; nunca o texto livre do provedor",
                    example = "HTTP 400, codigo 131030: destinatario fora da lista de numeros de teste")
            String lastFailure
    ) {
    }

    @Schema(description = "Assistente virtual (IA)")
    public record Assistant(
            boolean enabled,
            @Schema(description = "true quando AI_API_URL, AI_API_KEY e AI_MODEL estao preenchidos")
            boolean configured,
            @Schema(description = "Host do endpoint configurado", example = "generativelanguage.googleapis.com")
            String host,
            @Schema(example = "gemini-3.5-flash-lite")
            String model,
            @Schema(description = "true quando a chamada mais recente falhou e o fluxo local respondeu")
            boolean failing,
            Instant lastSuccessAt,
            Instant lastFailureAt,
            @Schema(description = "Status HTTP, codigo do erro e explicacao fixa; nunca o texto livre do provedor",
                    example = "HTTP 429 (RESOURCE_EXHAUSTED): cota ou limite de requisicoes esgotado")
            String lastFailure
    ) {
    }
}
