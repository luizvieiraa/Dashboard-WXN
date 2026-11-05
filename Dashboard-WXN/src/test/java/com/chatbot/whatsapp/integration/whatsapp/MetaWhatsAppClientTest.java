package com.chatbot.whatsapp.integration.whatsapp;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withTooManyRequests;

import java.net.ConnectException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class MetaWhatsAppClientTest {

    /** Os testes nao esperam de verdade entre as tentativas. */
    private static final MetaWhatsAppClient.Sleeper NO_WAIT = duration -> { };

    private static WhatsAppProperties properties(String accessToken, String phoneNumberId) {
        return new WhatsAppProperties(
                true,
                "https://graph.example.test",
                "v21.0",
                accessToken,
                phoneNumberId,
                "5511999999999",
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

        WhatsAppSendResult result = client.sendMessage("5511988887777", "Olá! Como posso ajudar?");

        server.verify();
        assertThat(result.sent()).isTrue();
        assertThat(result.providerMessageId()).isEqualTo("wamid.ENVIADA");
    }

    @Test
    void naoDevePropagarExcecaoQuandoProvedorFalhar() {
        WhatsAppProperties properties = properties("token-secreto", "1234567890");
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MetaWhatsAppClient client = new MetaWhatsAppClient(properties, builder.build(), NO_WAIT);

        // HTTP 5xx e temporario: o envio e tentado novamente ate o limite.
        server.expect(ExpectedCount.times(MetaWhatsAppClient.MAX_ATTEMPTS),
                        requestTo("https://graph.example.test/v21.0/1234567890/messages"))
                .andRespond(withServerError());

        // A mensagem do cliente ja foi processada e persistida; uma falha no
        // envio nao pode derrubar o fluxo nem fazer a Meta reentregar o webhook.
        assertThatCode(() -> assertThat(client.sendMessage("5511988887777", "texto").sent()).isFalse())
                .doesNotThrowAnyException();
        server.verify();
    }

    @Test
    void deveTentarNovamenteQuandoAMetaLimitarOEnvio() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        List<Duration> waits = new ArrayList<>();
        MetaWhatsAppClient client = new MetaWhatsAppClient(
                properties("token-secreto", "1234567890"), builder.build(), waits::add);

        server.expect(requestTo("https://graph.example.test/v21.0/1234567890/messages"))
                .andRespond(withTooManyRequests());
        server.expect(requestTo("https://graph.example.test/v21.0/1234567890/messages"))
                .andRespond(withSuccess("{\"messages\":[{\"id\":\"wamid.APOS_LIMITE\"}]}",
                        MediaType.APPLICATION_JSON));

        WhatsAppSendResult result = client.sendMessage("5511988887777", "texto");

        server.verify();
        assertThat(result.sent()).isTrue();
        assertThat(result.providerMessageId()).isEqualTo("wamid.APOS_LIMITE");
        assertThat(waits).hasSize(1);
    }

    @Test
    void deveTentarNovamenteCodigoDeLimiteDaCloudApiMesmoComHttp400() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MetaWhatsAppClient client = new MetaWhatsAppClient(
                properties("token-secreto", "1234567890"), builder.build(), NO_WAIT);

        server.expect(requestTo("https://graph.example.test/v21.0/1234567890/messages"))
                .andRespond(withBadRequest().contentType(MediaType.APPLICATION_JSON).body("""
                        {"error": {"message": "(#131056) (Business Account, Consumer Account) pair rate limit hit",
                                   "type": "OAuthException", "code": 131056}}
                        """));
        server.expect(requestTo("https://graph.example.test/v21.0/1234567890/messages"))
                .andRespond(withSuccess("{\"messages\":[{\"id\":\"wamid.OK\"}]}", MediaType.APPLICATION_JSON));

        assertThat(client.sendMessage("5511988887777", "texto").sent()).isTrue();
        server.verify();
    }

    @Test
    void naoDeveRepetirErroDefinitivoComoJanelaDe24HorasEncerrada() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MetaWhatsAppClient client = new MetaWhatsAppClient(
                properties("token-secreto", "1234567890"), builder.build(), NO_WAIT);

        server.expect(ExpectedCount.once(), requestTo("https://graph.example.test/v21.0/1234567890/messages"))
                .andRespond(withBadRequest().contentType(MediaType.APPLICATION_JSON).body("""
                        {"error": {"message": "(#131047) Re-engagement message", "code": 131047}}
                        """));

        WhatsAppSendResult result = client.sendMessage("5511988887777", "texto");

        server.verify();
        assertThat(result.sent()).isFalse();
        assertThat(result.error()).contains("HTTP 400", "131047");
    }

    @Test
    void deveTentarNovamenteQuandoAConexaoFalharAntesDoEnvio() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MetaWhatsAppClient client = new MetaWhatsAppClient(
                properties("token-secreto", "1234567890"), builder.build(), NO_WAIT);

        server.expect(requestTo("https://graph.example.test/v21.0/1234567890/messages"))
                .andRespond(withException(new ConnectException("Connection refused")));
        server.expect(requestTo("https://graph.example.test/v21.0/1234567890/messages"))
                .andRespond(withSuccess("{\"messages\":[{\"id\":\"wamid.RECONECTADO\"}]}",
                        MediaType.APPLICATION_JSON));

        assertThat(client.sendMessage("5511988887777", "texto").sent()).isTrue();
        server.verify();
    }

    @Test
    void naoDeveRepetirQuandoOTempoEsgotarDepoisDoEnvio() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MetaWhatsAppClient client = new MetaWhatsAppClient(
                properties("token-secreto", "1234567890"), builder.build(), NO_WAIT);

        // A Meta pode ter entregue a mensagem: repetir duplicaria a resposta ao cliente.
        server.expect(ExpectedCount.once(), requestTo("https://graph.example.test/v21.0/1234567890/messages"))
                .andRespond(withException(new HttpTimeoutException("request timed out")));

        assertThat(client.sendMessage("5511988887777", "texto").sent()).isFalse();
        server.verify();
    }

    @Test
    void naoDeveChamarApiQuandoCredenciaisEstiveremIncompletas() {
        WhatsAppProperties properties = properties("", "");
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MetaWhatsAppClient client = new MetaWhatsAppClient(properties, builder.build());

        WhatsAppSendResult result = client.sendMessage("5511988887777", "texto");

        // Nenhuma requisicao esperada: o cliente apenas registra erro em log.
        server.verify();
        assertThat(result.sent()).isFalse();
    }

    @Test
    void deveMontarUrlSemBarraDuplicadaQuandoApiUrlTerminarComBarra() {
        WhatsAppProperties properties = new WhatsAppProperties(
                true, "https://graph.example.test/", "v21.0", "token", "999",
                "5511999999999", "verify", "secret");
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MetaWhatsAppClient client = new MetaWhatsAppClient(properties, builder.build());

        server.expect(requestTo("https://graph.example.test/v21.0/999/messages"))
                .andRespond(withSuccess("{\"messages\":[{\"id\":\"wamid.X\"}]}",
                        MediaType.APPLICATION_JSON));

        WhatsAppSendResult result = client.sendMessage("5511988887777", "texto");

        server.verify();
        assertThat(result.sent()).isTrue();
    }
}
