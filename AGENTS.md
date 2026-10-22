# Repository instructions

## Goal and precedence

Deliver the small Stripe-to-ERP integration described in docs/design.md.
Follow the user's current instructions first, then the supplied assignment and
clarifications, this file, and the project documents. A material conflict should
be surfaced explicitly. Routine implementation choices do not need confirmation.

Read docs/design.md and docs/scenarios.md before architectural work. Read the
relevant section and HTTP contract before a slice. Keep one authoritative
description of each rule; link to it rather than copying whole specifications.

## Scope

Use the agreed Java 21/Spring Boot stack and two separately running applications.
Use H2 on disk for development/demo and separate storage for the simulator.
Use durable DB-backed work and a single glue-service instance.
Keep runtime AI, a custom MCP server, external brokers, Redis, PostgreSQL,
PDF generation, and a separate frontend in the backlog.

## Correctness rules

- Persist a supported verified webhook and its work request atomically before 2xx.
- A recorded event is not necessarily a completed synchronization.
- Deduplicate event IDs and make the ERP effect idempotent independently.
- Derive current payment facts from Stripe resources, not arrival order or an
  invented PaymentIntent version/refunded status.
- Use integral minor-unit amounts and explicit currency; never floating point.
- Serialize processing for a payment. Preserve notifications received during work.
- Keep DB transactions short; do not hold them across remote calls or retry waits.
- Retry transient failures with persisted scheduling and a bounded budget.
- Hold business conflicts for review without silently changing invoice facts.
- Recover accepted unfinished work after process restart.
- Reconciliation must handle the first missing webhook and later refunds on old
  paid invoices, within the documented linked-invoice scope.

## Development workflow

Use the project skill matching the task. Implement small complete slices.
For cross-component questions, use CodeGraph through MCP or its CLI when
available; verify the index and complement it with direct source/config reads.
For simple text lookup, use targeted searches. If CodeGraph is unavailable,
continue with ordinary search and reading.

Inspect affected code and contracts before editing. Maintain meaningful tests
for externally visible behavior and failure recovery. Do not weaken acceptance
criteria, remove assertions, or invent successful check results to finish a task.

Once bootstrap exists, the default verification command is ./mvnw verify.
Choose focused checks during iteration and run the complete relevant suite before
handoff. Bootstrap must document the actual commands and any required profiles.
Do not make real Stripe network access a prerequisite for the default test suite.

## Execution boundaries

Develop locally within the agreed scope without repeated approval requests.
Do not overwrite unrelated files, push/publish the project, or change user-global
agent settings unless instructed. Inspect an existing repository before merging
this starter into it.

Application/demo Stripe access is test/sandbox only. Load credentials from local
environment variables; never request secrets in chat or commit/log them.
Local fixtures may create only explicitly test-mode resources. Reject live-mode
events/resources and live credentials in this exercise. Runtime sync never
creates charges, refunds, or payment instructions.

## Honest reporting

Record actual AI assistance in docs/ai-usage.md as work happens.
Keep docs/implementation-plan.md and README status accurate.
Report changes, commands actually executed, results, skipped checks, and remaining
limitations. Treat agent review as supplementary to executable verification.
