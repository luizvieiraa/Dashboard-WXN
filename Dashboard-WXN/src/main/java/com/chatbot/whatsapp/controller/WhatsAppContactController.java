package com.chatbot.whatsapp.controller;

import com.chatbot.whatsapp.dto.response.WhatsAppContactResponse;
import com.chatbot.whatsapp.integration.whatsapp.WhatsAppProperties;
import com.chatbot.whatsapp.util.PhoneNumbers;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Expõe somente o link público de contato; nenhuma credencial é enviada ao navegador. */
@RestController
@RequestMapping("/api/v1/whatsapp")
public class WhatsAppContactController {

    private static final String INITIAL_MESSAGE =
            "Olá! Quero conversar com o assistente da WXN.";

    private final WhatsAppProperties properties;

    public WhatsAppContactController(WhatsAppProperties properties) {
        this.properties = properties;
    }

    @GetMapping("/contact")
    public WhatsAppContactResponse contact() {
        Optional<String> link = PhoneNumbers.whatsAppLink(properties.contactPhone());
        if (!properties.enabled() || link.isEmpty()) {
            return new WhatsAppContactResponse(false, null);
        }

        String message = URLEncoder.encode(INITIAL_MESSAGE, StandardCharsets.UTF_8)
                .replace("+", "%20");
        return new WhatsAppContactResponse(true, link.get() + "?text=" + message);
    }
}
