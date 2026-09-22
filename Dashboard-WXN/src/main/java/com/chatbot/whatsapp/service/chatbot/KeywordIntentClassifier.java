package com.chatbot.whatsapp.service.chatbot;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Classificador de intencao baseado em palavras-chave.
 *
 * <p>Nao e uma inteligencia artificial: e um casamento de texto simples,
 * suficiente para demonstrar o fluxo completo do chatbot (receber -> entender
 * -> responder) em um projeto academico. A normalizacao remove acentos e
 * caixa alta para tornar o casamento um pouco mais tolerante.</p>
 */
@Component
public class KeywordIntentClassifier implements IntentClassifier {

    private static final List<String> GREETING_KEYWORDS = List.of("oi", "ola", "bom dia", "boa tarde", "boa noite", "eae");
    private static final List<String> PRICE_KEYWORDS = List.of("preco", "valor", "quanto custa", "orcamento");
    private static final List<String> HELP_KEYWORDS = List.of("ajuda", "help", "menu", "comandos");

    @Override
    public ChatIntent classify(String messageContent) {
        String normalized = normalize(messageContent);

        if (containsAny(normalized, HELP_KEYWORDS)) {
            return ChatIntent.HELP;
        }
        if (containsAny(normalized, PRICE_KEYWORDS)) {
            return ChatIntent.PRICE_INQUIRY;
        }
        if (containsAny(normalized, GREETING_KEYWORDS)) {
            return ChatIntent.GREETING;
        }
        return ChatIntent.UNKNOWN;
    }

    private boolean containsAny(String normalizedText, List<String> keywords) {
        return keywords.stream().anyMatch(keyword -> containsWholeKeyword(normalizedText, keyword));
    }

    /**
     * Evita falsos positivos de busca por substring. Por exemplo, a saudacao
     * "oi" nao deve classificar palavras como "dois" ou "coisa".
     */
    private boolean containsWholeKeyword(String text, String keyword) {
        String regex = "(?<![\\p{L}\\p{N}])" + Pattern.quote(keyword) + "(?![\\p{L}\\p{N}])";
        return Pattern.compile(regex).matcher(text).find();
    }

    private String normalize(String text) {
        if (text == null) {
            return "";
        }
        String withoutAccents = Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return withoutAccents.toLowerCase(Locale.forLanguageTag("pt-BR")).trim();
    }
}
