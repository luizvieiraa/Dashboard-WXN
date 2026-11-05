package com.chatbot.whatsapp.service.chatbot;

/** Proximo passo decidido para uma mensagem tratada pelo bot. */
public enum BotAction {
    /** O bot respondeu e a mensagem esta resolvida. */
    RESOLVE,
    /** O bot pediu mais informacoes; conta como tentativa sem solucao. */
    ASK_MORE,
    /** A conversa deve ser encaminhada para um atendente humano. */
    HANDOFF
}
