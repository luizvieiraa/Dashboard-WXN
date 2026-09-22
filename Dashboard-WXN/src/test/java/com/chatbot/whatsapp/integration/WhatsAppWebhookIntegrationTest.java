package com.chatbot.whatsapp.integration;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chatbot.whatsapp.entity.Customer;
import com.chatbot.whatsapp.entity.Triage;
import com.chatbot.whatsapp.entity.enums.TriageCategory;
import com.chatbot.whatsapp.entity.enums.TriagePriority;
import com.chatbot.whatsapp.repository.CustomerRepository;
import com.chatbot.whatsapp.repository.TriageRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Testa o fluxo completo descrito no README:
 * POST /api/v1/webhook/whatsapp -&gt; Controller -&gt; Service -&gt; Processamento -&gt; Banco -&gt; Resposta,
 * e a consulta subsequente da conversa criada.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WhatsAppWebhookIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private TriageRepository triageRepository;

    @Test
    void deveReceberMensagemProcessarEPersistirConversa() throws Exception {
        String payload = """
                {
                  "phone": "5511988887777",
                  "message": "Olá, gostaria de saber o preço de um produto",
                  "timestamp": "2026-09-09T12:00:00Z"
                }
                """;

        String responseJson = mockMvc.perform(post("/api/v1/webhook/whatsapp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.conversationId", notNullValue()))
                .andExpect(jsonPath("$.customerPhone", is("5511988887777")))
                .andExpect(jsonPath("$.botReply", is("Para começar, qual é o seu nome?")))
                .andReturn().getResponse().getContentAsString();

        Long conversationId = objectMapper.readTree(responseJson).get("conversationId").asLong();

        mockMvc.perform(get("/api/v1/conversations/{id}", conversationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerPhone", is("5511988887777")))
                .andExpect(jsonPath("$.status", is("COLLECTING_INFORMATION")))
                .andExpect(jsonPath("$.messages.length()", is(2)));

        mockMvc.perform(get("/api/v1/conversations/{id}/messages", conversationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].direction", is("INBOUND")))
                .andExpect(jsonPath("$[1].direction", is("OUTBOUND")));
    }

    @Test
    void deveColetarInformacoesEConcluirTriagem() throws Exception {
        String phone = "5511970000001";

        JsonNode firstResponse = sendMessage(phone, "Quero contratar um chatbot");
        long conversationId = firstResponse.get("conversationId").asLong();
        org.assertj.core.api.Assertions.assertThat(firstResponse.get("botReply").asText())
                .isEqualTo("Para começar, qual é o seu nome?");

        JsonNode nameResponse = sendMessage(phone, "  Maria   Silva  ");
        org.assertj.core.api.Assertions.assertThat(nameResponse.get("botReply").asText())
                .isEqualTo("Obrigado, Maria Silva. Qual é o nome da sua empresa?");

        JsonNode companyResponse = sendMessage(phone, "Empresa Exemplo");
        org.assertj.core.api.Assertions.assertThat(companyResponse.get("botReply").asText())
                .isEqualTo("Certo. Qual assunto ou informação você procura?");

        JsonNode subjectResponse = sendMessage(phone, "Automatizar a prospecção comercial");
        org.assertj.core.api.Assertions.assertThat(subjectResponse.get("botReply").asText())
                .isEqualTo("Obrigado! Registrei suas informações e concluímos a triagem inicial.");

        mockMvc.perform(get("/api/v1/conversations/{id}", conversationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("QUALIFIED")))
                .andExpect(jsonPath("$.context", is("TRIAGE_COMPLETED")))
                .andExpect(jsonPath("$.messages.length()", is(8)));

        Customer customer = customerRepository.findByPhoneNumber(phone).orElseThrow();
        Triage triage = triageRepository.findByConversationId(conversationId).orElseThrow();

        org.assertj.core.api.Assertions.assertThat(customer.getName()).isEqualTo("Maria Silva");
        org.assertj.core.api.Assertions.assertThat(customer.getCompanyName()).isEqualTo("Empresa Exemplo");
        org.assertj.core.api.Assertions.assertThat(triage.getCategory()).isEqualTo(TriageCategory.INFORMATION);
        org.assertj.core.api.Assertions.assertThat(triage.getSubject())
                .isEqualTo("Automatizar a prospecção comercial");
        org.assertj.core.api.Assertions.assertThat(triage.getCustomerNeed())
                .isEqualTo("Quero contratar um chatbot");
        org.assertj.core.api.Assertions.assertThat(triage.getMissingInformation()).isEmpty();
        org.assertj.core.api.Assertions.assertThat(triage.getSummary())
                .contains("Maria Silva", "Empresa Exemplo", "Automatizar a prospecção comercial");
    }

    @Test
    void deveInterromperColetaEEncaminharReclamacaoParaHumano() throws Exception {
        String phone = "5511970000002";

        JsonNode firstResponse = sendMessage(phone, "Quero informações sobre o serviço");
        long conversationId = firstResponse.get("conversationId").asLong();
        JsonNode complaintResponse = sendMessage(phone, "Na verdade estou insatisfeito, o sistema não funciona");

        org.assertj.core.api.Assertions.assertThat(complaintResponse.get("botReply").asText())
                .contains("atendente humano");

        mockMvc.perform(get("/api/v1/conversations/{id}", conversationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("WAITING_HUMAN")))
                .andExpect(jsonPath("$.context", is("COMPLAINT_ESCALATED")));

        Triage triage = triageRepository.findByConversationId(conversationId).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(triage.getCategory()).isEqualTo(TriageCategory.COMPLAINT);
        org.assertj.core.api.Assertions.assertThat(triage.getPriority()).isEqualTo(TriagePriority.HIGH);
        org.assertj.core.api.Assertions.assertThat(triage.isRequiresHuman()).isTrue();
        org.assertj.core.api.Assertions.assertThat(triage.getMissingInformation()).isEmpty();
        org.assertj.core.api.Assertions.assertThat(triage.getSubject()).contains("não funciona");
    }

    @Test
    void devePermitirAtendenteAssumirResponderEEncerrarConversa() throws Exception {
        String phone = "5511970000003";
        long conversationId = sendMessage(phone, "Quero reclamar de uma cobrança indevida")
                .get("conversationId").asLong();

        JsonNode queuedMessage = sendMessage(phone, "Tenho o comprovante aqui");
        org.assertj.core.api.Assertions.assertThat(queuedMessage.get("botReply").isNull()).isTrue();

        mockMvc.perform(post("/api/v1/conversations/{id}/human/claim", conversationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("attendant", "Carlos Lima"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("HUMAN_ACTIVE")))
                .andExpect(jsonPath("$.assignedTo", is("Carlos Lima")))
                .andExpect(jsonPath("$.assignedAt", notNullValue()));

        mockMvc.perform(post("/api/v1/conversations/{id}/human/reply", conversationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "message", "Olá, sou Carlos. Vou analisar seu comprovante."
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.context", is("HUMAN_REPLY")))
                .andExpect(jsonPath("$.messages.length()", is(4)))
                .andExpect(jsonPath("$.messages[3].direction", is("OUTBOUND")));

        mockMvc.perform(post("/api/v1/conversations/{id}/close", conversationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("CLOSED")))
                .andExpect(jsonPath("$.closedAt", notNullValue()));
    }

    @Test
    void deveRetornar409AoResponderConversaNaoAssumida() throws Exception {
        long conversationId = sendMessage("5511970000004", "Quero fazer uma reclamação")
                .get("conversationId").asLong();

        mockMvc.perform(post("/api/v1/conversations/{id}/human/reply", conversationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("message", "Resposta"))))
                .andExpect(status().isConflict());
    }

    @Test
    void deveDisponibilizarIndicadoresETriagensParaDashboard() throws Exception {
        String phone = "5511970000005";
        sendMessage(phone, "Quero reclamar, o serviço está péssimo");

        mockMvc.perform(get("/api/v1/dashboard/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCustomers").isNumber())
                .andExpect(jsonPath("$.totalConversations").isNumber())
                .andExpect(jsonPath("$.totalMessages").isNumber())
                .andExpect(jsonPath("$.botActive").isNumber())
                .andExpect(jsonPath("$.complaints").isNumber())
                .andExpect(jsonPath("$.requiringHuman").isNumber())
                .andExpect(jsonPath("$.generatedAt", notNullValue()));

        mockMvc.perform(get("/api/v1/dashboard/triages")
                        .param("category", "COMPLAINT")
                        .param("status", "WAITING_HUMAN")
                        .param("requiresHuman", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].customerPhone", hasItem(phone)))
                .andExpect(jsonPath("$[*].category", hasItem("COMPLAINT")))
                .andExpect(jsonPath("$[*].priority", hasItem("HIGH")));
    }

    @Test
    void deveRetornar400QuandoTelefoneEstiverAusente() throws Exception {
        String payload = """
                {
                  "message": "Olá"
                }
                """;

        mockMvc.perform(post("/api/v1/webhook/whatsapp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deveRetornar400QuandoJsonForInvalido() throws Exception {
        mockMvc.perform(post("/api/v1/webhook/whatsapp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("JSON invalido ou incompatível com o formato esperado")));
    }

    @Test
    void deveRetornar404ParaConversaInexistente() throws Exception {
        mockMvc.perform(get("/api/v1/conversations/{id}", 999999))
                .andExpect(status().isNotFound());
    }

    @Test
    void deveRetornar400ParaIdNaoNumerico() throws Exception {
        mockMvc.perform(get("/api/v1/conversations/abc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void devePreservarErrosHttpDeRoteamentoENegociacaoDeConteudo() throws Exception {
        mockMvc.perform(get("/api/v1/recurso-inexistente"))
                .andExpect(status().isNotFound());

        mockMvc.perform(put("/api/v1/health"))
                .andExpect(status().isMethodNotAllowed());

        mockMvc.perform(post("/api/v1/webhook/whatsapp")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("mensagem"))
                .andExpect(status().isUnsupportedMediaType());
    }

    private JsonNode sendMessage(String phone, String message) throws Exception {
        String payload = objectMapper.writeValueAsString(Map.of(
                "phone", phone,
                "message", message
        ));

        String response = mockMvc.perform(post("/api/v1/webhook/whatsapp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readTree(response);
    }
}
