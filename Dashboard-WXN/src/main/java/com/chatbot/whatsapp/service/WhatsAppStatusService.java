package com.chatbot.whatsapp.service;

import com.chatbot.whatsapp.entity.Message;
import com.chatbot.whatsapp.entity.enums.MessageDirection;
import com.chatbot.whatsapp.entity.enums.MessageStatus;
import com.chatbot.whatsapp.integration.whatsapp.WhatsAppStatusUpdate;
import com.chatbot.whatsapp.repository.MessageRepository;
import com.chatbot.whatsapp.util.LogSanitizer;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Aplica os status de entrega informados pela Meta as mensagens enviadas.
 *
 * <p>O status so avanca ({@code SENT -> DELIVERED -> READ}), porque a Meta pode
 * entregar as notificacoes fora de ordem ou repetidas. {@code FAILED} so e
 * aceito enquanto a mensagem ainda nao foi entregue. Status de mensagens
 * desconhecidas (ex.: enviadas por outro sistema no mesmo numero) sao
 * ignorados.</p>
 */
@Service
public class WhatsAppStatusService {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppStatusService.class);

    private final MessageRepository messageRepository;

    public WhatsAppStatusService(MessageRepository messageRepository) {
        this.messageRepository = messageRepository;
    }

    /** @return quantidade de mensagens cujo status foi atualizado. */
    @Transactional
    public int apply(List<WhatsAppStatusUpdate> updates) {
        int applied = 0;
        for (WhatsAppStatusUpdate update : updates) {
            Optional<MessageStatus> target = toMessageStatus(update.status());
            if (target.isEmpty()) {
                log.debug("Status '{}' da mensagem {} nao e acompanhado",
                        update.status(), LogSanitizer.messageRef(update.messageId()));
                continue;
            }
            Optional<Message> message = messageRepository.findByExternalId(update.messageId())
                    .filter(found -> found.getDirection() == MessageDirection.OUTBOUND);
            if (message.isEmpty()) {
                log.debug("Status recebido para mensagem desconhecida {}",
                        LogSanitizer.messageRef(update.messageId()));
                continue;
            }
            if (!canTransition(message.get().getStatus(), target.get())) {
                continue;
            }
            message.get().setStatus(target.get());
            messageRepository.save(message.get());
            applied++;
            if (target.get() == MessageStatus.FAILED) {
                log.warn("Meta informou falha na entrega da mensagem {} (codigo {}: {})",
                        LogSanitizer.messageRef(update.messageId()), update.errorCode(), update.errorTitle());
            }
        }
        return applied;
    }

    static boolean canTransition(MessageStatus current, MessageStatus target) {
        if (current == target || current == MessageStatus.FAILED) {
            return false;
        }
        if (target == MessageStatus.FAILED) {
            return current == MessageStatus.SENT;
        }
        return rank(target) > rank(current);
    }

    private static int rank(MessageStatus status) {
        return switch (status) {
            case SENT -> 1;
            case DELIVERED -> 2;
            case READ -> 3;
            default -> 0;
        };
    }

    private static Optional<MessageStatus> toMessageStatus(String status) {
        if (status == null) {
            return Optional.empty();
        }
        return switch (status) {
            case WhatsAppStatusUpdate.SENT -> Optional.of(MessageStatus.SENT);
            case WhatsAppStatusUpdate.DELIVERED -> Optional.of(MessageStatus.DELIVERED);
            case WhatsAppStatusUpdate.READ -> Optional.of(MessageStatus.READ);
            case WhatsAppStatusUpdate.FAILED -> Optional.of(MessageStatus.FAILED);
            default -> Optional.empty();
        };
    }
}
