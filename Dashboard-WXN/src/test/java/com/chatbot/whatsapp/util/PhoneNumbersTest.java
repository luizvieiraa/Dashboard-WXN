package com.chatbot.whatsapp.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class PhoneNumbersTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(delimiter = '|', value = {
            // Formato internacional, com e sem formatacao
            "5511988887777|5511988887777",
            "+55 (11) 98888-7777|5511988887777",
            "+5511988887777|5511988887777",
            "0055 11 98888-7777|5511988887777",
            "55 11 98888.7777|5511988887777",
            // Formato nacional (DDD + numero)
            "11988887777|5511988887777",
            "(11) 98888-7777|5511988887777",
            "(21) 3333-4444|552133334444",
            "1133334444|551133334444",
            // Prefixo de discagem 0 e codigo de operadora
            "011 98888-7777|5511988887777",
            "0 21 11 98888-7777|5511988887777",
            // Identificador do WhatsApp sem o nono digito (contas antigas)
            "551188887777|551188887777",
            // Numeros estrangeiros ja em formato internacional
            "+1 202 555 0123|12025550123",
            "12025550123|12025550123",
            "351912345678|351912345678",
            "+44 20 7946 0958|442079460958"
    })
    void deveNormalizarParaFormatoInternacionalSomenteComDigitos(String input, String expected) {
        assertThat(PhoneNumbers.normalize(input)).contains(expected);
    }

    @ParameterizedTest(name = "invalido: [{0}]")
    @NullAndEmptySource
    @ValueSource(strings = {
            "   ",
            "988887777",            // celular sem DDD
            "12345678",             // curto e ambiguo
            "5500988887777",        // DDD inexistente
            "552088887777",         // DDD 20 nao existe
            "5511088887777",        // celular nao comeca com 9
            "55110888777",          // fixo comecando com 0
            "+55 11 988",           // incompleto
            "0800 123 4567",        // 0800 nao recebe WhatsApp
            "telefone: 11 98888-7777",
            "11 98888-7777 ramal 2",
            "+0 123 456 7890",
            "+123456",              // curto demais
            "+1234567890123456",    // mais de 15 digitos
            "abc"
    })
    void deveRecusarTelefoneAusenteOuInvalido(String input) {
        assertThat(PhoneNumbers.normalize(input)).isEmpty();
        assertThat(PhoneNumbers.whatsAppLink(input)).isEmpty();
    }

    @Test
    void deveMontarLinkDoWhatsAppSemTextoPreenchido() {
        assertThat(PhoneNumbers.whatsAppLink("+55 (11) 98888-7777"))
                .contains("https://wa.me/5511988887777");
    }

    @Test
    void deveMascararTelefoneParaLogs() {
        assertThat(PhoneNumbers.mask("5511988887777")).isEqualTo("*********7777");
        assertThat(PhoneNumbers.mask("+55 (11) 98888-7777")).isEqualTo("*********7777");
        assertThat(PhoneNumbers.mask("123")).isEqualTo("****");
        assertThat(PhoneNumbers.mask(null)).isEqualTo("(sem telefone)");
        assertThat(PhoneNumbers.mask("5511988887777")).doesNotContain("55119888");
    }
}
