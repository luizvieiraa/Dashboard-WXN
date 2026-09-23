package com.chatbot.whatsapp.integration.whatsapp;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Impede que o modo real suba parcialmente configurado e perca mensagens. */
@Component
@ConditionalOnProperty(prefix = "whatsapp", name = "enabled", havingValue = "true")
public class WhatsAppConfigurationValidator {

    private final WhatsAppProperties properties;

    public WhatsAppConfigurationValidator(WhatsAppProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void validate() {
        List<String> missing = new ArrayList<>();
        require(properties.apiUrl(), "WHATSAPP_API_URL", missing);
        require(properties.apiVersion(), "WHATSAPP_API_VERSION", missing);
        require(properties.accessToken(), "WHATSAPP_ACCESS_TOKEN", missing);
        require(properties.phoneNumberId(), "WHATSAPP_PHONE_NUMBER_ID", missing);
        requirePhone(properties.contactPhone(), missing);
        require(properties.webhookVerifyToken(), "WHATSAPP_WEBHOOK_VERIFY_TOKEN", missing);
        require(properties.appSecret(), "WHATSAPP_APP_SECRET", missing);
        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "Integracao real do WhatsApp habilitada sem configurar: "
                            + String.join(", ", missing));
        }
    }

    private void require(String value, String name, List<String> missing) {
        if (value == null || value.isBlank()) {
            missing.add(name);
        }
    }

    private void requirePhone(String value, List<String> missing) {
        if (value == null || !value.matches("\\d{8,15}")) {
            missing.add("WHATSAPP_CONTACT_PHONE (8 a 15 digitos)");
        }
    }
}
