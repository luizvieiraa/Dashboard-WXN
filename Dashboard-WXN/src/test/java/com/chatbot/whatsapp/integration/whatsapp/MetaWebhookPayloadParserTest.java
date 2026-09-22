package com.chatbot.whatsapp.integration.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Os payloads usados aqui seguem os exemplos da documentacao oficial da
 * WhatsApp Cloud API (webhooks/payload-examples).
 */
class MetaWebhookPayloadParserTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MetaWebhookPayloadParser parser = new MetaWebhookPayloadParser();

    private List<WhatsAppInboundMessage> parse(String json) throws Exception {
        return parser.parse(objectMapper.readValue(json, MetaWebhookPayload.class));
    }

    @Test
    void deveExtrairMensagemDeTexto() throws Exception {
        String payload = """
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
                        "contacts": [{
                          "profile": { "name": "Maria Silva" },
                          "wa_id": "5511988887777"
                        }],
                        "messages": [{
                          "from": "5511988887777",
                          "id": "wamid.HBgLMTY1MDM4Nzk0MzkVAgASGBQzQTRB",
                          "timestamp": "1749416383",
                          "type": "text",
                          "text": { "body": "Olá, quero saber o preço" }
                        }]
                      }
                    }]
                  }]
                }
                """;

        List<WhatsAppInboundMessage> messages = parse(payload);

        assertThat(messages).hasSize(1);
        WhatsAppInboundMessage message = messages.get(0);
        assertThat(message.messageId()).isEqualTo("wamid.HBgLMTY1MDM4Nzk0MzkVAgASGBQzQTRB");
        assertThat(message.phone()).isEqualTo("5511988887777");
        assertThat(message.type()).isEqualTo("text");
        assertThat(message.text()).isEqualTo("Olá, quero saber o preço");
        assertThat(message.contactName()).isEqualTo("Maria Silva");
        assertThat(message.timestamp()).isEqualTo(Instant.ofEpochSecond(1749416383L));
        assertThat(message.isText()).isTrue();
    }

    @Test
    void deveIgnorarNotificacaoDeStatusDeEntrega() throws Exception {
        String payload = """
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
                        "statuses": [{
                          "id": "wamid.HBgLMTY1MDM4Nzk0MzkVAgARGBI3",
                          "status": "delivered",
                          "timestamp": "1750263773",
                          "recipient_id": "16505551234"
                        }]
                      }
                    }]
                  }]
                }
                """;

        assertThat(parse(payload)).isEmpty();
    }

    @Test
    void deveMarcarMensagemNaoTextualComoNaoInterpretavel() throws Exception {
        String payload = """
                {
                  "object": "whatsapp_business_account",
                  "entry": [{
                    "changes": [{
                      "value": {
                        "messages": [{
                          "from": "5511988887777",
                          "id": "wamid.IMAGEM",
                          "timestamp": "1749416383",
                          "type": "image",
                          "image": { "id": "1234", "mime_type": "image/jpeg" }
                        }]
                      }
                    }]
                  }]
                }
                """;

        List<WhatsAppInboundMessage> messages = parse(payload);

        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).type()).isEqualTo("image");
        assertThat(messages.get(0).isText()).isFalse();
        assertThat(messages.get(0).text()).isNull();
    }

    @Test
    void deveExtrairVariasMensagensDeUmMesmoLote() throws Exception {
        String payload = """
                {
                  "entry": [{
                    "changes": [{
                      "value": {
                        "messages": [
                          {"from": "5511111111111", "id": "wamid.A", "timestamp": "1749416383",
                           "type": "text", "text": {"body": "primeira"}},
                          {"from": "5522222222222", "id": "wamid.B", "timestamp": "1749416384",
                           "type": "text", "text": {"body": "segunda"}}
                        ]
                      }
                    }]
                  }]
                }
                """;

        List<WhatsAppInboundMessage> messages = parse(payload);

        assertThat(messages).extracting(WhatsAppInboundMessage::text)
                .containsExactly("primeira", "segunda");
    }

    @Test
    void deveDescartarMensagemSemIdOuRemetente() throws Exception {
        String payload = """
                {
                  "entry": [{
                    "changes": [{
                      "value": {
                        "messages": [
                          {"id": "wamid.SEM_FROM", "type": "text", "text": {"body": "x"}},
                          {"from": "5511988887777", "type": "text", "text": {"body": "y"}}
                        ]
                      }
                    }]
                  }]
                }
                """;

        assertThat(parse(payload)).isEmpty();
    }

    @Test
    void deveNormalizarTelefoneRemovendoCaracteresNaoNumericos() throws Exception {
        String payload = """
                {
                  "entry": [{
                    "changes": [{
                      "value": {
                        "contacts": [{"profile": {"name": "Joao"}, "wa_id": "+55 11 98888-7777"}],
                        "messages": [{
                          "from": "+55 11 98888-7777", "id": "wamid.C", "timestamp": "1749416383",
                          "type": "text", "text": {"body": "oi"}
                        }]
                      }
                    }]
                  }]
                }
                """;

        List<WhatsAppInboundMessage> messages = parse(payload);

        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).phone()).isEqualTo("5511988887777");
        // O nome do contato continua vinculado mesmo apos a normalizacao.
        assertThat(messages.get(0).contactName()).isEqualTo("Joao");
    }

    @Test
    void deveUsarHorarioAtualQuandoTimestampForInvalido() throws Exception {
        String payload = """
                {
                  "entry": [{
                    "changes": [{
                      "value": {
                        "messages": [{
                          "from": "5511988887777", "id": "wamid.D", "timestamp": "nao-e-numero",
                          "type": "text", "text": {"body": "oi"}
                        }]
                      }
                    }]
                  }]
                }
                """;

        List<WhatsAppInboundMessage> messages = parse(payload);

        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).timestamp()).isNotNull();
    }

    @Test
    void deveTolerarPayloadsVaziosOuIncompletos() throws Exception {
        assertThat(parser.parse(null)).isEmpty();
        assertThat(parse("{}")).isEmpty();
        assertThat(parse("{\"entry\": []}")).isEmpty();
        assertThat(parse("{\"entry\": [{}]}")).isEmpty();
        assertThat(parse("{\"entry\": [{\"changes\": [{}]}]}")).isEmpty();
        assertThat(parse("{\"entry\": [{\"changes\": [{\"value\": {}}]}]}")).isEmpty();
    }

    @Test
    void deveIgnorarCamposDesconhecidosDaMeta() throws Exception {
        String payload = """
                {
                  "object": "whatsapp_business_account",
                  "campo_futuro": {"algo": 1},
                  "entry": [{
                    "changes": [{
                      "value": {
                        "outro_campo_novo": true,
                        "messages": [{
                          "from": "5511988887777", "id": "wamid.E", "timestamp": "1749416383",
                          "type": "text", "text": {"body": "oi"}, "context": {"id": "wamid.X"}
                        }]
                      }
                    }]
                  }]
                }
                """;

        assertThat(parse(payload)).hasSize(1);
    }
}
