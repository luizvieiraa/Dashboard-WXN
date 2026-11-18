package com.chatbot.whatsapp.integration.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class OpenAiResponsesClientTest {

    @Test
    void deveGerarRespostaQuandoIntegracaoEstiverHabilitada() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AiProperties properties = new AiProperties(
                true,
                "https://ai.example.test/v1/responses",
                "secret-test",
                "model-test",
                5,
                200
        );
        OpenAiResponsesClient client = new OpenAiResponsesClient(properties, builder.build());

        server.expect(requestTo(properties.apiUrl()))
                .andExpect(header("Authorization", "Bearer secret-test"))
                .andExpect(jsonPath("$.model").value("model-test"))
                .andExpect(jsonPath("$.store").value(false))
                .andRespond(withSuccess("""
                        {
                          "output": [{
                            "type": "message",
                            "content": [{"type": "output_text", "text": "Resposta personalizada"}]
                          }]
                        }
                        """, MediaType.APPLICATION_JSON));

        Optional<String> reply = client.generateReply("Mensagem", "nome=Maria");

        assertThat(reply).contains("Resposta personalizada");
        server.verify();
    }

    @Test
    void deveUsarFallbackSemChamarApiQuandoIntegracaoEstiverDesabilitada() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AiProperties properties = new AiProperties(false, "", "", "", 10, 300);
        OpenAiResponsesClient client = new OpenAiResponsesClient(properties, builder.build());

        assertThat(client.generateReply("Mensagem", "Contexto")).isEmpty();
        server.verify();
    }

    @Test
    void naoExpoeOTextoDoErroDoProvedor() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AiProperties properties = new AiProperties(
                true, "https://ai.example.test/v1/responses", "secret-test", "model-test", 5, 200);
        OpenAiResponsesClient client = new OpenAiResponsesClient(properties, builder.build());

        // A OpenAI repete parte da chave recusada na mensagem de erro.
        server.expect(requestTo(properties.apiUrl())).andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"error": {"message": "Incorrect API key provided: sk-proj-abc1****************xyz9.",
                          "type": "invalid_request_error", "param": null, "code": "invalid_api_key"}}
                        """));

        assertThat(client.generateReply("Mensagem", "Contexto")).isEmpty();

        server.verify();
        assertThat(client.status().lastFailure())
                .isEqualTo("HTTP 401 (invalid_api_key): chave recusada; confira AI_API_KEY");
    }

    @Test
    void respostaSemTextoRegistraFalhaNoPainel() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        AiProperties properties = new AiProperties(
                true, "https://ai.example.test/v1/responses", "secret-test", "model-test", 5, 200);
        OpenAiResponsesClient client = new OpenAiResponsesClient(properties, builder.build());

        server.expect(requestTo(properties.apiUrl())).andRespond(withSuccess("""
                {"output": [{"type": "message", "content": [{"type": "output_text", "text": "Oi!"}]}]}
                """, MediaType.APPLICATION_JSON));
        server.expect(requestTo(properties.apiUrl())).andRespond(withSuccess("""
                {"output": []}
                """, MediaType.APPLICATION_JSON));

        assertThat(client.generateReply("Oi", "Contexto")).contains("Oi!");
        assertThat(client.generateReply("Oi de novo", "Contexto")).isEmpty();

        server.verify();
        assertThat(client.status().failing()).isTrue();
        assertThat(client.status().lastFailure()).isEqualTo("resposta sem texto do provedor");
    }
}
