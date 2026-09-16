package com.chatbot.whatsapp.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI chatbotOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("WhatsApp Chatbot API")
                        .description("API backend responsavel por receber mensagens (via WhatsApp/webhook), "
                                + "processa-las atraves do chatbot e devolver uma resposta ao cliente.")
                        .version("v0.1.0")
                        .contact(new Contact().name("Equipe do projeto")));
    }
}
