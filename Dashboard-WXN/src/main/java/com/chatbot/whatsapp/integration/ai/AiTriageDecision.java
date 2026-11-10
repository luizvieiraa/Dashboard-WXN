package com.chatbot.whatsapp.integration.ai;

/**
 * Decisao estruturada devolvida pelo provedor para uma mensagem do cliente.
 *
 * <p>O formato e imposto por JSON Schema na requisicao e validado novamente na
 * leitura: qualquer campo ausente ou fora do dominio descarta a decisao inteira
 * e o chamador volta ao fluxo local.</p>
 *
 * @param intent     intencao identificada.
 * @param action     proxima acao sugerida.
 * @param confidence certeza do provedor, de 0 a 1.
 * @param sensitive  assunto sensivel (juridico, cobranca, privacidade, risco etc.).
 * @param reply      texto sugerido para o cliente; pode ser vazio em {@code HANDOFF}.
 */
public record AiTriageDecision(
        Intent intent,
        Action action,
        double confidence,
        boolean sensitive,
        String reply
) {

    public enum Intent {
        GREETING,
        INFORMATION,
        PRICE_INQUIRY,
        SUPPORT,
        COMPLAINT,
        HUMAN_REQUEST,
        OTHER
    }

    public enum Action {
        /** O bot responde e considera a mensagem resolvida. */
        RESOLVE,
        /** O bot pede mais informacoes ao cliente. */
        ASK_MORE,
        /** A conversa deve ir para um atendente humano. */
        HANDOFF
    }
}
