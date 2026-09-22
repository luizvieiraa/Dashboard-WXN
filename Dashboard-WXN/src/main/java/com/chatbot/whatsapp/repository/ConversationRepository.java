package com.chatbot.whatsapp.repository;

import com.chatbot.whatsapp.entity.Conversation;
import com.chatbot.whatsapp.entity.Customer;
import com.chatbot.whatsapp.entity.enums.ConversationStatus;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    Optional<Conversation> findFirstByCustomerAndStatusNotOrderByStartedAtDesc(
            Customer customer,
            ConversationStatus excludedStatus);

    @EntityGraph(attributePaths = "customer")
    Optional<Conversation> findOneById(Long id);
}
