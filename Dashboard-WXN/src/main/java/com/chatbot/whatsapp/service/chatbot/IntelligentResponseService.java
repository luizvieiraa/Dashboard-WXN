package com.chatbot.whatsapp.service.chatbot;

import com.chatbot.whatsapp.entity.Conversation;
import com.chatbot.whatsapp.entity.Customer;
import com.chatbot.whatsapp.entity.Chatbot;
import com.chatbot.whatsapp.integration.ai.AiClient;
import com.chatbot.whatsapp.integration.ai.AiProperties;
import com.chatbot.whatsapp.integration.ai.AiTriageDecision;
import com.chatbot.whatsapp.integration.ai.AiTriageRequest;
import com.chatbot.whatsapp.service.triage.HandoffReason;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Escolhe IA quando habilitada e preserva a resposta local como fallback.
 *
 * <p>Com o assistente virtual ligado, cada mensagem livre recebe uma decisao
 * estruturada (intencao, acao e confianca). As regras de negocio ficam aqui, e
 * nao no prompt: confianca baixa, reclamacao, pedido de atendente ou assunto
 * sensivel sempre levam a um humano, e a resposta gerada so e enviada depois de
 * passar pelo {@link AssistantReplyGuard}. Se o provedor estiver desligado,
 * falhar ou responder fora do formato, vale o fluxo local por palavras-chave.</p>
 */
@Service
public class IntelligentResponseService {

    private static final Logger log = LoggerFactory.getLogger(IntelligentResponseService.class);

    private final AiClient aiClient;
    private final ChatbotResponseService fallbackService;
    private final AssistantContextBuilder contextBuilder;
    private final AssistantReplyGuard replyGuard;
    private final AiProperties aiProperties;

    public IntelligentResponseService(AiClient aiClient,
                                      ChatbotResponseService fallbackService,
                                      AssistantContextBuilder contextBuilder,
                                      AssistantReplyGuard replyGuard,
                                      AiProperties aiProperties) {
        this.aiClient = aiClient;
        this.fallbackService = fallbackService;
        this.contextBuilder = contextBuilder;
        this.replyGuard = replyGuard;
        this.aiProperties = aiProperties;
    }

    public GeneratedReply generateReply(String message, ChatIntent intent, Customer customer) {
        return generateReply(message, intent, customer, Chatbot.standard());
    }

    public GeneratedReply generateReply(String message, ChatIntent intent, Customer customer, Chatbot chatbot) {
        String context = "nome=%s; empresa=%s"
                .formatted(orUnknown(customer.getName()), orUnknown(customer.getCompanyName()));

        return aiClient.generateReply(message, context)
                .map(text -> new GeneratedReply(text, "AI_RESPONSE"))
                .orElseGet(() -> new GeneratedReply(
                        fallbackService.generateReply(chatbot, intent),
                        intent.name()
                ));
    }

    /**
     * Decide a resposta para uma mensagem livre, depois da coleta estruturada.
     *
     * @param fallbackIntent intencao do classificador local, usada quando o
     *                       assistente virtual nao responde.
     */
    public BotDecision decide(Conversation conversation,
                              Customer customer,
                              String message,
                              ChatIntent fallbackIntent,
                              Long currentMessageId) {
        return askAssistant(AssistantStage.CONVERSATION, conversation, customer, message, currentMessageId)
                .orElseGet(() -> localDecision(conversation.getChatbot(), fallbackIntent));
    }

    /**
     * Na primeira mensagem, a triagem estruturada sempre comeca, exceto quando o
     * assistente virtual identifica com confianca que a pessoa precisa de um
     * atendente (reclamacao, pedido explicito ou assunto sensivel).
     */
    public Optional<HandoffReason> handoffOnFirstMessage(Conversation conversation,
                                                         Customer customer,
                                                         String message,
                                                         Long currentMessageId) {
        return askAssistant(AssistantStage.FIRST_MESSAGE, conversation, customer, message, currentMessageId)
                .filter(decision -> decision.action() == BotAction.HANDOFF)
                .map(BotDecision::handoffReason);
    }

    private Optional<BotDecision> askAssistant(AssistantStage stage,
                                               Conversation conversation,
                                               Customer customer,
                                               String message,
                                               Long currentMessageId) {
        if (!aiClient.isAvailable()) {
            return Optional.empty();
        }
        AiTriageRequest request = contextBuilder.build(stage, conversation, customer, message, currentMessageId);
        return aiClient.decide(request).flatMap(decision -> {
            log.info("Assistente virtual na conversa {}: etapa={}, intencao={}, acao={}, confianca={}, sensivel={}",
                    conversation.getId(), stage, decision.intent(), decision.action(),
                    "%.2f".formatted(decision.confidence()), decision.sensitive());
            return interpret(stage, decision);
        });
    }

    Optional<BotDecision> interpret(AssistantStage stage, AiTriageDecision decision) {
        boolean confident = decision.confidence() >= aiProperties.effectiveMinConfidence();
        Optional<HandoffReason> handoffReason = handoffReason(decision);

        if (stage == AssistantStage.FIRST_MESSAGE) {
            // Sem confianca, segue a triagem normal; a decisao so vale se for encaminhar.
            return confident ? handoffReason.map(BotDecision::handoff) : Optional.empty();
        }

        if (!confident) {
            return Optional.of(BotDecision.handoff(HandoffReason.LOW_CONFIDENCE));
        }
        if (handoffReason.isPresent()) {
            return Optional.of(BotDecision.handoff(handoffReason.get()));
        }
        Optional<String> reply = replyGuard.check(decision.reply());
        if (reply.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(decision.action() == AiTriageDecision.Action.ASK_MORE
                ? BotDecision.askMore(reply.get(), "AI_ASK_MORE")
                : BotDecision.resolve(reply.get(), "AI_RESPONSE"));
    }

    private Optional<HandoffReason> handoffReason(AiTriageDecision decision) {
        if (decision.intent() == AiTriageDecision.Intent.COMPLAINT) {
            return Optional.of(HandoffReason.COMPLAINT);
        }
        if (decision.intent() == AiTriageDecision.Intent.HUMAN_REQUEST) {
            return Optional.of(HandoffReason.CUSTOMER_REQUEST);
        }
        if (decision.sensitive()) {
            return Optional.of(HandoffReason.SENSITIVE_TOPIC);
        }
        if (decision.action() == AiTriageDecision.Action.HANDOFF) {
            return Optional.of(HandoffReason.ASSISTANT_RECOMMENDATION);
        }
        return Optional.empty();
    }

    /** Fluxo sem IA: intencao nao reconhecida conta como tentativa sem solucao. */
    private BotDecision localDecision(Chatbot chatbot, ChatIntent intent) {
        String reply = fallbackService.generateReply(chatbot, intent);
        return intent == ChatIntent.UNKNOWN
                ? BotDecision.askMore(reply, intent.name())
                : BotDecision.resolve(reply, intent.name());
    }

    private String orUnknown(String value) {
        return value == null || value.isBlank() ? "nao informado" : value;
    }
}
