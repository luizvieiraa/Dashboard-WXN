package com.chatbot.whatsapp.exception;

/**
 * Lancada quando o payload recebido no webhook nao pode ser processado
 * (ex.: numero de telefone em formato invalido apos validacoes adicionais
 * de negocio que vao alem da validacao basica de Bean Validation).
 * Mapeada pelo {@link GlobalExceptionHandler} para HTTP 400.
 */
public class InvalidWebhookPayloadException extends RuntimeException {

    public InvalidWebhookPayloadException(String message) {
        super(message);
    }
}
