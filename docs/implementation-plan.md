# Implementation milestones

The original expected effort is approximately 8-10 focused hours. Treat this as a
scope constraint, not a demand to invent evidence or skip correctness.
Implement in vertical slices; stop adding extensions when the core is complete.

| Milestone | Scope | Exit evidence |
| --- | --- | --- |
| M0 | Inspect starter/existing repo, verify compatible dependencies, note assumptions | Recorded versions and a bounded plan; no unresolved core contract conflict. |
| M1 | Maven Wrapper; two Spring Boot apps; separate H2 files; migrations; health/OpenAPI; Compose | Both apps start; persistence survives a normal restart; basic build passes. |
| M2 | Stateful ERP invoice/projection API with idempotency and fault controls | Matching effect, replay, changed-payload rejection, and stale-version behavior verified. |
| M3 | Stripe adapter, signed webhook ingress, durable inbox/work request, normal sync | Matching payment reaches ERP through queued work; signed/invalid/duplicate cases verified. |
| M4 | Retry, timeout-after-commit, restart recovery, current-state refund handling | S04-S07 and the lost-wake-up condition verified. |
| M5 | Paginated ERP-reference reconciliation, operational APIs, basic metrics/logs | First missing webhook and old-invoice refund recovery verified. |
| M6 | Focused review, final verification, optional sandbox smoke, README/design/AI evidence | Accurate scenario matrix, actual check results, setup/demo instructions, known limits. |

M2 and M3 may be interleaved to get one real end-to-end slice working early.
The implementation agent can change sequencing when it reduces rework while
preserving the acceptance criteria.

## Working discipline

- Use the smallest implementation that can demonstrate the guarantees.
- Prefer existing Spring/JDK mechanisms and the official Stripe SDK.
- Add dependencies only for a concrete responsibility. Do not introduce a
  broker, cache, workflow platform, or LLM to make the diagram look richer.
- Pin actual compatible versions and record them after verifying official docs
  and resolving a build. Do not copy stale version numbers from a conversation.
- Keep progress and checks in the table below. Do not pre-populate success.
- If time runs short, cut optional presentation/features first. Explain unfinished
  core behavior explicitly rather than hiding it behind a passing happy path.

## Progress log

| Milestone | Status | Evidence / next step |
| --- | --- | --- |
| M0-M6 | NOT_STARTED | This package contains instructions and contracts only. |

## Local baseline to establish

The agent should provide and verify ./mvnw verify and documented commands for:
starting each module in an IDE/terminal; starting the full local stack with
docker compose; configuring only sandbox Stripe credentials; forwarding
webhooks with Stripe CLI; seeding linked invoices; and exercising fault controls.

Default automated verification must work offline from Stripe. Failure-injection
controls and operational APIs are local exercise tooling, not a production
authorization design. Record that deployment limitation.
