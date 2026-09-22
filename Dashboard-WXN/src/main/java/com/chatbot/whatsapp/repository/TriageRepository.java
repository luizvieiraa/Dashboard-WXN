package com.chatbot.whatsapp.repository;

import com.chatbot.whatsapp.entity.Triage;
import java.util.Optional;
import java.util.List;
import com.chatbot.whatsapp.entity.enums.TriageCategory;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TriageRepository extends JpaRepository<Triage, Long> {

    long countByCategory(TriageCategory category);

    long countByRequiresHumanTrue();

    @EntityGraph(attributePaths = {"conversation", "conversation.customer"})
    List<Triage> findAllByOrderByUpdatedAtDesc();

    Optional<Triage> findByConversationId(Long conversationId);
}
