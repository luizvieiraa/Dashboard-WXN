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

Na primeira conversa de um cliente, o bot inicia uma coleta guiada. A
mensagem inicial é registrada como necessidade, e as mensagens seguintes
preenchem nome, empresa e assunto, uma informação por vez.

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
  "botReply": "Para começar, qual é o seu nome?",
  "processedAt": "2026-09-09T12:00:01Z"
}
```

Mantendo o mesmo `phone`, as próximas respostas devem informar, nesta
ordem: nome, empresa e assunto. Ao final, a conversa passa para
`QUALIFIED` e a triagem recebe um resumo.

Reclamações têm precedência em qualquer ponto desse fluxo. Ao identificar
termos de reclamação ou um pedido explícito por atendente, a conversa passa
para `WAITING_HUMAN`, a triagem recebe categoria `COMPLAINT`, prioridade
`HIGH` e `requiresHuman=true`.

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

## Atendimento humano

### `POST /api/v1/conversations/{id}/human/claim`

Atribui uma conversa em `WAITING_HUMAN` a um atendente e altera o status
para `HUMAN_ACTIVE`. Body: `{"attendant":"Carlos Lima"}`.

### `POST /api/v1/conversations/{id}/human/reply`

Registra e envia uma resposta do atendente em uma conversa `HUMAN_ACTIVE`.
Body: `{"message":"Olá, vou analisar o ocorrido."}`.

### `POST /api/v1/conversations/{id}/close`

Encerra a conversa e registra `closedAt`. Uma mensagem posterior do cliente
abrirá uma nova conversa.

As operações incompatíveis com o estado atual retornam `409 Conflict`.

---

## Dashboard

### `GET /api/v1/dashboard/summary`

Retorna totais de clientes, conversas e mensagens, contagens por estado
operacional, triagens de informação, reclamações e casos que requerem humano.

### `GET /api/v1/dashboard/triages`

Lista os dados estruturados da triagem junto com cliente e conversa. Aceita
os filtros opcionais `category`, `status` e `requiresHuman`. O resultado é
ordenado por prioridade e depois pela interação mais recente.

Exemplo para a fila de reclamações:

`GET /api/v1/dashboard/triages?category=COMPLAINT&status=WAITING_HUMAN&requiresHuman=true`

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
| 409 | Operação incompatível com o estado atual da conversa |
| 500 | Erro interno inesperado |
