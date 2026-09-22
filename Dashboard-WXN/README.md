# WhatsApp Chatbot API

## 1. Sobre o projeto

Backend de um **chatbot integrado ao WhatsApp**: recebe mensagens de um
cliente, interpreta e processa a solicitação, aplica regras de negócio,
consulta/persiste dados em um banco relacional e devolve uma resposta ao
cliente. O projeto está estruturado para permitir a integração real com o
WhatsApp mais adiante, sem precisar refazer a arquitetura.

## 2. Objetivo

Resolver a necessidade de atender clientes automaticamente por mensagem,
sem depender de um atendente humano disponível o tempo todo, mantendo um
histórico completo e consultável de cada conversa.

## 3. Funcionamento

O backend é o núcleo da aplicação e recebe mensagens por **dois canais**,
que compartilham exatamente a mesma lógica de chatbot:

```
                  ┌──▶ Simulador web (/simulator)
CHATBOT ◀── BACKEND
                  └──▶ WhatsApp (Meta Cloud API)
```

Fluxo do canal WhatsApp, de ponta a ponta:

```
Cliente
  │
  ▼
WhatsApp
  │
  ▼
Meta Cloud API
  │
  ▼
Webhook  (POST /api/v1/webhook/whatsapp/meta - valida a assinatura da Meta)
  │
  ▼
API Spring Boot (Controller -> Parser -> WhatsAppInboundService)
  │
  ▼
Processamento (MessageProcessingService: identifica o cliente/conversa e conduz a coleta da triagem)
  │
  ▼
Banco de Dados (PostgreSQL: histórico de clientes, conversas e mensagens)
  │
  ▼
Resposta (gerada pelo chatbot)
  │
  ▼
Meta Cloud API  (POST /{versão}/{phone-number-id}/messages)
  │
  ▼
WhatsApp
  │
  ▼
Cliente
```

O canal do simulador entra no mesmo `MessageProcessingService` a partir do
`POST /api/v1/webhook/whatsapp`, sem passar pela Meta. Para colocar o
WhatsApp real no ar, ver a **seção 13**.

Detalhamento completo do fluxo em [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).

## 4. Tecnologias

* **Java 21** (LTS)
* **Spring Boot 3.5** (Web, Validation, Data JPA, Actuator)
* **PostgreSQL** (banco relacional) + **Flyway** (migrations)
* **Maven** (ver justificativa na seção 4.1)
* **springdoc-openapi** (Swagger UI / OpenAPI 3)
* **API de IA configurável** (formato OpenAI Responses, com fallback local)
* **Lombok**
* **JUnit 5, MockMvc, AssertJ, H2** (testes)
* **Docker / Docker Compose**
* **GitHub Actions** (CI)
* **Git / GitHub**

### 4.1. Maven vs. Gradle

Optamos por **Maven**: sintaxe declarativa em XML mais previsível para uma
equipe iniciante (menos "mágica" que o DSL Groovy/Kotlin do Gradle),
integração de primeira classe com o Spring Initializr/ecossistema Spring,
e é o padrão mais comumente ensinado em cursos e usado em projetos
acadêmicos Java, reduzindo a curva de aprendizado para todos os
integrantes da equipe. Gradle traria builds incrementalmente mais rápidos,
mas esse ganho não compensa a complexidade extra para o escopo atual.

### 4.2. Banco de dados: por que PostgreSQL

Ver [`docs/DATABASE.md`](docs/DATABASE.md) para a justificativa completa.
Resumo: melhor integração com Spring Boot, deploy simples em qualquer
plataforma cogitada, boa escalabilidade futura, manutenção e custo baixos.

### 4.3. Spring Boot 3.5 (e não a versão 4.x mais recente)

No momento em que este projeto foi criado, a série 4.x do Spring Boot já
era a mais recente, mas optamos pela série **3.5** (ainda amplamente usada
e documentada) por ser a mais estável, testada e conhecida por toda a
comunidade e por ferramentas/bibliotecas de terceiros no momento da
criação deste projeto - reduzindo o risco de incompatibilidades logo na
fundação do sistema. Migrar para 4.x mais adiante é um passo relativamente
mecânico e fica registrado no roadmap (seção 18).

