package com.chatbot.whatsapp.service.lifecycle;

import com.chatbot.whatsapp.entity.Conversation;
import com.chatbot.whatsapp.entity.enums.ConversationStatus;
import com.chatbot.whatsapp.repository.ConversationRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConversationLifecycleService {

    static final String AUTO_CLOSE_CONTEXT = "AUTO_CLOSED_INACTIVITY";

    private static final Logger log = LoggerFactory.getLogger(ConversationLifecycleService.class);
    private static final EnumSet<ConversationStatus> AUTOMATED_STATUSES = EnumSet.of(
            ConversationStatus.BOT_ACTIVE,
            ConversationStatus.COLLECTING_INFORMATION,
            ConversationStatus.QUALIFIED
    );

    private final ConversationRepository conversationRepository;
    private final ConversationLifecycleProperties properties;

    public ConversationLifecycleService(
            ConversationRepository conversationRepository,
            ConversationLifecycleProperties properties) {
        this.conversationRepository = conversationRepository;
        this.properties = properties;
    }

    @Transactional
    public int closeInactiveConversations(Instant now) {
        int inactivityHours = properties.inactivityHours() > 0
                ? properties.inactivityHours()
                : 24;
        Instant cutoff = now.minus(inactivityHours, ChronoUnit.HOURS);
        List<Conversation> inactive = conversationRepository
                .findByStatusInAndLastInteractionAtBefore(AUTOMATED_STATUSES, cutoff);

        inactive.forEach(conversation -> {
            conversation.setStatus(ConversationStatus.CLOSED);
            conversation.setClosedAt(now);
            conversation.setContext(AUTO_CLOSE_CONTEXT);
        });
        conversationRepository.saveAll(inactive);

        if (!inactive.isEmpty()) {
            log.info("Conversas encerradas por inatividade: {}", inactive.size());
        }
        return inactive.size();
    }
}
