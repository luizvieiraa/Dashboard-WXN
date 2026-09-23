package com.chatbot.whatsapp.service;

import com.chatbot.whatsapp.dto.request.WhatsAppWebhookRequest;
import com.chatbot.whatsapp.dto.response.WhatsAppWebhookResponse;
import com.chatbot.whatsapp.integration.whatsapp.WhatsAppInboundMessage;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Ponto de entrada do canal WhatsApp real.
 *
 * <p>Nao contem nenhuma regra de chatbot: traduz cada mensagem recebida para o
 * mesmo {@link WhatsAppWebhookRequest} que o simulador usa e delega para o
 * {@link MessageProcessingService} existente. O envio da resposta tambem nao
 * acontece aqui - quem envia e o proprio {@code MessageProcessingService}, no
 * fim do processamento, atraves do {@link WhatsAppClient}. Assim os dois canais
 * (frontend e WhatsApp) compartilham exatamente o mesmo chatbot.</p>
 *
 * <p>Deliberadamente sem {@code @Transactional}: cada mensagem e processada na
 * sua propria transacao, para que uma mensagem problematica em um lote nao
 * desfaca as outras.</p>
 */
@Service
public class WhatsAppInboundService {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppInboundService.class);

    private final MessageProcessingService messageProcessingService;
    private final MessageService messageService;

    public WhatsAppInboundService(MessageProcessingService messageProcessingService,
                                  MessageService messageService) {
        this.messageProcessingService = messageProcessingService;
        this.messageService = messageService;
    }

    /**
     * Processa todas as mensagens de uma notificacao do webhook.
     *
     * <p>Erros de uma mensagem sao registrados e nao interrompem as demais: a
     * Meta reentrega o lote inteiro quando nao recebe 200, e reprocessar o que
     * ja deu certo geraria respostas duplicadas.</p>
     *
     * @return resumo das mensagens processadas, ignoradas e com falha.
     */
    public WhatsAppInboundBatchResult handleAll(List<WhatsAppInboundMessage> messages) {
        int processed = 0;
        int ignored = 0;
        int failed = 0;
        for (WhatsAppInboundMessage message : messages) {
            try {
                if (handle(message)) {
                    processed++;
                } else {
                    ignored++;
                }
            } catch (RuntimeException ex) {
                failed++;
                log.error("Falha ao processar a mensagem {} de {}: {}",
                        message.messageId(), message.phone(), ex.getMessage(), ex);
            }
        }
        return new WhatsAppInboundBatchResult(processed, ignored, failed);
    }

    private boolean handle(WhatsAppInboundMessage message) {
        if (!isValidPhone(message.phone())) {
            log.warn("Mensagem {} ignorada: telefone '{}' fora do formato esperado",
                    message.messageId(), message.phone());
            return false;
        }

        if (messageService.alreadyProcessed(message.messageId())) {
            log.info("Mensagem {} ja processada anteriormente; reentrega descartada",
                    message.messageId());
            return false;
        }

        if (!message.isText()) {
            log.info("Mensagem {} de {} e do tipo '{}' e nao sera interpretada pelo chatbot",
                    message.messageId(), message.phone(), message.type());
            messageProcessingService.processUnsupported(
                    message.phone(), message.messageId(), message.type());
            return true;
        }

        WhatsAppWebhookRequest request = new WhatsAppWebhookRequest(
                message.phone(),
                message.text(),
                message.timestamp()
        );

        try {
            WhatsAppWebhookResponse response =
                    messageProcessingService.process(request, message.messageId());
            log.info("Mensagem {} processada na conversa {}",
                    message.messageId(), response.conversationId());
            return true;
        } catch (DataIntegrityViolationException ex) {
            // Duas entregas simultaneas da mesma mensagem: o indice unico de
            // external_id garante que apenas uma seja gravada.
            log.info("Mensagem {} processada em paralelo por outra entrega; ignorando",
                    message.messageId());
            return false;
        }
    }

    /** Mesma regra do {@link WhatsAppWebhookRequest}: somente digitos, 8 a 15. */
    private boolean isValidPhone(String phone) {
        return phone != null && phone.matches("\\d{8,15}");
    }
}
