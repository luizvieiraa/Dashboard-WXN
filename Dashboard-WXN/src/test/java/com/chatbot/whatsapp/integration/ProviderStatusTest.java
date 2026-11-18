package com.chatbot.whatsapp.integration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ProviderStatusTest {

    @Test
    void oUltimoResultadoRegistradoDefineOEstadoMesmoNoMesmoInstante() {
        ProviderStatus status = new ProviderStatus();

        status.recordSuccess();
        status.recordFailure("HTTP 500: instabilidade no provedor");
        assertThat(status.snapshot().failing()).isTrue();

        status.recordSuccess();
        assertThat(status.snapshot().failing()).isFalse();
        assertThat(status.snapshot().lastFailure()).isEqualTo("HTTP 500: instabilidade no provedor");
    }
}
