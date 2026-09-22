# API

Documentação interativa (Swagger UI) disponível em `/swagger-ui.html` quando
a aplicação está no ar (ver README). Este documento traz a referência
completa dos endpoints.

Prefixo comum: `/api/v1`.

---

## `GET /api/v1/health`

Verifica se a aplicação está no ar.

* **Parâmetros**: nenhum.
* **Body**: nenhum.
* **Resposta `200 OK`**:

```json
{
  "status": "UP",
  "service": "whatsapp-chatbot-api",
  "timestamp": "2026-09-09T12:00:00Z"
}
```

Também está disponível o health check padrão do Spring Boot Actuator em
`GET /actuator/health` (usado por ferramentas de infraestrutura/orquestração).

---

## `POST /api/v1/webhook/whatsapp`

Recebe uma mensagem do cliente. **Hoje simula o webhook do WhatsApp** (ver
README, seção "Integração real com WhatsApp"): aceita o mesmo tipo de
informação que chegaria de um provedor real, permitindo testar todo o
fluxo antes de uma integração real estar configurada.

* **Body** (`application/json`):

| Campo | Tipo | Obrigatório | Descrição |
|---|---|---|---|
| `phone` | string | sim | Telefone do cliente, apenas dígitos (8 a 15 caracteres), ex.: `5511999999999` |
| `message` | string | sim | Texto da mensagem enviada pelo cliente |
| `timestamp` | string (ISO-8601) | não | Data/hora do envio; se omitido, usa o horário do servidor |

```json
{
  "phone": "5511999999999",
  "message": "Olá, gostaria de saber o preço de um produto",
  "timestamp": "2026-09-09T12:00:00Z"
}
```

* **Resposta `201 Created`**:

```json
{
  "conversationId": 1,
  "customerPhone": "5511999999999",
  "receivedMessage": "Olá, gostaria de saber o preço de um produto",
  "botReply": "Claro. Vou verificar o preço solicitado e já te retorno.",
  "processedAt": "2026-09-09T12:00:01Z"
}
```

* **Erros**:
  * `400 Bad Request` - `phone` ou `message` ausentes/inválidos (corpo no formato padrão de erro, ver abaixo).

---

## `GET /api/v1/conversations/{id}`

Consulta uma conversa e todas as suas mensagens, em ordem cronológica.

* **Parâmetros de rota**: `id` (long) - id da conversa.
* **Resposta `200 OK`**:

```json
{
  "id": 1,
  "customerPhone": "5511999999999",
  "status": "BOT_ACTIVE",
  "channel": "WHATSAPP",
  "context": "PRICE_INQUIRY",
  "startedAt": "2026-09-09T12:00:00Z",
  "lastInteractionAt": "2026-09-09T12:00:01Z",
  "messages": [
    { "id": 1, "direction": "INBOUND", "content": "Olá, gostaria de saber o preço de um produto", "status": "PROCESSED", "createdAt": "2026-09-09T12:00:00Z" },
    { "id": 2, "direction": "OUTBOUND", "content": "Claro. Vou verificar o preço solicitado e já te retorno.", "status": "SENT", "createdAt": "2026-09-09T12:00:01Z" }
  ]
}
```

* **Erros**: `404 Not Found` - conversa não existe.

---

## `GET /api/v1/conversations/{id}/messages`

Lista apenas as mensagens de uma conversa (sem os dados da conversa em si),
em ordem cronológica.

* **Parâmetros de rota**: `id` (long) - id da conversa.
* **Resposta `200 OK`**: array de mensagens, mesmo formato de `messages` acima (pode ser vazio, mas a conversa precisa existir).
* **Erros**: `404 Not Found` - conversa não existe.

---

## Formato padrão de erro

Toda resposta de erro da API segue este formato:

```json
{
  "timestamp": "2026-09-09T12:00:00Z",
  "status": 404,
  "error": "Not Found",
  "message": "Conversa nao encontrada: id=999",
  "path": "/api/v1/conversations/999",
  "details": null
}
```

`details` é preenchido (com uma lista de strings `campo: mensagem`) apenas
em erros de validação (`400`).

## Códigos HTTP usados

| Código | Quando |
|---|---|
| 200 | Consulta bem-sucedida |
| 201 | Mensagem recebida e processada com sucesso |
| 400 | Payload inválido (validação) |
| 404 | Recurso não encontrado (ex.: conversa) |
| 500 | Erro interno inesperado |
