package com.chatbot.whatsapp.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.chatbot.whatsapp.dto.response.IntegrationStatusResponse;
import com.chatbot.whatsapp.integration.ProviderStatus;
import com.chatbot.whatsapp.integration.ai.AiClient;
import com.chatbot.whatsapp.integration.ai.AiProperties;
import com.chatbot.whatsapp.integration.whatsapp.WhatsAppClient;
import com.chatbot.whatsapp.integration.whatsapp.WhatsAppProperties;
import com.chatbot.whatsapp.integration.whatsapp.WhatsAppSendResult;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class IntegrationStatusControllerTest {

    private static final String GEMINI_URL =
            "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions";

    @Test
    void deveMostrarFalhaDaIaSemExporCredenciais() {
        ProviderStatus aiStatus = new ProviderStatus();
        aiStatus.recordFailure("HTTP 403: The Gemini API free tier is not available for this project.");
        AiProperties ai = new AiProperties(true, GEMINI_URL, "chave-secreta", "gemini-3.5-flash-lite", 20, 400);

        IntegrationStatusResponse response = controller(ai, aiStatus, whatsapp(false)).status();

        assertThat(response.assistant().enabled()).isTrue();
        assertThat(response.assistant().configured()).isTrue();
        assertThat(response.assistant().host()).isEqualTo("generativelanguage.googleapis.com");
        assertThat(response.assistant().model()).isEqualTo("gemini-3.5-flash-lite");
        assertThat(response.assistant().failing()).isTrue();
        assertThat(response.assistant().lastFailure()).startsWith("HTTP 403");
        assertThat(response.toString()).doesNotContain("chave-secreta", "token-secreto", "app-secret");
    }

    @Test
    void deveIndicarModoSimuladoEIaDesligada() {
        AiProperties ai = new AiProperties(false, "", "", "", 10, 300);

        IntegrationStatusResponse response = controller(ai, new ProviderStatus(), whatsapp(false)).status();

        assertThat(response.whatsapp().mode()).isEqualTo(IntegrationStatusController.MODE_SIMULATED);
        assertThat(response.whatsapp().failing()).isFalse();
        assertThat(response.assistant().enabled()).isFalse();
        assertThat(response.assistant().configured()).isFalse();
        assertThat(response.assistant().host()).isNull();
        assertThat(response.assistant().model()).isNull();
    }

    @Test
    void deveIndicarEnvioRealPelaMeta() {
        AiProperties ai = new AiProperties(false, "", "", "", 10, 300);

        IntegrationStatusResponse response = controller(ai, new ProviderStatus(), whatsapp(true)).status();

        assertThat(response.whatsapp().mode()).isEqualTo(IntegrationStatusController.MODE_REAL);
    }

    private IntegrationStatusController controller(AiProperties ai,
                                                   ProviderStatus aiStatus,
                                                   WhatsAppProperties whatsapp) {
        AiClient aiClient = new AiClient() {
            @Override
            public Optional<String> generateReply(String customerMessage, String customerContext) {
                return Optional.empty();
            }

            @Override
            public boolean isAvailable() {
                return ai.enabled() && !ai.apiKey().isBlank();
            }

            @Override
            public ProviderStatus.Snapshot status() {
                return aiStatus.snapshot();
            }
        };
        WhatsAppClient whatsAppClient = (phone, text) -> WhatsAppSendResult.sent(null);
        return new IntegrationStatusController(aiClient, ai, whatsAppClient, whatsapp);
    }

    private WhatsAppProperties whatsapp(boolean enabled) {
        return new WhatsAppProperties(enabled, "https://graph.facebook.com", "vXX.0", "token-secreto",
                "123", "5511999999999", "verify-token", "app-secret");
    }
}
