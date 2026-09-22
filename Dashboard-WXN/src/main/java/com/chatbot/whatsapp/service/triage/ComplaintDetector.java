package com.chatbot.whatsapp.service.triage;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Detecta sinais explicitos de reclamacao sem depender de servico externo. */
@Component
public class ComplaintDetector {

    private static final List<Pattern> COMPLAINT_PATTERNS = List.of(
            pattern("reclamacao"),
            pattern("reclamar"),
            pattern("insatisfeito"),
            pattern("insatisfeita"),
            pattern("pessimo"),
            pattern("problema"),
            pattern("nao funciona"),
            pattern("nao resolveu"),
            pattern("cobranca indevida"),
            pattern("quero cancelar"),
            pattern("falar com (um )?atendente")
    );

    public boolean isComplaint(String message) {
        String normalized = normalize(message);
        return COMPLAINT_PATTERNS.stream().anyMatch(pattern -> pattern.matcher(normalized).find());
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
