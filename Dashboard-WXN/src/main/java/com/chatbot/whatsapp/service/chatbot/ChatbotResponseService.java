package com.chatbot.whatsapp.service.chatbot;

import com.chatbot.whatsapp.entity.Chatbot;
import org.springframework.stereotype.Service;

@Service
public class ChatbotResponseService {
    public String generateReply(ChatIntent intent) {
        return generateReply(Chatbot.standard(), intent);
    }

    public String generateReply(Chatbot chatbot, ChatIntent intent) {
        return chatbot.reply(intent);
    }
}
