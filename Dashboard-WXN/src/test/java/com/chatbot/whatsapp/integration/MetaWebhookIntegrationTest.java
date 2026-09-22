package com.chatbot.whatsapp.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chatbot.whatsapp.entity.Conversation;
import com.chatbot.whatsapp.entity.Customer;
import com.chatbot.whatsapp.entity.enums.ConversationStatus;
import com.chatbot.whatsapp.repository.ConversationRepository;
import com.chatbot.whatsapp.repository.CustomerRepository;
import com.chatbot.whatsapp.repository.MessageRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Fluxo real do canal WhatsApp:
 * POST /api/v1/webhook/whatsapp/meta -&gt; parser -&gt; WhatsAppInboundService
 * -&gt; MessageProcessingService (o MESMO usado pelo frontend) -&gt; banco -&gt; resposta.
 *
 * <p>No perfil de teste {@code whatsapp.enabled=false}, portanto o envio da
 * resposta e feito pelo {@code MockWhatsAppSender}. O que este teste prova e que
 * uma notificacao no formato da Meta chega ate o chatbot existente e que a
 * resposta e persistida - nao que a Meta esteja realmente entregando mensagens
 * (isso depende de configuracao externa, ver README).</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MetaWebhookIntegrationTest {

    private static final String WEBHOOK_PATH = "/api/v1/webhook/whatsapp/meta";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private MessageRepository messageRepository;

    private String textPayload(String phone, String messageId, String text) {
        return """
                {
                  "object": "whatsapp_business_account",
                  "entry": [{
                    "id": "102290129340398",
                    "changes": [{
                      "field": "messages",
                      "value": {
                        "messaging_product": "whatsapp",
                        "metadata": {
                          "display_phone_number": "15550783881",
                          "phone_number_id": "106540352242922"
                        },
                        "contacts": [{"profile": {"name": "Cliente Teste"}, "wa_id": "%s"}],
                        "messages": [{
                          "from": "%s",
                          "id": "%s",
                          "timestamp": "1749416383",
                          "type": "text",
                          "text": {"body": "%s"}
                        }]
                      }
                    }]
                  }]
                }
                """.formatted(phone, phone, messageId, text);
    }

    private Optional<Conversation> activeConversationOf(String phone) {
        return customerRepository.findByPhoneNumber(phone)
                .flatMap(customer -> conversationRepository
                        .findFirstByCustomerAndStatusNotOrderByStartedAtDesc(
                                customer, ConversationStatus.CLOSED));
    }

    // ---------- Handshake de verificacao (GET) ----------

    @Test
    void deveResponderOChallengeQuandoOTokenDeVerificacaoConferir() throws Exception {
        mockMvc.perform(get(WEBHOOK_PATH)
                        .param("hub.mode", "subscribe")
                        .param("hub.verify_token", "token-de-teste")
                        .param("hub.challenge", "1158201444"))
                .andExpect(status().isOk())
                .andExpect(content().string("1158201444"));
    }

    @Test
    void deveRecusarVerificacaoComTokenIncorreto() throws Exception {
        mockMvc.perform(get(WEBHOOK_PATH)
                        .param("hub.mode", "subscribe")
                        .param("hub.verify_token", "token-errado")
                        .param("hub.challenge", "1158201444"))
                .andExpect(status().isForbidden());
    }

    @Test
    void deveRecusarVerificacaoComModoInesperado() throws Exception {
        mockMvc.perform(get(WEBHOOK_PATH)
                        .param("hub.mode", "unsubscribe")
                        .param("hub.verify_token", "token-de-teste")
                        .param("hub.challenge", "1158201444"))
                .andExpect(status().isForbidden());
    }

    // ---------- Recebimento de mensagens (POST) ----------

    @Test
    void deveProcessarMensagemDeTextoNoMesmoChatbotDoFrontend() throws Exception {
        String phone = "5511970001001";

        mockMvc.perform(post(WEBHOOK_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(textPayload(phone, "wamid.TESTE001", "Quero contratar um chatbot")))
                .andExpect(status().isOk());

        Conversation conversation = activeConversationOf(phone).orElseThrow();

        // Mesmo comportamento do canal do frontend: a triagem inicia pedindo o nome.
        mockMvc.perform(get("/api/v1/conversations/{id}", conversation.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerPhone", is(phone)))
                .andExpect(jsonPath("$.status", is("COLLECTING_INFORMATION")))
                .andExpect(jsonPath("$.messages.length()", is(2)))
                .andExpect(jsonPath("$.messages[0].direction", is("INBOUND")))
                .andExpect(jsonPath("$.messages[1].direction", is("OUTBOUND")))
                .andExpect(jsonPath("$.messages[1].content",
                        org.hamcrest.Matchers.containsString("como posso te chamar")));

        // O id da Meta fica guardado para servir de chave de idempotencia.
        assertThat(messageRepository.existsByExternalId("wamid.TESTE001")).isTrue();
    }

    @Test
    void deveConduzirATriagemCompletaPeloCanalDoWhatsApp() throws Exception {
        String phone = "5511970001002";

        sendText(phone, "wamid.T01", "Quero informações");
        sendText(phone, "wamid.T02", "Maria Silva");
        sendText(phone, "wamid.T03", "Empresa Exemplo");
        sendText(phone, "wamid.T04", "Automatizar o atendimento");

        Conversation conversation = activeConversationOf(phone).orElseThrow();

        mockMvc.perform(get("/api/v1/conversations/{id}", conversation.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("QUALIFIED")))
                .andExpect(jsonPath("$.context", is("TRIAGE_COMPLETED")));

        Customer customer = customerRepository.findByPhoneNumber(phone).orElseThrow();
        assertThat(customer.getName()).isEqualTo("Maria Silva");
        assertThat(customer.getCompanyName()).isEqualTo("Empresa Exemplo");
    }

    @Test
    void deveEncaminharReclamacaoParaHumanoPeloCanalDoWhatsApp() throws Exception {
        String phone = "5511970001003";

        sendText(phone, "wamid.R01", "Estou insatisfeito, o sistema não funciona");

        Conversation conversation = activeConversationOf(phone).orElseThrow();
        assertThat(conversation.getStatus()).isEqualTo(ConversationStatus.WAITING_HUMAN);
    }

    @Test
    void deveDescartarReentregaDaMesmaMensagem() throws Exception {
        String phone = "5511970001004";
        String payload = textPayload(phone, "wamid.DUPLICADA", "Olá");

        mockMvc.perform(post(WEBHOOK_PATH)
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isOk());
        // A Meta reentrega a mesma notificacao quando nao recebe 200 em tempo.
        mockMvc.perform(post(WEBHOOK_PATH)
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isOk());

        Conversation conversation = activeConversationOf(phone).orElseThrow();

        // Continua com 2 mensagens (1 recebida + 1 resposta), nao 4.
        mockMvc.perform(get("/api/v1/conversations/{id}", conversation.getId()))
                .andExpect(jsonPath("$.messages.length()", is(2)));
    }

    @Test
    void deveIgnorarNotificacaoDeStatusSemCriarConversa() throws Exception {
        String payload = """
                {
                  "object": "whatsapp_business_account",
                  "entry": [{"changes": [{"value": {
                    "messaging_product": "whatsapp",
                    "statuses": [{
                      "id": "wamid.STATUS", "status": "delivered",
                      "timestamp": "1750263773", "recipient_id": "5511970009999"
                    }]
                  }}]}]
                }
                """;

        mockMvc.perform(post(WEBHOOK_PATH)
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isOk());

        assertThat(customerRepository.findByPhoneNumber("5511970009999")).isEmpty();
    }

    @Test
    void deveResponderOkSemCriarConversaParaMensagemNaoTextual() throws Exception {
        String phone = "5511970001005";
        String payload = """
                {
                  "entry": [{"changes": [{"value": {
                    "messages": [{
                      "from": "%s", "id": "wamid.AUDIO", "timestamp": "1749416383",
                      "type": "audio", "audio": {"id": "999", "mime_type": "audio/ogg"}
                    }]
                  }}]}]
                }
                """.formatted(phone);

        mockMvc.perform(post(WEBHOOK_PATH)
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isOk());

        // Nao contamina a triagem com conteudo que o chatbot nao interpreta.
        assertThat(customerRepository.findByPhoneNumber(phone)).isEmpty();
    }

    @Test
    void deveResponderOkParaPayloadIrreconheciveisSemQuebrar() throws Exception {
        mockMvc.perform(post(WEBHOOK_PATH)
                        .contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isOk());

        mockMvc.perform(post(WEBHOOK_PATH)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
    }

    @Test
    void deveIgnorarMensagemComTelefoneForaDoFormato() throws Exception {
        String payload = textPayload("123", "wamid.CURTO", "Olá");

        mockMvc.perform(post(WEBHOOK_PATH)
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isOk());

        assertThat(customerRepository.findByPhoneNumber("123")).isEmpty();
    }

    // ---------- O canal existente continua intacto ----------

    @Test
    void deveManterOEndpointDoSimuladorFuncionando() throws Exception {
        mockMvc.perform(post("/api/v1/webhook/whatsapp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"5511970002001\",\"message\":\"Olá\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.botReply",
                        org.hamcrest.Matchers.containsString("como posso te chamar")));
    }

    private void sendText(String phone, String messageId, String text) throws Exception {
        mockMvc.perform(post(WEBHOOK_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(textPayload(phone, messageId, text)))
                .andExpect(status().isOk());
    }
}
