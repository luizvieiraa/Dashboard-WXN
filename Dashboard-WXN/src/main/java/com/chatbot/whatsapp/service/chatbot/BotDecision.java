package com.chatbot.whatsapp.service.chatbot;

import com.chatbot.whatsapp.service.triage.HandoffReason;
import java.util.Objects;

/**
 * Decisao do bot para uma mensagem: responder, pedir mais informacoes ou
 * encaminhar para um atendente.
 *
 * @param action        proximo passo.
 * @param reply         texto para o cliente; nulo em {@link BotAction#HANDOFF}, cuja
 *                      mensagem e definida pelo servico de encaminhamento.
 * @param context       origem da resposta, gravada no contexto da conversa.
 * @param handoffReason motivo do encaminhamento; somente em {@link BotAction#HANDOFF}.
 */
public record BotDecision(BotAction action, String reply, String context, HandoffReason handoffReason) {

    public static BotDecision resolve(String reply, String context) {
        return new BotDecision(BotAction.RESOLVE, reply, context, null);
    }

    public static BotDecision askMore(String reply, String context) {
        return new BotDecision(BotAction.ASK_MORE, reply, context, null);
    }

    public static BotDecision handoff(HandoffReason reason) {
        return new BotDecision(BotAction.HANDOFF, null, null, Objects.requireNonNull(reason));
    }
}
