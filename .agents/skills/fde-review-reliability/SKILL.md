---
name: fde-review-reliability
description: "Review architecture and reliability of the Stripe-to-ERP FDE project. Use for an independent review of transaction boundaries, idempotency, failure recovery, reconciliation, conflict handling, and verification evidence; review-only by default."
---

# Review reliability

1. Read project-root AGENTS.md, docs/design.md, docs/scenarios.md, and the ERP API
   contract before judging the implementation. Keep the review task read-only.
2. Trace actual acceptance/persistence/processing boundaries, not just class names.
   Inspect the DB uniqueness constraint and what happens on duplicate concurrent
   receipt, persistence failure, and process interruption.
3. Trace destination effect and idempotency storage through timeout after commit.
   Check same-key replay before version checks and changed-payload rejection.
4. Check current Stripe facts, full/pending/failed/partial refunds, delivery-order
   independence, per-payment serialization, and triggers received during work.
5. Check durable bounded retries, exhausted/held states, operator requeue, restart
   recovery, short transactions, and the actual retry stack.
6. Check that reconciliation can find a never-received first notification from
   ERP references, includes old paid invoices, handles pagination, and reports
   incomplete enumeration honestly.
7. Inspect executed evidence and the difference between stubbed tests, persistent
   restart checks, and a real sandbox demonstration. Do not infer a pass.

Return concrete findings ordered by severity. Include source locations, a
reproducible failure sequence, evidence/verification gaps, and a focused remedy.
Separate design violations from optional future features. If no defect is found,
state the practical verification limits. Editing requires a fix/implementation
task, not this review-only invocation.
