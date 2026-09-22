package com.chatbot.whatsapp.integration.whatsapp;

import java.time.Instant;

/**
 * Mensagem recebida do WhatsApp, ja normalizada e livre do formato do provedor.
 *
 * <p>E o "contrato de entrada" entre a camada de integracao (que conhece o
 * formato da Meta) e a camada de servico (que conhece apenas o dominio). Se o
 * provedor mudar, apenas o parser muda; este record e o resto do sistema
 * continuam iguais.</p>
 *
 * @param messageId   id da mensagem no provedor (ex.: {@code wamid.HBgL...}),
 *                    usado como chave de idempotencia contra reentregas.
 * @param phone       telefone do remetente, apenas digitos (o {@code wa_id} da Meta).
 * @param type        tipo informado pelo provedor ({@code text}, {@code image},
 *                    {@code audio}, ...). Somente {@code text} segue para o chatbot.
 * @param text        conteudo textual; nulo quando {@code type} nao e {@code text}.
 * @param timestamp   momento em que o provedor registrou o envio.
 * @param contactName nome de perfil informado pelo provedor, quando disponivel.
 */
public record WhatsAppInboundMessage(
        String messageId,
        String phone,
        String type,
        String text,
        Instant timestamp,
        String contactName
) {

    public static final String TYPE_TEXT = "text";

    /** Somente mensagens de texto tem conteudo que o chatbot sabe interpretar. */
    public boolean isText() {
        return TYPE_TEXT.equals(type) && text != null && !text.isBlank();
    }
}
