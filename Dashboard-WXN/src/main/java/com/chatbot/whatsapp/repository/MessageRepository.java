package com.chatbot.whatsapp.repository;

import com.chatbot.whatsapp.entity.Message;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageRepository extends JpaRepository<Message, Long> {

    List<Message> findByConversationIdOrderByCreatedAtAscIdAsc(Long conversationId);

    /**
     * Usado para descartar reentregas do webhook: a Meta pode entregar a mesma
     * notificacao mais de uma vez, e reprocessar a mensagem faria o bot
     * responder em duplicidade e a triagem avancar de etapa indevidamente.
     */
    boolean existsByExternalId(String externalId);
}
