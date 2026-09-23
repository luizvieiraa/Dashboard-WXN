package com.chatbot.whatsapp.integration.whatsapp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Implementacao de {@link WhatsAppClient} usada quando o envio real esta
 * desligado ({@code whatsapp.enabled=false}, o padrao).
 *
 * <p>Apenas registra em log a mensagem que seria enviada, permitindo rodar e
 * testar todo o fluxo do chatbot de ponta a ponta - inclusive pelo simulador em
 * {@code /simulator} - sem depender de credenciais da Meta. Com
 * {@code whatsapp.enabled=true}, o {@link MetaWhatsAppClient} assume o lugar
 * deste bean (as duas condicoes sao mutuamente exclusivas).</p>
 */
@Component
@ConditionalOnProperty(prefix = "whatsapp", name = "enabled", havingValue = "false", matchIfMissing = true)
public class MockWhatsAppSender implements WhatsAppClient {

    private static final Logger log = LoggerFactory.getLogger(MockWhatsAppSender.class);

    @Override
    public WhatsAppSendResult sendMessage(String phoneNumber, String text) {
        log.info("[WHATSAPP MOCK] Para: {} | Mensagem: \"{}\"", phoneNumber, text);
        return WhatsAppSendResult.sent(null);
    }
}
