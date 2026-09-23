package com.chatbot.whatsapp.service;

/** Resumo do processamento de um lote entregue pelo webhook da Meta. */
public record WhatsAppInboundBatchResult(int processed, int ignored, int failed) {

    public boolean hasFailures() {
        return failed > 0;
    }
}
