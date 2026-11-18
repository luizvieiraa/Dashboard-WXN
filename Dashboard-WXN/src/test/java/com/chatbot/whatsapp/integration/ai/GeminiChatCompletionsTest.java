package com.chatbot.whatsapp.integration.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class GeminiChatCompletionsTest {
    private static final String URL = "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions";
    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final OpenAiResponsesClient client = new OpenAiResponsesClient(
            new AiProperties(true, URL, "test-key", "gemini-3.5-flash-lite", 20, 400),
            builder.build(), new AssistantResources("Regras oficiais", "Atendimento em portugues"));

    private AiTriageRequest request() {
        return new AiTriageRequest("CONVERSA", "Maria", "Empresa", "Suporte", List.of(), "Preciso de ajuda");
    }

    private String response(String content, String reason) throws Exception {
        return new ObjectMapper().writeValueAsString(Map.of("choices", List.of(Map.of(
                "finish_reason", reason, "message", Map.of("role", "assistant", "content", content)))));
    }

    @Test
    void enviaContratoGeminiEInterpretaDecisao() throws Exception {
        server.expect(requestTo(URL))
                .andExpect(header("Authorization", "Bearer test-key"))
                .andExpect(jsonPath("$.model").value("gemini-3.5-flash-lite"))
                .andExpect(jsonPath("$.max_tokens").value(400))
                .andExpect(jsonPath("$.messages[0].role").value("system"))
                .andExpect(jsonPath("$.messages[0].content").value(org.hamcrest.Matchers.containsString("Regras oficiais")))
                .andExpect(jsonPath("$.messages[1].role").value("user"))
                .andExpect(jsonPath("$.response_format.json_schema.schema.required").isArray())
                .andExpect(jsonPath("$.input").doesNotExist())
                .andExpect(jsonPath("$.store").doesNotExist())
                .andRespond(withSuccess(response("""
                        {"intent":"SUPPORT","action":"ASK_MORE","confidence":0.9,
                         "sensitive":false,"reply":"Qual problema voce encontrou?"}
                        """, "stop"), MediaType.APPLICATION_JSON));
        assertThat(client.decide(request())).hasValueSatisfying(decision -> {
            assertThat(decision.action()).isEqualTo(AiTriageDecision.Action.ASK_MORE);
            assertThat(decision.reply()).isEqualTo("Qual problema voce encontrou?");
        });
        server.verify();
    }

    @Test
    void geraRespostaLivrePeloMesmoEndpoint() throws Exception {
        server.expect(requestTo(URL))
                .andExpect(jsonPath("$.response_format").doesNotExist())
                .andRespond(withSuccess(response("Como posso ajudar?", "stop"), MediaType.APPLICATION_JSON));
        assertThat(client.generateReply("Oi", "Cliente")).contains("Como posso ajudar?");
        server.verify();
    }

    @Test
    void descartaRespostaTruncadaMesmoComJsonValido() throws Exception {
        server.expect(requestTo(URL)).andRespond(withSuccess(response("""
                {"intent":"SUPPORT","action":"RESOLVE","confidence":0.9,"sensitive":false,"reply":"Oi"}
                """, "length"), MediaType.APPLICATION_JSON));
        assertThat(client.decide(request())).isEmpty();
        assertThat(client.status().failing()).isTrue();
        assertThat(client.status().lastFailure()).contains("finish_reason=length", "AI_MAX_OUTPUT_TOKENS");
        server.verify();
    }

    @Test
    void registraStatusECodigoDoGeminiSemOTextoLivreDoErro() {
        server.expect(requestTo(URL)).andRespond(withStatus(org.springframework.http.HttpStatus.FORBIDDEN)
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        [{"error": {"code": 403, "status": "PERMISSION_DENIED",
                          "message": "The Gemini API free tier is not available for this project 123456789012."}}]
                        """));

        assertThat(client.decide(request())).isEmpty();

        assertThat(client.status().failing()).isTrue();
        assertThat(client.status().lastFailure())
                .startsWith("HTTP 403 (PERMISSION_DENIED): acesso negado")
                .doesNotContain("free tier", "123456789012", "test-key");
        server.verify();
    }

    @Test
    void sucessoDepoisDeUmaFalhaLimpaOEstadoDeErro() throws Exception {
        server.expect(requestTo(URL)).andRespond(withStatus(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS));
        server.expect(requestTo(URL)).andRespond(withSuccess(response("""
                {"intent":"GREETING","action":"RESOLVE","confidence":0.95,"sensitive":false,"reply":"Oi!"}
                """, "stop"), MediaType.APPLICATION_JSON));

        client.decide(request());
        assertThat(client.status().lastFailure()).isEqualTo("HTTP 429: cota ou limite de requisicoes esgotado");
        assertThat(client.decide(request())).isPresent();

        assertThat(client.status().failing()).isFalse();
        assertThat(client.status().lastSuccessAt()).isNotNull();
        server.verify();
    }

    @Test
    void preservaFluxoLocalQuandoCotaAcaba() {
        server.expect(requestTo(URL)).andRespond(withStatus(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS));
        assertThat(client.decide(request())).isEmpty();
        server.verify();
    }

    @Test
    void descartaDecisaoInvalida() throws Exception {
        server.expect(requestTo(URL)).andRespond(withSuccess(response("{\"reply\":\"Oi\"}", "stop"), MediaType.APPLICATION_JSON));
        assertThat(client.decide(request())).isEmpty();
        server.verify();
    }
}
