package com.chatbot.whatsapp.integration.whatsapp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Envio real de mensagens pela Meta WhatsApp Cloud API.
 *
 * <p>Ativado por {@code whatsapp.enabled=true}; caso contrario o bean de
 * {@link WhatsAppClient} continua sendo o {@link MockWhatsAppSender}. Como as
 * duas implementacoes sao mutuamente exclusivas por configuracao, a camada de
 * negocio nunca precisa saber qual esta ativa.</p>
 *
 * <p>Segue o mesmo padrao do {@code OpenAiResponsesClient}: uma falha do
 * provedor externo e registrada em log, mas nunca propaga excecao - a mensagem
 * ja foi processada e persistida, e derrubar o fluxo faria a Meta reentregar a
 * notificacao e o cliente ser atendido duas vezes.</p>
 */
@Component
@ConditionalOnProperty(prefix = "whatsapp", name = "enabled", havingValue = "true")
public class MetaWhatsAppClient implements WhatsAppClient {

    private static final Logger log = LoggerFactory.getLogger(MetaWhatsAppClient.class);
    private static final int TIMEOUT_SECONDS = 15;

    private final WhatsAppProperties properties;
    private final RestClient restClient;

    @Autowired
    public MetaWhatsAppClient(WhatsAppProperties properties, RestClient.Builder restClientBuilder) {
        this(properties, createClient(restClientBuilder));
    }

    MetaWhatsAppClient(WhatsAppProperties properties, RestClient restClient) {
        this.properties = properties;
        this.restClient = restClient;
    }

    @Override
    public void sendMessage(String phoneNumber, String text) {
        if (isBlank(properties.apiUrl())
                || isBlank(properties.apiVersion())
                || isBlank(properties.accessToken())
                || isBlank(properties.phoneNumberId())) {
            log.error("whatsapp.enabled=true mas WHATSAPP_API_URL, WHATSAPP_API_VERSION, "
                    + "WHATSAPP_ACCESS_TOKEN ou WHATSAPP_PHONE_NUMBER_ID nao foi configurado. "
                    + "Mensagem para {} NAO foi enviada.", phoneNumber);
            return;
        }

        try {
            SendMessageResponse response = restClient.post()
                    .uri(properties.sendMessageUrl())
                    .headers(headers -> headers.setBearerAuth(properties.accessToken()))
                    .body(new SendMessageRequest(
                            "whatsapp",
                            "individual",
                            phoneNumber,
                            "text",
                            new TextBody(false, text)
                    ))
                    .retrieve()
                    .body(SendMessageResponse.class);

            log.info("Mensagem enviada ao WhatsApp de {} (id do provedor: {})",
                    phoneNumber, extractMessageId(response));
        } catch (RestClientException ex) {
            // Causas tipicas: token expirado, janela de 24h fechada (exige
            // template aprovado) ou numero fora da lista de teste.
            log.error("Falha ao enviar mensagem para {} via Meta Cloud API: {}",
                    phoneNumber, ex.getMessage());
        }
    }

    private String extractMessageId(SendMessageResponse response) {
        if (response == null || response.messages() == null || response.messages().isEmpty()) {
            return "desconhecido";
        }
        return response.messages().get(0).id();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static RestClient createClient(RestClient.Builder builder) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory();
        requestFactory.setReadTimeout(Duration.ofSeconds(TIMEOUT_SECONDS));
        return builder.requestFactory(requestFactory).build();
    }

    /** Corpo esperado por {@code POST /{version}/{phone-number-id}/messages}. */
    private record SendMessageRequest(
            String messaging_product,
            String recipient_type,
            String to,
            String type,
            TextBody text
    ) {
    }

    private record TextBody(boolean preview_url, String body) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SendMessageResponse(List<SentMessage> messages) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record SentMessage(String id) {
    }
}
