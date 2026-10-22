---
name: fde-start-project
description: "Bootstrap the agreed Adobe FDE Stripe-to-ERP take-home project. Use when starting the local repository, choosing compatible dependencies, or establishing its build, two-app setup, persistence, and verification baseline."
---

# Start the project

1. Locate the actual project root and read AGENTS.md, docs/design.md,
   docs/scenarios.md, contracts/erp-api.yaml, and docs/implementation-plan.md.
   If they are absent, ask for the project directory rather than inventing context.
2. Inspect existing files and installed Java, Maven/Wrapper, Docker/Compose, and
   optional Stripe CLI/CodeGraph. Preserve unrelated work.
3. Verify compatible stable Spring Boot, springdoc, Stripe SDK, and build versions
   using primary documentation. Pin actual choices and record them.
4. Establish two Maven application modules with separate H2 files/migrations,
   health/OpenAPI, IDE/terminal startup, Compose, and offline verification.
5. Keep application AI, a custom MCP server, external brokers, Redis, PostgreSQL,
   PDFs, and a separate frontend outside implementation scope.
6. Use local test-mode Stripe configuration only. Missing credentials must not
   block compilation, the simulator, or offline tests.
7. Verify real build/startup commands and a persistent normal restart. Mark only
   executed checks as passed and update milestone evidence.

Follow the task's requested extent: a bootstrap-only prompt stops at M1; the main
build prompt proceeds through all milestones using the slice workflow.
Do not stop the main build after producing a plan or skeleton.
