package com.chatbot.whatsapp.service.triage;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Detecta pedidos explicitos para falar com uma pessoa, sem depender de servico
 * externo. Complementa o {@link ComplaintDetector}: o pedido de atendente e
 * atendido mesmo quando o assistente virtual esta desligado ou indisponivel.
 *
 * <p>Usa expressoes completas ("falar com uma pessoa", "atendimento humano") e
 * nao palavras soltas, para nao confundir respostas da triagem - um sobrenome
 * como "Pessoa", por exemplo - com um pedido de atendimento.</p>
 */
@Component
public class HumanRequestDetector {

    private static final String HUMAN = "(?:atendente|humano|humana|pessoa|alguem|operador|operadora|especialista)";
    private static final String ARTICLE = "(?:(?:um|uma|algum|alguma|o|a) )?";

    private static final List<Pattern> HUMAN_REQUEST_PATTERNS = List.of(
            pattern("(?:falar|conversar|falando) com " + ARTICLE + HUMAN),
            pattern("atendimento (?:humano|com " + ARTICLE + "pessoa)"),
            pattern("(?:quero|preciso(?: de)?|chama|chamar|chame|me passa|me passe|me transfere|transfere|"
                    + "transferir|transfira|me transfira)(?: (?:para|pra))? " + ARTICLE
                    + "(?:atendente|humano|humana|pessoa real|operador|operadora)"),
            pattern("(?:pessoa|atendente|humano) de verdade"),
            pattern("nao (?:quero|gosto de) (?:falar com )?(?:um )?(?:robo|bot|maquina)")
    );

    public boolean isHumanRequest(String message) {
        String normalized = normalize(message);
        return HUMAN_REQUEST_PATTERNS.stream().anyMatch(pattern -> pattern.matcher(normalized).find());
    }

    private static Pattern pattern(String expression) {
        return Pattern.compile("\\b(?:" + expression + ")\\b");
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        String withoutAccents = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return withoutAccents.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }
}
