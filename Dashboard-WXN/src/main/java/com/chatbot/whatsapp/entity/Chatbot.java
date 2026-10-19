package com.chatbot.whatsapp.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AccessLevel;
import com.chatbot.whatsapp.service.chatbot.ChatIntent;

/** Identidade e comportamento do assistente automatizado. */
@Entity
@Table(name = "chatbots")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Chatbot {
    @Id
    private Long id;
    @Column(nullable = false)
    private String name;

    public static Chatbot standard() {
        Chatbot bot = new Chatbot();
        bot.id = 1L;
        bot.name = "WXN";
        return bot;
    }

    public String reply(ChatIntent intent) {
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
