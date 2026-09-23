package com.chatbot.whatsapp.integration.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * Garante que exista sempre EXATAMENTE uma implementacao de
 * {@link WhatsAppClient} no contexto.
 *
 * <p>As duas implementacoes sao selecionadas por {@code whatsapp.enabled}. Se as
 * condicoes se sobrepusessem, a aplicacao nem subiria
 * ({@code NoUniqueBeanDefinitionException}); se nenhuma casasse, subiria e
 * quebraria na primeira mensagem. Nos dois casos a falha apareceria somente em
 * producao, ja que o perfil de teste usa apenas {@code enabled=false}.</p>
 */
class WhatsAppClientSelectionTest {

    @Nested
    @SpringBootTest
    @ActiveProfiles("test")
    @TestPropertySource(properties = "whatsapp.enabled=false")
    class QuandoDesabilitado {

        @Autowired
        private ApplicationContext context;

        @Autowired
        private WhatsAppClient whatsAppClient;

        @Test
        void deveUsarSomenteOMock() {
            assertThat(context.getBeansOfType(WhatsAppClient.class)).hasSize(1);
            assertThat(whatsAppClient).isInstanceOf(MockWhatsAppSender.class);
        }
    }

    @Nested
    @SpringBootTest
    @ActiveProfiles("test")
    @TestPropertySource(properties = {
            "whatsapp.enabled=true",
            "whatsapp.access-token=token-de-teste",
            "whatsapp.phone-number-id=1234567890",
            "whatsapp.contact-phone=5511999999999",
            "whatsapp.app-secret=app-secret-de-teste"
    })
    class QuandoHabilitado {

        @Autowired
        private ApplicationContext context;

        @Autowired
        private WhatsAppClient whatsAppClient;

        @Test
        void deveUsarSomenteOClienteRealDaMeta() {
            assertThat(context.getBeansOfType(WhatsAppClient.class)).hasSize(1);
            assertThat(whatsAppClient).isInstanceOf(MetaWhatsAppClient.class);
        }
    }
}
