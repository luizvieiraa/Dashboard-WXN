package com.chatbot.whatsapp.exception;

public class InvalidConversationStateException extends RuntimeException {

    public InvalidConversationStateException(String message) {
        super(message);
    }
}
