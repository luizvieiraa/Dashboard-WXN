package com.chatbot.whatsapp.service.chatbot;

/**
 * Identifica a intencao de uma mensagem de texto recebida do cliente.
 *
 * <p>A implementacao atual ({@link KeywordIntentClassifier}) usa um
 * casamento simples por palavras-chave, suficiente para o escopo academico
 * atual. A interface existe justamente para permitir trocar essa
 * implementacao por algo mais sofisticado no futuro (regras mais elaboradas,
 * ou uma integracao com um modelo de IA) sem tocar em nenhum outro ponto do
 * sistema.</p>
 */
public interface IntentClassifier {

    ChatIntent classify(String messageContent);
}
