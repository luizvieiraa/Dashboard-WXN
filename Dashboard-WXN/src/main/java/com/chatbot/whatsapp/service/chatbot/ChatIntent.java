package com.chatbot.whatsapp.service.chatbot;

/**
 * Intencoes que o chatbot consegue reconhecer nesta primeira versao.
 *
 * <p>A lista e propositalmente pequena. Novas intencoes (comandos, integracao
 * com IA, consulta ao banco de produtos, etc.) podem ser adicionadas aqui e
 * tratadas em {@link ChatbotResponseService} sem alterar o restante do fluxo
 * de processamento (ver {@code MessageProcessingService}).</p>
 */
public enum ChatIntent {
    GREETING,
    PRICE_INQUIRY,
    HELP,
    UNKNOWN
}
