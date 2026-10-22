# Main prompt: build the agreed project

Use $fde-start-project, then $fde-implement-slice for each milestone. If skills are
not auto-discovered, read their SKILL.md files under .agents/skills.

Read AGENTS.md, docs/design.md, docs/scenarios.md, contracts/erp-api.yaml, and
docs/implementation-plan.md. Build the runnable Stripe-to-ERP glue service and
stateful ERP simulator described there on my local laptop.

First inspect the actual repository and environment. Preserve unrelated work.
Use Java 21, Spring Boot, Maven Wrapper, the official Stripe Java SDK, Spring Data
JPA, H2 on disk, OpenAPI/Swagger UI, Spring scheduling, persisted asynchronous work,
basic observability, and Docker Compose. Select/pin compatible stable versions
after verifying official documentation. Keep tests offline from Stripe by default.

Proceed through M0-M6 autonomously within scope. Do not stop after a plan,
skeleton, happy path, or a milestone unless genuinely blocked. Make routine
implementation choices and record meaningful trade-offs. Ask only if a missing
fact would materially change the business contract or required access; continue
independent offline work while any sandbox access is unavailable.

Preserve the invariants: commit accepted work before webhook 2xx; distinguish
receipt from completion; use current Stripe facts; independently enforce ERP
idempotency; preserve wake-ups during processing; persist bounded retries and
recover after restart; reconcile all linked ERP references including old paid
invoices; hold business conflicts visibly.

Use CodeGraph when available for cross-component context/impact questions.
Continue with normal source search if unavailable. Do not install global tooling
or change global agent settings as a prerequisite.

Keep runtime AI, a custom MCP server, Redis, brokers, PostgreSQL, PDFs, and a
separate frontend in the backlog. Do not expand scope for appearance.

Use local environment variables for Stripe test credentials. Never request them
in chat, commit/log them, or use live mode. The service reads Stripe; only
explicit sandbox fixture tooling may create test payments/refunds.

Maintain progress and actual AI-use evidence. Run the relevant checks, perform a
reliability review with $fde-review-reliability, fix concrete defects, and verify
again as needed. Report what was implemented, actual check results, skipped
checks, remaining limitations, and exact local startup/demo commands.
