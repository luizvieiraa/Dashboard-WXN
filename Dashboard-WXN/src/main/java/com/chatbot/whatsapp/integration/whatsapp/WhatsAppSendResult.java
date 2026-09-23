package com.chatbot.whatsapp.integration.whatsapp;

/** Resultado observavel de uma tentativa de envio pelo provedor de WhatsApp. */
public record WhatsAppSendResult(
        boolean sent,
        String providerMessageId,
        String error
) {

    public static WhatsAppSendResult sent(String providerMessageId) {
        return new WhatsAppSendResult(true, providerMessageId, null);
    }

    public static WhatsAppSendResult failed(String error) {
        return new WhatsAppSendResult(false, null, error);
    }
}
