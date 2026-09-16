package com.chatbot.whatsapp.repository;

import com.chatbot.whatsapp.entity.Conversation;
import com.chatbot.whatsapp.entity.Customer;
import com.chatbot.whatsapp.entity.enums.ConversationStatus;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    Optional<Conversation> findFirstByCustomerAndStatusOrderByStartedAtDesc(Customer customer, ConversationStatus status);
}
