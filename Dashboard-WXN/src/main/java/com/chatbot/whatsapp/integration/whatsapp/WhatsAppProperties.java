package com.chatbot.whatsapp.integration.whatsapp;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuracoes da futura integracao real com o WhatsApp.
 *
 * <p>PENDENTE DE DEFINICAO COM O CLIENTE: qual provedor sera utilizado
 * (Meta WhatsApp Cloud API, Twilio, 360dialog, etc.). Os campos abaixo
 * refletem o formato mais comum (Meta Cloud API) e sao lidos a partir de
 * variaveis de ambiente (ver .env.example e README, secao "Integracao real
 * com WhatsApp"). Nenhum valor sensivel fica hardcoded no codigo.</p>
 *
 * @param enabled        liga/desliga o envio real de mensagens. Enquanto for
 *                       {@code false}, o {@link MockWhatsAppSender} apenas
 *                       loga a resposta que seria enviada.
 * @param apiUrl         URL base da API do provedor de WhatsApp.
 * @param accessToken    token de acesso da API (nunca deve ser commitado).
 * @param phoneNumberId  identificador do numero de telefone remetente configurado no provedor.
 * @param webhookVerifyToken token usado pelo provedor para validar o endpoint de webhook (handshake de verificacao).
 */
@ConfigurationProperties(prefix = "whatsapp")
public record WhatsAppProperties(
        boolean enabled,
        String apiUrl,
        String accessToken,
        String phoneNumberId,
        String webhookVerifyToken
) {
}
