package com.chatbot.whatsapp.integration.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class AiPropertiesTest {

    private static AiProperties bind(Map<String, String> values) {
        return new Binder(new MapConfigurationPropertySource(values))
                .bind("ai", AiProperties.class)
                .get();
    }

    @Test
    void deveLerOsLimitesDoAssistenteVirtualDasVariaveisDeAmbiente() {
        AiProperties properties = bind(Map.of(
                "ai.enabled", "true",
                "ai.model", "modelo",
                "ai.history-messages", "7",
                "ai.max-input-chars", "500",
                "ai.min-confidence", "0.75",
                "ai.knowledge-base", "file:/dados/base.md"));

        assertThat(properties.enabled()).isTrue();
        assertThat(properties.model()).isEqualTo("modelo");
        assertThat(properties.effectiveHistoryMessages()).isEqualTo(7);
        assertThat(properties.effectiveMaxInputChars()).isEqualTo(500);
        assertThat(properties.effectiveMinConfidence()).isEqualTo(0.75);
        assertThat(properties.effectiveKnowledgeBase()).isEqualTo("file:/dados/base.md");
    }

    @Test
    void deveAplicarPadroesSegurosQuandoOsValoresFaltaremOuForemInvalidos() {
        AiProperties properties = bind(Map.of(
                "ai.enabled", "false",
                "ai.history-messages", "500",
                "ai.min-confidence", "3"));

        assertThat(properties.effectiveHistoryMessages()).isEqualTo(30);
        assertThat(properties.effectiveMaxInputChars()).isEqualTo(1000);
        assertThat(properties.effectiveMinConfidence()).isEqualTo(0.6);
        assertThat(properties.effectiveKnowledgeBase()).isEqualTo("classpath:assistant/knowledge-base.md");
    }
}
