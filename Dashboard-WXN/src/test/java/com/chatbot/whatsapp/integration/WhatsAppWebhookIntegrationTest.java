package com.chatbot.whatsapp.integration;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
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
                .andExpect(jsonPath("$.botReply", is("Claro. Vou verificar o preço solicitado e já te retorno.")))
                .andReturn().getResponse().getContentAsString();

        Long conversationId = objectMapper.readTree(responseJson).get("conversationId").asLong();

        mockMvc.perform(get("/api/v1/conversations/{id}", conversationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerPhone", is("5511988887777")))
                .andExpect(jsonPath("$.messages.length()", is(2)));

        mockMvc.perform(get("/api/v1/conversations/{id}/messages", conversationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].direction", is("INBOUND")))
                .andExpect(jsonPath("$[1].direction", is("OUTBOUND")));
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
}
