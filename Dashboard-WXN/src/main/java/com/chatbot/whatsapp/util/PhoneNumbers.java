package com.chatbot.whatsapp.util;

import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Ponto central de tratamento de telefones do canal WhatsApp.
 *
 * <p>Converte telefones brasileiros escritos de formas diferentes - com ou sem
 * {@code +55}, com DDD entre parenteses, com o prefixo de discagem {@code 0} ou
 * com codigo de operadora - para o formato internacional usado pelo WhatsApp:
 * somente digitos, com pais e DDD (ex.: {@code 5511988887777}).</p>
 *
 * <p>Numeros estrangeiros sao aceitos quando ja estao em formato internacional:
 * com {@code +}/{@code 00} explicito, ou com 11 a 15 digitos que nao formam um
 * numero brasileiro valido. Numeros curtos e ambiguos (ex.: celular sem DDD)
 * sao recusados de proposito: e melhor nao gerar link do que gerar um link
 * para a pessoa errada.</p>
 */
public final class PhoneNumbers {

    private static final String BRAZIL_COUNTRY_CODE = "55";
    private static final String WHATSAPP_LINK_PREFIX = "https://wa.me/";
    private static final Pattern ALLOWED_CHARACTERS = Pattern.compile("[0-9+()\\-.\\s]+");

    /** DDDs em uso no Brasil (Anatel). */
    private static final Set<String> BRAZILIAN_AREA_CODES = Set.of(
            "11", "12", "13", "14", "15", "16", "17", "18", "19",
            "21", "22", "24", "27", "28",
            "31", "32", "33", "34", "35", "37", "38",
            "41", "42", "43", "44", "45", "46", "47", "48", "49",
            "51", "53", "54", "55",
            "61", "62", "63", "64", "65", "66", "67", "68", "69",
            "71", "73", "74", "75", "77", "79",
            "81", "82", "83", "84", "85", "86", "87", "88", "89",
            "91", "92", "93", "94", "95", "96", "97", "98", "99"
    );

    private PhoneNumbers() {
    }

    /**
     * Normaliza um telefone para o formato internacional, somente digitos.
     *
     * @param raw telefone em qualquer formato usual (ex.: {@code +55 (11) 98888-7777},
     *            {@code 11 98888-7777}, {@code 011 98888-7777}, {@code 5511988887777}).
     * @return o telefone normalizado, ou vazio quando ausente ou invalido.
     */
    public static Optional<String> normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String trimmed = raw.strip();
        if (!ALLOWED_CHARACTERS.matcher(trimmed).matches()) {
            return Optional.empty();
        }

        boolean explicitInternational = trimmed.startsWith("+");
        String digits = trimmed.replaceAll("\\D", "");
        if (!explicitInternational && digits.startsWith("00")) {
            // Prefixo internacional de discagem (ex.: 0055 11 ...).
            explicitInternational = true;
            digits = digits.substring(2);
        }
        if (digits.isEmpty()) {
            return Optional.empty();
        }

        if (explicitInternational) {
            return digits.startsWith(BRAZIL_COUNTRY_CODE)
                    ? brazilianInternational(digits)
                    : foreignInternational(digits);
        }
        if (digits.startsWith("0")) {
            return brazilianWithTrunkPrefix(digits);
        }

        Optional<String> national = brazilianNational(digits);
        if (national.isPresent()) {
            return national;
        }
        if (digits.startsWith(BRAZIL_COUNTRY_CODE)) {
            // Comeca com 55 e nao e um numero nacional: precisa ser um numero
            // brasileiro valido. Nao cai no caso estrangeiro.
            return brazilianInternational(digits);
        }
        return digits.length() >= 11 ? foreignInternational(digits) : Optional.empty();
    }

    /**
     * Link {@code https://wa.me/<telefone>} para abrir a conversa no WhatsApp,
     * sem texto pre-preenchido.
     *
     * @return o link, ou vazio quando o telefone for ausente ou invalido.
     */
    public static Optional<String> whatsAppLink(String raw) {
        return normalize(raw).map(phone -> WHATSAPP_LINK_PREFIX + phone);
    }

    /**
     * Mascara um telefone para uso em logs, mantendo apenas os 4 ultimos
     * digitos (ex.: {@code *********7777}).
     */
    public static String mask(String phone) {
        if (phone == null || phone.isBlank()) {
            return "(sem telefone)";
        }
        String digits = phone.replaceAll("\\D", "");
        if (digits.length() <= 4) {
            return "****";
        }
        return "*".repeat(digits.length() - 4) + digits.substring(digits.length() - 4);
    }

    /** 0 + DDD + numero, ou 0 + codigo de operadora (2 digitos) + DDD + numero. */
    private static Optional<String> brazilianWithTrunkPrefix(String digits) {
        if (digits.length() == 11 || digits.length() == 12) {
            return brazilianNational(digits.substring(1));
        }
        if (digits.length() == 13 || digits.length() == 14) {
            return brazilianNational(digits.substring(3));
        }
        return Optional.empty();
    }

    /** 55 + DDD + numero (12 ou 13 digitos). */
    private static Optional<String> brazilianInternational(String digits) {
        if (!digits.startsWith(BRAZIL_COUNTRY_CODE)) {
            return Optional.empty();
        }
        return brazilianNational(digits.substring(BRAZIL_COUNTRY_CODE.length()));
    }

    /** DDD + numero (10 ou 11 digitos), devolvido ja com o codigo do pais. */
    private static Optional<String> brazilianNational(String digits) {
        if (digits.length() != 10 && digits.length() != 11) {
            return Optional.empty();
        }
        String areaCode = digits.substring(0, 2);
        String subscriber = digits.substring(2);
        if (!BRAZILIAN_AREA_CODES.contains(areaCode) || !isValidSubscriber(subscriber)) {
            return Optional.empty();
        }
        return Optional.of(BRAZIL_COUNTRY_CODE + digits);
    }

    /**
     * Celular: 9 digitos iniciados por 9. Fixo: 8 digitos iniciados de 2 a 5.
     * Tambem aceita 8 digitos iniciados de 6 a 9, formato que o WhatsApp ainda
     * usa como identificador de contas criadas antes do nono digito.
     */
    private static boolean isValidSubscriber(String subscriber) {
        if (subscriber.length() == 9) {
            return subscriber.charAt(0) == '9';
        }
        return subscriber.length() == 8 && subscriber.charAt(0) >= '2';
    }

    /** E.164: de 8 a 15 digitos, sem zero inicial. */
    private static Optional<String> foreignInternational(String digits) {
        if (digits.length() < 8 || digits.length() > 15 || digits.charAt(0) == '0') {
            return Optional.empty();
        }
        return Optional.of(digits);
    }
}
