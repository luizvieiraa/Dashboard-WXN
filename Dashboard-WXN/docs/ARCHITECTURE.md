# Arquitetura

## Visão geral

O backend é uma API REST em camadas (*layered architecture*), o padrão mais
simples e mais fácil de explicar/manter em um projeto acadêmico com uma
equipe pequena. Não há necessidade de algo mais elaborado (hexagonal,
CQRS, microsserviços) para o escopo atual: um único serviço que recebe
mensagens, aplica regras simples e persiste em um banco relacional.

```
Cliente (WhatsApp)
      │
      ▼
Webhook / Controller  (camada web: valida entrada, define rotas e códigos HTTP)
      │
      ▼
Service                (regras de negócio e orquestração)
      │
      ├──▶ Repository  (acesso a dados via Spring Data JPA)
      │        │
      │        ▼
      │     PostgreSQL
      │
      └──▶ Integration (WhatsAppClient - envio da resposta ao cliente)
```

## Pacotes

```
com.chatbot.whatsapp
├── ChatbotApplication.java     # ponto de entrada (main)
├── controller/                 # camada web: recebe HTTP, valida, delega ao service, define status code
├── service/                    # regras de negócio e orquestração
│   ├── chatbot/                # classificação de intenção e geração de resposta do bot
│   └── triage/                 # coleta guiada e estruturação da triagem
├── repository/                 # interfaces Spring Data JPA (acesso a dados)
├── entity/                     # entidades JPA (mapeiam as tabelas do banco)
│   └── enums/                  # enums de domínio (status, direção, canal)
├── dto/
│   ├── request/                # payloads recebidos pela API
│   └── response/                # payloads devolvidos pela API
├── integration/
│   └── whatsapp/                # abstração de envio de mensagens (hoje: mock; futuro: provedor real)
├── config/                      # beans de configuração (ex.: OpenAPI/Swagger)
└── exception/                   # exceções de negócio + tratamento centralizado (RestControllerAdvice)
```

Cada camada só conhece a camada imediatamente abaixo dela:

* `controller` conhece `service` e `dto`, nunca `repository` ou `entity` diretamente.
* `service` conhece `repository`, `entity` e `integration`.
* `repository` conhece apenas `entity`.

Essa separação existe para que, por exemplo, trocar o banco de dados ou o
provedor de WhatsApp não exija alterar os controllers, e para que regras de
negócio não fiquem espalhadas (o pedido do projeto de "não colocar toda a
regra dentro do Controller" é resolvido colocando a orquestração em
`MessageProcessingService`, e a lógica de "o que o bot entende/responde" em
`service.chatbot`).

## Fluxo de uma mensagem (`POST /api/v1/webhook/whatsapp`)

1. `WebhookController` recebe o payload, valida com Bean Validation
   (`@Valid`) e delega para `MessageProcessingService`.
2. `MessageProcessingService` (a orquestração):
   1. busca ou cria o `Customer` pelo telefone (`CustomerService`);
   2. busca a conversa ativa do cliente ou cria uma nova (`ConversationService`);
   3. grava a mensagem recebida (`MessageService.recordInbound`);
   4. inicia ou continua a coleta de nome, empresa e assunto
      (`InformationCollectionService`);
   5. quando a coleta já terminou, classifica a intenção do texto e usa a
      resposta do chatbot atual;
   6. ao completar os campos, gera um resumo e marca a conversa como `QUALIFIED`;
   7. marca a mensagem recebida como processada e grava a mensagem de resposta;
   8. atualiza o "contexto" e o horário da última interação da conversa;
   9. envia a resposta ao cliente via `WhatsAppClient` (hoje, um mock que
      apenas loga a mensagem - ver `docs/DEVELOPMENT.md` e o README).
3. O controller devolve `201 Created` com um resumo do que foi processado.

## Por que não uma "IA" ou motor de regras complexo agora?

O escopo pedido é uma base **funcional e explicável**. `IntentClassifier` é
uma interface; a implementação atual (`KeywordIntentClassifier`) é
propositalmente simples (casamento de palavras-chave). Trocar essa
implementação por algo mais sofisticado (regras mais ricas, ou integração
com um modelo de IA) no futuro não exige mudar nenhum outro ponto do
sistema - é só implementar `IntentClassifier` de novo e trocar o bean.

## Tratamento de erros

Todas as exceções de negócio (`ResourceNotFoundException`,
`InvalidWebhookPayloadException`) e de validação (`MethodArgumentNotValidException`)
são tratadas centralizadamente em `GlobalExceptionHandler`
(`@RestControllerAdvice`), garantindo que toda resposta de erro da API
siga o mesmo formato (`ErrorResponse`): `timestamp`, `status`, `error`,
`message`, `path` e, quando aplicável, `details` com os campos inválidos.
