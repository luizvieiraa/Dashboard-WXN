package com.chatbot.whatsapp.integration.ai;

import java.util.List;

/**
 * Dados minimos enviados ao provedor para decidir o proximo passo do atendimento.
 *
 * <p>Somente o necessario para a resposta: primeiro nome, empresa, assunto da
 * triagem, historico recente limitado e a mensagem atual. Telefone e demais
 * dados pessoais nunca fazem parte da requisicao. Todos os textos ja chegam
 * aqui sanitizados e truncados pela camada de servico.</p>
 *
 * @param stage             etapa do atendimento ({@code PRIMEIRA_MENSAGEM} ou {@code CONVERSA}).
 * @param customerFirstName primeiro nome do cliente, quando conhecido.
 * @param companyName       empresa informada na triagem, quando conhecida.
 * @param triageSubject     assunto registrado na triagem, quando conhecido.
 * @param history           mensagens anteriores da conversa, em ordem cronologica.
 * @param message           mensagem atual do cliente.
 */
public record AiTriageRequest(
        String stage,
        String customerFirstName,
        String companyName,
        String triageSubject,
        List<HistoryEntry> history,
        String message
) {

    public AiTriageRequest {
        history = history == null ? List.of() : List.copyOf(history);
    }

    /** @param author {@code cliente} ou {@code assistente}. */
    public record HistoryEntry(String author, String text) {
    }
}
