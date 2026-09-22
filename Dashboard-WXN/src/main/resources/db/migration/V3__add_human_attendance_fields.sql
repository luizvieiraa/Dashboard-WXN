-- V3: dados operacionais do atendimento humano.

ALTER TABLE conversations ADD COLUMN assigned_to VARCHAR(255);
ALTER TABLE conversations ADD COLUMN assigned_at TIMESTAMP;
ALTER TABLE conversations ADD COLUMN closed_at TIMESTAMP;

CREATE INDEX ix_conversations_status_last_interaction
    ON conversations (status, last_interaction_at);
