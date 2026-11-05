package com.chatbot.whatsapp.service.triage;

/** Motivo pelo qual uma conversa automatizada foi encaminhada para um atendente. */
public enum HandoffReason {
    COMPLAINT("reclamação"),
    CUSTOMER_REQUEST("cliente pediu atendimento humano"),
    SENSITIVE_TOPIC("assunto sensível"),
    LOW_CONFIDENCE("assistente virtual sem confiança suficiente para responder"),
    UNRESOLVED_ATTEMPTS("tentativas automáticas sem solução"),
    ASSISTANT_RECOMMENDATION("assistente virtual recomendou atendimento humano");

    private final String description;

    HandoffReason(String description) {
        this.description = description;
    }

    /** Descricao registrada no resumo da triagem exibido no dashboard. */
    public String description() {
        return description;
    }
}
