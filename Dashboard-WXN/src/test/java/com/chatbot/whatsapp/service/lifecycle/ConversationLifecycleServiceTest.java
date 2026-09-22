package com.chatbot.whatsapp.service.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chatbot.whatsapp.entity.Conversation;
import com.chatbot.whatsapp.entity.enums.ConversationStatus;
import com.chatbot.whatsapp.repository.ConversationRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConversationLifecycleServiceTest {

    @Mock
    private ConversationRepository conversationRepository;

    @Test
    void deveEncerrarSomenteConversasAutomatizadasEncontradasComoInativas() {
        Instant now = Instant.parse("2026-09-22T12:00:00Z");
        Instant expectedCutoff = Instant.parse("2026-09-21T12:00:00Z");
        Conversation conversation = Conversation.builder()
                .status(ConversationStatus.QUALIFIED)
                .lastInteractionAt(Instant.parse("2026-09-20T12:00:00Z"))
                .context("PRODUCT_INFO")
                .build();
        when(conversationRepository.findByStatusInAndLastInteractionAtBefore(
                any(), eq(expectedCutoff)))
                .thenReturn(List.of(conversation));

        ConversationLifecycleService service = new ConversationLifecycleService(
                conversationRepository,
                new ConversationLifecycleProperties(true, 24));

        int closed = service.closeInactiveConversations(now);

        assertThat(closed).isEqualTo(1);
        assertThat(conversation.getStatus()).isEqualTo(ConversationStatus.CLOSED);
        assertThat(conversation.getClosedAt()).isEqualTo(now);
        assertThat(conversation.getContext()).isEqualTo(ConversationLifecycleService.AUTO_CLOSE_CONTEXT);
        verify(conversationRepository).saveAll(List.of(conversation));
    }

    @Test
    void deveConsultarApenasEstadosControladosPeloBot() {
        Instant now = Instant.parse("2026-09-22T12:00:00Z");
        when(conversationRepository.findByStatusInAndLastInteractionAtBefore(
                any(), any(Instant.class)))
                .thenReturn(List.of());
        ConversationLifecycleService service = new ConversationLifecycleService(
                conversationRepository,
                new ConversationLifecycleProperties(true, 12));

        service.closeInactiveConversations(now);

        verify(conversationRepository).findByStatusInAndLastInteractionAtBefore(
                argThat(statuses -> statuses.size() == 3
                        && statuses.contains(ConversationStatus.BOT_ACTIVE)
                        && statuses.contains(ConversationStatus.COLLECTING_INFORMATION)
                        && statuses.contains(ConversationStatus.QUALIFIED)
                        && !statuses.contains(ConversationStatus.WAITING_HUMAN)
                        && !statuses.contains(ConversationStatus.HUMAN_ACTIVE)),
                eq(Instant.parse("2026-09-22T00:00:00Z")));
    }
}
