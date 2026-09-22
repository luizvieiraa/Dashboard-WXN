# Banco de Dados

## Escolha: PostgreSQL

PostgreSQL foi escolhido (confirmando a preferência inicial do time) pelos
seguintes motivos, avaliados especificamente para este projeto:

* **Facilidade de desenvolvimento**: integração de primeira classe com
  Spring Data JPA/Hibernate, sem surpresas de compatibilidade.
* **Compatibilidade com Spring Boot**: é o banco relacional mais usado com
  Spring Boot, com farta documentação e exemplos, o que ajuda uma equipe
  que está aprendendo.
* **Deploy**: praticamente todas as plataformas de hospedagem simples
  (Railway, Render, Fly.io, etc.) oferecem PostgreSQL gerenciado com
  poucos cliques - ver `docs/DEPLOY.md`.
* **Escalabilidade**: suporta o crescimento do projeto (índices,
  particionamento, replicação) caso ele evolua além do escopo acadêmico.
* **Manutenção**: open-source, maduro, grande comunidade, sem custo de
  licença.
* **Custo**: gratuito; planos gerenciados de baixo custo estão disponíveis
  para o período de demonstração acadêmica.

A alternativa mais próxima seria MySQL/MariaDB; a escolha por PostgreSQL se
deu por ter suporte ainda mais direto no ecossistema Spring (e por ser a
preferência inicial do time).

## Entidades

Foram modeladas quatro entidades para representar o contato, a conversa,
seu histórico e o resultado estruturado da triagem: `Customer`,
`Conversation`, `Message` e `Triage`.

### `customers`

Representa quem está conversando com o chatbot. O telefone é o dado
confiável vindo do WhatsApp, por isso é único e obrigatório. Os demais
campos são preenchidos gradualmente durante a conversa.

| Coluna | Tipo | Notas |
|---|---|---|
| id | BIGINT (identity) | PK |
| phone_number | VARCHAR(20) | único, obrigatório |
| name | VARCHAR(255) | opcional |
| email | VARCHAR(320) | opcional |
| company_name | VARCHAR(255) | opcional |
| job_title | VARCHAR(255) | opcional |
| created_at / updated_at | TIMESTAMP | preenchidos automaticamente |

### `conversations`

Representa uma sessão de conversa entre um cliente e o chatbot. Agrupa
mensagens para que o histórico de um atendimento fique junto. Uma nova
mensagem do mesmo cliente reaproveita a conversa ativa mais recente; se
todas estiverem `CLOSED`, uma nova é criada com status `BOT_ACTIVE`.

| Coluna | Tipo | Notas |
|---|---|---|
| id | BIGINT (identity) | PK |
| customer_id | BIGINT | FK -> customers.id |
| status | VARCHAR(30) | estado atual do atendimento |
| channel | VARCHAR(20) | hoje sempre `WHATSAPP` |
| context | VARCHAR(1000) | texto livre e opcional com o último assunto identificado (ex.: nome da última intenção reconhecida) |
| started_at | TIMESTAMP | criação da conversa |
| last_interaction_at | TIMESTAMP | atualizado a cada mensagem |

Estados disponíveis: `BOT_ACTIVE`, `COLLECTING_INFORMATION`, `QUALIFIED`,
`WAITING_HUMAN`, `HUMAN_ACTIVE` e `CLOSED`. A regra de encerramento
automático por inatividade ainda será definida.

### `messages`

Cada mensagem trocada (do cliente para o bot, ou do bot para o cliente)
dentro de uma conversa.

| Coluna | Tipo | Notas |
|---|---|---|
| id | BIGINT (identity) | PK |
| conversation_id | BIGINT | FK -> conversations.id |
| direction | VARCHAR(10) | `INBOUND` (cliente -> bot) ou `OUTBOUND` (bot -> cliente) |
| content | TEXT | conteúdo da mensagem |
| status | VARCHAR(20) | `RECEIVED`, `PROCESSED`, `SENT` ou `FAILED` |
| external_id | VARCHAR(255) | id da mensagem no provedor externo (WhatsApp), preenchido quando a integração real existir |
| created_at | TIMESTAMP | data/hora da mensagem |

### `triages`

Representa o resultado estruturado da análise de uma conversa. Cada
conversa pode possuir no máximo uma triagem.

| Coluna | Tipo | Notas |
|---|---|---|
| id | BIGINT (identity) | PK |
| conversation_id | BIGINT | FK única -> conversations.id |
| category | VARCHAR(20) | `INFORMATION`, `COMPLAINT` ou `OTHER`; opcional enquanto não classificada |
| priority | VARCHAR(20) | `LOW`, `MEDIUM`, `HIGH` ou `URGENT` |
| subject | VARCHAR(255) | assunto principal |
| customer_need | TEXT | necessidade descrita pelo cliente |
| summary | TEXT | resumo estruturado da conversa |
| requires_human | BOOLEAN | indica encaminhamento para atendimento humano |
| created_at / updated_at | TIMESTAMP | preenchidos automaticamente |

Os campos que ainda precisam ser coletados ficam em
`triage_missing_information`, relacionados à triagem por `triage_id`.

## Relacionamentos

```
customers (1) ──── (N) conversations (1) ──── (N) messages
                              └──── (0..1) triages
```

Um cliente pode ter várias conversas ao longo do tempo; cada conversa
pertence a exatamente um cliente. Uma conversa tem várias mensagens; cada
mensagem pertence a exatamente uma conversa. Uma conversa pode ter uma
única triagem estruturada.

## Migrations (Flyway)

O schema é versionado com Flyway (`src/main/resources/db/migration`).
`V1__init.sql` cria o schema inicial. `V2__add_triage_domain.sql` adiciona
os campos de perfil do cliente, os novos estados de conversa e as tabelas
de triagem. O Hibernate roda em modo `validate`: ele **não** gera
nem altera o schema, apenas confere se as entidades batem com as tabelas
já criadas pelo Flyway. Isso evita divergência entre o que o código espera
e o que realmente existe no banco, e mantém um histórico auditável de
mudanças de schema.

Novas mudanças de schema devem ser feitas criando a próxima migration
versionada (nunca editando uma migration já aplicada).

## Configuração local

Ver README, seção "Como executar localmente" e "Como executar com Docker".
Em resumo, a conexão é 100% configurada por variáveis de ambiente
(`DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`), sem nenhuma
credencial no código-fonte.
