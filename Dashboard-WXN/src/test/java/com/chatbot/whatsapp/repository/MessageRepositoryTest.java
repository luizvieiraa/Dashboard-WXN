package com.chatbot.whatsapp.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.chatbot.whatsapp.entity.Conversation;
import com.chatbot.whatsapp.entity.Customer;
import com.chatbot.whatsapp.entity.Message;
import com.chatbot.whatsapp.entity.enums.ChannelType;
import com.chatbot.whatsapp.entity.enums.ConversationStatus;
import com.chatbot.whatsapp.entity.enums.MessageDirection;
import com.chatbot.whatsapp.entity.enums.MessageStatus;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class MessageRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private MessageRepository messageRepository;

    @Test
    void deveListarMensagensDeUmaConversaEmOrdemCronologica() {
        Customer customer = entityManager.persistAndFlush(
                Customer.builder().phoneNumber("5511999999999").build());

        Conversation conversation = entityManager.persistAndFlush(
                Conversation.builder()
                        .customer(customer)
                        .status(ConversationStatus.OPEN)
                        .channel(ChannelType.WHATSAPP)
                        .lastInteractionAt(Instant.now())
                        .build());

        Message first = Message.builder()
                .conversation(conversation)
                .direction(MessageDirection.INBOUND)
                .content("Olá")
                .status(MessageStatus.RECEIVED)
                .build();
        Message second = Message.builder()
                .conversation(conversation)
                .direction(MessageDirection.OUTBOUND)
                .content("Olá! Como posso ajudar?")
                .status(MessageStatus.SENT)
                .build();

        entityManager.persistAndFlush(first);
        entityManager.persistAndFlush(second);

        List<Message> messages = messageRepository.findByConversationIdOrderByCreatedAtAscIdAsc(conversation.getId());

        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).getContent()).isEqualTo("Olá");
        assertThat(messages.get(1).getContent()).isEqualTo("Olá! Como posso ajudar?");
    }
}
