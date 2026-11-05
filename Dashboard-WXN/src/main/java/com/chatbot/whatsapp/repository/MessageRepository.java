package com.chatbot.whatsapp.repository;

import com.chatbot.whatsapp.entity.Message;
import com.chatbot.whatsapp.entity.enums.MessageDirection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageRepository extends JpaRepository<Message, Long> {

    List<Message> findByConversationIdOrderByCreatedAtAscIdAsc(Long conversationId);

    /** Mensagens mais recentes primeiro; usado para montar o historico limitado da conversa. */
    List<Message> findByConversationIdOrderByCreatedAtDescIdDesc(Long conversationId, Limit limit);

    /** Usado para aplicar os status de entrega informados pela Meta ({@code wamid}). */
    Optional<Message> findByExternalId(String externalId);

    /** Ultima mensagem do cliente em qualquer conversa: define a janela de 24h da Cloud API. */
    Optional<Message> findFirstByConversationCustomerIdAndDirectionOrderByCreatedAtDescIdDesc(
            Long customerId,
            MessageDirection direction);

    /**
     * Usado para descartar reentregas do webhook: a Meta pode entregar a mesma
     * notificacao mais de uma vez, e reprocessar a mensagem faria o bot
     * responder em duplicidade e a triagem avancar de etapa indevidamente.
     */
    boolean existsByExternalId(String externalId);
}
