package com.chatbot.whatsapp.integration.whatsapp;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class MetaWhatsAppClientTest {

    private static WhatsAppProperties properties(String accessToken, String phoneNumberId) {
        return new WhatsAppProperties(
                true,
                "https://graph.example.test",
                "v21.0",
                accessToken,
                phoneNumberId,
                "verify-token",
                "app-secret"
        );
    }

    @Test
    void deveEnviarMensagemDeTextoNoFormatoDaCloudApi() {
        WhatsAppProperties properties = properties("token-secreto", "1234567890");
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MetaWhatsAppClient client = new MetaWhatsAppClient(properties, builder.build());

        server.expect(requestTo("https://graph.example.test/v21.0/1234567890/messages"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer token-secreto"))
                .andExpect(jsonPath("$.messaging_product").value("whatsapp"))
                .andExpect(jsonPath("$.recipient_type").value("individual"))
                .andExpect(jsonPath("$.to").value("5511988887777"))
                .andExpect(jsonPath("$.type").value("text"))
                .andExpect(jsonPath("$.text.body").value("Olá! Como posso ajudar?"))
                .andRespond(withSuccess("""
                        {"messages": [{"id": "wamid.ENVIADA"}]}
                        """, MediaType.APPLICATION_JSON));

        client.sendMessage("5511988887777", "Olá! Como posso ajudar?");

        server.verify();
    }

    @Test
    void naoDevePropagarExcecaoQuandoProvedorFalhar() {
        WhatsAppProperties properties = properties("token-secreto", "1234567890");
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MetaWhatsAppClient client = new MetaWhatsAppClient(properties, builder.build());

        server.expect(requestTo("https://graph.example.test/v21.0/1234567890/messages"))
                .andRespond(withServerError());

        // A mensagem do cliente ja foi processada e persistida; uma falha no
        // envio nao pode derrubar o fluxo nem fazer a Meta reentregar o webhook.
        assertThatCode(() -> client.sendMessage("5511988887777", "texto"))
                .doesNotThrowAnyException();
        server.verify();
    }

    @Test
    void naoDeveChamarApiQuandoCredenciaisEstiveremIncompletas() {
        WhatsAppProperties properties = properties("", "");
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MetaWhatsAppClient client = new MetaWhatsAppClient(properties, builder.build());

        client.sendMessage("5511988887777", "texto");

        // Nenhuma requisicao esperada: o cliente apenas registra erro em log.
        server.verify();
    }

    @Test
    void deveMontarUrlSemBarraDuplicadaQuandoApiUrlTerminarComBarra() {
        WhatsAppProperties properties = new WhatsAppProperties(
                true, "https://graph.example.test/", "v21.0", "token", "999", "verify", "secret");
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MetaWhatsAppClient client = new MetaWhatsAppClient(properties, builder.build());

        server.expect(requestTo("https://graph.example.test/v21.0/999/messages"))
                .andRespond(withSuccess("{\"messages\":[{\"id\":\"wamid.X\"}]}",
                        MediaType.APPLICATION_JSON));

        client.sendMessage("5511988887777", "texto");

        server.verify();
    }
}
