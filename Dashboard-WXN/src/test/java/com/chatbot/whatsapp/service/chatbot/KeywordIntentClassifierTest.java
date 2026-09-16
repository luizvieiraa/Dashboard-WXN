package com.chatbot.whatsapp.service.chatbot;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class KeywordIntentClassifierTest {

    private final KeywordIntentClassifier classifier = new KeywordIntentClassifier();

    @ParameterizedTest
    @CsvSource({
            "Olá, GREETING",
            "oi tudo bem?, GREETING",
            "Bom dia!, GREETING",
            "Qual o preço do produto X?, PRICE_INQUIRY",
            "Quanto custa o plano premium, PRICE_INQUIRY",
            "ajuda, HELP",
            "menu, HELP",
            "xablau contexto aleatorio, UNKNOWN"
    })
    void deveClassificarIntencaoCorretamente(String mensagem, ChatIntent intentEsperada) {
        assertThat(classifier.classify(mensagem)).isEqualTo(intentEsperada);
    }
}
