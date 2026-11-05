package com.chatbot.whatsapp.service;

import com.chatbot.whatsapp.entity.Conversation;
import com.chatbot.whatsapp.entity.Attendant;
import com.chatbot.whatsapp.entity.enums.MessageStatus;
import com.chatbot.whatsapp.exception.InvalidConversationStateException;
import com.chatbot.whatsapp.integration.whatsapp.WhatsAppClient;
import com.chatbot.whatsapp.integration.whatsapp.WhatsAppSendResult;
import com.chatbot.whatsapp.repository.ConversationRepository;
import com.chatbot.whatsapp.repository.AttendantRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Opera o ciclo de atendimento humano iniciado por uma triagem.
 *
 * <p>Toda operacao bloqueia o cliente da conversa antes de le-la (o mesmo
 * bloqueio usado pelo processamento das mensagens). Com isso, assumir,
 * responder ou encerrar nunca acontece no meio de uma resposta automatica, e
 * uma resposta automatica nunca e enviada depois de a conversa ter sido
 * assumida.</p>
 */
@Service
public class HumanAttendanceService {

    static final String SERVICE_WINDOW_CLOSED_MESSAGE =
            "A janela de 24 horas do WhatsApp para esta conversa foi encerrada: o cliente não envia "
                    + "mensagens há mais de 24 horas. Fora dessa janela a Meta só aceita templates aprovados, "
                    + "recurso que este projeto ainda não utiliza. A resposta não foi enviada.";

    private final ConversationService conversationService;
    private final ConversationRepository conversationRepository;
    private final MessageService messageService;
    private final WhatsAppClient whatsAppClient;
    private final AttendantRepository attendantRepository;
    private final CustomerService customerService;
    private final CustomerServiceWindow customerServiceWindow;

    public HumanAttendanceService(ConversationService conversationService,
                                  ConversationRepository conversationRepository,
                                  MessageService messageService,
                                  WhatsAppClient whatsAppClient,
                                  AttendantRepository attendantRepository,
                                  CustomerService customerService,
                                  CustomerServiceWindow customerServiceWindow) {
        this.conversationService = conversationService;
        this.conversationRepository = conversationRepository;
        this.messageService = messageService;
        this.whatsAppClient = whatsAppClient;
        this.attendantRepository = attendantRepository;
        this.customerService = customerService;
        this.customerServiceWindow = customerServiceWindow;
    }

    @Transactional
    public Conversation claim(Long conversationId, String attendant) {
        Conversation conversation = findForUpdate(conversationId);
        Attendant candidate = new Attendant(attendant);
        var operator = attendantRepository.findFirstByName(candidate.getName())
                .orElseGet(() -> attendantRepository.save(candidate));
        conversation.assume(operator);
        return conversationRepository.save(conversation);
    }

    @Transactional
    public Conversation reply(Long conversationId, String text) {
        Conversation conversation = findForUpdate(conversationId);
        conversation.requireHumanAttendance();
        if (!customerServiceWindow.isOpen(conversation.getCustomer(), Instant.now())) {
            throw new InvalidConversationStateException(SERVICE_WINDOW_CLOSED_MESSAGE);
        }

        String message = text.trim();
        WhatsAppSendResult sendResult = whatsAppClient.sendMessage(
                conversation.getCustomer().getPhoneNumber(), message);
        messageService.recordOutbound(
                conversation,
                message,
                sendResult.sent() ? MessageStatus.SENT : MessageStatus.FAILED,
                sendResult.providerMessageId()
        );
        conversation.recordInteraction("HUMAN_REPLY");
        Conversation saved = conversationRepository.save(conversation);
        return saved;
    }

    @Transactional
    public Conversation close(Long conversationId) {
        Conversation conversation = findForUpdate(conversationId);
        conversation.close();
        return conversationRepository.save(conversation);
    }

    /** Bloqueia o cliente e so entao carrega a conversa, ja com o estado mais recente. */
    private Conversation findForUpdate(Long conversationId) {
        customerService.lockById(conversationService.findCustomerId(conversationId));
        return conversationService.findById(conversationId);
    }

}
