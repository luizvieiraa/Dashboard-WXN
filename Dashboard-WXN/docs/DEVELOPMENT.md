# Guia de desenvolvimento

## Ambiente local

Ver README, seção "Como executar localmente", para o passo a passo
completo (clonar, subir o banco, rodar a aplicação).

## Convenções de código

* Pacotes por camada (`controller`, `service`, `repository`, `entity`,
  `dto`, `config`, `exception`, `integration`) - ver `docs/ARCHITECTURE.md`.
* DTOs são `record`s Java (imutáveis, sem boilerplate).
* Entidades usam Lombok (`@Getter`/`@Setter`/`@Builder`) para reduzir
  código repetitivo; evite adicionar lógica de negócio dentro de uma
  entidade - isso pertence à camada de `service`.
* Toda regra de negócio fica em `service`, nunca em `controller`.
* Nomes em português são aceitáveis em comentários/documentação; nomes de
  classes, métodos e variáveis ficam em inglês, seguindo a convenção do
  ecossistema Java/Spring.

## Rodando os testes

```bash
mvn test
```

Os testes usam H2 em memória (perfil `test`, ver
`src/test/resources/application-test.yml`) rodando as mesmas migrations
Flyway do banco real, então não é necessário ter PostgreSQL instalado
para rodar a suíte de testes localmente.

## Fluxo de branches (Git)

Para uma equipe pequena, um fluxo simplificado de duas camadas é
suficiente - não é necessário Git Flow completo:

```
main        -> sempre estável; é o que está (ou vai) em produção/demo
  ↑ merge (Pull Request)
develop     -> integração do que já foi revisado, antes de ir para main
  ↑ merge (Pull Request)
feature/xxx -> uma branch por funcionalidade/tarefa
```

Regras sugeridas:

1. Nunca commitar diretamente em `main`.
2. Cada tarefa (ex.: "endpoint de conversas", "integração com WhatsApp")
   vira uma branch `feature/nome-da-tarefa` a partir de `develop`.
3. Abrir Pull Request de `feature/*` para `develop`; pelo menos um outro
   integrante revisa antes do merge.
4. Periodicamente (ex.: ao fechar uma sprint), `develop` é mesclada em
   `main` via Pull Request, depois de confirmar que o CI passou.
5. Mensagens de commit curtas e no imperativo (ex.: `Adiciona endpoint de
   consulta de conversas`), preferencialmente referenciando a tarefa.

## Comandos Git úteis para a equipe

```bash
# Criar uma branch de feature a partir de develop atualizada
git checkout develop
git pull
git checkout -b feature/minha-tarefa

# Subir a branch e abrir PR
git push -u origin feature/minha-tarefa

# Atualizar sua branch com o que já foi mesclado em develop
git checkout feature/minha-tarefa
git fetch origin
git rebase origin/develop   # ou: git merge origin/develop

# Depois do PR aprovado e mesclado, limpar a branch local
git checkout develop
git pull
git branch -d feature/minha-tarefa
```

## Sugestão de divisão de responsabilidades

Como o projeto é pequeno, a divisão pode ser por fluxo/funcionalidade em
vez de por camada (evita que uma pessoa fique "só no banco" e outra "só
no controller", o que dificulta revisão de código):

* **Pessoa/dupla A**: fluxo de recebimento de mensagem (webhook,
  processamento, integração com WhatsApp).
* **Pessoa/dupla B**: consulta de conversas/mensagens, documentação da
  API (Swagger) e testes de integração.
* **Pessoa/dupla C**: infraestrutura (Docker, CI/CD, deploy) e
  README/documentação.

Essa divisão é apenas uma sugestão inicial; ajuste conforme o tamanho real
da equipe.

## Adicionando uma nova migration

1. Crie um novo arquivo em `src/main/resources/db/migration`, seguindo o
   padrão `V{numero}__descricao.sql` (nunca edite uma migration já
   aplicada/commitada).
2. Ajuste as entidades JPA correspondentes.
3. Rode `mvn test` para confirmar que o Hibernate (`ddl-auto=validate`)
   não acusa divergência entre entidades e schema.
