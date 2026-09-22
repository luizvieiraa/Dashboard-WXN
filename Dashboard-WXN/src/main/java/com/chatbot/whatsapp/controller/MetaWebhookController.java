package com.chatbot.whatsapp.controller;

import com.chatbot.whatsapp.integration.whatsapp.MetaWebhookPayload;
import com.chatbot.whatsapp.integration.whatsapp.MetaWebhookPayloadParser;
import com.chatbot.whatsapp.integration.whatsapp.WhatsAppInboundMessage;
import com.chatbot.whatsapp.integration.whatsapp.WhatsAppProperties;
import com.chatbot.whatsapp.integration.whatsapp.WhatsAppSignatureVerifier;
import com.chatbot.whatsapp.service.WhatsAppInboundService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Webhook real da Meta WhatsApp Cloud API.
 *
 * <p>Endpoint separado do {@code POST /api/v1/webhook/whatsapp} de proposito: o
 * envelope da Meta e completamente diferente do payload simplificado usado pelo
 * simulador, e a Meta espera {@code 200 OK} (nao {@code 201}) com corpo vazio.
 * Manter as duas rotas independentes preserva o simulador e o contrato da API
 * existente intactos.</p>
 *
 * <p>A Meta usa dois metodos na mesma URL:</p>
 * <ul>
 *   <li>{@code GET} - handshake de verificacao, uma vez, ao cadastrar a URL.</li>
 *   <li>{@code POST} - notificacoes de mensagens e de status de entrega.</li>
 * </ul>
 *
 * <p>O controller nao contem regra de negocio: valida a origem, delega a
 * traducao ao parser e o processamento ao service.</p>
 */
@RestController
@RequestMapping("/api/v1/webhook/whatsapp/meta")
@Tag(name = "Webhook WhatsApp (Meta)",
        description = "Recebimento real de mensagens via WhatsApp Cloud API")
public class MetaWebhookController {

    private static final Logger log = LoggerFactory.getLogger(MetaWebhookController.class);
    private static final String SUBSCRIBE_MODE = "subscribe";

    private final WhatsAppProperties properties;
    private final WhatsAppSignatureVerifier signatureVerifier;
    private final MetaWebhookPayloadParser payloadParser;
    private final WhatsAppInboundService whatsAppInboundService;
    private final ObjectMapper objectMapper;

    public MetaWebhookController(WhatsAppProperties properties,
                                 WhatsAppSignatureVerifier signatureVerifier,
                                 MetaWebhookPayloadParser payloadParser,
                                 WhatsAppInboundService whatsAppInboundService,
                                 ObjectMapper objectMapper) {
        this.properties = properties;
        this.signatureVerifier = signatureVerifier;
        this.payloadParser = payloadParser;
        this.whatsAppInboundService = whatsAppInboundService;
        this.objectMapper = objectMapper;
    }

    /**
     * Handshake de verificacao exigido pela Meta ao cadastrar a URL do webhook.
     *
     * <p>A Meta chama esta URL com {@code hub.mode=subscribe}, o
     * {@code hub.verify_token} cadastrado no painel e um {@code hub.challenge}.
     * Se o token confere, a resposta deve ser o challenge em texto puro; caso
     * contrario, {@code 403}.</p>
     */
    @GetMapping(produces = MediaType.TEXT_PLAIN_VALUE)
    @Operation(summary = "Handshake de verificacao do webhook da Meta",
            description = "Responde o hub.challenge quando o hub.verify_token confere com "
                    + "WHATSAPP_WEBHOOK_VERIFY_TOKEN. Chamado pela Meta ao cadastrar a URL.")
    @ApiResponse(responseCode = "200", description = "Token valido; devolve o challenge")
    @ApiResponse(responseCode = "403", description = "Token invalido ou modo nao suportado")
    public ResponseEntity<String> verify(
            @RequestParam(name = "hub.mode", required = false) String mode,
            @RequestParam(name = "hub.verify_token", required = false) String verifyToken,
            @RequestParam(name = "hub.challenge", required = false) String challenge) {

        String expectedToken = properties.webhookVerifyToken();
        if (expectedToken == null || expectedToken.isBlank()) {
            log.error("Tentativa de verificacao do webhook sem WHATSAPP_WEBHOOK_VERIFY_TOKEN configurado");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        if (!SUBSCRIBE_MODE.equals(mode) || !expectedToken.equals(verifyToken)) {
            log.warn("Verificacao do webhook recusada (mode='{}')", mode);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        log.info("Webhook do WhatsApp verificado com sucesso pela Meta");
        return ResponseEntity.ok(challenge);
    }

    /**
     * Recebe as notificacoes de mensagens.
     *
     * <p>O corpo e recebido como {@code byte[]} porque a assinatura
     * {@code X-Hub-Signature-256} e calculada sobre os bytes exatos enviados
     * pela Meta - reserializar o JSON invalidaria a comparacao.</p>
     *
     * <p>Sempre responde {@code 200} quando a origem e legitima, mesmo que
     * nenhuma mensagem seja aproveitada (ex.: notificacao de status de entrega)
     * ou que o processamento de alguma falhe. Qualquer outro status faz a Meta
     * reentregar o lote e, em caso de falhas repetidas, desativar o webhook.</p>
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Recebe notificacoes de mensagens da Meta",
            description = "Valida a assinatura X-Hub-Signature-256, extrai as mensagens de texto "
                    + "e encaminha cada uma para o mesmo chatbot usado pelo frontend.")
    @ApiResponse(responseCode = "200", description = "Notificacao recebida")
    @ApiResponse(responseCode = "403", description = "Assinatura invalida")
    public ResponseEntity<Void> receive(
            @RequestBody byte[] rawBody,
            @RequestHeader(name = "X-Hub-Signature-256", required = false) String signature) {

        if (!signatureVerifier.isValid(rawBody, signature)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        List<WhatsAppInboundMessage> messages;
        try {
            MetaWebhookPayload payload = objectMapper.readValue(rawBody, MetaWebhookPayload.class);
            messages = payloadParser.parse(payload);
        } catch (Exception ex) {
            // Responder 200 evita um loop de reentrega de um payload que nunca
            // vamos conseguir interpretar. O corpo fica registrado em log.
            log.error("Nao foi possivel interpretar a notificacao do webhook: {}", ex.getMessage());
            return ResponseEntity.ok().build();
        }

        if (messages.isEmpty()) {
            log.debug("Notificacao recebida sem mensagens de usuario (provavelmente status de entrega)");
            return ResponseEntity.ok().build();
        }

        int processed = whatsAppInboundService.handleAll(messages);
        log.info("Notificacao do webhook: {} mensagem(ns) recebida(s), {} processada(s)",
                messages.size(), processed);
        return ResponseEntity.ok().build();
    }
}
