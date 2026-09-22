package com.chatbot.whatsapp.service;

import com.chatbot.whatsapp.dto.request.WhatsAppWebhookRequest;
import com.chatbot.whatsapp.dto.response.WhatsAppWebhookResponse;
import com.chatbot.whatsapp.entity.Conversation;
import com.chatbot.whatsapp.entity.Customer;
import com.chatbot.whatsapp.entity.Message;
import com.chatbot.whatsapp.entity.enums.MessageStatus;
import com.chatbot.whatsapp.integration.whatsapp.WhatsAppClient;
import com.chatbot.whatsapp.service.chatbot.ChatIntent;
import com.chatbot.whatsapp.service.chatbot.ChatbotResponseService;
import com.chatbot.whatsapp.service.chatbot.IntentClassifier;
import com.chatbot.whatsapp.service.triage.InformationCollectionService;
import com.chatbot.whatsapp.service.triage.TriageCollectionResult;
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
    private final IntentClassifier intentClassifier;
    private final ChatbotResponseService chatbotResponseService;
    private final WhatsAppClient whatsAppClient;

    public MessageProcessingService(CustomerService customerService,
                                     ConversationService conversationService,
                                     MessageService messageService,
                                     InformationCollectionService informationCollectionService,
                                     IntentClassifier intentClassifier,
                                     ChatbotResponseService chatbotResponseService,
                                     WhatsAppClient whatsAppClient) {
        this.customerService = customerService;
        this.conversationService = conversationService;
        this.messageService = messageService;
        this.informationCollectionService = informationCollectionService;
        this.intentClassifier = intentClassifier;
        this.chatbotResponseService = chatbotResponseService;
        this.whatsAppClient = whatsAppClient;
    }

    @Transactional
    public WhatsAppWebhookResponse process(WhatsAppWebhookRequest request) {
        Customer customer = customerService.findOrCreateByPhone(request.phone());
        Conversation conversation = conversationService.getOrCreateActiveConversation(customer);

        Message inboundMessage = messageService.recordInbound(conversation, request.message());

        Optional<TriageCollectionResult> collectionResult = informationCollectionService.collect(
                conversation,
                customer,
                request.message()
        );

        String reply;
        String context;
        if (collectionResult.isPresent()) {
            TriageCollectionResult result = collectionResult.get();
            reply = result.reply();
            context = result.context();
        } else {
            ChatIntent intent = intentClassifier.classify(request.message());
            reply = chatbotResponseService.generateReply(intent);
            context = intent.name();
        }

        messageService.updateStatus(inboundMessage, MessageStatus.PROCESSED);
        messageService.recordOutbound(conversation, reply, MessageStatus.SENT);
        conversationService.touch(conversation, context);

        whatsAppClient.sendMessage(customer.getPhoneNumber(), reply);

        return new WhatsAppWebhookResponse(
                conversation.getId(),
                customer.getPhoneNumber(),
                request.message(),
                reply,
                Instant.now()
        );
    }
}
