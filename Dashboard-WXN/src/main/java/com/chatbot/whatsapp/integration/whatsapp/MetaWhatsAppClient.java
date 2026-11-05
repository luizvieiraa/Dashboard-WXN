package com.chatbot.whatsapp.integration.whatsapp;

import com.chatbot.whatsapp.util.LogSanitizer;
import com.chatbot.whatsapp.util.PhoneNumbers;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Envio real de mensagens pela Meta WhatsApp Cloud API.
 *
 * <p>Ativado por {@code whatsapp.enabled=true}; caso contrario o bean de
 * {@link WhatsAppClient} continua sendo o {@link MockWhatsAppSender}. Como as
 * duas implementacoes sao mutuamente exclusivas por configuracao, a camada de
 * negocio nunca precisa saber qual esta ativa.</p>
 *
 * <p>Uma falha do provedor e registrada em log e devolvida como resultado. A
 * camada de servico consegue assim persistir {@code FAILED}, em vez de marcar
 * como enviada uma mensagem que a Meta recusou.</p>
 *
 * <p>Falhas temporarias sao tentadas novamente, no maximo {@link #MAX_ATTEMPTS}
 * vezes, com espera crescente: HTTP 429 e 5xx, codigos de limite de envio da
 * Meta e falhas de conexao (quando a requisicao nem chegou a Meta). Um tempo
 * esgotado depois do envio NAO e repetido, porque a Meta pode ter entregue a
 * mensagem e uma nova tentativa duplicaria a resposta para o cliente.</p>
 */
@Component
@ConditionalOnProperty(prefix = "whatsapp", name = "enabled", havingValue = "true")
public class MetaWhatsAppClient implements WhatsAppClient {

    private static final Logger log = LoggerFactory.getLogger(MetaWhatsAppClient.class);
    private static final ObjectMapper ERROR_READER = new ObjectMapper();
    private static final int TIMEOUT_SECONDS = 15;
    private static final int CONNECT_TIMEOUT_SECONDS = 5;

    /** Total de tentativas por mensagem: o envio original e ate duas novas tentativas. */
    static final int MAX_ATTEMPTS = 3;
    private static final Duration INITIAL_BACKOFF = Duration.ofMillis(500);
    private static final Duration MAX_BACKOFF = Duration.ofSeconds(4);

    /**
     * Codigos de erro da Cloud API que indicam limite de envio ou instabilidade
     * temporaria: 1/2 (erro desconhecido/servico indisponivel), 4 (limite de
     * chamadas do app), 80007 (limite da conta), 130429 (limite de envio por
     * segundo), 131000 (falha generica), 131016 (servico indisponivel), 131056
     * (limite de mensagens para o mesmo destinatario) e 133004 (servidor
     * temporariamente indisponivel).
     */
    private static final Set<Integer> RETRYABLE_ERROR_CODES =
            Set.of(1, 2, 4, 80007, 130429, 131000, 131016, 131056, 133004);

    /** Mensagem livre fora da janela de 24h: exige template aprovado. */
    private static final int REENGAGEMENT_ERROR_CODE = 131047;
    /** Token de acesso expirado ou invalido. */
    private static final int EXPIRED_TOKEN_ERROR_CODE = 190;

    private final WhatsAppProperties properties;
    private final RestClient restClient;
    private final Sleeper sleeper;

    @Autowired
    public MetaWhatsAppClient(WhatsAppProperties properties, RestClient.Builder restClientBuilder) {
        this(properties, createClient(restClientBuilder));
    }

    MetaWhatsAppClient(WhatsAppProperties properties, RestClient restClient) {
        this(properties, restClient, duration -> Thread.sleep(duration.toMillis()));
    }

    MetaWhatsAppClient(WhatsAppProperties properties, RestClient restClient, Sleeper sleeper) {
        this.properties = properties;
        this.restClient = restClient;
        this.sleeper = sleeper;
    }

    @Override
    public WhatsAppSendResult sendMessage(String phoneNumber, String text) {
        if (isBlank(properties.apiUrl())
                || isBlank(properties.apiVersion())
                || isBlank(properties.accessToken())
                || isBlank(properties.phoneNumberId())) {
            log.error("whatsapp.enabled=true mas WHATSAPP_API_URL, WHATSAPP_API_VERSION, "
                    + "WHATSAPP_ACCESS_TOKEN ou WHATSAPP_PHONE_NUMBER_ID nao foi configurado. "
                    + "Mensagem para {} NAO foi enviada.", PhoneNumbers.mask(phoneNumber));
            return WhatsAppSendResult.failed("Configuracao da Meta Cloud API incompleta");
        }

        SendMessageRequest body = new SendMessageRequest(
                "whatsapp",
                "individual",
                phoneNumber,
                "text",
                new TextBody(false, text)
        );

        Duration backoff = INITIAL_BACKOFF;
        for (int attempt = 1; ; attempt++) {
            try {
                SendMessageResponse response = restClient.post()
                        .uri(properties.sendMessageUrl())
                        .headers(headers -> headers.setBearerAuth(properties.accessToken()))
                        .body(body)
                        .retrieve()
                        .body(SendMessageResponse.class);

                String providerMessageId = extractMessageId(response);
                if (providerMessageId == null) {
                    log.error("Meta Cloud API respondeu sem o id da mensagem enviada para {}",
                            PhoneNumbers.mask(phoneNumber));
                    return WhatsAppSendResult.failed("Resposta da Meta sem id da mensagem");
                }
                log.info("Mensagem enviada ao WhatsApp de {} (id do provedor: {})",
                        PhoneNumbers.mask(phoneNumber), LogSanitizer.messageRef(providerMessageId));
                return WhatsAppSendResult.sent(providerMessageId);
            } catch (RestClientException ex) {
                ProviderError error = ProviderError.from(ex);
                boolean retryable = isRetryable(ex, error);
                if (!retryable || attempt >= MAX_ATTEMPTS) {
                    logFailure(phoneNumber, ex, error, attempt);
                    return WhatsAppSendResult.failed(error.summary());
                }
                Duration wait = waitBeforeRetry(ex, backoff);
                log.warn("Falha temporaria ao enviar mensagem para {} ({}); nova tentativa {}/{} em {} ms",
                        PhoneNumbers.mask(phoneNumber), error.summary(), attempt + 1, MAX_ATTEMPTS,
                        wait.toMillis());
                if (!pause(wait)) {
                    return WhatsAppSendResult.failed("Envio interrompido: " + error.summary());
                }
                backoff = min(backoff.multipliedBy(2), MAX_BACKOFF);
            }
        }
    }

    private boolean isRetryable(RestClientException ex, ProviderError error) {
        if (ex instanceof RestClientResponseException response) {
            int status = response.getStatusCode().value();
            return status == 429
                    || response.getStatusCode().is5xxServerError()
                    || (error.code() != null && RETRYABLE_ERROR_CODES.contains(error.code()));
        }
        if (ex instanceof ResourceAccessException) {
            // Somente quando a conexao nem foi estabelecida: a mensagem com
            // certeza nao chegou a Meta, entao repetir nao gera duplicidade.
            return hasCause(ex, ConnectException.class)
                    || hasCause(ex, HttpConnectTimeoutException.class)
                    || hasCause(ex, UnknownHostException.class);
        }
        return false;
    }

    /** Respeita o Retry-After enviado pela Meta, limitado a {@link #MAX_BACKOFF}. */
    private Duration waitBeforeRetry(RestClientException ex, Duration backoff) {
        if (ex instanceof RestClientResponseException response && response.getResponseHeaders() != null) {
            String retryAfter = response.getResponseHeaders().getFirst(HttpHeaders.RETRY_AFTER);
            if (retryAfter != null) {
                try {
                    long seconds = Long.parseLong(retryAfter.trim());
                    if (seconds > 0) {
                        return min(Duration.ofSeconds(seconds), MAX_BACKOFF);
                    }
                } catch (NumberFormatException ignored) {
                    // Retry-After em formato de data: usa a espera padrao.
                }
            }
        }
        return backoff;
    }

    private boolean pause(Duration wait) {
        try {
            sleeper.sleep(wait);
            return true;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void logFailure(String phoneNumber, RestClientException ex, ProviderError error, int attempts) {
        String hint = "";
        if (error.code() != null && error.code() == REENGAGEMENT_ERROR_CODE) {
            hint = " A janela de 24h esta encerrada: fora dela a Meta so aceita templates aprovados.";
        } else if (error.code() != null && error.code() == EXPIRED_TOKEN_ERROR_CODE) {
            hint = " Verifique WHATSAPP_ACCESS_TOKEN (token expirado ou invalido).";
        }
        // A mensagem completa da excecao nao e registrada: ela pode conter o
        // corpo da resposta da Meta e dados do destinatario.
        log.error("Falha ao enviar mensagem para {} via Meta Cloud API apos {} tentativa(s): {} [{}].{}",
                PhoneNumbers.mask(phoneNumber), attempts, error.summary(), LogSanitizer.describe(ex), hint);
    }

    private String extractMessageId(SendMessageResponse response) {
        if (response == null || response.messages() == null || response.messages().isEmpty()) {
            return null;
        }
        return response.messages().get(0).id();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean hasCause(Throwable error, Class<? extends Throwable> type) {
        Throwable current = error;
        int depth = 0;
        while (current != null && depth < 10) {
            if (type.isInstance(current)) {
                return true;
            }
            current = current.getCause();
            depth++;
        }
        return false;
    }

    private static Duration min(Duration first, Duration second) {
        return first.compareTo(second) <= 0 ? first : second;
    }

    private static RestClient createClient(RestClient.Builder builder) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(TIMEOUT_SECONDS));
        return builder.requestFactory(requestFactory).build();
    }

    /** Pausa entre tentativas; substituida nos testes para nao esperar de verdade. */
    @FunctionalInterface
    interface Sleeper {
        void sleep(Duration duration) throws InterruptedException;
    }

    /** Erro devolvido pela Graph API: {@code {"error": {"code": 131047, "message": "..."}}}. */
    private record ProviderError(Integer httpStatus, Integer code, String title) {

        static ProviderError from(RestClientException ex) {
            if (!(ex instanceof RestClientResponseException response)) {
                return new ProviderError(null, null, null);
            }
            Integer code = null;
            String title = null;
            try {
                JsonNode error = ERROR_READER.readTree(response.getResponseBodyAsString()).path("error");
                if (error.path("code").isInt()) {
                    code = error.path("code").asInt();
                }
                // Sequencias longas de digitos (ex.: telefone) sao omitidas.
                String message = error.path("message").asText("").replaceAll("\\d{8,}", "[omitido]");
                title = message.isBlank() ? null : message;
            } catch (Exception ignored) {
                // Corpo vazio ou fora do formato da Graph API.
            }
            return new ProviderError(response.getStatusCode().value(), code, title);
        }

        /** Resumo sem dados do cliente, usado em log e no resultado do envio. */
        String summary() {
            if (httpStatus == null) {
                return "falha de comunicacao com a Meta";
            }
            StringBuilder summary = new StringBuilder("HTTP ").append(httpStatus);
            if (code != null) {
                summary.append(", codigo ").append(code);
            }
            if (title != null) {
                summary.append(": ").append(title.length() > 160 ? title.substring(0, 160) : title);
            }
            return summary.toString();
        }
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
