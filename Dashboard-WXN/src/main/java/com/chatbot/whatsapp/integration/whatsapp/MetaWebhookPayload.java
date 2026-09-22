package com.chatbot.whatsapp.integration.whatsapp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * Envelope de notificacao enviado pela Meta Cloud API no webhook.
 *
 * <p>Espelha apenas os campos que este projeto consome. Todos os records usam
 * {@link JsonIgnoreProperties} porque a Meta acrescenta campos novos com
 * frequencia (precos, tipos de midia, metadados de conversa) e uma notificacao
 * desconhecida nunca deve derrubar o recebimento.</p>
 *
 * <p>Formato de uma mensagem de texto recebida:</p>
 * <pre>
 * {
 *   "object": "whatsapp_business_account",
 *   "entry": [{
 *     "id": "...",
 *     "changes": [{
 *       "field": "messages",
 *       "value": {
 *         "messaging_product": "whatsapp",
 *         "metadata": { "display_phone_number": "...", "phone_number_id": "..." },
 *         "contacts": [{ "profile": { "name": "Maria" }, "wa_id": "5511999999999" }],
 *         "messages": [{
 *           "from": "5511999999999",
 *           "id": "wamid.HBgL...",
 *           "timestamp": "1749416383",
 *           "type": "text",
 *           "text": { "body": "Ola" }
 *         }]
 *       }
 *     }]
 *   }]
 * }
 * </pre>
 *
 * <p>Notificacoes de status de entrega (enviada/entregue/lida) chegam no mesmo
 * formato, mas com um array {@code statuses} em vez de {@code messages} - por
 * isso {@code messages} pode ser nulo.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MetaWebhookPayload(
        String object,
        List<Entry> entry
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Entry(String id, List<Change> changes) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Change(String field, Value value) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Value(
            String messaging_product,
            Metadata metadata,
            List<Contact> contacts,
            List<MessageNode> messages
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Metadata(String display_phone_number, String phone_number_id) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Contact(String wa_id, Profile profile) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Profile(String name) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MessageNode(
            String id,
            String from,
            String timestamp,
            String type,
            Text text
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Text(String body) {
    }
}
