package com.chatbot.whatsapp.integration.whatsapp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Traduz o envelope do webhook da Meta em mensagens de dominio.
 *
 * <p>Isola o unico ponto do sistema que conhece o formato do provedor. E
 * deliberadamente tolerante: uma notificacao com campos faltando, de um tipo
 * desconhecido, ou de status de entrega (entregue/lida) nunca gera excecao -
 * apenas nao produz mensagens. A Meta reentrega qualquer notificacao que nao
 * receba 200, entao falhar por um campo inesperado criaria um loop de retry.</p>
 */
@Component
public class MetaWebhookPayloadParser {

    private static final Logger log = LoggerFactory.getLogger(MetaWebhookPayloadParser.class);

    /**
     * Extrai as mensagens enviadas por usuarios, em ordem de chegada.
     *
     * @return lista possivelmente vazia (ex.: notificacao de status de entrega).
     */
    public List<WhatsAppInboundMessage> parse(MetaWebhookPayload payload) {
        if (payload == null || payload.entry() == null) {
            return List.of();
        }

        List<WhatsAppInboundMessage> messages = new ArrayList<>();
        for (MetaWebhookPayload.Entry entry : payload.entry()) {
            if (entry == null || entry.changes() == null) {
                continue;
            }
            for (MetaWebhookPayload.Change change : entry.changes()) {
                if (change == null || change.value() == null) {
                    continue;
                }
                messages.addAll(parseValue(change.value()));
            }
        }
        return messages;
    }

    private List<WhatsAppInboundMessage> parseValue(MetaWebhookPayload.Value value) {
        if (value.messages() == null || value.messages().isEmpty()) {
            // Notificacoes de status (sent/delivered/read) chegam sem "messages".
            return List.of();
        }

        Map<String, String> namesByWaId = contactNames(value.contacts());

        List<WhatsAppInboundMessage> messages = new ArrayList<>();
        for (MetaWebhookPayload.MessageNode node : value.messages()) {
            if (node == null) {
                continue;
            }
            String phone = normalizePhone(node.from());
            if (node.id() == null || node.id().isBlank() || phone.isEmpty()) {
                log.warn("Mensagem do webhook ignorada por falta de id ou remetente");
                continue;
            }
            messages.add(new WhatsAppInboundMessage(
                    node.id(),
                    phone,
                    node.type(),
                    node.text() == null ? null : node.text().body(),
                    parseTimestamp(node.timestamp()),
                    namesByWaId.get(phone)
            ));
        }
        return messages;
    }

    private Map<String, String> contactNames(List<MetaWebhookPayload.Contact> contacts) {
        if (contacts == null) {
            return Map.of();
        }
        return contacts.stream()
                .filter(Objects::nonNull)
                .filter(contact -> contact.wa_id() != null && contact.profile() != null)
                .filter(contact -> contact.profile().name() != null)
                .collect(Collectors.toMap(
                        contact -> normalizePhone(contact.wa_id()),
                        contact -> contact.profile().name(),
                        (first, second) -> first
                ));
    }

    /**
     * A Meta envia o {@code wa_id} apenas com digitos, mas remover qualquer
     * outro caractere garante compatibilidade com a validacao de telefone do
     * dominio, que aceita somente digitos.
     */
    private String normalizePhone(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replaceAll("\\D", "");
    }

    /** O timestamp da Meta vem como epoch em segundos, em formato de texto. */
    private Instant parseTimestamp(String raw) {
        if (raw == null || raw.isBlank()) {
            return Instant.now();
        }
        try {
            return Instant.ofEpochSecond(Long.parseLong(raw.trim()));
        } catch (NumberFormatException ex) {
            log.warn("Timestamp invalido no webhook: '{}'; usando o horario atual", raw);
            return Instant.now();
        }
    }
}
