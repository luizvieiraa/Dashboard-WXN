package com.chatbot.whatsapp.service.triage;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class HumanRequestDetectorTest {

    private final HumanRequestDetector detector = new HumanRequestDetector();

    @ParameterizedTest
    @ValueSource(strings = {
            "Quero falar com um humano",
            "Prefiro conversar com uma pessoa",
            "Posso falar com alguém?",
            "Quero atendimento humano",
            "Me transfere pra um atendente",
            "Preciso de um atendente",
            "Chama uma pessoa real, por favor",
            "Quero uma pessoa de verdade",
            "Não quero falar com robô",
            "FALAR COM O OPERADOR"
    })
    void deveDetectarPedidoDeAtendimentoHumano(String message) {
        assertThat(detector.isHumanRequest(message)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Maria Pessoa",
            "Pessoa & Filhos Advocacia",
            "Quero saber o preço para uma pessoa física",
            "Sou atendente de uma loja e quero automatizar o WhatsApp",
            "Olá, bom dia",
            "Quero contratar um chatbot"
    })
    void naoDeveConfundirRespostasDaTriagemComPedidoDeAtendente(String message) {
        assertThat(detector.isHumanRequest(message)).isFalse();
    }
}