## 5. Arquitetura

Arquitetura em camadas (`controller -> service -> repository`), com uma
camada extra de integração (`integration/whatsapp`) isolando o envio de
mensagens ao cliente. Estrutura completa de diretórios, diagrama de
pacotes e o porquê de cada decisão em [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).

```
src/main/java/com/chatbot/whatsapp/
├── controller/      # endpoints REST
├── service/         # regras de negócio (+ subpacotes chatbot e triage)
├── repository/      # Spring Data JPA
├── entity/          # entidades JPA (+ subpacote enums)
├── dto/             # request/ e response/
├── integration/     # integração externa (whatsapp/)
├── config/          # beans de configuração (OpenAPI)
└── exception/       # exceções + tratamento centralizado
```

## 6. Banco de dados

Quatro entidades: `Customer`, `Conversation`, `Message` e `Triage`. A triagem
organiza os dados consumidos pelo dashboard operacional. Justificativa
de cada entidade, atributos, relacionamentos e como as
migrations Flyway funcionam: [`docs/DATABASE.md`](docs/DATABASE.md).

## 7. API

Referência completa (método, rota, parâmetros, body, resposta, códigos
HTTP e erros) em [`docs/API.md`](docs/API.md). Com a aplicação no ar, a
documentação interativa fica em `/swagger-ui.html`.

Resumo dos endpoints:

| Método | Rota | Finalidade |
|---|---|---|
| GET | `/api/v1/health` | Health check da aplicação |
| POST | `/api/v1/webhook/whatsapp` | Recebe uma mensagem em formato simplificado (usado pelo simulador) e devolve a resposta do chatbot |
| GET | `/api/v1/webhook/whatsapp/meta` | Handshake de verificação do webhook da Meta |
| POST | `/api/v1/webhook/whatsapp/meta` | **Webhook real da WhatsApp Cloud API**: recebe as mensagens enviadas pelos usuários no WhatsApp |
| GET | `/api/v1/conversations/{id}` | Consulta uma conversa e suas mensagens |
| GET | `/api/v1/conversations/{id}/messages` | Lista as mensagens de uma conversa |
| POST | `/api/v1/conversations/{id}/human/claim` | Atendente assume uma conversa encaminhada |
| POST | `/api/v1/conversations/{id}/human/reply` | Atendente responde ao cliente |
| POST | `/api/v1/conversations/{id}/close` | Encerra uma conversa |
| GET | `/api/v1/dashboard/summary` | Indicadores consolidados da operação |
| GET | `/api/v1/dashboard/triages` | Triagens filtráveis para o dashboard |

## 8. Como executar localmente

Pré-requisitos: **Java 21**, **Maven** (ou use o wrapper, se adicionado
pela sua IDE), e **PostgreSQL** rodando localmente (ou use o Docker
Compose - seção 9 - só para o banco).

```bash
git clone https://github.com/luizvieiraa/Dashboard-WXN.git
cd Dashboard-WXN

# 1. Copie o arquivo de variáveis de ambiente de exemplo
cp .env.example .env
# Edite o .env se necessário (usuário/senha do seu Postgres local, etc.)

# 2. Suba um PostgreSQL local (se ainda não tiver um), por exemplo via Docker:
docker run --name chatbot-db -e POSTGRES_DB=chatbot_db \
  -e POSTGRES_USER=chatbot -e POSTGRES_PASSWORD=chatbot \
  -p 5432:5432 -d postgres:16-alpine

# 3. Exporte as variáveis de ambiente do .env na sua sessão do terminal
#    (ou configure-as na sua IDE ao rodar ChatbotApplication)
export $(grep -v '^#' .env | xargs)

# 4. Rode a aplicação
mvn spring-boot:run
```

A API sobe em `http://localhost:8080`. O Flyway cria o schema
automaticamente na primeira execução.

Teste rápido:

```bash
curl http://localhost:8080/api/v1/health
```

## 9. Como executar com Docker

Sobe a aplicação **e** o PostgreSQL juntos:

