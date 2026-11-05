package com.chatbot.whatsapp.service.chatbot;

import java.text.Normalizer;
import java.util.regex.Pattern;

/**
 * Prepara textos do cliente antes de envia-los ao assistente virtual.
 *
 * <ul>
 *   <li>Remove caracteres invisiveis e de controle, usados para esconder instrucoes.</li>
 *   <li>Troca {@code <} e {@code >} por aspas angulares, para que o cliente nao consiga
 *       imitar as marcacoes que delimitam os dados no prompt.</li>
 *   <li>Omite e-mails e sequencias longas de digitos (telefone, CPF, CNPJ, cartao):
 *       o assistente nao precisa desses dados para responder.</li>
 *   <li>Limita o tamanho, para controlar custo.</li>
 * </ul>
 */
public final class AssistantInputSanitizer {

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern INVISIBLE = Pattern.compile("[\\p{Cc}\\p{Cf}]");
    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+(?:\\.[\\w-]+)+");
    private static final Pattern LONG_NUMBER = Pattern.compile("\\d(?:[ .\\-]?\\d){7,}");

    private AssistantInputSanitizer() {
    }

    public static String sanitize(String text, int maxChars) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String value = Normalizer.normalize(text, Normalizer.Form.NFKC);
        value = WHITESPACE.matcher(value).replaceAll(" ");
        value = INVISIBLE.matcher(value).replaceAll("");
        value = value.replace('<', '‹').replace('>', '›');
        value = EMAIL.matcher(value).replaceAll("[e-mail omitido]");
        value = LONG_NUMBER.matcher(value).replaceAll("[número omitido]");
        value = value.strip();
        if (value.length() <= maxChars) {
            return value;
        }
        return value.substring(0, Math.max(0, maxChars - 1)).stripTrailing() + "…";
    }
}
