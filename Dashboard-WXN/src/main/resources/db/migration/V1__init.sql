-- V1: schema inicial do chatbot.
-- Tres tabelas cobrem o dominio necessario nesta primeira versao:
--   customers     -> quem esta conversando com o chatbot
--   conversations -> uma sessao de conversa entre um customer e o chatbot
--   messages      -> cada mensagem trocada dentro de uma conversation
--
-- Ver docs/DATABASE.md para a justificativa completa do modelo.

CREATE TABLE customers (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    phone_number VARCHAR(20) NOT NULL,
    name        VARCHAR(255),
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_customers_phone_number UNIQUE (phone_number)
);

CREATE TABLE conversations (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    customer_id         BIGINT NOT NULL,
    status              VARCHAR(20) NOT NULL,
    channel             VARCHAR(20) NOT NULL,
    context             VARCHAR(1000),
    started_at          TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_interaction_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_conversations_customer FOREIGN KEY (customer_id) REFERENCES customers (id)
);

CREATE INDEX ix_conversations_customer_id ON conversations (customer_id);
CREATE INDEX ix_conversations_customer_status ON conversations (customer_id, status);

CREATE TABLE messages (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    conversation_id BIGINT NOT NULL,
    direction       VARCHAR(10) NOT NULL,
    content         TEXT NOT NULL,
    status          VARCHAR(20) NOT NULL,
    external_id     VARCHAR(255),
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_messages_conversation FOREIGN KEY (conversation_id) REFERENCES conversations (id)
);

CREATE INDEX ix_messages_conversation_id ON messages (conversation_id);
