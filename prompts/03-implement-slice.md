# Reusable prompt: implement one slice

Use $fde-implement-slice.

Task: implement the next incomplete milestone or acceptance behavior identified
in docs/implementation-plan.md. If I named a specific scenario in my message,
that scenario takes precedence.

Read the authoritative rule and API contract, inspect affected source, and explain
the intended observable result briefly. Use CodeGraph for relevant cross-component
questions when available. Make the smallest coherent implementation.
Add/run meaningful verification for the behavior and relevant failures.
Inspect the diff and update actual scenario/progress evidence.

Do not silently change business assumptions, add backlog infrastructure, weaken
tests, or claim an unexecuted check passed. Finish with changed behavior, actual
verification results, and material remaining limits.
