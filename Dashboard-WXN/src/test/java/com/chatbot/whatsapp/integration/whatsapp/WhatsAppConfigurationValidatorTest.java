package com.chatbot.whatsapp.integration.whatsapp;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class WhatsAppConfigurationValidatorTest {

    @Test
    void deveAceitarConfiguracaoCompleta() {
        WhatsAppProperties properties = new WhatsAppProperties(
                true, "https://graph.facebook.com", "v21.0", "token", "123", "5511999999999",
                "verify-token", "app-secret");

        assertThatCode(() -> new WhatsAppConfigurationValidator(properties).validate())
                .doesNotThrowAnyException();
    }

    @Test
    void deveFalharRapidoQuandoFaltaremCredenciais() {
        WhatsAppProperties properties = new WhatsAppProperties(
                true, "https://graph.facebook.com", "v21.0", "", "123", "", "", "");

        assertThatThrownBy(() -> new WhatsAppConfigurationValidator(properties).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("WHATSAPP_ACCESS_TOKEN")
                .hasMessageContaining("WHATSAPP_CONTACT_PHONE")
                .hasMessageContaining("WHATSAPP_WEBHOOK_VERIFY_TOKEN")
                .hasMessageContaining("WHATSAPP_APP_SECRET");
    }
}
