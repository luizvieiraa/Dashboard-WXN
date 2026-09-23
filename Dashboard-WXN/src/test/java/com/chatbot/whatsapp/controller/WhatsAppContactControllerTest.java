package com.chatbot.whatsapp.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.chatbot.whatsapp.dto.response.WhatsAppContactResponse;
import com.chatbot.whatsapp.integration.whatsapp.WhatsAppProperties;
import org.junit.jupiter.api.Test;

class WhatsAppContactControllerTest {

    @Test
    void deveCriarLinkPublicoQuandoIntegracaoEstiverAtiva() {
        WhatsAppContactController controller = new WhatsAppContactController(properties(true, "5511999999999"));

        WhatsAppContactResponse response = controller.contact();

        assertThat(response.available()).isTrue();
        assertThat(response.url())
                .startsWith("https://wa.me/5511999999999?text=")
                .contains("Ol%C3%A1%21%20Quero%20conversar");
    }

    @Test
    void naoDeveExporLinkSemIntegracaoAtivaOuNumeroValido() {
        assertThat(new WhatsAppContactController(properties(false, "5511999999999"))
                .contact().available()).isFalse();
        assertThat(new WhatsAppContactController(properties(true, "numero-invalido"))
                .contact().available()).isFalse();
    }

    private WhatsAppProperties properties(boolean enabled, String contactPhone) {
        return new WhatsAppProperties(
                enabled,
                "https://graph.facebook.com",
                "vXX.0",
                "token",
                "123",
                contactPhone,
                "verify-token",
                "app-secret"
        );
    }
}