```bash
cp .env.example .env   # ajuste se quiser

docker compose up --build      # inicia (e reconstrói a imagem da app)
docker compose up -d --build   # o mesmo, em background

docker compose down            # para os containers
docker compose down -v         # para e remove também o volume do banco (apaga os dados!)

docker compose logs -f app     # acompanha os logs da aplicação
docker compose logs -f db      # acompanha os logs do banco
```

A API fica disponível em `http://localhost:8080`.

O dashboard operacional fica em `http://localhost:8080/dashboard`.

## 10. Como executar os testes

```bash
mvn test
```

Os testes usam H2 em memória (não precisam de PostgreSQL rodando) e
cobrem: health check, o fluxo completo do webhook (recebimento →
processamento → persistência → resposta), classificação de intenção,
geração de resposta do bot e o repository de mensagens. Detalhes em
[`docs/DEVELOPMENT.md`](docs/DEVELOPMENT.md).

Para o canal WhatsApp, cobrem também: o handshake de verificação, a
validação da assinatura `X-Hub-Signature-256`, o parsing do envelope da
Meta, a deduplicação de reentregas, o formato da chamada de envio e a
seleção do `WhatsAppClient` por configuração. A comunicação com os
servidores da Meta é simulada (`MockRestServiceServer`) - o que depende de
configuração externa está na seção 13.7.

## 11. Como testar a API

Com a aplicação rodando (local ou via Docker), usando `curl`:

```bash
# Health check
curl http://localhost:8080/api/v1/health

# Simular uma mensagem do WhatsApp
curl -X POST http://localhost:8080/api/v1/webhook/whatsapp \
  -H "Content-Type: application/json" \
  -d '{"phone":"5511999999999","message":"Olá, gostaria de saber o preço de um produto"}'

# Consultar a conversa criada (troque 1 pelo conversationId retornado acima)
curl http://localhost:8080/api/v1/conversations/1

# Listar apenas as mensagens dessa conversa
curl http://localhost:8080/api/v1/conversations/1/messages
```

Para testar o **webhook real da Meta** sem a Meta, envie um envelope no
formato dela (útil para validar a integração antes de expor a URL; funciona
enquanto `WHATSAPP_APP_SECRET` estiver vazio, pois aí a assinatura não é
exigida):

```bash
curl -X POST http://localhost:8080/api/v1/webhook/whatsapp/meta \
  -H "Content-Type: application/json" \
  -d '{"object":"whatsapp_business_account","entry":[{"changes":[{"field":"messages",
       "value":{"messaging_product":"whatsapp",
       "contacts":[{"profile":{"name":"Teste"},"wa_id":"5511999999999"}],
       "messages":[{"from":"5511999999999","id":"wamid.TESTE1","timestamp":"1749416383",
       "type":"text","text":{"body":"Olá"}}]}}]}]}'
```

A resposta HTTP é `200` com corpo vazio - a resposta do chatbot vai para o
log (com `WHATSAPP_ENABLED=false`) ou para o WhatsApp (com `true`). Consulte
a conversa criada com `curl http://localhost:8080/api/v1/conversations/1`.

Ou importe a URL base `http://localhost:8080` no Postman/Insomnia e use os
mesmos métodos/rotas/bodies descritos em [`docs/API.md`](docs/API.md).
Também é possível testar diretamente pelo Swagger UI em
`http://localhost:8080/swagger-ui.html`.

## 12. Como simular uma mensagem do WhatsApp

Este é o canal de testes local, que **continua funcionando** depois da
integração real (seção 13) - os dois convivem. Ele é a forma mais rápida de
exercitar o chatbot sem depender da Meta.

Com a aplicação no ar, abra `http://localhost:8080/simulator` para usar a
interface visual de conversa. Ela permite trocar o telefone simulado, iniciar
novas sessões, acompanhar o estado da triagem e testar cenários de informação,
preço e reclamação. O atalho **Simulador** também está disponível no menu do
dashboard.

Por baixo, o simulador usa o endpoint
`POST /api/v1/webhook/whatsapp`, com um payload simplificado:

