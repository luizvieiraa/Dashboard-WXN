package com.chatbot.whatsapp.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chatbot.whatsapp.repository.CustomerRepository;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Com um App Secret configurado, o webhook passa a exigir a assinatura
 * {@code X-Hub-Signature-256} em toda notificacao.
 *
 * <p>Sem isso, qualquer pessoa que descobrisse a URL publica poderia injetar
 * mensagens falsas no sistema. A correcao do calculo do HMAC em si e verificada
 * contra um vetor externo em {@code WhatsAppSignatureVerifierTest}; aqui o foco
 * e o comportamento HTTP do controller.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "whatsapp.app-secret=segredo-do-app")
class MetaWebhookSignatureEnforcementTest {

    private static final String WEBHOOK_PATH = "/api/v1/webhook/whatsapp/meta";
    private static final String APP_SECRET = "segredo-do-app";
    private static final String PHONE = "5511970003001";

    private static final String PAYLOAD = """
            {"entry":[{"changes":[{"value":{"messages":[{\
            "from":"%s","id":"wamid.ASSINADA","timestamp":"1749416383",\
            "type":"text","text":{"body":"Olá"}}]}}]}]}\
            """.formatted(PHONE);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CustomerRepository customerRepository;

    private static String sign(String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(APP_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + HexFormat.of()
                .formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void deveAceitarNotificacaoAssinadaCorretamente() throws Exception {
        mockMvc.perform(post(WEBHOOK_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Hub-Signature-256", sign(PAYLOAD))
                        .content(PAYLOAD))
                .andExpect(status().isOk());

        assertThat(customerRepository.findByPhoneNumber(PHONE)).isPresent();
    }

    @Test
    void deveRejeitarNotificacaoSemAssinatura() throws Exception {
        mockMvc.perform(post(WEBHOOK_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PAYLOAD))
                .andExpect(status().isForbidden());
    }

    @Test
    void deveRejeitarNotificacaoComAssinaturaInvalida() throws Exception {
        mockMvc.perform(post(WEBHOOK_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Hub-Signature-256", "sha256=" + "0".repeat(64))
                        .content(PAYLOAD))
                .andExpect(status().isForbidden());
    }

    @Test
    void deveRejeitarQuandoOCorpoForAlteradoDepoisDeAssinado() throws Exception {
        String assinaturaDoOriginal = sign(PAYLOAD);
        String corpoAlterado = PAYLOAD.replace("Olá", "Mensagem injetada");

        mockMvc.perform(post(WEBHOOK_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Hub-Signature-256", assinaturaDoOriginal)
                        .content(corpoAlterado))
                .andExpect(status().isForbidden());
    }
}
