---
name: fde-debug-sync
description: "Diagnose and fix a concrete synchronization failure in the Stripe-to-ERP FDE project. Use for a failing test, incorrect ERP projection, stuck/retrying job, missed notification, or restart/reconciliation defect."
---

# Diagnose and fix synchronization

1. Read project-root AGENTS.md and the relevant contract, design rule, and scenario.
2. Capture the actual failure and redacted event/payment/invoice/operation IDs.
   Identify source facts, work state/history, and observed ERP state.
3. Reproduce the failure. Inspect the relevant path with CodeGraph/source reading.
   Form a cause hypothesis that can be disproved by a focused check.
4. Add or strengthen a meaningful regression check for the observed failure.
   Do not manufacture a defect when no failing evidence exists.
5. Correct the cause with a bounded change while preserving financial facts,
   destination idempotency, persisted work, and the declared scope.
6. Run the focused regression and relevant broader checks. Investigate any new
   failure rather than hiding it with larger retries, sleeps, or removed assertions.
7. Update actual evidence and limitations. Record a material AI correction honestly.

Report reproduction, cause, correction, and actual verification results. State
unknowns explicitly if blocked, and continue independent offline diagnosis.
