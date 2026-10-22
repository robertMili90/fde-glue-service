# Backlog: deliberately outside the initial implementation

These are considered extensions, not promises or hidden acceptance requirements.

## Operational MCP adapter

Value: let an external coding/operations agent investigate synchronization state.
Expose read-only get_sync_status, get_sync_history, and
get_reconciliation_summary tools over existing operational application functions.
Keep business logic in one place. An external agent supplies any LLM reasoning;
the glue service need not embed a model.
Acceptance: tool results match the existing API; authorization and deployment
scope are explicit; failed calls have structured errors; no financial mutation
is exposed in the first adapter.

## AI-assisted exception explanation

Value: summarize evidence for an operator investigating an invoice.
Consider only when operator research establishes an unmet need.
Use bounded read access, evidence references, explicit uncertainty, versioned
prompts, and a small evaluation set. Financial decisions remain deterministic.
No implementation or model dependency is planned for the take-home slice.

## Scaling and scope extensions

- PostgreSQL plus coordinated work claiming and stale-writer protection.
- RabbitMQ plus an outbox if measured buffering/independent scaling warrants it.
- Redis only for a measured cache/rate-control need, or Redis Streams as a
  deliberately chosen alternative queue implementation.
- Partial refunds, multiple payments per invoice, disputes, more providers,
  multi-tenancy, and account-wide discovery.
- Advanced scheduling only for a real scheduling requirement.
- PDF reconciliation report and a small operations page after the core is complete.

Reassess each against customer value and operating cost before implementing it.
