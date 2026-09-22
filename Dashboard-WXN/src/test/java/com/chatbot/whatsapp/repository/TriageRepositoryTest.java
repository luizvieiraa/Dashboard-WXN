package com.chatbot.whatsapp.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.chatbot.whatsapp.entity.Conversation;
import com.chatbot.whatsapp.entity.Customer;
import com.chatbot.whatsapp.entity.Triage;
import com.chatbot.whatsapp.entity.enums.ChannelType;
import com.chatbot.whatsapp.entity.enums.ConversationStatus;
import com.chatbot.whatsapp.entity.enums.TriageCategory;
import com.chatbot.whatsapp.entity.enums.TriagePriority;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class TriageRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private TriageRepository triageRepository;

    @Test
    void devePersistirEConsultarTriagemPelaConversa() {
        Customer customer = entityManager.persistAndFlush(
                Customer.builder()
                        .phoneNumber("5511966665555")
                        .name("Maria")
                        .email("maria@example.com")
                        .companyName("Empresa Exemplo")
                        .jobTitle("Gerente")
                        .build());

        Conversation conversation = entityManager.persistAndFlush(
                Conversation.builder()
                        .customer(customer)
                        .status(ConversationStatus.COLLECTING_INFORMATION)
                        .channel(ChannelType.WHATSAPP)
                        .lastInteractionAt(Instant.now())
                        .build());

        Triage triage = triageRepository.saveAndFlush(
                Triage.builder()
                        .conversation(conversation)
                        .category(TriageCategory.INFORMATION)
                        .priority(TriagePriority.MEDIUM)
                        .subject("Contratacao de chatbot")
                        .customerNeed("Automatizar a triagem comercial")
                        .summary("Cliente busca um chatbot para qualificacao de leads.")
                        .missingInformation(new LinkedHashSet<>(Set.of("monthlyVolume")))
                        .build());

        entityManager.clear();

        Triage persisted = triageRepository.findByConversationId(conversation.getId()).orElseThrow();

        assertThat(persisted.getId()).isEqualTo(triage.getId());
        assertThat(persisted.getCategory()).isEqualTo(TriageCategory.INFORMATION);
        assertThat(persisted.getPriority()).isEqualTo(TriagePriority.MEDIUM);
        assertThat(persisted.isRequiresHuman()).isFalse();
        assertThat(persisted.getMissingInformation()).containsExactly("monthlyVolume");
    }
}
