package com.chatbot.whatsapp.service.triage;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ComplaintDetectorTest {

    private final ComplaintDetector detector = new ComplaintDetector();

    @ParameterizedTest
    @ValueSource(strings = {
            "Quero fazer uma reclamação",
            "Estou muito insatisfeito com o serviço",
            "O sistema não funciona",
            "Houve uma cobrança indevida",
            "Quero falar com um atendente"
    })
    void deveDetectarReclamacoes(String message) {
        assertThat(detector.isComplaint(message)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Quero informações sobre preços",
            "Gostaria de contratar um chatbot",
            "Qual é o horário de atendimento?"
    })
    void naoDeveClassificarSolicitacoesComunsComoReclamacao(String message) {
        assertThat(detector.isComplaint(message)).isFalse();
    }
}
