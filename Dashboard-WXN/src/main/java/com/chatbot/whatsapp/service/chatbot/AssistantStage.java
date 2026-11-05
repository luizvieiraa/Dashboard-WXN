package com.chatbot.whatsapp.service.chatbot;

/** Etapa do atendimento em que o assistente virtual e consultado. */
public enum AssistantStage {
    /** Primeira mensagem da conversa: so interessa saber se ela deve ir direto para um atendente. */
    FIRST_MESSAGE("PRIMEIRA_MENSAGEM"),
    /** Triagem concluida: o assistente responde, pede detalhes ou encaminha. */
    CONVERSATION("CONVERSA");

    private final String label;

    AssistantStage(String label) {
        this.label = label;
    }

    /** Nome da etapa enviado ao provedor, igual ao usado no prompt de sistema. */
    public String label() {
        return label;
    }
}
