package com.chatbot.whatsapp.service.chatbot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chatbot.whatsapp.entity.Conversation;
import com.chatbot.whatsapp.entity.Customer;
import com.chatbot.whatsapp.entity.enums.ConversationStatus;
import com.chatbot.whatsapp.integration.ai.AiClient;
import com.chatbot.whatsapp.integration.ai.AiProperties;
import com.chatbot.whatsapp.integration.ai.AiTriageDecision;
import com.chatbot.whatsapp.integration.ai.AiTriageDecision.Action;
import com.chatbot.whatsapp.integration.ai.AiTriageDecision.Intent;
import com.chatbot.whatsapp.integration.ai.AiTriageRequest;
import com.chatbot.whatsapp.integration.ai.AssistantResources;
import com.chatbot.whatsapp.service.triage.HandoffReason;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class IntelligentResponseServiceTest {

    private final AiProperties properties = new AiProperties(
            true, "https://ai.example.test", "chave", "modelo", 5, 200, 10, 1000, 0.6, null);
    private final AiClient aiClient = mock(AiClient.class);
    private final AssistantContextBuilder contextBuilder = mock(AssistantContextBuilder.class);
    private final IntelligentResponseService service = new IntelligentResponseService(
            aiClient,
            new ChatbotResponseService(),
            contextBuilder,
            new AssistantReplyGuard(AssistantResources.fromClasspath(properties)),
            properties);

    private final Conversation conversation = Conversation.builder()
            .id(1L).status(ConversationStatus.QUALIFIED).build();
    private final Customer customer = Customer.builder().phoneNumber("5511988887777").name("Maria").build();

    private static AiTriageDecision decision(Intent intent, Action action, double confidence,
                                             boolean sensitive, String reply) {
        return new AiTriageDecision(intent, action, confidence, sensitive, reply);
    }

    private void assistantAnswers(AiTriageDecision decision) {
        when(aiClient.isAvailable()).thenReturn(true);
        when(contextBuilder.build(any(), any(), any(), any(), any()))
                .thenReturn(new AiTriageRequest("CONVERSA", "Maria", null, null, List.of(), "texto"));
        when(aiClient.decide(any())).thenReturn(Optional.ofNullable(decision));
    }

    private BotDecision decide(String message, ChatIntent fallbackIntent) {
        return service.decide(conversation, customer, message, fallbackIntent, 10L);
    }

    @Test
    void deveResponderQuandoOAssistenteResolveComConfianca() {
        assistantAnswers(decision(Intent.GREETING, Action.RESOLVE, 0.95, false, "Olá, Maria! Em que posso ajudar?"));

        BotDecision result = decide("Oi", ChatIntent.GREETING);

        assertThat(result.action()).isEqualTo(BotAction.RESOLVE);
        assertThat(result.reply()).isEqualTo("Olá, Maria! Em que posso ajudar?");
        assertThat(result.context()).isEqualTo("AI_RESPONSE");
    }

    @Test
    void devePedirMaisInformacoesQuandoOAssistenteSugerir() {
        assistantAnswers(decision(Intent.SUPPORT, Action.ASK_MORE, 0.8, false, "Pode me contar o que aconteceu?"));

        BotDecision result = decide("Deu um problema", ChatIntent.UNKNOWN);

        assertThat(result.action()).isEqualTo(BotAction.ASK_MORE);
        assertThat(result.context()).isEqualTo("AI_ASK_MORE");
    }

    @Test
    void deveEncaminharParaHumanoQuandoAConfiancaForBaixa() {
        assistantAnswers(decision(Intent.INFORMATION, Action.RESOLVE, 0.4, false, "Talvez seja isso."));

        BotDecision result = decide("Qual o prazo?", ChatIntent.UNKNOWN);

        assertThat(result.action()).isEqualTo(BotAction.HANDOFF);
        assertThat(result.handoffReason()).isEqualTo(HandoffReason.LOW_CONFIDENCE);
        assertThat(result.reply()).isNull();
    }

    @Test
    void deveEncaminharReclamacaoPedidoDeAtendenteEAssuntoSensivel() {
        assistantAnswers(decision(Intent.COMPLAINT, Action.RESOLVE, 0.9, false, "Sinto muito."));
        assertThat(decide("Me cobraram duas vezes", ChatIntent.UNKNOWN).handoffReason())
                .isEqualTo(HandoffReason.COMPLAINT);

        assistantAnswers(decision(Intent.HUMAN_REQUEST, Action.HANDOFF, 0.9, false, ""));
        assertThat(decide("Quero alguém da equipe", ChatIntent.UNKNOWN).handoffReason())
                .isEqualTo(HandoffReason.CUSTOMER_REQUEST);

        assistantAnswers(decision(Intent.OTHER, Action.RESOLVE, 0.9, true, "Entendo."));
        assertThat(decide("Vou procurar o Procon", ChatIntent.UNKNOWN).handoffReason())
                .isEqualTo(HandoffReason.SENSITIVE_TOPIC);

        assistantAnswers(decision(Intent.SUPPORT, Action.HANDOFF, 0.9, false, ""));
        assertThat(decide("Preciso alterar meu contrato", ChatIntent.UNKNOWN).handoffReason())
                .isEqualTo(HandoffReason.ASSISTANT_RECOMMENDATION);
    }

    @Test
    void deveUsarFluxoLocalQuandoARespostaGeradaForReprovadaPelasRegras() {
        // Preco inventado: a base de conhecimento padrao nao tem valores.
        assistantAnswers(decision(Intent.PRICE_INQUIRY, Action.RESOLVE, 0.9, false, "Custa R$ 10,00."));

        BotDecision result = decide("Quanto custa?", ChatIntent.PRICE_INQUIRY);

        assertThat(result.action()).isEqualTo(BotAction.RESOLVE);
        assertThat(result.context()).isEqualTo("PRICE_INQUIRY");
        assertThat(result.reply()).doesNotContain("R$");
    }

    @Test
    void deveUsarFluxoLocalQuandoOProvedorFalharOuEstiverDesligado() {
        assistantAnswers(null);
        BotDecision afterFailure = decide("xpto", ChatIntent.UNKNOWN);
        assertThat(afterFailure.action()).isEqualTo(BotAction.ASK_MORE);
        assertThat(afterFailure.context()).isEqualTo("UNKNOWN");
        assertThat(afterFailure.reply()).contains("Não entendi");

        when(aiClient.isAvailable()).thenReturn(false);
        BotDecision disabled = decide("Oi", ChatIntent.GREETING);
        assertThat(disabled.action()).isEqualTo(BotAction.RESOLVE);
        assertThat(disabled.context()).isEqualTo("GREETING");
    }

    @Test
    void naPrimeiraMensagemSoEncaminhaQuandoHaSinalClaroDeAtendimentoHumano() {
        assistantAnswers(decision(Intent.COMPLAINT, Action.HANDOFF, 0.9, true, ""));
        assertThat(service.handoffOnFirstMessage(conversation, customer, "Fui enganado", 10L))
                .contains(HandoffReason.COMPLAINT);

        // Pouca confianca ou mensagem comum: a triagem estruturada continua.
        assistantAnswers(decision(Intent.COMPLAINT, Action.HANDOFF, 0.3, true, ""));
        assertThat(service.handoffOnFirstMessage(conversation, customer, "hmm", 10L)).isEmpty();

        assistantAnswers(decision(Intent.GREETING, Action.RESOLVE, 0.99, false, "Olá!"));
        assertThat(service.handoffOnFirstMessage(conversation, customer, "Olá", 10L)).isEmpty();
    }

    @Test
    void naoDeveMontarContextoNemChamarProvedorQuandoIndisponivel() {
        when(aiClient.isAvailable()).thenReturn(false);

        assertThat(service.handoffOnFirstMessage(conversation, customer, "Olá", 10L)).isEmpty();

        verify(contextBuilder, never()).build(any(), any(), any(), any(), any());
        verify(aiClient, never()).decide(any());
    }
}
