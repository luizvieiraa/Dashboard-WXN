package com.chatbot.whatsapp.repository;

import com.chatbot.whatsapp.entity.*;
import com.chatbot.whatsapp.entity.enums.*;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class AttendanceRepositoryTest {
    @Autowired private TestEntityManager em;

    @Test
    void persistsRelationshipsAndAttendanceClosure() {
        Customer customer = em.persist(Customer.builder().phoneNumber("5511990011223").build());
        Attendant attendant = em.persist(new Attendant("Carlos"));
        Conversation conversation = em.persistAndFlush(Conversation.builder().customer(customer)
                .status(ConversationStatus.WAITING_HUMAN).channel(ChannelType.WHATSAPP)
                .lastInteractionAt(Instant.now()).build());
        conversation.assume(attendant);
        em.flush();
        Long id = conversation.getId();
        em.clear();

        Conversation stored = em.find(Conversation.class, id);
        assertThat(stored.getChatbot().getName()).isEqualTo("WXN");
        assertThat(stored.getAttendance().getAttendant().getId()).isEqualTo(attendant.getId());
        assertThat(stored.getAttendance().getConversation()).isSameAs(stored);
        stored.close();
        em.flush();
        em.clear();
        stored = em.find(Conversation.class, id);
        assertThat(stored.getAttendance().getClosedAt()).isEqualTo(stored.getClosedAt());
    }
}
