package com.chatbot.whatsapp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chatbot.whatsapp.integration.whatsapp.WhatsAppInboundMessage;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class WhatsAppInboundServiceTest {

    @Mock
    private MessageProcessingService messageProcessingService;

    @Mock
    private MessageService messageService;

    @InjectMocks
    private WhatsAppInboundService service;

    private static WhatsAppInboundMessage text(String id) {
        return new WhatsAppInboundMessage(id, "5511988887777", "text", "Olá", Instant.now(), "Maria");
    }

    @Test
    void deveIgnorarMensagemJaProcessadaSemChamarOChatbot() {
        when(messageService.alreadyProcessed("wamid.REPETIDA")).thenReturn(true);

        WhatsAppInboundBatchResult result = service.handleAll(List.of(text("wamid.REPETIDA")));

        assertThat(result.ignored()).isEqualTo(1);
        assertThat(result.hasFailures()).isFalse();
        verify(messageProcessingService, never()).process(any(), any());
    }

    @Test
    void deveTratarComoDuplicadaAEntregaSimultaneaDaMesmaMensagem() {
        // Primeira verificacao: ainda nao gravada. Depois da violacao do indice unico: ja gravada.
        when(messageService.alreadyProcessed("wamid.PARALELA")).thenReturn(false, true);
        when(messageProcessingService.process(any(), eq("wamid.PARALELA")))
                .thenThrow(new DataIntegrityViolationException("ux_messages_external_id"));

        WhatsAppInboundBatchResult result = service.handleAll(List.of(text("wamid.PARALELA")));

        assertThat(result.ignored()).isEqualTo(1);
        assertThat(result.hasFailures()).isFalse();
    }

    @Test
    void deveSinalizarFalhaQuandoAViolacaoNaoForDeMensagemDuplicada() {
        // Ex.: cadastro simultaneo do mesmo cliente. A mensagem nao foi gravada e
        // precisa ser reentregue pela Meta, e nao descartada em silencio.
        when(messageService.alreadyProcessed("wamid.NOVA")).thenReturn(false, false);
        when(messageProcessingService.process(any(), eq("wamid.NOVA")))
                .thenThrow(new DataIntegrityViolationException("uk_customers_phone_number"));

        WhatsAppInboundBatchResult result = service.handleAll(List.of(text("wamid.NOVA")));

        assertThat(result.failed()).isEqualTo(1);
        assertThat(result.hasFailures()).isTrue();
    }
}
