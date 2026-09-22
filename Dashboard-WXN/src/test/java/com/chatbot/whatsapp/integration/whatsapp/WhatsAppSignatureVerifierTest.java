package com.chatbot.whatsapp.integration.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class WhatsAppSignatureVerifierTest {

    private static final String APP_SECRET = "test-app-secret";
    private static final String BODY = "{\"object\":\"whatsapp_business_account\"}";

    /**
     * HMAC-SHA256 de BODY com a chave APP_SECRET, calculado fora deste projeto
     * (via System.Security.Cryptography.HMACSHA256) para que o teste valide a
     * implementacao contra um valor independente, e nao contra ela mesma.
     */
    private static final String EXPECTED_HEX =
            "b6978b21c4467654c466607663db9b43fae44b71083568df403e0a077089208e";

    private WhatsAppSignatureVerifier verifierWith(String appSecret) {
        return new WhatsAppSignatureVerifier(new WhatsAppProperties(
                true,
                "https://graph.example.test",
                "v21.0",
                "token",
                "123",
                "verify",
                appSecret
        ));
    }

    @Test
    void deveAceitarAssinaturaCorreta() {
        WhatsAppSignatureVerifier verifier = verifierWith(APP_SECRET);

        boolean valid = verifier.isValid(
                BODY.getBytes(StandardCharsets.UTF_8),
                "sha256=" + EXPECTED_HEX
        );

        assertThat(valid).isTrue();
        assertThat(verifier.isConfigured()).isTrue();
    }

    @Test
    void deveRejeitarAssinaturaIncorreta() {
        WhatsAppSignatureVerifier verifier = verifierWith(APP_SECRET);

        assertThat(verifier.isValid(
                BODY.getBytes(StandardCharsets.UTF_8),
                "sha256=" + "0".repeat(64)
        )).isFalse();
    }

    @Test
    void deveRejeitarQuandoCorpoFoiAlterado() {
        WhatsAppSignatureVerifier verifier = verifierWith(APP_SECRET);

        assertThat(verifier.isValid(
                "{\"object\":\"outro\"}".getBytes(StandardCharsets.UTF_8),
                "sha256=" + EXPECTED_HEX
        )).isFalse();
    }

    @Test
    void deveRejeitarQuandoHeaderEstiverAusenteOuMalFormado() {
        WhatsAppSignatureVerifier verifier = verifierWith(APP_SECRET);
        byte[] body = BODY.getBytes(StandardCharsets.UTF_8);

        assertThat(verifier.isValid(body, null)).isFalse();
        assertThat(verifier.isValid(body, EXPECTED_HEX)).isFalse();
        assertThat(verifier.isValid(body, "sha1=" + EXPECTED_HEX)).isFalse();
    }

    @Test
    void deveIgnorarValidacaoQuandoAppSecretNaoEstiverConfigurado() {
        WhatsAppSignatureVerifier verifier = verifierWith("");

        assertThat(verifier.isConfigured()).isFalse();
        // Permite subir a integracao antes de configurar o App Secret; o
        // comportamento e registrado como aviso em log.
        assertThat(verifier.isValid(BODY.getBytes(StandardCharsets.UTF_8), null)).isTrue();
    }
}
