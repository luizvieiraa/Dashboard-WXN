package com.chatbot.whatsapp.repository;

import com.chatbot.whatsapp.entity.Triage;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TriageRepository extends JpaRepository<Triage, Long> {

    Optional<Triage> findByConversationId(Long conversationId);
}
