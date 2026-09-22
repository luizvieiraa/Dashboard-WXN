package com.chatbot.whatsapp.service.triage;

import com.chatbot.whatsapp.entity.Conversation;
import com.chatbot.whatsapp.entity.Customer;
import com.chatbot.whatsapp.entity.Triage;
import com.chatbot.whatsapp.entity.enums.ConversationStatus;
import com.chatbot.whatsapp.entity.enums.TriageCategory;
import com.chatbot.whatsapp.entity.enums.TriageField;
import com.chatbot.whatsapp.entity.enums.TriagePriority;
import com.chatbot.whatsapp.repository.CustomerRepository;
import com.chatbot.whatsapp.repository.TriageRepository;
import com.chatbot.whatsapp.service.ConversationService;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Coleta os campos minimos da triagem, uma pergunta por vez. Esta primeira
 * implementacao e deterministica; a extracao por IA sera adicionada depois.
 */
@Service
public class InformationCollectionService {

    private static final int MAX_SHORT_FIELD_LENGTH = 255;
    private static final List<TriageField> COLLECTION_ORDER = List.of(
            TriageField.CUSTOMER_NAME,
            TriageField.COMPANY_NAME,
            TriageField.SUBJECT
    );

    private final TriageRepository triageRepository;
    private final CustomerRepository customerRepository;
    private final ConversationService conversationService;

    public InformationCollectionService(TriageRepository triageRepository,
                                        CustomerRepository customerRepository,
                                        ConversationService conversationService) {
        this.triageRepository = triageRepository;
        this.customerRepository = customerRepository;
        this.conversationService = conversationService;
    }

    /**
     * Processa uma etapa de coleta. Retorna vazio quando a triagem ja esta
     * completa e a mensagem deve seguir para o chatbot normal.
     */
    @Transactional
    public Optional<TriageCollectionResult> collect(
            Conversation conversation,
            Customer customer,
            String message) {
        Optional<Triage> existingTriage = triageRepository.findByConversationId(conversation.getId());

        if (existingTriage.isEmpty()) {
            Triage triage = startTriage(conversation, customer, message);
            return Optional.of(questionFor(nextMissingField(triage).orElseThrow(), customer));
        }

        Triage triage = existingTriage.get();
        Optional<TriageField> currentField = nextMissingField(triage);
        if (currentField.isEmpty()) {
            return Optional.empty();
        }

        String value = normalizeShortField(message);
        if (value.length() > MAX_SHORT_FIELD_LENGTH) {
            return Optional.of(new TriageCollectionResult(
                    "Quase lá! Essa resposta ficou um pouco longa. Pode resumi-la em até 255 caracteres, por favor?",
                    "TRIAGE_INVALID_" + currentField.get().name()
            ));
        }

        applyField(currentField.get(), value, customer, triage);
        triage.getMissingInformation().remove(currentField.get());

        Optional<TriageField> nextField = nextMissingField(triage);
        if (nextField.isPresent()) {
            triageRepository.save(triage);
            return Optional.of(questionFor(nextField.get(), customer));
        }

        triage.setSummary(buildSummary(customer, triage));
        triageRepository.save(triage);
        conversationService.changeStatus(conversation, ConversationStatus.QUALIFIED);
        return Optional.of(new TriageCollectionResult(
                "Tudo certo, " + firstName(customer.getName()) + "! ✨ Registrei suas informações e concluí a triagem inicial. "
                        + "A equipe da WXN já terá esse contexto para continuar o atendimento.",
                "TRIAGE_COMPLETED"
        ));
    }

    private Triage startTriage(Conversation conversation, Customer customer, String firstMessage) {
        LinkedHashSet<TriageField> missingFields = new LinkedHashSet<>();
        if (isBlank(customer.getName())) {
            missingFields.add(TriageField.CUSTOMER_NAME);
        }
        if (isBlank(customer.getCompanyName())) {
            missingFields.add(TriageField.COMPANY_NAME);
        }
        missingFields.add(TriageField.SUBJECT);

        Triage triage = Triage.builder()
                .conversation(conversation)
                .category(TriageCategory.INFORMATION)
                .priority(TriagePriority.MEDIUM)
                .customerNeed(firstMessage.trim())
                .missingInformation(missingFields)
                .build();

        conversationService.changeStatus(conversation, ConversationStatus.COLLECTING_INFORMATION);
        return triageRepository.save(triage);
    }

    private void applyField(TriageField field, String value, Customer customer, Triage triage) {
        switch (field) {
            case CUSTOMER_NAME -> {
                customer.setName(value);
                customerRepository.save(customer);
            }
            case COMPANY_NAME -> {
                customer.setCompanyName(value);
                customerRepository.save(customer);
            }
            case SUBJECT -> triage.setSubject(value);
        }
    }

    private Optional<TriageField> nextMissingField(Triage triage) {
        return COLLECTION_ORDER.stream()
                .filter(triage.getMissingInformation()::contains)
                .findFirst();
    }

    private TriageCollectionResult questionFor(TriageField field, Customer customer) {
        return switch (field) {
            case CUSTOMER_NAME -> new TriageCollectionResult(
                    "Olá! Que bom ter você por aqui 😊 Vou fazer algumas perguntas rápidas para entender como a WXN pode ajudar. "
                            + "Para começar, como posso te chamar?",
                    "TRIAGE_AWAITING_CUSTOMER_NAME"
            );
            case COMPANY_NAME -> new TriageCollectionResult(
                    "Prazer, " + firstName(customer.getName()) + "! Em qual empresa você trabalha? "
                            + "Se estiver falando por conta própria, pode responder “particular”.",
                    "TRIAGE_AWAITING_COMPANY_NAME"
            );
            case SUBJECT -> new TriageCollectionResult(
                    "Perfeito, " + firstName(customer.getName()) + "! Agora me conte um pouco mais: "
                            + "qual assunto você gostaria de resolver ou entender melhor?",
                    "TRIAGE_AWAITING_SUBJECT"
            );
        };
    }

    private String firstName(String fullName) {
        if (isBlank(fullName)) {
            return "por aqui";
        }
        return fullName.trim().split("\\s+", 2)[0];
    }

    private String buildSummary(Customer customer, Triage triage) {
        return "%s, da empresa %s, entrou em contato sobre %s. Necessidade inicial: %s"
                .formatted(
                        customer.getName(),
                        customer.getCompanyName(),
                        triage.getSubject(),
                        triage.getCustomerNeed()
                );
    }

    private String normalizeShortField(String value) {
        return value.trim().replaceAll("\\s+", " ");
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
