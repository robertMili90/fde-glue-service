ALTER TABLE glue.work_gate ADD current_scan_id VARCHAR(36);
CREATE TABLE glue.reconciliation_run (
    id VARCHAR(36) PRIMARY KEY,
    state VARCHAR(32) NOT NULL,
    cursor VARCHAR(255),
    enqueued_references BIGINT NOT NULL,
    pages_processed BIGINT NOT NULL,
    failed_batches INTEGER NOT NULL,
    last_failure VARCHAR(100),
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE
);
