# Reusable prompt: architecture and reliability review

Use $fde-review-reliability. Read AGENTS.md and the authoritative design,
scenarios, and ERP contract, then inspect the actual implementation.

Perform a review only; do not change application code. Trace transaction and
network boundaries. Examine crash windows, event receipt versus completion,
idempotent destination effects, ordering, retry budgets, reconciliation discovery,
pending/failed refunds, source/target conflicts, and unverified claims.

Report only substantiated findings, ordered by severity. Give the affected source,
a concrete failure sequence, observed or missing evidence, and a focused
recommendation. Separate blockers from optional improvements. If no defect is
found, say so and identify the practical verification limits.
