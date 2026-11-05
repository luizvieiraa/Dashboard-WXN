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
import com.chatbot.whatsapp.service.chatbot.BotDecision;
import com.chatbot.whatsapp.service.chatbot.ChatIntent;
import com.chatbot.whatsapp.service.chatbot.IntelligentResponseService;
import com.chatbot.whatsapp.service.chatbot.IntentClassifier;
import com.chatbot.whatsapp.service.triage.ConversationHandoffProperties;
import com.chatbot.whatsapp.service.triage.HandoffReason;
import com.chatbot.whatsapp.service.triage.HumanHandoffService;
import com.chatbot.whatsapp.service.triage.HumanRequestDetector;
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
 *
 * <p>Ordem das decisoes para uma conversa operada pelo bot:</p>
 * <ol>
 *   <li>reclamacao detectada por palavras-chave: fila humana com prioridade alta;</li>
 *   <li>pedido explicito de atendente: fila humana;</li>
 *   <li>primeira mensagem: o assistente virtual (se ligado) pode encaminhar direto;</li>
 *   <li>coleta guiada de nome, empresa e assunto (deterministica);</li>
 *   <li>mensagens livres: o assistente virtual responde, pede detalhes ou encaminha;
 *       sem ele, vale o classificador local. Respostas seguidas sem solucao levam a
 *       um atendente.</li>
 * </ol>
 *
 * <p>O cliente fica bloqueado durante todo o processamento (ver
 * {@link CustomerService#findOrCreateByPhone(String)}): o bot nunca responde ao
 * mesmo tempo que um atendente assume a conversa.</p>
 */
@Service
public class MessageProcessingService {

    private static final String UNSUPPORTED_REPLY =
            "Recebi seu envio, mas por aqui eu consigo ler apenas mensagens de texto. "
                    + "Pode me escrever o que você precisa?";
    private static final int MAX_MESSAGE_TYPE_LENGTH = 30;

    private final CustomerService customerService;
    private final ConversationService conversationService;
    private final MessageService messageService;
    private final InformationCollectionService informationCollectionService;
    private final ComplaintDetector complaintDetector;
    private final ComplaintEscalationService complaintEscalationService;
    private final IntentClassifier intentClassifier;
    private final IntelligentResponseService intelligentResponseService;
    private final WhatsAppClient whatsAppClient;
    private final HumanRequestDetector humanRequestDetector;
    private final HumanHandoffService humanHandoffService;
    private final ConversationHandoffProperties handoffProperties;

    public MessageProcessingService(CustomerService customerService,
                                     ConversationService conversationService,
                                     MessageService messageService,
                                     InformationCollectionService informationCollectionService,
                                     ComplaintDetector complaintDetector,
                                     ComplaintEscalationService complaintEscalationService,
                                     IntentClassifier intentClassifier,
                                     IntelligentResponseService intelligentResponseService,
                                     WhatsAppClient whatsAppClient,
                                     HumanRequestDetector humanRequestDetector,
                                     HumanHandoffService humanHandoffService,
                                     ConversationHandoffProperties handoffProperties) {
        this.customerService = customerService;
        this.conversationService = conversationService;
        this.messageService = messageService;
        this.informationCollectionService = informationCollectionService;
        this.complaintDetector = complaintDetector;
        this.complaintEscalationService = complaintEscalationService;
        this.intentClassifier = intentClassifier;
        this.intelligentResponseService = intelligentResponseService;
        this.whatsAppClient = whatsAppClient;
        this.humanRequestDetector = humanRequestDetector;
        this.humanHandoffService = humanHandoffService;
        this.handoffProperties = handoffProperties;
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

        if (isWithHumanTeam(conversation)) {
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

        BotTurn turn = decideTurn(conversation, customer, request.message(), inboundMessage);

        messageService.updateStatus(inboundMessage, MessageStatus.PROCESSED);
        conversationService.touch(conversation, turn.context());

        WhatsAppSendResult sendResult = sendToWhatsApp
                ? whatsAppClient.sendMessage(customer.getPhoneNumber(), turn.reply())
                : WhatsAppSendResult.sent(null);
        messageService.recordOutbound(
                conversation,
                turn.reply(),
                sendResult.sent() ? MessageStatus.SENT : MessageStatus.FAILED,
                sendResult.providerMessageId()
        );

        return new WhatsAppWebhookResponse(
                conversation.getId(),
                customer.getPhoneNumber(),
                request.message(),
                turn.reply(),
                Instant.now()
        );
    }

    private BotTurn decideTurn(Conversation conversation, Customer customer, String message, Message inbound) {
        if (complaintDetector.isComplaint(message)) {
            complaintEscalationService.escalate(conversation, message);
            return new BotTurn(ComplaintEscalationService.HANDOFF_REPLY, "COMPLAINT_ESCALATED");
        }
        if (humanRequestDetector.isHumanRequest(message)) {
            return handoff(conversation, message, HandoffReason.CUSTOMER_REQUEST);
        }

        if (!informationCollectionService.hasStarted(conversation)) {
            Optional<HandoffReason> firstMessageHandoff = intelligentResponseService.handoffOnFirstMessage(
                    conversation, customer, message, inbound.getId());
            if (firstMessageHandoff.isPresent()) {
                return handoff(conversation, message, firstMessageHandoff.get());
            }
        }

        Optional<TriageCollectionResult> collectionResult = informationCollectionService.collect(
                conversation,
                customer,
                message
        );
        if (collectionResult.isPresent()) {
            TriageCollectionResult result = collectionResult.get();
            return new BotTurn(result.reply(), result.context());
        }

        ChatIntent intent = intentClassifier.classify(message);
        BotDecision decision = intelligentResponseService.decide(
                conversation, customer, message, intent, inbound.getId());
        return switch (decision.action()) {
            case HANDOFF -> handoff(conversation, message, decision.handoffReason());
            case RESOLVE -> {
                conversation.resetUnresolvedBotAttempts();
                yield new BotTurn(decision.reply(), decision.context());
            }
            case ASK_MORE -> {
                int attempts = conversation.registerUnresolvedBotAttempt();
                yield attempts >= handoffProperties.effectiveMaxUnresolvedAttempts()
                        ? handoff(conversation, message, HandoffReason.UNRESOLVED_ATTEMPTS)
                        : new BotTurn(decision.reply(), decision.context());
            }
        };
    }

    private BotTurn handoff(Conversation conversation, String message, HandoffReason reason) {
        String reply = humanHandoffService.handoff(conversation, reason, message);
        return new BotTurn(reply, reason == HandoffReason.COMPLAINT ? "COMPLAINT_ESCALATED" : "HUMAN_HANDOFF");
    }

    /**
     * Registra uma midia recebida sem usa-la como resposta da coleta guiada.
     * O id externo fica persistido, portanto uma reentrega da Meta nao gera
     * outro aviso para o cliente.
     *
     * <p>Se a conversa estiver com a equipe humana, o aviso automatico nao e
     * enviado: quem responde e o atendente.</p>
     */
    @Transactional
    public void processUnsupported(String phone, String externalId, String type) {
        Customer customer = customerService.findOrCreateByPhone(phone);
        Conversation conversation = conversationService.getOrCreateActiveConversation(customer);
        String inboundDescription = "[Mensagem do WhatsApp do tipo: " + describeType(type) + "]";
        Message inboundMessage = messageService.recordInbound(
                conversation, inboundDescription, externalId);
        messageService.updateStatus(inboundMessage, MessageStatus.PROCESSED);

        if (isWithHumanTeam(conversation)) {
            conversationService.touch(conversation, "HUMAN_QUEUE_MESSAGE");
            return;
        }

        WhatsAppSendResult sendResult = whatsAppClient.sendMessage(phone, UNSUPPORTED_REPLY);
        messageService.recordOutbound(
                conversation,
                UNSUPPORTED_REPLY,
                sendResult.sent() ? MessageStatus.SENT : MessageStatus.FAILED,
                sendResult.providerMessageId()
        );
        conversationService.touch(conversation, "UNSUPPORTED_WHATSAPP_MESSAGE");
    }

    /** O bot fica pausado somente nesta conversa enquanto ela estiver com a equipe humana. */
    private boolean isWithHumanTeam(Conversation conversation) {
        return conversation.getStatus() == ConversationStatus.WAITING_HUMAN
                || conversation.getStatus() == ConversationStatus.HUMAN_ACTIVE;
    }

    /** O tipo vem do provedor: so letras e sublinhado, com tamanho limitado. */
    private String describeType(String type) {
        if (type == null || type.isBlank()) {
            return "desconhecido";
        }
        String clean = type.replaceAll("[^A-Za-z_]", "");
        if (clean.isEmpty()) {
            return "desconhecido";
        }
        return clean.length() > MAX_MESSAGE_TYPE_LENGTH ? clean.substring(0, MAX_MESSAGE_TYPE_LENGTH) : clean;
    }

    private record BotTurn(String reply, String context) {
    }
}
