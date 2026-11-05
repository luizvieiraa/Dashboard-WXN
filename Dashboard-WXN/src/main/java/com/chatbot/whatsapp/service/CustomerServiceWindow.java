package com.chatbot.whatsapp.service;

import com.chatbot.whatsapp.entity.Customer;
import com.chatbot.whatsapp.entity.Message;
import com.chatbot.whatsapp.entity.enums.MessageDirection;
import com.chatbot.whatsapp.repository.MessageRepository;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Janela de atendimento da WhatsApp Cloud API.
 *
 * <p>A Meta so aceita mensagens de texto livre ate 24 horas depois da ultima
 * mensagem enviada pelo cliente. Fora dessa janela, apenas templates
 * aprovados podem ser enviados - recurso que este projeto nao possui. As
 * respostas do bot sempre acontecem dentro da janela (respondem a uma mensagem
 * que acabou de chegar); a verificacao protege as respostas do atendente, que
 * podem ser enviadas muito depois.</p>
 */
@Service
public class CustomerServiceWindow {

    public static final Duration WINDOW = Duration.ofHours(24);

    private final MessageRepository messageRepository;

    public CustomerServiceWindow(MessageRepository messageRepository) {
        this.messageRepository = messageRepository;
    }

    /** {@code true} quando o cliente enviou alguma mensagem nas ultimas 24 horas. */
    @Transactional(readOnly = true)
    public boolean isOpen(Customer customer, Instant now) {
        return messageRepository
                .findFirstByConversationCustomerIdAndDirectionOrderByCreatedAtDescIdDesc(
                        customer.getId(), MessageDirection.INBOUND)
                .map(Message::getCreatedAt)
                .map(lastInbound -> lastInbound.isAfter(now.minus(WINDOW)))
                .orElse(false);
    }
}
