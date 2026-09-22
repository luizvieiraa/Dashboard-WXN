package com.chatbot.whatsapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Ponto de entrada da aplicacao.
 *
 * <p>Este backend expoe uma API REST responsavel por receber mensagens de um
 * cliente (hoje simuladas via webhook, futuramente vindas do WhatsApp),
 * processa-las atraves de uma camada de chatbot baseada em regras simples e
 * persiste o historico da conversa em um banco de dados relacional.</p>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class ChatbotApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChatbotApplication.class, args);
    }
}
