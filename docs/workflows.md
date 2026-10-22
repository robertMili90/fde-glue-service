# Agent development workflows

## Implement one behavior

Read AGENTS.md, the relevant design rule, the ERP contract, and the scenario.
Inspect the existing code; use CodeGraph for a cross-component question when
available. State the intended behavior and a short implementation plan.
Implement one coherent change. Verify the observable outcome with a focused
check, inspect the diff, and run the relevant broader suite once stable.
Update evidence and report what changed, why, and the actual check results.

Use $fde-implement-slice and prompts/03-implement-slice.md.

## Review reliability

Read the design and acceptance criteria independently of the implementation.
Trace the actual transaction/HTTP boundaries and failure paths. Check observable
behavior and evidence rather than trusting names such as idempotent or durable.
Report concrete defects by severity with a failure sequence and affected source.
Do not edit code during a review-only task.

Use $fde-review-reliability and prompts/06-review.md.

## Diagnose a failure

Capture the failing scenario and current work/ERP state, reproduce it, and trace
the evidence. Form a falsifiable cause hypothesis. Add a meaningful regression
check, fix the cause, and run the relevant suite. Preserve the required behavior;
do not suppress failures by widening retry limits or deleting assertions.

Use $fde-debug-sync and prompts/07-debug.md.

## Evidence and autonomy

Proceed through routine local implementation choices. Escalate a genuinely
ambiguous business contract or unavailable required access with a precise question,
while continuing independent offline work. Do not ask for approval at every
milestone. Do not invent API keys, test results, token savings, or review outcomes.
