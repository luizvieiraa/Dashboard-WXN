package com.chatbot.whatsapp.service.chatbot;

import org.springframework.stereotype.Service;

/**
 * Gera a resposta textual do chatbot a partir de uma {@link ChatIntent}.
 *
 * <p>As respostas atuais sao fixas (canned responses). O objetivo desta
 * classe e isolar "o que o bot fala" do restante da orquestracao, para que
 * regras de negocio mais ricas (consulta a produtos, integracao com um
 * modelo de IA, etc. - PENDENTE DE DEFINICAO COM O CLIENTE) possam ser
 * plugadas aqui no futuro.</p>
 */
@Service
public class ChatbotResponseService {

    public String generateReply(ChatIntent intent) {
        return switch (intent) {
            case GREETING -> "Olá! Como posso ajudar?";
            case PRICE_INQUIRY -> "Claro. Vou verificar o preço solicitado e já te retorno.";
            case HELP -> "Posso te ajudar com informações gerais. Diga 'oi' para começar ou pergunte sobre preços.";
            case UNKNOWN -> "Não entendi sua mensagem. Pode reformular ou digitar 'ajuda' para ver as opções?";
        };
    }
}
