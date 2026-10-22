---
name: fde-implement-slice
description: "Implement one complete behavior in the agreed Stripe-to-ERP FDE project. Use for a milestone or acceptance scenario spanning persistence, Stripe/ERP adapters, retries, reconciliation, or operational APIs."
---

# Implement one complete behavior

1. Read AGENTS.md and the relevant project-root design/scenario/contract sections.
   State the observable result and inspect existing implementation before editing.
2. Use CodeGraph for relevant call-path/impact questions when available.
   Complement it with source/config/HTTP-contract reading and use ordinary search
   if unavailable. Never make graph availability a build prerequisite.
3. Implement the smallest coherent slice that preserves the financial and
   reliability invariants. Avoid adding dependencies without a concrete role.
4. Keep external calls outside local transactions. Distinguish event deduplication
   from destination idempotency and receipt from completed work. Preserve pending
   wake-ups, persisted retries, and the documented single-instance boundary.
5. Add meaningful externally observable verification, including the relevant
   failure window. Use deterministic provider stubs and controllable time where
   appropriate. Use file persistence for a restart-durability check.
6. Run the focused checks, inspect the diff, and run the relevant broader suite.
   Fix failures instead of weakening assertions or silently changing the contract.
7. Update actual scenario/progress evidence and AI usage when material.

Report changed behavior, the reason for the approach, actual check results,
skipped checks, and material limits. Continue to the next milestone when the
main task authorizes end-to-end delivery.