```json
{
  "phone": "5511999999999",
  "message": "Olá, gostaria de saber o preço de um produto",
  "timestamp": "2026-09-09T12:00:00Z"
}
```

`timestamp` é opcional. O fluxo completo (API → Controller → Service →
Processamento → Banco → Resposta) roda exatamente como rodaria com uma
mensagem real do WhatsApp - a única diferença é a origem da chamada HTTP.

> Atenção: com `WHATSAPP_ENABLED=true`, a resposta gerada aqui é entregue
> de verdade, pelo WhatsApp, ao número digitado no simulador. Use
> `WHATSAPP_ENABLED=false` para testar sem enviar nada.

## 13. Integração real com WhatsApp

A integração é implementada com a **WhatsApp Business Platform (Cloud API)
da Meta**, a API oficial. Escolhemos ela em vez de intermediários (Twilio,
360dialog) por ser *first-party* - sem custo de revenda e sem depender de um
terceiro - e em vez de bibliotecas como `whatsapp-web.js`/Baileys, que
automatizam o WhatsApp Web, violam os Termos de Uso e quebram a cada
atualização do app.

O código está pronto e testado. **Falta apenas a configuração externa
descrita abaixo** - nada disso pode ser feito por código.

### 13.1. O que já está implementado

| Componente | Responsabilidade |
|---|---|
| `MetaWebhookController` | `GET` de verificação + `POST` de recebimento em `/api/v1/webhook/whatsapp/meta` |
| `WhatsAppSignatureVerifier` | Valida a assinatura `X-Hub-Signature-256` de cada notificação |
| `MetaWebhookPayloadParser` | Traduz o envelope da Meta em mensagens de domínio |
| `WhatsAppInboundService` | Deduplica reentregas e encaminha ao chatbot existente |
| `MetaWhatsAppClient` | Envia a resposta via `POST /{versão}/{phone-number-id}/messages` |

