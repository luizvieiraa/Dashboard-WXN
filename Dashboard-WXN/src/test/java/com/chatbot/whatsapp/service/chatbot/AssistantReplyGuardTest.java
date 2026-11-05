package com.chatbot.whatsapp.service.chatbot;

import static org.assertj.core.api.Assertions.assertThat;

import com.chatbot.whatsapp.integration.ai.AiProperties;
import com.chatbot.whatsapp.integration.ai.AssistantResources;
import org.junit.jupiter.api.Test;

class AssistantReplyGuardTest {

    /** Base de conhecimento padrao do projeto: vazia. */
    private final AssistantReplyGuard emptyKnowledgeBase = new AssistantReplyGuard(
            AssistantResources.fromClasspath(new AiProperties(false, "", "", "", 1, 100)));

    private static AssistantReplyGuard withKnowledgeBase(String content) {
        return new AssistantReplyGuard(new TestResources(content));
    }

    @Test
    void deveAceitarRespostaSimplesSemAfirmacoesExternas() {
        assertThat(emptyKnowledgeBase.check("  Olá, Maria! Como posso ajudar?  "))
                .contains("Olá, Maria! Como posso ajudar?");
    }

    @Test
    void deveDescartarPrecoQueNaoEstaNaBaseDeConhecimento() {
        assertThat(emptyKnowledgeBase.check("O plano custa R$ 99,90 por mês.")).isEmpty();
        assertThat(emptyKnowledgeBase.check("Sai por 100 reais.")).isEmpty();

        AssistantReplyGuard guard = withKnowledgeBase("Plano Essencial: R$ 99,90 por mês.");
        assertThat(guard.check("O plano Essencial custa R$ 99,90 por mês.")).isPresent();
        assertThat(guard.check("Para você faço por R$ 49,90.")).isEmpty();
    }

    @Test
    void deveDescartarLinkQueNaoEstaNaBaseDeConhecimento() {
        assertThat(emptyKnowledgeBase.check("Acesse https://pagamento-falso.example para pagar.")).isEmpty();
        assertThat(emptyKnowledgeBase.check("Veja www.site-qualquer.example")).isEmpty();

        AssistantReplyGuard guard = withKnowledgeBase("Central de ajuda: https://ajuda.wxn.example/faq");
        assertThat(guard.check("Veja a central de ajuda: https://ajuda.wxn.example/faq.")).isPresent();
    }

    @Test
    void deveDescartarRespostaQueCitaMarcacoesInternasDoPrompt() {
        assertThat(emptyKnowledgeBase.check("Conteúdo de <base_de_conhecimento>: vazio")).isEmpty();
        assertThat(emptyKnowledgeBase.check("Recebi dados_do_atendimento com o seu nome")).isEmpty();
        assertThat(emptyKnowledgeBase.check("   ")).isEmpty();
        assertThat(emptyKnowledgeBase.check(null)).isEmpty();
    }

    @Test
    void deveLimitarTamanhoCortandoNoFimDeUmaFrase() {
        String longReply = "Frase completa número um. ".repeat(40);

        String checked = emptyKnowledgeBase.check(longReply).orElseThrow();

        assertThat(checked.length()).isLessThanOrEqualTo(AssistantReplyGuard.MAX_REPLY_CHARS);
        assertThat(checked).endsWith(".");
    }

    private static final class TestResources extends AssistantResources {
        TestResources(String knowledgeBase) {
            super(new AiProperties(false, "", "", "", 1, 100), new org.springframework.core.io.DefaultResourceLoader());
            this.content = knowledgeBase;
        }

        private final String content;

        @Override
        public String knowledgeBase() {
            return content;
        }
    }
}
