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
            case GREETING -> "Olá! 😊 Que bom falar com você. Como posso ajudar agora?";
            case PRICE_INQUIRY -> "Claro! Entendi que você quer saber mais sobre valores. "
                    + "Registrei seu interesse para que a equipe compartilhe as informações corretas para sua necessidade.";
            case HELP -> "Estou por aqui para ajudar! Você pode perguntar sobre os serviços da WXN, "
                    + "valores ou explicar o que gostaria de automatizar.";
            case UNKNOWN -> "Não entendi direitinho, mas quero ajudar. Pode me contar de outra forma o que você precisa? "
                    + "Se preferir, digite “ajuda” para ver algumas opções.";
        };
    }
}
