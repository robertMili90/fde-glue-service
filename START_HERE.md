# Stripe-to-ERP Glue Service: local agent handoff

This is an English, repository-ready development brief for Robert Militaru's
Adobe Senior Forward Deployed Engineer take-home assignment. It contains
instructions and acceptance criteria. The implementation and verified startup
commands are described in [README.md](README.md).

## Start on your laptop

If you received a Git bundle, clone it into a new directory:

    git clone /path/to/fde-glue-service-starter.bundle fde-glue-service
    cd fde-glue-service

Open the resulting directory in your local coding agent. Give it the full
contents of prompts/01-build-project.md. That prompt authorizes local development
through implementation, verification, and documentation within the agreed scope.
For smaller sessions, use the numbered milestone prompts instead.

Codex discovers the four project skills under .agents/skills. If your agent does
not discover them, tell it to read the relevant SKILL.md directly. No model API
key is needed for the application: AI is used in the development workflow only.

## Agreed decisions

- Real Stripe sandbox/test API plus a fictional, stateful ERP simulator.
- Java 21, Spring Boot, Maven Wrapper, official stripe-java SDK, Spring Data JPA,
  H2 on disk for development/demo, H2 in memory for fast isolated tests.
- OpenAPI/Swagger UI, Spring scheduling, bounded asynchronous processing using
  persisted jobs, Actuator/basic metrics, structured logs, Docker Compose.
- One running glue-service instance. Concurrent HTTP requests still need safe
  deduplication and wake-up handling.
- Unidirectional synchronization of payment facts from Stripe to the ERP.
- CodeGraph over MCP is optional developer tooling with a search/read fallback.
- Runtime AI, a custom MCP server, Redis, RabbitMQ, Kafka, PostgreSQL, Quartz,
  Spring Batch, PDF generation, and a separate frontend are backlog items.

## Explicit defaults chosen to remove ambiguity

1. One ERP invoice is linked to exactly one Stripe PaymentIntent.
2. The ERP stores that reference independently of webhook delivery. Reconciliation
   can therefore recover even when the very first webhook was never received.
3. Automatic full capture and an optional full successful refund are supported.
   Partial refunds and ambiguous financial states are held for review.
4. Posted invoice amount, currency, and payment reference are fixed business facts.
   A simulator-only control may deliberately create conflicting data.
5. The glue service reads Stripe and updates the ERP projection. Payment/refund
   creation belongs to explicitly sandbox-only demo fixtures outside the sync path.
6. Webhooks are notifications; current Stripe resources determine financial facts.
7. Reviewer discussion is architecture-focused and language-neutral. Keep code
   small enough to inspect and explain within the original 8-10-hour effort.

These are documented assumptions, not additional requirements from Adobe.
The candidate can change one by updating the design and affected scenarios.

## Read in this order

1. AGENTS.md: operating instructions.
2. docs/design.md: authoritative scope, business rules, and reliability guarantees.
3. contracts/erp-api.yaml: simulator HTTP contract.
4. docs/scenarios.md: observable acceptance criteria.
5. docs/implementation-plan.md: milestones and completion rules.
6. docs/agent-tooling.md: CodeGraph/MCP developer setup.

The original assignment and a faithful English rendering of the recruiter's
clarifications are in sources/. These take precedence over implementation
preferences if an actual conflict is found.

## Skills and prompts

| Skill | Purpose |
| --- | --- |
| $fde-start-project | Inspect the starter, choose compatible dependencies, bootstrap both apps. |
| $fde-implement-slice | Implement one acceptance behavior and verify its observable result. |
| $fde-review-reliability | Review correctness and recovery; report findings without editing code. |
| $fde-debug-sync | Reproduce a specific failure, fix its cause, and verify the regression. |

prompts/01-build-project.md is the main end-to-end prompt.
prompts/02-bootstrap.md through prompts/05-finish.md allow staged execution.
prompts/06-review.md and prompts/07-debug.md are reusable follow-up prompts.

## Required outcomes

Runnable source, clear setup instructions, offline automated checks, an optional
real Stripe sandbox demonstration, concise architecture/decision notes, honest
AI-use records, and explicit limitations. A preflight failure, skipped live test,
or unexecuted check must be reported honestly rather than represented as a pass.

No product choice, runtime LLM, production platform, or exhaustive coverage is
required by the assignment. Build the agreed slice before considering extensions.
