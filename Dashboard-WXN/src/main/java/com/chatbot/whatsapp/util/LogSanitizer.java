package com.chatbot.whatsapp.util;

/**
 * Descreve excecoes em log sem expor dados pessoais.
 *
 * <p>Mensagens de excecoes de banco e de clientes HTTP costumam trazer valores
 * da requisicao (ex.: "Key (phone_number)=(5511...) already exists"). Para
 * cumprir a regra de nao registrar telefones nem conteudo de conversas, os
 * logs da aplicacao usam apenas os tipos da cadeia de causas e o ponto do
 * codigo da aplicacao onde a falha ocorreu.</p>
 */
public final class LogSanitizer {

    private static final String APPLICATION_PACKAGE = "com.chatbot.whatsapp";
    private static final int MAX_CAUSES = 5;
    private static final int MESSAGE_REF_LENGTH = 12;
    private static final int SHORT_ID_LENGTH = 16;

    private LogSanitizer() {
    }

    /**
     * Referencia curta a um id de mensagem do WhatsApp, para logs.
     *
     * <p>O {@code wamid} e um base64 que traz o telefone do cliente no inicio
     * (ex.: {@code wamid.HBgLMTY1MDM4...}). O log usa somente os ultimos
     * caracteres, suficientes para localizar a mensagem no banco
     * ({@code external_id LIKE '%sufixo'}) sem expor o telefone.</p>
     */
    public static String messageRef(String externalId) {
        if (externalId == null || externalId.isBlank()) {
            return "(sem id)";
        }
        String id = externalId.strip();
        if (id.length() <= SHORT_ID_LENGTH) {
            return id;
        }
        return "…" + id.substring(id.length() - MESSAGE_REF_LENGTH);
    }

    /**
     * Ex.: {@code DataIntegrityViolationException <- PSQLException em
     * com.chatbot.whatsapp.service.MessageService.recordInbound(MessageService.java:36)}.
     */
    public static String describe(Throwable error) {
        if (error == null) {
            return "erro desconhecido";
        }
        StringBuilder description = new StringBuilder(error.getClass().getSimpleName());
        Throwable cause = error.getCause();
        int depth = 0;
        while (cause != null && cause != error && depth < MAX_CAUSES) {
            description.append(" <- ").append(cause.getClass().getSimpleName());
            cause = cause.getCause();
            depth++;
        }
        StackTraceElement origin = applicationFrame(error);
        if (origin != null) {
            description.append(" em ").append(origin);
        }
        return description.toString();
    }

    private static StackTraceElement applicationFrame(Throwable error) {
        for (StackTraceElement element : error.getStackTrace()) {
            if (element.getClassName().startsWith(APPLICATION_PACKAGE)
                    && !element.getClassName().equals(LogSanitizer.class.getName())) {
                return element;
            }
        }
        return null;
    }
}
