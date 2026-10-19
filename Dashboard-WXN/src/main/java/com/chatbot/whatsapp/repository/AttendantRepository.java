package com.chatbot.whatsapp.repository;

import com.chatbot.whatsapp.entity.Attendant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttendantRepository extends JpaRepository<Attendant, Long> {
    Optional<Attendant> findFirstByName(String name);
}
