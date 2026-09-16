package com.chatbot.whatsapp.integration.whatsapp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Implementacao padrao (e unica, por enquanto) de {@link WhatsAppClient}.
 *
 * <p>Nao existe ainda uma integracao real configurada com o WhatsApp
 * (PENDENTE DE DEFINICAO COM O CLIENTE - ver README). Esta implementacao
 * apenas registra em log a mensagem que seria enviada, permitindo testar
 * todo o fluxo do chatbot de ponta a ponta sem depender de credenciais
 * externas. Quando {@code whatsapp.enabled=true} e uma implementacao real
 * existir, ela deve substituir esta classe como o bean primario de
 * {@link WhatsAppClient}.</p>
 */
@Component
public class MockWhatsAppSender implements WhatsAppClient {

    private static final Logger log = LoggerFactory.getLogger(MockWhatsAppSender.class);

    private final WhatsAppProperties properties;

    public MockWhatsAppSender(WhatsAppProperties properties) {
        this.properties = properties;
    }

    @Override
    public void sendMessage(String phoneNumber, String text) {
        if (properties.enabled()) {
            log.warn("whatsapp.enabled=true mas nenhuma integracao real esta implementada ainda. "
                    + "Mensagem para {} sera apenas logada.", phoneNumber);
        }
        log.info("[WHATSAPP MOCK] Para: {} | Mensagem: \"{}\"", phoneNumber, text);
    }
}
