CREATE TABLE glue.work_gate (id BIGINT PRIMARY KEY);
INSERT INTO glue.work_gate VALUES (1);
CREATE TABLE glue.received_event (
    id VARCHAR(255) PRIMARY KEY,
    event_type VARCHAR(100) NOT NULL,
    payment_intent_id VARCHAR(255) NOT NULL,
    received_at TIMESTAMP WITH TIME ZONE NOT NULL,
    payload CLOB NOT NULL
);
CREATE TABLE glue.work_item (
    payment_intent_id VARCHAR(255) PRIMARY KEY,
    state VARCHAR(32) NOT NULL,
    requested_generation BIGINT NOT NULL,
    completed_generation BIGINT NOT NULL,
    processing_generation BIGINT NOT NULL,
    transient_failures INTEGER NOT NULL,
    total_attempts INTEGER NOT NULL,
    next_eligible_at TIMESTAMP WITH TIME ZONE,
    pending_since TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    reason_code VARCHAR(100),
    invoice_id VARCHAR(255),
    operation_key VARCHAR(255),
    last_event_id VARCHAR(255),
    facts CLOB
);
CREATE INDEX eligible_work ON glue.work_item(state, next_eligible_at);
CREATE TABLE glue.attempt (
    id VARCHAR(36) PRIMARY KEY,
    payment_intent_id VARCHAR(255) NOT NULL REFERENCES glue.work_item(payment_intent_id),
    generation BIGINT NOT NULL,
    attempt_number INTEGER NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    finished_at TIMESTAMP WITH TIME ZONE,
    outcome VARCHAR(32) NOT NULL,
    reason_code VARCHAR(100),
    invoice_id VARCHAR(255),
    operation_key VARCHAR(255),
    facts CLOB
);
CREATE INDEX attempts_by_payment ON glue.attempt(payment_intent_id, started_at);
