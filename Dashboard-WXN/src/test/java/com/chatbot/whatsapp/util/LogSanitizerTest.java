package com.chatbot.whatsapp.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class LogSanitizerTest {

    @Test
    void deveDescreverAExcecaoSemAMensagemQuePodeConterDadosPessoais() {
        RuntimeException error = new DataIntegrityViolationException(
                "could not execute statement",
                new IllegalStateException("Key (phone_number)=(5511988887777) already exists"));

        String description = LogSanitizer.describe(error);

        assertThat(description)
                .startsWith("DataIntegrityViolationException <- IllegalStateException")
                .contains("LogSanitizerTest")
                .doesNotContain("5511988887777")
                .doesNotContain("phone_number");
    }

    @Test
    void deveTolerarExcecaoNula() {
        assertThat(LogSanitizer.describe(null)).isEqualTo("erro desconhecido");
    }

    @Test
    void deveReferenciarIdDoWhatsAppSemOTrechoQueCarregaOTelefone() {
        // O inicio do wamid e o telefone 16503879439 em base64 ("MTY1MDM4Nzk0Mzk").
        String wamid = "wamid.HBgLMTY1MDM4Nzk0MzkVAgASGBQzQTRBNTU2QkVFNzlBQzZBRTMA";

        assertThat(LogSanitizer.messageRef(wamid))
                .isEqualTo("…NzlBQzZBRTMA")
                .doesNotContain("MTY1MDM4Nzk0Mzk");
        assertThat(LogSanitizer.messageRef("wamid.TESTE1")).isEqualTo("wamid.TESTE1");
        assertThat(LogSanitizer.messageRef(null)).isEqualTo("(sem id)");
    }
}
