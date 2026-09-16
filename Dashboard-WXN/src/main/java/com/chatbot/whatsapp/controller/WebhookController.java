package com.chatbot.whatsapp.controller;

import com.chatbot.whatsapp.dto.request.WhatsAppWebhookRequest;
import com.chatbot.whatsapp.dto.response.WhatsAppWebhookResponse;
import com.chatbot.whatsapp.service.MessageProcessingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint que recebe mensagens do cliente.
 *
 * <p>Hoje este endpoint funciona como uma SIMULACAO do webhook do WhatsApp:
 * ele aceita o mesmo tipo de informacao (telefone, texto e timestamp) que
 * chegaria de um provedor real, permitindo testar toda a cadeia
 * API -&gt; Controller -&gt; Service -&gt; Processamento -&gt; Banco -&gt; Resposta
 * sem depender de credenciais externas. Ver README ("Integracao real com
 * WhatsApp") para o que falta configurar quando o provedor for definido.</p>
 */
@RestController
@RequestMapping("/api/v1/webhook")
@Tag(name = "Webhook", description = "Recebimento de mensagens (simulacao do webhook do WhatsApp)")
public class WebhookController {

    private final MessageProcessingService messageProcessingService;

    public WebhookController(MessageProcessingService messageProcessingService) {
        this.messageProcessingService = messageProcessingService;
    }

    @PostMapping("/whatsapp")
    @Operation(summary = "Recebe uma mensagem do cliente (simulacao do webhook do WhatsApp)",
            description = "Processa a mensagem recebida (cria/recupera cliente e conversa, "
                    + "classifica a intencao, gera e persiste a resposta do chatbot) e devolve o resultado.")
    @ApiResponse(responseCode = "201", description = "Mensagem processada com sucesso")
    @ApiResponse(responseCode = "400", description = "Payload invalido")
    public ResponseEntity<WhatsAppWebhookResponse> receiveMessage(@Valid @RequestBody WhatsAppWebhookRequest request) {
        WhatsAppWebhookResponse response = messageProcessingService.process(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
