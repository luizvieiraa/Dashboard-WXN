package com.chatbot.whatsapp.integration.whatsapp;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Valida a assinatura {@code X-Hub-Signature-256} das notificacoes da Meta.
 *
 * <p>Esta e a defesa real do webhook: a URL e publica, entao qualquer pessoa
 * poderia enviar um POST fingindo ser a Meta e injetar mensagens no sistema. A
 * Meta assina o corpo de cada notificacao com HMAC-SHA256 usando o App Secret,
 * e o header vem no formato {@code sha256=<hex>}.</p>
 *
 * <p>O {@code webhook-verify-token} NAO cumpre esse papel: ele e usado apenas
 * uma vez, no handshake {@code GET} de cadastro do webhook.</p>
 *
 * <p>A verificacao usa o corpo <em>cru</em> da requisicao. Reserializar o JSON
 * mudaria os bytes (espacos, ordem de campos) e invalidaria a assinatura.</p>
 */
@Component
public class WhatsAppSignatureVerifier {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppSignatureVerifier.class);
    private static final String HEADER_PREFIX = "sha256=";
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final WhatsAppProperties properties;

    public WhatsAppSignatureVerifier(WhatsAppProperties properties) {
        this.properties = properties;
    }

    /**
     * Informa se a validacao de assinatura esta ativa. Fica inativa quando o
     * App Secret nao foi configurado, permitindo subir a integracao por partes.
     */
    public boolean isConfigured() {
        return properties.appSecret() != null && !properties.appSecret().isBlank();
    }

    /**
     * @param rawBody   bytes exatos recebidos no corpo da requisicao.
     * @param signature valor do header {@code X-Hub-Signature-256}.
     * @return {@code true} quando a assinatura confere, ou quando a validacao
     *         esta desativada por falta de App Secret (nesse caso, loga um aviso).
     */
    public boolean isValid(byte[] rawBody, String signature) {
        if (!isConfigured()) {
            log.warn("WHATSAPP_APP_SECRET nao configurado: a assinatura do webhook nao esta sendo "
                    + "validada. Configure o App Secret antes de usar em producao.");
            return true;
        }
        if (rawBody == null || signature == null || !signature.startsWith(HEADER_PREFIX)) {
            log.warn("Notificacao recebida sem header X-Hub-Signature-256 valido");
            return false;
        }

        String expected = hmacSha256Hex(rawBody, properties.appSecret());
        String received = signature.substring(HEADER_PREFIX.length()).trim();

        // Comparacao em tempo constante: evita distinguir uma assinatura
        // parcialmente correta pelo tempo de resposta.
        boolean valid = MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                received.getBytes(StandardCharsets.UTF_8)
        );
        if (!valid) {
            log.warn("Assinatura do webhook nao confere; notificacao rejeitada");
        }
        return valid;
    }

    private String hmacSha256Hex(byte[] body, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(body));
        } catch (java.security.GeneralSecurityException ex) {
            // HmacSHA256 e obrigatorio em qualquer JVM; chegar aqui indica um
            // ambiente quebrado, nao um erro de entrada.
            throw new IllegalStateException("Nao foi possivel calcular o HMAC do webhook", ex);
        }
    }
}
