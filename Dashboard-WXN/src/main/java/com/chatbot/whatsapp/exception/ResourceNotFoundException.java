package com.chatbot.whatsapp.exception;

/**
 * Lancada quando um recurso solicitado (ex.: uma conversa) nao existe.
 * Mapeada pelo {@link GlobalExceptionHandler} para HTTP 404.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
