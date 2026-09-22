package com.chatbot.whatsapp.integration.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Cliente HTTP para APIs compatíveis com o formato Responses da OpenAI.
 * Falhas externas nunca interrompem o atendimento: o chamador usa fallback.
 */
@Component
public class OpenAiResponsesClient implements AiClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiResponsesClient.class);
    private static final String INSTRUCTIONS = """
            Você é o assistente virtual da WXN. Responda em português do Brasil,
            com clareza, cordialidade e no máximo 600 caracteres. Use o contexto
            informado apenas para personalizar a conversa. Não invente preços,
            prazos, contratos, políticas ou funcionalidades. Quando não houver
            informação suficiente, diga que a solicitação foi registrada e que
            a equipe da WXN poderá complementar a resposta.
            """;

    private final AiProperties properties;
    private final RestClient restClient;

    @Autowired
    public OpenAiResponsesClient(AiProperties properties, RestClient.Builder restClientBuilder) {
        this(properties, createClient(properties, restClientBuilder));
    }

    OpenAiResponsesClient(AiProperties properties, RestClient restClient) {
        this.properties = properties;
        this.restClient = restClient;
    }

    @Override
    public Optional<String> generateReply(String customerMessage, String customerContext) {
        if (!properties.enabled()) {
            return Optional.empty();
        }
        if (isBlank(properties.apiUrl()) || isBlank(properties.apiKey()) || isBlank(properties.model())) {
            log.warn("IA habilitada, mas AI_API_URL, AI_API_KEY ou AI_MODEL nao foi configurado");
            return Optional.empty();
        }

        try {
            AiApiResponse response = restClient.post()
                    .uri(properties.apiUrl())
                    .headers(headers -> headers.setBearerAuth(properties.apiKey()))
                    .body(new AiApiRequest(
                            properties.model(),
                            INSTRUCTIONS,
                            "Contexto do cliente: " + customerContext + "\nMensagem: " + customerMessage,
                            false,
                            properties.maxOutputTokens() > 0 ? properties.maxOutputTokens() : 300
                    ))
                    .retrieve()
                    .body(AiApiResponse.class);

            return extractText(response);
        } catch (RestClientException | IllegalArgumentException ex) {
            log.warn("Provedor de IA indisponivel; usando resposta deterministica: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    private Optional<String> extractText(AiApiResponse response) {
        if (response == null || response.output() == null) {
            return Optional.empty();
        }
        return response.output().stream()
                .filter(output -> output.content() != null)
                .flatMap(output -> output.content().stream())
                .filter(content -> "output_text".equals(content.type()))
                .map(AiOutputContent::text)
                .filter(text -> text != null && !text.isBlank())
                .map(String::trim)
                .findFirst();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static RestClient createClient(AiProperties properties, RestClient.Builder builder) {
        int timeout = properties.timeoutSeconds() > 0 ? properties.timeoutSeconds() : 10;
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory();
        requestFactory.setReadTimeout(Duration.ofSeconds(timeout));
        return builder.requestFactory(requestFactory).build();
    }

    private record AiApiRequest(
            String model,
            String instructions,
            String input,
            boolean store,
            int max_output_tokens
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record AiApiResponse(List<AiOutput> output) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record AiOutput(List<AiOutputContent> content) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record AiOutputContent(String type, String text) {
    }
}
