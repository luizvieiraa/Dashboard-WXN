package com.chatbot.whatsapp.service.chatbot;

import com.chatbot.whatsapp.entity.Conversation;
import com.chatbot.whatsapp.entity.Customer;
import com.chatbot.whatsapp.entity.Message;
import com.chatbot.whatsapp.entity.Triage;
import com.chatbot.whatsapp.entity.enums.MessageDirection;
import com.chatbot.whatsapp.integration.ai.AiProperties;
import com.chatbot.whatsapp.integration.ai.AiTriageRequest;
import com.chatbot.whatsapp.repository.TriageRepository;
import com.chatbot.whatsapp.service.MessageService;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Monta os dados enviados ao assistente virtual a partir do atendimento.
 *
 * <p>Minimizacao de dados: vai somente o necessario para responder - primeiro
 * nome, empresa, assunto da triagem, as ultimas mensagens da conversa (em
 * quantidade e tamanho limitados) e a mensagem atual. O telefone nunca e
 * enviado. Todos os textos passam pelo {@link AssistantInputSanitizer}.</p>
 */
@Component
public class AssistantContextBuilder {

    static final int HISTORY_ENTRY_MAX_CHARS = 500;
    private static final int SHORT_FIELD_MAX_CHARS = 120;
    private static final int SUBJECT_MAX_CHARS = 255;

    private final MessageService messageService;
    private final TriageRepository triageRepository;
    private final AiProperties aiProperties;

    public AssistantContextBuilder(MessageService messageService,
                                   TriageRepository triageRepository,
                                   AiProperties aiProperties) {
        this.messageService = messageService;
        this.triageRepository = triageRepository;
        this.aiProperties = aiProperties;
    }

    /**
     * @param currentMessageId id da mensagem atual, ja gravada; ela vai no campo
     *                         proprio e nao se repete no historico.
     */
    public AiTriageRequest build(AssistantStage stage,
                                 Conversation conversation,
                                 Customer customer,
                                 String message,
                                 Long currentMessageId) {
        int limit = aiProperties.effectiveHistoryMessages();
        List<AiTriageRequest.HistoryEntry> history = messageService
                .recentMessages(conversation.getId(), limit + 1).stream()
                .filter(item -> currentMessageId == null || !currentMessageId.equals(item.getId()))
                .map(this::toHistoryEntry)
                .toList();
        if (history.size() > limit) {
            history = history.subList(history.size() - limit, history.size());
        }

        String subject = triageRepository.findByConversationId(conversation.getId())
                .map(Triage::getSubject)
                .orElse(null);

        return new AiTriageRequest(
                stage.label(),
                AssistantInputSanitizer.sanitize(firstName(customer.getName()), SHORT_FIELD_MAX_CHARS),
                AssistantInputSanitizer.sanitize(customer.getCompanyName(), SHORT_FIELD_MAX_CHARS),
                AssistantInputSanitizer.sanitize(subject, SUBJECT_MAX_CHARS),
                history,
                AssistantInputSanitizer.sanitize(message, aiProperties.effectiveMaxInputChars())
        );
    }

    private AiTriageRequest.HistoryEntry toHistoryEntry(Message item) {
        // Enquanto o bot conduz a conversa, toda mensagem enviada e do bot: a
        // conversa nunca volta do atendimento humano para o automatico.
        String author = item.getDirection() == MessageDirection.INBOUND ? "cliente" : "assistente";
        return new AiTriageRequest.HistoryEntry(
                author,
                AssistantInputSanitizer.sanitize(item.getContent(), HISTORY_ENTRY_MAX_CHARS));
    }

    private static String firstName(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return null;
        }
        return fullName.trim().split("\\s+", 2)[0];
    }
}
