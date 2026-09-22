-- V2: estrutura de triagem e estados do atendimento.

ALTER TABLE customers ADD COLUMN email VARCHAR(320);
ALTER TABLE customers ADD COLUMN company_name VARCHAR(255);
ALTER TABLE customers ADD COLUMN job_title VARCHAR(255);

UPDATE conversations SET status = 'BOT_ACTIVE' WHERE status = 'OPEN';
ALTER TABLE conversations ALTER COLUMN status TYPE VARCHAR(30);

CREATE TABLE triages (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    conversation_id BIGINT NOT NULL,
    category        VARCHAR(20),
    priority        VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    subject         VARCHAR(255),
    customer_need   TEXT,
    summary         TEXT,
    requires_human  BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_triages_conversation UNIQUE (conversation_id),
    CONSTRAINT fk_triages_conversation
        FOREIGN KEY (conversation_id) REFERENCES conversations (id)
);

CREATE TABLE triage_missing_information (
    triage_id  BIGINT NOT NULL,
    field_name VARCHAR(100) NOT NULL,
    CONSTRAINT pk_triage_missing_information PRIMARY KEY (triage_id, field_name),
    CONSTRAINT fk_triage_missing_information
        FOREIGN KEY (triage_id) REFERENCES triages (id) ON DELETE CASCADE
);

CREATE INDEX ix_triages_category ON triages (category);
CREATE INDEX ix_triages_priority ON triages (priority);
CREATE INDEX ix_triages_requires_human ON triages (requires_human);
