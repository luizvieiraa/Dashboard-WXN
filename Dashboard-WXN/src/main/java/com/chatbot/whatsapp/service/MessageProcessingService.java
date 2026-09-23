package com.chatbot.whatsapp.service;

import com.chatbot.whatsapp.dto.request.WhatsAppWebhookRequest;
import com.chatbot.whatsapp.dto.response.WhatsAppWebhookResponse;
import com.chatbot.whatsapp.entity.Conversation;
import com.chatbot.whatsapp.entity.Customer;
import com.chatbot.whatsapp.entity.Message;
import com.chatbot.whatsapp.entity.enums.MessageStatus;
import com.chatbot.whatsapp.entity.enums.ConversationStatus;
import com.chatbot.whatsapp.integration.whatsapp.WhatsAppClient;
import com.chatbot.whatsapp.integration.whatsapp.WhatsAppSendResult;
import com.chatbot.whatsapp.service.chatbot.ChatIntent;
import com.chatbot.whatsapp.service.chatbot.GeneratedReply;
import com.chatbot.whatsapp.service.chatbot.IntelligentResponseService;
import com.chatbot.whatsapp.service.chatbot.IntentClassifier;
import com.chatbot.whatsapp.service.triage.InformationCollectionService;
import com.chatbot.whatsapp.service.triage.TriageCollectionResult;
import com.chatbot.whatsapp.service.triage.ComplaintDetector;
import com.chatbot.whatsapp.service.triage.ComplaintEscalationService;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orquestra o fluxo completo de processamento de uma mensagem recebida:
 *
 * <pre>
 * webhook -&gt; encontra/cria cliente -&gt; encontra/cria conversa -&gt; grava mensagem inbound
 *         -&gt; classifica intencao -&gt; gera resposta -&gt; grava mensagem outbound
 *         -&gt; envia resposta (via {@link WhatsAppClient}) -&gt; retorna resultado
 * </pre>
 *
 * <p>Esta e a camada de "regra de negocio" mencionada no README: nenhuma
 * dessas decisoes fica no controller.</p>
 */
@Service
public class MessageProcessingService {

    private final CustomerService customerService;
    private final ConversationService conversationService;
    private final MessageService messageService;
    private final InformationCollectionService informationCollectionService;
    private final ComplaintDetector complaintDetector;
    private final ComplaintEscalationService complaintEscalationService;
    private final IntentClassifier intentClassifier;
    private final IntelligentResponseService intelligentResponseService;
    private final WhatsAppClient whatsAppClient;

    public MessageProcessingService(CustomerService customerService,
                                     ConversationService conversationService,
                                     MessageService messageService,
                                     InformationCollectionService informationCollectionService,
                                     ComplaintDetector complaintDetector,
                                     ComplaintEscalationService complaintEscalationService,
                                     IntentClassifier intentClassifier,
                                     IntelligentResponseService intelligentResponseService,
                                     WhatsAppClient whatsAppClient) {
        this.customerService = customerService;
        this.conversationService = conversationService;
        this.messageService = messageService;
        this.informationCollectionService = informationCollectionService;
        this.complaintDetector = complaintDetector;
        this.complaintEscalationService = complaintEscalationService;
        this.intentClassifier = intentClassifier;
        this.intelligentResponseService = intelligentResponseService;
        this.whatsAppClient = whatsAppClient;
    }

    @Transactional
    public WhatsAppWebhookResponse process(WhatsAppWebhookRequest request) {
        return processInternal(request, null, false);
    }

    /**
     * Mesmo processamento, guardando o id da mensagem no provedor externo.
     *
     * <p>Este e o metodo usado pelo canal real do WhatsApp; o simulador e os
     * testes continuam chamando {@link #process(WhatsAppWebhookRequest)}. A
     * logica de chatbot e identica para os dois canais - e exatamente o ponto
     * de reuso entre eles.</p>
     *
     * @param externalId id da mensagem no provedor, ou {@code null} quando a
     *                   origem nao tem um.
     */
    @Transactional
    public WhatsAppWebhookResponse process(WhatsAppWebhookRequest request, String externalId) {
        return processInternal(request, externalId, true);
    }

