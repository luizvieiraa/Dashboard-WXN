package com.chatbot.whatsapp.repository;

import com.chatbot.whatsapp.entity.Conversation;
import com.chatbot.whatsapp.entity.Customer;
import com.chatbot.whatsapp.entity.enums.ConversationStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    long countByStatus(ConversationStatus status);

    List<Conversation> findByStatusInAndLastInteractionAtBefore(
            Collection<ConversationStatus> statuses,
            Instant cutoff);

    Optional<Conversation> findFirstByCustomerAndStatusNotOrderByStartedAtDesc(
            Customer customer,
            ConversationStatus excludedStatus);

    @EntityGraph(attributePaths = "customer")
    Optional<Conversation> findOneById(Long id);

    /** Somente o id do cliente, sem carregar a conversa na sessao. */
    @Query("select c.customer.id from Conversation c where c.id = :id")
    Optional<Long> findCustomerIdById(@Param("id") Long id);
}
