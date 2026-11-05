package com.chatbot.whatsapp.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chatbot.whatsapp.dto.request.WhatsAppWebhookRequest;
import com.chatbot.whatsapp.dto.response.WhatsAppWebhookResponse;
import com.chatbot.whatsapp.entity.enums.ConversationStatus;
import com.chatbot.whatsapp.integration.ai.AiClient;
import com.chatbot.whatsapp.integration.ai.AiTriageDecision;
import com.chatbot.whatsapp.integration.ai.AiTriageDecision.Action;
import com.chatbot.whatsapp.integration.ai.AiTriageDecision.Intent;
import com.chatbot.whatsapp.integration.ai.AiTriageRequest;
import com.chatbot.whatsapp.integration.whatsapp.WhatsAppClient;
import com.chatbot.whatsapp.integration.whatsapp.WhatsAppSendResult;
import com.chatbot.whatsapp.repository.ConversationRepository;
import com.chatbot.whatsapp.repository.CustomerRepository;
import com.chatbot.whatsapp.service.MessageProcessingService;
import com.chatbot.whatsapp.service.triage.HumanHandoffService;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Garante que o bot nao responde depois que a conversa foi encaminhada para a
 * equipe humana, mesmo com mensagens chegando ao mesmo tempo.
 *
 * <p>A primeira mensagem fica parada dentro da chamada ao assistente virtual
 * (que decide encaminhar). Nesse meio tempo chega a segunda mensagem. Como o
 * cliente fica bloqueado durante o processamento, a segunda so e lida depois
 * do encaminhamento e, portanto, nao recebe resposta automatica.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties =
        "spring.datasource.url=jdbc:h2:mem:chatbot_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000")
class BotAndAttendantConcurrencyTest {

    private static final String PHONE = "5511970202001";
    private static final String RUN = UUID.randomUUID().toString().substring(0, 8);

    @Autowired
    private MessageProcessingService messageProcessingService;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private ConversationRepository conversationRepository;

    @MockitoBean
    private WhatsAppClient whatsAppClient;

    @MockitoBean
    private AiClient aiClient;

    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    void mensagemQueChegaDuranteOEncaminhamentoNaoRecebeRespostaAutomatica() throws Exception {
        AtomicInteger sequence = new AtomicInteger();
        when(whatsAppClient.sendMessage(anyString(), anyString())).thenAnswer(invocation ->
                WhatsAppSendResult.sent("wamid.CONC-OUT-" + RUN + "-" + sequence.incrementAndGet()));

        // Triagem concluida pelo fluxo local (assistente desligado).
        process("Quero conhecer os serviços", "1");
        process("Ana", "2");
        process("Empresa", "3");
        process("Automação", "4");

        CountDownLatch firstInsideAssistant = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        when(aiClient.isAvailable()).thenReturn(true);
        when(aiClient.decide(any())).thenAnswer(invocation -> {
            AiTriageRequest request = invocation.getArgument(0);
            if (request.message().equals("primeira")) {
                firstInsideAssistant.countDown();
                assertThat(releaseFirst.await(5, TimeUnit.SECONDS)).isTrue();
                return Optional.of(new AiTriageDecision(Intent.HUMAN_REQUEST, Action.HANDOFF, 0.95, false, ""));
            }
            return Optional.of(new AiTriageDecision(Intent.OTHER, Action.RESOLVE, 0.95, false, "Resposta automática"));
        });

        Future<WhatsAppWebhookResponse> first = executor.submit(() -> process("primeira", "5"));
        assertThat(firstInsideAssistant.await(5, TimeUnit.SECONDS)).isTrue();
        Future<WhatsAppWebhookResponse> second = executor.submit(() -> process("segunda", "6"));

        // A segunda mensagem precisa esperar o processamento da primeira.
        Thread.sleep(300);
        assertThat(second.isDone()).isFalse();
        releaseFirst.countDown();

        assertThat(first.get(10, TimeUnit.SECONDS).botReply()).isEqualTo(HumanHandoffService.HANDOFF_REPLY);
        assertThat(second.get(10, TimeUnit.SECONDS).botReply()).isNull();

        verify(aiClient, times(1)).decide(any());
        verify(whatsAppClient, times(1)).sendMessage(PHONE, HumanHandoffService.HANDOFF_REPLY);
        verify(whatsAppClient, times(5)).sendMessage(anyString(), anyString());
        assertThat(customerRepository.findByPhoneNumber(PHONE)
                .flatMap(customer -> conversationRepository.findFirstByCustomerAndStatusNotOrderByStartedAtDesc(
                        customer, ConversationStatus.CLOSED))
                .orElseThrow().getStatus())
                .isEqualTo(ConversationStatus.WAITING_HUMAN);
    }

    private WhatsAppWebhookResponse process(String text, String id) {
        return messageProcessingService.process(
                new WhatsAppWebhookRequest(PHONE, text, null), "wamid.CONC-IN-" + RUN + "-" + id);
    }
}