    private WhatsAppWebhookResponse processInternal(WhatsAppWebhookRequest request,
                                                    String externalId,
                                                    boolean sendToWhatsApp) {
        Customer customer = customerService.findOrCreateByPhone(request.phone());
        Conversation conversation = conversationService.getOrCreateActiveConversation(customer);

        Message inboundMessage = messageService.recordInbound(conversation, request.message(), externalId);

        if (conversation.getStatus() == ConversationStatus.WAITING_HUMAN
                || conversation.getStatus() == ConversationStatus.HUMAN_ACTIVE) {
            messageService.updateStatus(inboundMessage, MessageStatus.PROCESSED);
            conversationService.touch(conversation, "HUMAN_QUEUE_MESSAGE");
            return new WhatsAppWebhookResponse(
                    conversation.getId(),
                    customer.getPhoneNumber(),
                    request.message(),
                    null,
                    Instant.now()
            );
        }

        String reply;
        String context;
        if (complaintDetector.isComplaint(request.message())) {
            complaintEscalationService.escalate(conversation, request.message());
            reply = ComplaintEscalationService.HANDOFF_REPLY;
            context = "COMPLAINT_ESCALATED";
        } else {
            Optional<TriageCollectionResult> collectionResult = informationCollectionService.collect(
                    conversation,
                    customer,
                    request.message()
            );
            if (collectionResult.isPresent()) {
                TriageCollectionResult result = collectionResult.get();
                reply = result.reply();
                context = result.context();
            } else {
                ChatIntent intent = intentClassifier.classify(request.message());
                GeneratedReply generatedReply = intelligentResponseService.generateReply(
                        request.message(), intent, customer
                );
                reply = generatedReply.text();
                context = generatedReply.context();
            }
        }

        messageService.updateStatus(inboundMessage, MessageStatus.PROCESSED);
        conversationService.touch(conversation, context);

        WhatsAppSendResult sendResult = sendToWhatsApp
                ? whatsAppClient.sendMessage(customer.getPhoneNumber(), reply)
                : WhatsAppSendResult.sent(null);
        messageService.recordOutbound(
                conversation,
                reply,
                sendResult.sent() ? MessageStatus.SENT : MessageStatus.FAILED,
                sendResult.providerMessageId()
        );

        return new WhatsAppWebhookResponse(
                conversation.getId(),
                customer.getPhoneNumber(),
                request.message(),
                reply,
                Instant.now()
        );
    }

    /**
     * Registra uma midia recebida sem usa-la como resposta da coleta guiada.
     * O id externo fica persistido, portanto uma reentrega da Meta nao gera
     * outro aviso para o cliente.
     */
    @Transactional
    public void processUnsupported(String phone, String externalId, String type) {
        Customer customer = customerService.findOrCreateByPhone(phone);
        Conversation conversation = conversationService.getOrCreateActiveConversation(customer);
        String inboundDescription = "[Mensagem do WhatsApp do tipo: " + type + "]";
        Message inboundMessage = messageService.recordInbound(
                conversation, inboundDescription, externalId);
        messageService.updateStatus(inboundMessage, MessageStatus.PROCESSED);

        String reply = "Recebi seu envio, mas por aqui eu consigo ler apenas mensagens de texto. "
                + "Pode me escrever o que você precisa?";
        WhatsAppSendResult sendResult = whatsAppClient.sendMessage(phone, reply);
        messageService.recordOutbound(
                conversation,
                reply,
                sendResult.sent() ? MessageStatus.SENT : MessageStatus.FAILED,
                sendResult.providerMessageId()
        );
        conversationService.touch(conversation, "UNSUPPORTED_WHATSAPP_MESSAGE");
    }
}
