package com.chatbot.whatsapp.integration.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.DefaultResourceLoader;

class AssistantResourcesTest {

    private static AiProperties withKnowledgeBase(String location) {
        return new AiProperties(true, "https://ai.example.test", "chave", "modelo", 5, 200, 10, 1000, 0.6, location);
    }

    @Test
    void deveCarregarPromptEmpacotadoEBaseVaziaPorPadrao() {
        AssistantResources resources = AssistantResources.fromClasspath(withKnowledgeBase(null));

        assertThat(resources.systemPrompt())
                .contains("assistente virtual da WXN", "Nunca invente preços", "<dados_do_atendimento>");
        // A base padrao so tem comentarios de manutencao, que nao sao enviados.
        assertThat(resources.knowledgeBase()).isEmpty();
        assertThat(resources.instructions()).contains("(nenhuma informação cadastrada)");
    }

    @Test
    void deveLerBaseExternaSemComentariosELimitarTamanho(@TempDir Path folder) throws Exception {
        Path file = folder.resolve("base.md");
        Files.writeString(file, "<!-- nota interna -->\n## Horario\nSegunda a sexta.\n"
                + "x".repeat(AssistantResources.MAX_KNOWLEDGE_BASE_CHARS + 50));

        AssistantResources resources = new AssistantResources(
                withKnowledgeBase(file.toUri().toString()), new DefaultResourceLoader());

        assertThat(resources.knowledgeBase()).startsWith("## Horario").doesNotContain("nota interna");
        assertThat(resources.knowledgeBase()).hasSize(AssistantResources.MAX_KNOWLEDGE_BASE_CHARS);
    }

    @Test
    void deveIgnorarBaseAusenteOuComLocalNaoPermitido() {
        AssistantResources missing = new AssistantResources(
                withKnowledgeBase("classpath:assistant/nao-existe.md"), new DefaultResourceLoader());
        AssistantResources remote = new AssistantResources(
                withKnowledgeBase("http://exemplo.test/base.md"), new DefaultResourceLoader());

        assertThat(missing.knowledgeBase()).isEmpty();
        assertThat(remote.knowledgeBase()).isEmpty();
    }
}
