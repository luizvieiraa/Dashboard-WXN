package com.chatbot.whatsapp.service.chatbot;

import com.chatbot.whatsapp.integration.ai.AssistantResources;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Ultima verificacao do texto gerado pelo assistente virtual antes do envio.
 *
 * <p>O prompt ja proibe inventar informacoes, mas uma mensagem maliciosa do
 * cliente (prompt injection) ou uma alucinacao do modelo pode contornar
 * instrucoes. Por isso as regras criticas sao verificadas em codigo, contra a
 * base de conhecimento:</p>
 * <ul>
 *   <li>links so sao aceitos quando aparecem na base de conhecimento;</li>
 *   <li>valores em reais so sao aceitos quando aparecem na base de conhecimento;</li>
 *   <li>respostas que citam as marcacoes internas do prompt sao descartadas;</li>
 *   <li>o tamanho e limitado.</li>
 * </ul>
 * <p>Quando uma regra falha, a resposta e descartada e o fluxo local assume.</p>
 */
@Component
public class AssistantReplyGuard {

    static final int MAX_REPLY_CHARS = 700;

    private static final Logger log = LoggerFactory.getLogger(AssistantReplyGuard.class);
    private static final Pattern URL = Pattern.compile("(?i)(?:https?://|www\\.)[^\\s]+");
    private static final Pattern MONEY = Pattern.compile(
            "(?i)R\\$\\s*(\\d[\\d.,]*)|(\\d[\\d.,]*)\\s*(?:reais|real)\\b");
    private static final List<String> INTERNAL_MARKERS = List.of(
            "dados_do_atendimento", "base_de_conhecimento", "mensagem_atual");

    private final AssistantResources resources;

    public AssistantReplyGuard(AssistantResources resources) {
        this.resources = resources;
    }

    /** @return o texto pronto para envio, ou vazio quando a resposta deve ser descartada. */
    public Optional<String> check(String reply) {
        if (reply == null || reply.isBlank()) {
            return Optional.empty();
        }
        String text = reply.strip();
        String lower = text.toLowerCase(Locale.ROOT);
        if (INTERNAL_MARKERS.stream().anyMatch(lower::contains)) {
            return reject("resposta cita marcacoes internas do prompt");
        }

        String knowledgeBase = resources.knowledgeBase();
        Matcher url = URL.matcher(text);
        while (url.find()) {
            String link = url.group().replaceAll("[.,;:!?)\\]]+$", "");
            if (!knowledgeBase.contains(link)) {
                return reject("link que nao esta na base de conhecimento");
            }
        }

        Set<String> knownAmounts = amounts(knowledgeBase);
        for (String amount : amounts(text)) {
            if (!knownAmounts.contains(amount)) {
                return reject("valor em reais que nao esta na base de conhecimento");
            }
        }

        return Optional.of(truncate(text));
    }

    /** Valores monetarios citados, somente com os digitos (R$ 1.500,00 -> 150000). */
    private static Set<String> amounts(String text) {
        Set<String> amounts = new HashSet<>();
        Matcher matcher = MONEY.matcher(text);
        while (matcher.find()) {
            String value = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            amounts.add(value.replaceAll("\\D", ""));
        }
        return amounts;
    }

    /** Corta no fim da ultima frase completa que couber no limite. */
    private static String truncate(String text) {
        if (text.length() <= MAX_REPLY_CHARS) {
            return text;
        }
        String head = text.substring(0, MAX_REPLY_CHARS);
        int sentenceEnd = Math.max(head.lastIndexOf(". "), Math.max(head.lastIndexOf("! "), head.lastIndexOf("? ")));
        if (sentenceEnd > MAX_REPLY_CHARS / 2) {
            return head.substring(0, sentenceEnd + 1);
        }
        return head.substring(0, MAX_REPLY_CHARS - 1).stripTrailing() + "…";
    }

    private Optional<String> reject(String reason) {
        log.warn("Resposta do assistente virtual descartada: {}", reason);
        return Optional.empty();
    }
}
