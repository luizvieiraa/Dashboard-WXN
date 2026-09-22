package com.chatbot.whatsapp.service.lifecycle;

import java.time.Instant;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ConversationInactivityScheduler {

    private final ConversationLifecycleService lifecycleService;
    private final ConversationLifecycleProperties properties;

    public ConversationInactivityScheduler(
            ConversationLifecycleService lifecycleService,
            ConversationLifecycleProperties properties) {
        this.lifecycleService = lifecycleService;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${conversation.lifecycle.check-interval-ms:300000}")
    public void closeInactiveConversations() {
        if (properties.enabled()) {
            lifecycleService.closeInactiveConversations(Instant.now());
        }
    }
}
