package com.chatbot.whatsapp.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.chatbot.whatsapp.entity.enums.MessageStatus;
import org.junit.jupiter.api.Test;

class WhatsAppStatusServiceTest {

    @Test
    void oStatusSoAvancaMesmoComNotificacoesForaDeOrdem() {
        assertThat(WhatsAppStatusService.canTransition(MessageStatus.SENT, MessageStatus.DELIVERED)).isTrue();
        assertThat(WhatsAppStatusService.canTransition(MessageStatus.DELIVERED, MessageStatus.READ)).isTrue();
        assertThat(WhatsAppStatusService.canTransition(MessageStatus.SENT, MessageStatus.READ)).isTrue();

        assertThat(WhatsAppStatusService.canTransition(MessageStatus.READ, MessageStatus.DELIVERED)).isFalse();
        assertThat(WhatsAppStatusService.canTransition(MessageStatus.DELIVERED, MessageStatus.SENT)).isFalse();
        assertThat(WhatsAppStatusService.canTransition(MessageStatus.READ, MessageStatus.READ)).isFalse();
    }

    @Test
    void falhaSoValeAntesDaEntregaENaoEDesfeita() {
        assertThat(WhatsAppStatusService.canTransition(MessageStatus.SENT, MessageStatus.FAILED)).isTrue();
        assertThat(WhatsAppStatusService.canTransition(MessageStatus.DELIVERED, MessageStatus.FAILED)).isFalse();
        assertThat(WhatsAppStatusService.canTransition(MessageStatus.FAILED, MessageStatus.DELIVERED)).isFalse();
    }
}
