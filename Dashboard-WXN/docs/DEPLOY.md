# Deploy

## Plataforma escolhida: Railway

Para um projeto acadêmico que precisa ser demonstrado (e não rodar em
produção real, 24/7, por anos), **Railway** foi escolhido porque:

1. Faz deploy direto de um repositório GitHub (build automático via
   Nixpacks ou pelo `Dockerfile` do projeto - este projeto já tem um).
2. Oferece PostgreSQL gerenciado com um clique, já injetando a
   `DATABASE_URL` como variável de ambiente no serviço da aplicação -
   menos configuração manual do que a maioria das alternativas.
3. Cobrança por uso (não por assinatura fixa), o que costuma ser
   suficiente para o curto período de avaliação de um projeto acadêmico.
4. Interface simples de configurar variáveis de ambiente, ver logs e
   redeployar.

**Alternativas consideradas**: Render (também tem deploy simples e um
tier gratuito de *compute*, mas seu PostgreSQL gratuito expira 30 dias
após a criação, o que é ruim se o projeto for avaliado depois desse
prazo) e Fly.io (mais flexível, porém com uma curva de configuração maior
via CLI). Nenhuma das duas oferece uma vantagem clara sobre a Railway para
este caso de uso; a equipe pode reavaliar se o projeto crescer além do
escopo acadêmico.

> Custos e limites de planos gratuitos mudam com frequência. Antes de
> depender de um tier gratuito para a entrega do projeto, confirme os
> valores atuais no site da plataforma escolhida.

## O que será hospedado

* **Aplicação Spring Boot**: como um serviço web na Railway, construído a
  partir do `Dockerfile` deste repositório (ou via Nixpacks, que detecta
  automaticamente um projeto Maven).
* **Banco de dados**: um serviço PostgreSQL gerenciado pela própria
  Railway, dentro do mesmo projeto.

## Passo a passo

### 1. Criar a conta e o projeto

1. Crie uma conta em [railway.com](https://railway.com) (é possível
   entrar com a conta do GitHub).
2. Clique em **New Project**.

### 2. Adicionar o banco de dados

1. Dentro do projeto, clique em **New** -> **Database** -> **PostgreSQL**.
2. A Railway cria o serviço e expõe automaticamente variáveis como
   `DATABASE_URL`, `PGHOST`, `PGPORT`, `PGUSER`, `PGPASSWORD`, `PGDATABASE`.

### 3. Adicionar o serviço da aplicação

1. Clique em **New** -> **GitHub Repo** e selecione este repositório
   (`Dashboard-WXN`).
2. A Railway detecta o `Dockerfile` na raiz e o utiliza para o build
   (alternativamente, ela também consegue buildar projetos Maven puros via
   Nixpacks, caso o Dockerfile seja removido).

### 4. Configurar variáveis de ambiente

No serviço da aplicação, aba **Variables**, configure:

| Variável | Valor |
|---|---|
| `DATABASE_URL` | `jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}` (referencie o serviço de banco criado no passo 2; a sintaxe exata de referência entre serviços é exibida pela própria Railway ao digitar `${{`) |
| `DATABASE_USERNAME` | `${{Postgres.PGUSER}}` |
| `DATABASE_PASSWORD` | `${{Postgres.PGPASSWORD}}` |
| `SERVER_PORT` | `8080` |
| `WHATSAPP_ENABLED` | `false` (até a integração real ser configurada) |

> A Railway expõe a porta configurada em `SERVER_PORT`/`PORT`
> automaticamente por HTTPS em um domínio `*.up.railway.app`; não é
> necessário configurar TLS manualmente.

### 5. Deploy

O primeiro deploy acontece automaticamente após conectar o repositório. A
partir daí, **todo `git push` para a branch configurada (ex.: `main`)
dispara um novo deploy automaticamente**.

### 6. Obter a URL pública

Na aba **Settings** do serviço da aplicação, em **Networking**, clique em
**Generate Domain**. A Railway gera uma URL pública (`https://<algo>.up.railway.app`).

### 7. Testar a aplicação depois do deploy

```bash
curl https://<sua-url>.up.railway.app/api/v1/health

curl -X POST https://<sua-url>.up.railway.app/api/v1/webhook/whatsapp \
  -H "Content-Type: application/json" \
  -d '{"phone":"5511999999999","message":"oi"}'
```

E acesse `https://<sua-url>.up.railway.app/swagger-ui.html` para testar
pela interface do Swagger.

### 8. Ver logs

Aba **Deployments** -> selecione o deploy ativo -> aba **Logs**, com logs
em tempo real da aplicação (inclui os logs do `MockWhatsAppSender`,
úteis para conferir a resposta que o bot geraria).

### 9. Atualizar o sistema

Basta dar `git push` na branch configurada para deploy. Para reverter,
use a opção **Redeploy** em um deployment anterior na aba **Deployments**.

## PENDENTE DE DEFINIÇÃO COM O CLIENTE

* Domínio próprio (a Railway suporta domínio customizado, caso a equipe
  queira usar um).
* Se/quando a integração real com WhatsApp for definida, as variáveis
  `WHATSAPP_*` (ver README) precisarão ser configuradas aqui também, com
  os valores reais fornecidos pelo provedor escolhido.
