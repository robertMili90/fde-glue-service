# Observable acceptance scenarios

These define behavior independently of implementation classes. Link implemented
tests and demo commands here as they become available; do not label them passed
until actually run. Keep the automated suite independent of Stripe credentials.

| ID | Setup / action | Required observation |
| --- | --- | --- |
| S01 | Valid signed test payment event; matching invoice and succeeded payment | Event/work are stored, then accepted; ERP becomes PAID. |
| S02 | Deliver the same event twice, including concurrently | One received-event identity; no duplicate financial effect. |
| S03 | Two distinct events describe the same current payment facts | ERP effect remains idempotent; operation identity is delivery-independent. |
| S04 | ERP fails before write, then recovers | Persisted retry is visible; work eventually succeeds within the configured budget. |
| S05 | ERP commits, then response arrives after timeout | Safe retry returns the previous result; exactly one payment projection exists. |
| S06 | Stop/restart glue while accepted work is unfinished | Persistent accepted work resumes; an orphaned RUNNING item is recovered. |
| S07 | A full successful refund exists; deliver an older payment-success notification | ERP is REFUNDED and does not regress to PAID. |
| S08 | Suppress the very first webhook for a pre-linked ERP invoice | Reconciliation discovers the ERP reference and converges the invoice. |
| S09 | Amount, currency, or linked-payment association is incompatible | No unsafe financial write; visible NEEDS_REVIEW with reason and facts. |
| S10 | Invalid signature, or a live-mode event/resource | Request is rejected/held appropriately; no live financial operation or ERP effect. |
| S11 | A trigger arrives during RUNNING work | A subsequent pass is preserved and executed; no lost wake-up. |
| S12 | Reconciliation spans pages and includes an old paid invoice with a new refund | All linked references are considered; the old invoice is corrected. |

## Required assertions inside the relevant scenarios

- S01: a persistence failure produces non-2xx rather than an accepted lost job.
- S05: commit and idempotency record survive together; the next GET shows one
  financial projection, not merely a processed flag in the glue database.
- S07: the PaymentIntent can remain succeeded; derive refunds separately.
- S09: partial successful refunds are held; pending/failed refunds are not
  treated as completed refunds. Unknown business associations are visible.
- S11: test a new accepted trigger during a pass, not just duplicate event ID.
- S12: a partial/failed scan is reported as incomplete, not as a successful full run.
  With a low batch cap, a linked invoice beyond the first batch still converges
  through continuation; repeated runs must not starve later pages.

Add focused cases for an ERP stale-version response and retry exhaustion if they
are not already covered by S04/S05/S09. Verify repeated key/different payload is
rejected by the simulator without a side effect.

## Verification strategy

Use small pure-policy tests for normalization and retry decisions. Use HTTP-level
adapter/simulator tests for timeout-after-commit, idempotency, and version checks.
Exercise the application wiring with deterministic Stripe stub responses and
real local persistence where it matters. Use a temporary on-disk H2 database for
restart recovery; an in-memory-only test cannot establish restart durability.

Use an injected Clock or equivalent controllable time abstraction for scheduling.
Avoid long sleeps to test retry delays. A deliberately delayed simulator response
is allowed for the specific transport-timeout scenario.

Prefer the smallest meaningful suite covering these behaviors. Do not generate
large sets of tests that merely mirror private methods or increase coverage scores.

## Optional real Stripe sandbox check

With test credentials configured locally, create a tagged test PaymentIntent,
link it to a simulator invoice, confirm it with a documented test payment method,
and observe synchronization. Optionally create a full sandbox refund and observe
REFUNDED. Capture redacted evidence and exact commands. A skipped live check is
reported as skipped; offline adapter tests remain runnable without credentials.
