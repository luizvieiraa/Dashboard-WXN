package com.chatbot.whatsapp.service.chatbot;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ChatbotResponseServiceTest {

    private final ChatbotResponseService service = new ChatbotResponseService();

    @Test
    void devolveSaudacaoParaGreeting() {
        assertThat(service.generateReply(ChatIntent.GREETING)).contains("Olá");
    }

    @Test
    void devolveMensagemDeFallbackParaUnknown() {
        assertThat(service.generateReply(ChatIntent.UNKNOWN)).contains("Não entendi");
    }
}
