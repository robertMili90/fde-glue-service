# Reusable prompt: diagnose and fix a failure

Use $fde-debug-sync.

Investigate the failure described in my message. If no failure is specified,
select an actually failing check from the current verification output; do not
invent a defect.

Reproduce it, capture the relevant work/ERP state and redacted identifiers, inspect
the responsible path, and test a falsifiable cause hypothesis. Add a meaningful
regression check and make the smallest cause-level correction.
Run the focused check and the relevant broader suite. Preserve the design
guarantees and report actual evidence, not just a plausible explanation.
