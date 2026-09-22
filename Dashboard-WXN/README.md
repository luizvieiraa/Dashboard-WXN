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

```
Cliente
  │
  ▼
WhatsApp
  │
  ▼
Webhook  (POST /api/v1/webhook/whatsapp - hoje simulado, ver seção 13)
  │
  ▼
API Spring Boot (Controller)
  │
  ▼
Processamento (Service: identifica o cliente/conversa e conduz a coleta da triagem)
  │
  ▼
Banco de Dados (PostgreSQL: histórico de clientes, conversas e mensagens)
  │
  ▼
Resposta (gerada pelo chatbot)
  │
  ▼
WhatsApp
  │
  ▼
Cliente
```

Detalhamento completo do fluxo em [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).

## 4. Tecnologias

* **Java 21** (LTS)
* **Spring Boot 3.5** (Web, Validation, Data JPA, Actuator)
* **PostgreSQL** (banco relacional) + **Flyway** (migrations)
* **Maven** (ver justificativa na seção 4.1)
* **springdoc-openapi** (Swagger UI / OpenAPI 3)
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
organiza os dados que serão consumidos pelo futuro dashboard. Justificativa
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
| POST | `/api/v1/webhook/whatsapp` | Recebe uma mensagem do cliente (simulação do webhook do WhatsApp) e devolve a resposta do chatbot |
| GET | `/api/v1/conversations/{id}` | Consulta uma conversa e suas mensagens |
| GET | `/api/v1/conversations/{id}/messages` | Lista as mensagens de uma conversa |

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

## 10. Como executar os testes

```bash
mvn test
```

Os testes usam H2 em memória (não precisam de PostgreSQL rodando) e
cobrem: health check, o fluxo completo do webhook (recebimento →
processamento → persistência → resposta), classificação de intenção,
geração de resposta do bot e o repository de mensagens. Detalhes em
[`docs/DEVELOPMENT.md`](docs/DEVELOPMENT.md).

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

Ou importe a URL base `http://localhost:8080` no Postman/Insomnia e use os
mesmos métodos/rotas/bodies descritos em [`docs/API.md`](docs/API.md).
Também é possível testar diretamente pelo Swagger UI em
`http://localhost:8080/swagger-ui.html`.

## 12. Como simular uma mensagem do WhatsApp

Enquanto a integração real não está configurada (seção 13), o endpoint
`POST /api/v1/webhook/whatsapp` simula exatamente esse recebimento:

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

## 13. Integração real com WhatsApp

**PENDENTE DE DEFINIÇÃO COM O CLIENTE**: qual provedor será usado (Meta
WhatsApp Cloud API, Twilio, 360dialog, etc.) - isso muda credenciais e
alguns detalhes do payload do webhook.

O que já está preparado:

* Uma camada de integração isolada (`integration/whatsapp`), com uma
  interface (`WhatsAppClient`) que a regra de negócio usa para *enviar*
  respostas, e uma implementação mock (`MockWhatsAppSender`) que apenas
  loga a mensagem que seria enviada - assim o sistema inteiro já funciona
  de ponta a ponta sem depender de credenciais externas.
* Um endpoint de webhook (`POST /api/v1/webhook/whatsapp`) já no formato
  que uma integração real usaria para *receber* mensagens.
* Configuração via variáveis de ambiente (`WhatsAppProperties`), nunca
  hardcoded.

O que falta configurar quando o provedor for escolhido:

1. Criar uma nova implementação de `WhatsAppClient` (ex.: `MetaWhatsAppClient`)
   que efetivamente chame a API do provedor.
2. Validar/adaptar o formato do payload que o provedor realmente envia no
   webhook (pode não ser idêntico ao `WhatsAppWebhookRequest` atual - se
   necessário, um DTO/conversor específico do provedor é adicionado nessa
   mesma camada, sem alterar o resto do sistema).
3. Implementar o handshake de verificação do webhook, se o provedor exigir
   (ex.: Meta exige responder a um `GET` de verificação usando um
   `verify_token`) - o campo `webhook-verify-token` já existe em
   `WhatsAppProperties` para isso.
4. Definir e preencher as variáveis de ambiente reais (nomes já reservados
   em `.env.example`):

   ```
   WHATSAPP_ENABLED=true
   WHATSAPP_API_URL=<url da API do provedor>
   WHATSAPP_ACCESS_TOKEN=<token de acesso>
   WHATSAPP_PHONE_NUMBER_ID=<id do número remetente>
   WHATSAPP_WEBHOOK_VERIFY_TOKEN=<token de verificação do webhook>
   ```

   Essas credenciais devem ser guardadas como *secrets* da plataforma de
   deploy (ver seção 15) - nunca commitadas no repositório.
5. Testar localmente com uma ferramenta de túnel (ex.: `ngrok`) apontando
   para `localhost:8080/api/v1/webhook/whatsapp`, já que o provedor real
   precisa de uma URL pública para enviar o webhook.

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
| `WHATSAPP_ENABLED` | não (padrão false) | Liga a integração real com WhatsApp (quando implementada) |
| `WHATSAPP_API_URL` | não | URL da API do provedor de WhatsApp - PENDENTE DE DEFINIÇÃO |
| `WHATSAPP_ACCESS_TOKEN` | não | Token de acesso do provedor - PENDENTE DE DEFINIÇÃO |
| `WHATSAPP_PHONE_NUMBER_ID` | não | Id do número remetente - PENDENTE DE DEFINIÇÃO |
| `WHATSAPP_WEBHOOK_VERIFY_TOKEN` | não | Token de verificação do webhook - PENDENTE DE DEFINIÇÃO |

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

* API REST com endpoints de health check, webhook (simulado) e consulta
  de conversas/mensagens.
* Modelo de dados (`Customer`, `Conversation`, `Message`, `Triage`) com migrations
  Flyway.
* Coleta guiada de nome, empresa e assunto, com resumo persistido ao final
  da triagem.
* Detecção de reclamações com interrupção da automação e encaminhamento
  prioritário para atendimento humano.
* API de atendimento humano para assumir a fila, responder ao cliente e
  encerrar a conversa sem concorrência com respostas automáticas.
* Processamento de mensagens com classificação de intenção simples e
  geração de resposta, isolados em camadas próprias e extensíveis.
* Tratamento de erros centralizado e documentação da API via Swagger.
* Testes automatizados (unitários, repository e integração de ponta a
  ponta) - ver seção 10.
* Docker + Docker Compose para desenvolvimento local.
* CI (GitHub Actions) rodando build + testes a cada push/PR.
* Documentação completa (este README + `docs/`).

**Em desenvolvimento / próximos passos imediatos:**

* Definir com o cliente o provedor real de WhatsApp e implementar o
  `WhatsAppClient` correspondente (seção 13).
* Regras de encerramento automático de conversas inativas.
* Ampliar o `IntentClassifier` conforme os fluxos de negócio reais forem
  definidos (ex.: consulta a um catálogo de produtos).

**Possíveis melhorias futuras:**

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
