CREATE TABLE erp.invoice (
    id VARCHAR(255) PRIMARY KEY,
    payment_intent_id VARCHAR(255) NOT NULL UNIQUE,
    expected_amount_minor BIGINT NOT NULL CHECK (expected_amount_minor > 0),
    currency VARCHAR(3) NOT NULL,
    version BIGINT NOT NULL,
    financial_status VARCHAR(16) NOT NULL,
    amount_collected_minor BIGINT,
    amount_refunded_minor BIGINT
);
CREATE TABLE erp.idempotency_result (
    operation_key VARCHAR(255) PRIMARY KEY,
    payload CLOB NOT NULL,
    result CLOB NOT NULL
);
CREATE TABLE erp.demo_control (
    id BIGINT PRIMARY KEY,
    mode VARCHAR(32) NOT NULL,
    remaining_requests INTEGER NOT NULL,
    delay_ms INTEGER NOT NULL,
    applied_update_count BIGINT NOT NULL
);
INSERT INTO erp.demo_control VALUES (1, 'NONE', 0, 0, 0);
