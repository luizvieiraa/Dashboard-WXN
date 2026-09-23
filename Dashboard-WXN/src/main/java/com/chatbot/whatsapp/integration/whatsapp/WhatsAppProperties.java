package com.chatbot.whatsapp.integration.whatsapp;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuracoes da integracao com a WhatsApp Business Platform (Meta Cloud API).
 *
 * <p>Todos os valores vem de variaveis de ambiente (ver .env.example e README,
 * secao "Integracao real com WhatsApp"). Nenhum valor sensivel fica hardcoded
 * no codigo.</p>
 *
 * @param enabled        liga o envio real de mensagens. Quando {@code false},
 *                       {@link MockWhatsAppSender} apenas loga a resposta que
 *                       seria enviada, permitindo rodar o sistema inteiro sem
 *                       credenciais externas.
 * @param apiUrl         URL base da Graph API (ex.: {@code https://graph.facebook.com}).
 * @param apiVersion     versao da Graph API usada nas chamadas (ex.: {@code v21.0}).
 * @param accessToken    token de acesso da Cloud API (nunca deve ser commitado).
 * @param phoneNumberId  identificador do numero remetente configurado na Meta.
 * @param contactPhone   numero publico completo, somente digitos, usado no link wa.me.
 * @param webhookVerifyToken token comparado no handshake {@code GET} de verificacao do webhook.
 * @param appSecret      App Secret do app da Meta, usado para validar a assinatura
 *                       {@code X-Hub-Signature-256} de cada notificacao recebida.
 *                       Quando vazio, a validacao e ignorada (com aviso em log).
 */
@ConfigurationProperties(prefix = "whatsapp")
public record WhatsAppProperties(
        boolean enabled,
        String apiUrl,
        String apiVersion,
        String accessToken,
        String phoneNumberId,
        String contactPhone,
        String webhookVerifyToken,
        String appSecret
) {

    /** Endpoint completo de envio de mensagens do numero configurado. */
    public String sendMessageUrl() {
        return "%s/%s/%s/messages".formatted(
                trimTrailingSlash(apiUrl),
                apiVersion,
                phoneNumberId
        );
    }

    private static String trimTrailingSlash(String value) {
        if (value == null) {
            return "";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
