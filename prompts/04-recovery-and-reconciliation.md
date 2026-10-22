# Milestone prompt: recovery and reconciliation

Use $fde-implement-slice to complete M4-M5 against docs/design.md and
docs/scenarios.md.

Concentrate on ERP timeout after commit, independently idempotent financial effect,
bounded persisted retries, restart recovery, wake-ups received during processing,
current Stripe payment/refund facts, and old-notification handling.

Reconciliation must enumerate the ERP's pre-linked references with pagination,
including old paid invoices. Demonstrate recovery of a first missing webhook,
not only retry of an event already present in the inbox. Held business conflicts
must not have their budget/reset policy silently overridden.

Expose useful operational state and manual triggers, and verify the observable
ERP results. Keep network calls outside local DB transactions.
