CREATE TABLE chatbots (
    id BIGINT PRIMARY KEY,
    name VARCHAR(255) NOT NULL
);
INSERT INTO chatbots (id, name) VALUES (1, 'WXN');

ALTER TABLE conversations ADD COLUMN chatbot_id BIGINT NOT NULL DEFAULT 1;
ALTER TABLE conversations ADD CONSTRAINT fk_conversations_chatbot
    FOREIGN KEY (chatbot_id) REFERENCES chatbots(id);

CREATE TABLE attendants (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(255) NOT NULL
);
CREATE INDEX ix_attendants_name ON attendants(name);

CREATE TABLE attendances (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    conversation_id BIGINT NOT NULL UNIQUE REFERENCES conversations(id),
    attendant_id BIGINT NOT NULL REFERENCES attendants(id),
    started_at TIMESTAMP NOT NULL,
    closed_at TIMESTAMP
);

-- Preserva os operadores e atendimentos anteriores a esta modelagem.
INSERT INTO attendants (name)
    SELECT DISTINCT TRIM(assigned_to) FROM conversations
    WHERE assigned_to IS NOT NULL AND TRIM(assigned_to) <> '';
INSERT INTO attendances (conversation_id, attendant_id, started_at, closed_at)
    SELECT c.id, a.id, COALESCE(c.assigned_at, c.started_at), c.closed_at
    FROM conversations c JOIN attendants a ON a.name = TRIM(c.assigned_to);
