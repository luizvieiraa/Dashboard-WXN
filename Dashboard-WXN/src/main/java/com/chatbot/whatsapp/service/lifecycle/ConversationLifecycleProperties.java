package com.chatbot.whatsapp.service.lifecycle;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "conversation.lifecycle")
public record ConversationLifecycleProperties(
        boolean enabled,
        int inactivityHours
) {
}