O chatbot **não foi duplicado**: as mensagens do WhatsApp entram no mesmo
`MessageProcessingService` que o simulador usa, então triagem, detecção de
reclamação, respostas por IA, fila de atendimento humano e encerramento por
inatividade funcionam igual nos dois canais. Detalhes em
[`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md), seção "Canal WhatsApp".

### 13.2. Passo a passo na Meta

1. **Crie uma conta de desenvolvedor** em
   [developers.facebook.com](https://developers.facebook.com) e um **app**
   do tipo *Business*.
2. No app, adicione o produto **WhatsApp**. A Meta cria automaticamente uma
   *WhatsApp Business Account* e um **número de teste** - suficiente para
   validar a integração, sem precisar de um número próprio.
3. Em **WhatsApp > API Setup**, anote:
   * **Phone number ID** → `WHATSAPP_PHONE_NUMBER_ID`
     (é um id numérico, *não* o número de telefone)
   * **Temporary access token** → `WHATSAPP_ACCESS_TOKEN`
     (expira em 24h; ver 13.5 para um token permanente)
4. Ainda em **API Setup**, na seção *To*, **cadastre o seu número pessoal
   de WhatsApp** como destinatário de teste e confirme o código recebido.
   O número de teste da Meta só conversa com números nessa lista.
5. Em **App settings > Basic**, copie o **App secret** →
   `WHATSAPP_APP_SECRET`.
6. Escolha um **verify token**: qualquer string secreta inventada por você
   (ex.: gere com `openssl rand -hex 16`) → `WHATSAPP_WEBHOOK_VERIFY_TOKEN`.
   Ela só serve para o handshake do passo 8.

### 13.3. Expor o backend em uma URL pública

A Meta precisa alcançar seu servidor, então `localhost` não funciona. Para
desenvolvimento, use um túnel:

```bash
# Em um terminal, com a aplicação rodando na porta 8080:
ngrok http 8080
# Anote a URL https gerada, ex.: https://abc123.ngrok-free.app
```

Em produção, use a URL pública do deploy (ver seção 15).

### 13.4. Cadastrar o webhook

1. Suba a aplicação já com as variáveis preenchidas (seção 13.6) -
   inclusive `WHATSAPP_WEBHOOK_VERIFY_TOKEN`, senão a verificação falha
   com `403`.
2. No painel do app, vá em **WhatsApp > Configuration > Webhook** e clique
   em **Edit**:
   * **Callback URL**:
     `https://SUA-URL-PUBLICA/api/v1/webhook/whatsapp/meta`
   * **Verify token**: exatamente o valor de
     `WHATSAPP_WEBHOOK_VERIFY_TOKEN`
3. Clique em **Verify and save**. A Meta faz um `GET` no endpoint; se o
   token conferir, o backend devolve o `hub.challenge` e o cadastro é
   aceito. Nos logs aparece:
   `Webhook do WhatsApp verificado com sucesso pela Meta`.
4. Em **Webhook fields**, clique em **Manage** e **inscreva-se no campo
   `messages`**. Sem essa inscrição o webhook é aceito, mas nenhuma
   mensagem é entregue - é o esquecimento mais comum nessa configuração.

### 13.5. Token permanente (para uso contínuo)

O token temporário expira em 24 horas. Para um ambiente que fica no ar:

1. Em **business.facebook.com > Configurações do negócio > Usuários >
   Usuários do sistema**, crie um *System User* com papel de **Admin**.
2. Clique em **Adicionar ativos** e dê a ele acesso ao app e à WhatsApp
   Business Account.
3. Clique em **Gerar novo token**, selecione o app e as permissões
   `whatsapp_business_messaging` e `whatsapp_business_management`.
4. Use esse token em `WHATSAPP_ACCESS_TOKEN`.

### 13.6. Variáveis de ambiente

```
WHATSAPP_ENABLED=true
WHATSAPP_API_URL=https://graph.facebook.com
WHATSAPP_API_VERSION=v21.0
WHATSAPP_ACCESS_TOKEN=<token de acesso da Cloud API>
WHATSAPP_PHONE_NUMBER_ID=<Phone number ID>
WHATSAPP_WEBHOOK_VERIFY_TOKEN=<string secreta inventada por você>
WHATSAPP_APP_SECRET=<App secret do app da Meta>
```

Essas credenciais devem ser guardadas como *secrets* da plataforma de
deploy (ver seção 15) - **nunca** commitadas no repositório. Com
`WHATSAPP_ENABLED=false` (padrão) o sistema roda inteiro sem nenhuma delas.

### 13.7. Como testar de verdade

1. Envie uma mensagem do seu WhatsApp pessoal para o número de teste da
   Meta (o número aparece em **API Setup**).
2. Acompanhe os logs da aplicação. O caminho esperado é:

   ```
   Notificacao do webhook: 1 mensagem(ns) recebida(s), 1 processada(s)
   Mensagem wamid.XXX processada na conversa 1
   Mensagem enviada ao WhatsApp de 55... (id do provedor: wamid.YYY)
   ```

3. Você deve receber a resposta do chatbot no WhatsApp - na primeira
   mensagem, o início da triagem ("como posso te chamar?").
4. Confirme a persistência abrindo o dashboard em
   `https://SUA-URL/dashboard`, ou via API:
   `curl https://SUA-URL/api/v1/conversations/1`.
5. Responda no WhatsApp com nome, empresa e assunto para percorrer a
   triagem completa; envie uma reclamação para ver a conversa cair na fila
   de atendimento humano do dashboard.

### 13.8. Limitações conhecidas

* **Janela de 24 horas**: a Cloud API só permite mensagem de texto livre
  dentro de 24h após a última mensagem do usuário. Fora dela, a Meta exige
  um *template* previamente aprovado - **não implementado**. Na prática
  isso não afeta o chatbot, que sempre responde a uma mensagem recebida.
* **Somente texto**: mídias (imagem, áudio, documento, localização) recebem
  um aviso pedindo que o cliente escreva em texto, e não são registradas -
  isso evita contaminar a coleta da triagem com conteúdo não textual.
* **Número de teste**: só conversa com os números cadastrados na lista de
  destinatários. Para atender qualquer pessoa é preciso registrar um número
  próprio e passar pela verificação do negócio na Meta.
* **Processamento sincrônico**: o `200` para a Meta só volta depois de
  processar e enviar a resposta. Adequado ao volume atual; ver
  [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) para a discussão.
* **O simulador compartilha o `WhatsAppClient`**: com
  `WHATSAPP_ENABLED=true`, uma mensagem enviada pelo `/simulator` faz o
  backend tentar entregar a resposta de verdade, pelo WhatsApp, ao número
  digitado no simulador. Para testar sem enviar nada, use
  `WHATSAPP_ENABLED=false`.

## 14. Variáveis de ambiente

Todas documentadas em [`.env.example`](.env.example). Resumo:

| Variável | Obrigatória | Descrição |
|---|---|---|
| `DATABASE_URL` | sim | URL JDBC do PostgreSQL |
| `DATABASE_USERNAME` | sim | Usuário do banco |
| `DATABASE_PASSWORD` | sim | Senha do banco |
| `SERVER_PORT` | não (padrão 8080) | Porta HTTP da aplicação |
| `APP_LOG_LEVEL` | não (padrão INFO) | Nível de log da aplicação |
| `JPA_SHOW_SQL` | não (padrão false) | Loga o SQL gerado pelo Hibernate |
| `WHATSAPP_ENABLED` | não (padrão false) | Liga o envio real de mensagens pela Meta Cloud API |
| `WHATSAPP_API_URL` | não (padrão `https://graph.facebook.com`) | URL base da Graph API |
| `WHATSAPP_API_VERSION` | não (padrão `v21.0`) | Versão da Graph API usada nas chamadas |
| `WHATSAPP_ACCESS_TOKEN` | quando WhatsApp ativo | Token de acesso da Cloud API |
| `WHATSAPP_PHONE_NUMBER_ID` | quando WhatsApp ativo | Id do número remetente (não é o telefone) |
| `WHATSAPP_WEBHOOK_VERIFY_TOKEN` | para cadastrar o webhook | String secreta usada no handshake de verificação |
| `WHATSAPP_APP_SECRET` | sim, em produção | App Secret; valida a assinatura `X-Hub-Signature-256` das notificações |
| `AI_ENABLED` | não (padrão false) | Habilita respostas geradas por IA depois da triagem |
| `AI_API_URL` | quando IA ativa | Endpoint compatível com o formato OpenAI Responses |
| `AI_API_KEY` | quando IA ativa | Chave do provedor, mantida somente no ambiente |
| `AI_MODEL` | quando IA ativa | Modelo configurado no provedor |
| `AI_TIMEOUT_SECONDS` | não (padrão 10) | Limite de espera pela IA antes do fallback |
| `AI_MAX_OUTPUT_TOKENS` | não (padrão 300) | Limite de tokens da resposta gerada |
| `CONVERSATION_AUTO_CLOSE_ENABLED` | não (padrão true) | Liga o encerramento de conversas automatizadas inativas |
| `CONVERSATION_INACTIVITY_HOURS` | não (padrão 24) | Horas sem interação antes do encerramento automático |
| `CONVERSATION_CHECK_INTERVAL_MS` | não (padrão 300000) | Intervalo entre verificações de inatividade |

## 15. Deploy

Plataforma escolhida: **Railway**. Tutorial completo (criação do serviço,
banco, variáveis, build, deploy, URL pública, atualização e logs) em
[`docs/DEPLOY.md`](docs/DEPLOY.md).

## 16. Git/GitHub

Fluxo de branches sugerido para a equipe (`main` / `develop` /
`feature/*`), convenções de commit e comandos Git do dia a dia em
[`docs/DEVELOPMENT.md`](docs/DEVELOPMENT.md).

O `.gitignore` já cobre build (`target/`), variáveis de ambiente (`.env`),
arquivos de IDE e de sistema operacional. Nenhuma credencial deve ser
commitada; use sempre `.env.example` como referência e o `.env` real
(ignorado pelo Git) para valores locais.

## 17. Estrutura da equipe

Sugestão de divisão de responsabilidades (por fluxo, não por camada) em
[`docs/DEVELOPMENT.md`](docs/DEVELOPMENT.md), seção "Sugestão de divisão
de responsabilidades".

## 18. Próximos passos

**Pronto:**

* **Integração real com o WhatsApp via Meta Cloud API**: webhook com
  handshake de verificação e validação de assinatura
  `X-Hub-Signature-256`, deduplicação de reentregas, e envio das respostas
  pela Cloud API - reutilizando o mesmo chatbot do simulador, sem
  duplicação de lógica (ver seção 13). Depende apenas da configuração
  externa na Meta para entrar em operação.
* API REST com endpoints de health check, webhook (simplificado e real) e
  consulta de conversas/mensagens.
* Modelo de dados (`Customer`, `Conversation`, `Message`, `Triage`) com migrations
  Flyway.
* Coleta guiada de nome, empresa e assunto, com resumo persistido ao final
  da triagem.
* Detecção de reclamações com interrupção da automação e encaminhamento
  prioritário para atendimento humano.
* API de atendimento humano para assumir a fila, responder ao cliente e
  encerrar a conversa sem concorrência com respostas automáticas.
* API de dashboard com indicadores consolidados e listagem filtrável de
  triagens, ordenada por prioridade e interação mais recente.
* Interface web responsiva para acompanhar indicadores, filtrar triagens e
  operar a fila de atendimento humano.
* Simulador visual de conversa integrado ao webhook local, com histórico,
  estado da triagem e cenários rápidos de teste.
* Respostas opcionais por IA após a triagem, com prompt restritivo, timeout
  e fallback automático para o comportamento local.
* Encerramento automático e configurável de conversas do bot após
  inatividade, sem retirar conversas da fila ou do atendimento humano.
* Processamento de mensagens com classificação de intenção simples e
  geração de resposta, isolados em camadas próprias e extensíveis.
* Tratamento de erros centralizado e documentação da API via Swagger.
* Testes automatizados (unitários, repository e integração de ponta a
  ponta) - ver seção 10.
* Docker + Docker Compose para desenvolvimento local.
* CI (GitHub Actions) rodando build + testes a cada push/PR.
* Documentação completa (este README + `docs/`).

**Em desenvolvimento / próximos passos imediatos:**

* **Configurar o app na Meta** e cadastrar o webhook para colocar o canal
  WhatsApp em operação (seção 13.2 a 13.4). É a única pendência da
  integração, e não pode ser resolvida por código.
* Registrar um número próprio na WhatsApp Business Account, em vez do
  número de teste, para atender qualquer cliente (seção 13.8).
* Ampliar a base de conhecimento conforme os fluxos de negócio reais forem
  definidos (ex.: consulta a um catálogo de produtos).

**Possíveis melhorias futuras:**

* Processar o webhook do WhatsApp em background, respondendo `200` à Meta
  imediatamente, caso o volume de mensagens cresça (ver seção 13.8).
* Suporte a *template messages* aprovados, para poder iniciar conversa ou
  responder fora da janela de 24h da Cloud API.
* Tratar mídias recebidas (imagem, áudio, documento) em vez de apenas pedir
  que o cliente escreva em texto.
* Autenticação/autorização na API (hoje ela é aberta - ok para o escopo
  acadêmico atual, mas necessário antes de qualquer uso real com dados
  de clientes reais).
* Paginação em `GET /api/v1/conversations/{id}/messages` para conversas
  muito longas.
* Testes de integração com PostgreSQL real via Testcontainers (hoje os
  testes usam H2 em modo de compatibilidade com PostgreSQL, o que é
  suficiente para este escopo, mas Testcontainers daria uma garantia
  ainda maior antes de um ambiente de produção real).
* Migração para Spring Boot 4.x quando o ecossistema de bibliotecas
  utilizadas estiver amplamente validado nessa versão.
* Observabilidade (métricas/tracing) além do health check básico já
  exposto pelo Actuator.

---

### Sobre este repositório

Este README documenta o projeto **whatsapp-chatbot-api**, construído
dentro do repositório `Dashboard-WXN`. O nome do repositório é histórico;
o conteúdo e o propósito atual são os descritos acima.
