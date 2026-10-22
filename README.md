# Stripe-to-ERP Glue Service

Status: planning starter; implementation has not started.

A fictional company issues invoices in an internal ERP and collects payment
through Stripe. Manual updates leave invoices inconsistent with payment facts.
The glue service synchronizes those facts, recovers missed notifications, and
holds incompatible records for operator review.

Start with [START_HERE.md](START_HERE.md). A local coding agent should use
[the main build prompt](prompts/01-build-project.md).

The implementation agent must replace this status with verified prerequisites,
exact startup/build/test commands, environment variables, the real Stripe sandbox
workflow, and known limitations. Keep the customer problem and value prominent.
Do not claim that this starter already contains runnable applications.

## Design and evidence

- [Design](docs/design.md)
- [Acceptance scenarios](docs/scenarios.md)
- [ERP API contract](contracts/erp-api.yaml)
- [Implementation milestones](docs/implementation-plan.md)
- [Development workflows](docs/workflows.md)
- [CodeGraph and MCP tooling](docs/agent-tooling.md)
- [AI usage record](docs/ai-usage.md)
- [Backlog](docs/backlog.md)
- [Sources and reference documentation](docs/references.md)

The final repository is the primary interview artifact. Explain responsibilities,
failure handling, guarantees, and trade-offs in language-neutral terms; include
Java/framework details only where they affect behavior.
