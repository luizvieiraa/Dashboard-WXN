# Dashboard-WXN

Backend Java/Spring Boot de chatbot para WhatsApp, com dashboard operacional, simulador web e persistência em PostgreSQL.

## Início rápido (recomendado: Docker)

**Pré-requisitos:** Git e Docker com Docker Compose instalados e em execução. As portas **8080** (aplicação) e **5432** (PostgreSQL) precisam estar livres.

```bash
git clone https://github.com/luizvieiraa/Dashboard-WXN.git
cd Dashboard-WXN/Dashboard-WXN
cp .env.example .env
docker compose up -d --build
```

> **Atenção à pasta:** o repositório possui uma subpasta `Dashboard-WXN/` que contém o `docker-compose.yml`, o `pom.xml` e o código-fonte. Execute os comandos dentro dela.

Aguarde a inicialização do banco e da aplicação. O Flyway aplica as migrations automaticamente. Com as opções padrão do `.env.example`, o simulador funciona **sem credenciais reais de WhatsApp ou IA**.

| Interface | Endereço |
| --- | --- |
| Dashboard | http://localhost:8080/dashboard |
| Simulador de chatbot | http://localhost:8080/simulator |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| Health check | http://localhost:8080/api/v1/health |

Para conferir o estado e acompanhar os logs:

```bash
docker compose ps
docker compose logs -f app
```

Para encerrar: `docker compose down`. **Não use `docker compose down -v` a menos que queira apagar os dados do PostgreSQL armazenados no volume.**

## Desenvolvimento local (Java + Maven)

**Pré-requisitos:** JDK 21, Maven, Git e Docker (para o banco) ou uma instância PostgreSQL 16 compatível.

```bash
git clone https://github.com/luizvieiraa/Dashboard-WXN.git
cd Dashboard-WXN/Dashboard-WXN
cp .env.example .env
docker compose up -d db
set -a
source .env
set +a
mvn spring-boot:run
```

Os comandos `set -a` e `source` são para shells Bash (Linux/macOS). No Windows, use WSL/Git Bash ou configure as variáveis do `.env` na execução da IDE. **Não execute `docker compose up` para a aplicação ao mesmo tempo que `mvn spring-boot:run`**, pois ambos tentam usar a porta 8080.

A aplicação utiliza, por padrão, o banco `chatbot_db` em `localhost:5432`, usuário `chatbot` e senha `chatbot`. Se você já possui PostgreSQL na porta 5432, ajuste a configuração antes de subir os serviços.

### Testes

```bash
mvn test
```

Os testes usam H2 em memória e não exigem PostgreSQL em execução.

## Solução de problemas

- **Porta 5432 ocupada:** já existe um PostgreSQL local ou outro container utilizando a porta. Pare o serviço conflitante ou altere o mapeamento de porta e as configurações de conexão correspondentes.
- **Porta 8080 ocupada:** pare a aplicação anterior ou ajuste a porta exposta/configurada.
- **Banco ainda não disponível:** verifique `docker compose ps` e `docker compose logs -f db`.
- **Falha no build:** confirme Java 21 (`java -version`) e Maven (`mvn -version`) no modo local; no modo Docker, confira `docker compose logs -f app`.
- **Variáveis alteradas sem efeito:** confira se está usando o `.env` da subpasta do projeto e recrie os containers com `docker compose up -d --build`.

Nunca envie o arquivo `.env` com senhas ou tokens para o GitHub. Mantenha `WHATSAPP_ENABLED=false` para simulações locais sem envio real de mensagens.

## Colaboração e documentação

O guia completo de arquitetura, integração WhatsApp, API e desenvolvimento está em [Dashboard-WXN/README.md](Dashboard-WXN/README.md). Consulte também [docs/DEVELOPMENT.md](Dashboard-WXN/docs/DEVELOPMENT.md) para convenções, testes e fluxo sugerido de branches (`feature/*` → `develop` → `main`).

Para contribuir, crie uma branch para a sua alteração, execute os testes e abra um Pull Request descrevendo o que mudou.
