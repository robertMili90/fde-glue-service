# Design: payment reconciliation between Stripe and a fictional ERP

## Contents

1. Customer context and scope
2. Ownership and invariants
3. Components and data flow
4. Financial normalization
5. Persistence, ordering, and retries
6. Reconciliation and operations
7. Simulator behavior
8. Trade-offs and limits

## 1. Customer context and scope

An internal ERP tracks invoices while Stripe processes collections. Operators
currently inspect Stripe and manually update invoice payment facts. Delayed,
duplicated, or missed notifications create inconsistent records and investigation.

Synchronize payment facts into the ERP and expose unresolved cases. Success means
an eligible linked invoice converges to supported current Stripe facts after
transient failures, or has a visible reason for operator intervention.

Support one Stripe test account, one ERP, one glue instance, one PaymentIntent
per invoice, automatic full capture, and optional full successful refund.
Keep partial financial scenarios, multiple payments per invoice, multi-tenancy,
Connect, disputes, direct debit, subscriptions, payment UI, and production HA
outside the implemented slice. Explain these limits rather than pretending they
are unsupported by Stripe itself.

The ERP stores the PaymentIntent ID before synchronization. The fixture workflow
creates a test PaymentIntent, creates/links the ERP invoice, then confirms the
test payment. The glue service is not the checkout or payment-orchestration system.

## 2. Ownership and invariants

| Information | Authority |
| --- | --- |
| Invoice identity, expected amount/currency, PaymentIntent association | ERP |
| Collected funds and successful refunds | Current Stripe resources |
| Work state, attempts, reasons, reconciliation runs | Glue service |

Amounts are nonnegative integral minor units with a currency code. Use a wide
integer type, validate bounds, and normalize code casing. Do not assume every
currency has two decimal places.

Required invariants:

- Accepting an event cannot lose its work request on a normal process restart.
- Repeating a supported operation has the same ERP financial effect.
- An old notification cannot roll a refunded invoice back to paid.
- An incompatible amount, currency, association, or partial refund is visible
  and does not overwrite invoice business facts.
- A missing notification does not permanently prevent linked-invoice convergence.

Guarantee at-least-once attempts and idempotent ERP effects. Do not claim
distributed exactly-once execution.

## 3. Components and data flow

Two Maven application modules are sufficient: glue-service and erp-simulator.
They run as separate processes and communicate over HTTP. They have separate H2
files and do not share ORM entities or repositories.

The glue service contains a verified webhook ingress, a Stripe reader adapter,
normalization/sync policy, persistent work store, worker, ERP HTTP adapter,
reconciler, and operational API. These are components of one application,
not additional microservices.

Webhook ingress verifies Stripe's signature against the exact raw request body
using the SDK. For a supported test-mode event, one local transaction inserts
the unique event record and requests work for its PaymentIntent. Return 2xx only
after commit. A duplicate valid event returns 2xx even when its work is unfinished.
An invalid signature returns 400; an inability to persist accepted work returns
a retryable non-2xx response. Safely ignore unsupported valid event types.

Initially subscribe to payment_intent.succeeded, payment_intent.payment_failed,
refund.created, refund.updated, and refund.failed. Resolve a Refund notification
to its PaymentIntent. Verify the event names and SDK shapes for the pinned Stripe
API/SDK version; record any compatible adjustment.

The worker retrieves current Stripe resources, reads the ERP invoice, validates
ownership rules, and writes an absolute payment projection through the ERP API.
Both webhook and reconciliation requests use this same path.

## 4. Financial normalization

Retrieve the PaymentIntent and all pages of its Refund resources, filtered by
PaymentIntent ID. Count only refunds whose current status is succeeded.
Do not use a truncated embedded refunds list or event.created as a version.

For this slice:

- A PaymentIntent that has not succeeded remains waiting for provider state.
  It must not create a paid projection. A recoverable payment attempt failure is
  not automatically a permanent invoice failure.
- For a succeeded payment, collected amount must equal the ERP expected amount
  and currencies must match.
- No successful refund means PAID.
- Successful refund total equal to the collected amount means REFUNDED.
- A positive smaller refund total is unsupported partial refund: NEEDS_REVIEW.
- Invalid/excess totals, mismatched currencies, missing/ambiguous associations,
  and incompatible invoice facts are NEEDS_REVIEW.
- A pending refund does not count as refunded. Keep the currently supported
  financial projection and ensure subsequent provider refresh is possible.

A fully refunded PaymentIntent can still have status succeeded: derive refund
facts separately. Failed/canceled refunds do not subtract collected funds.
Repeated successful observations can be no-ops after checking the ERP state.

Construct the ERP operation key from a versioned canonical fingerprint of
invoice ID, PaymentIntent ID, currency, collected amount, and successful-refund
total. Exclude event IDs, attempt counts, current timestamps, and delivery source.
Equivalent observations from webhooks and reconciliation must produce the same key.

## 5. Persistence, ordering, and retries

Persist at least received-event identity, work state, attempt history, and
reconciliation outcomes. Concrete table/class names are implementation choices.
Use a UNIQUE event-ID constraint, not only a check-then-insert.

Use one worker in this version. HTTP requests and the reconciler enqueue work;
they do not start a competing direct synchronization.
Handle notifications arriving while work is running: a requested/completed
generation pair or an equivalent durable dirty marker must cause another pass.
A duplicate of an already recorded event alone should not create unlimited work.

Typical work states are READY, RUNNING, RETRY_SCHEDULED, WAITING_PROVIDER,
DONE, NEEDS_REVIEW, and FAILED. Separate workflow state from ERP financial status.
Record reason codes, attempt count, timestamps, and next eligible time.
On single-instance startup, recover orphaned RUNNING work to an eligible state.
Do not apply this recovery scheme unchanged to multiple running instances.

No DB transaction spans an external HTTP request. Persist a retry before releasing
the failed attempt. Use bounded exponential backoff, optional jitter, timeouts,
and a configurable attempt budget (default five transient attempts).
Do not layer unbounded SDK, HTTP, and application retries. Document the combined
retry budget. Provider waiting is distinct from consuming transient-error retries.

Timeouts, connection failures, 429, and eligible 5xx are transient. Respect a
reasonable Retry-After when supplied. Configuration/authentication failures and
business incompatibilities need an explicit visible state, not endless retries.
Held NEEDS_REVIEW and exhausted FAILED work require explicit operator requeue;
ordinary reconciliation must not silently reset their budgets/holds.

## 6. Reconciliation and operations

Periodically enumerate the ERP's linked invoices with pagination, including paid
and refunded invoices, and enqueue their PaymentIntent IDs. This is bounded to
the ERP's registered references; it is not account-wide payment discovery.
Do not restrict reconciliation to invoices created recently or only to unpaid
invoices: later refunds can affect an old successful payment.

This choice recovers a first missing webhook without depending on an inbox row
that was never created. Unlinked payments remain outside automatic matching.
Track incomplete/failed enumeration explicitly; never report a capped/partial
run as complete. Use configurable batch limits with a persisted continuation
cursor: resume subsequent batches rather than repeatedly starting from page one.
Only mark a scan complete after its final page; the next full scan starts anew.
Document the small-demo throughput limit. Coalesce repeated requests for one payment.

Provide operational endpoints to inspect work/attempt history, trigger a
reconciliation run, inspect run outcomes, and explicitly requeue a held/failed job.
Use 202 for queued operations. These APIs enqueue work rather than bypassing it.

Log event, payment, invoice, operation key, and attempt identifiers where available.
Expose basic health and a small set of useful metrics: pending work, oldest-work
age, failures, held conflicts, attempt latency, and last reconciliation outcome.
Choose a sensible single-instance polling interval; measure a small seeded burst
instead of making unsupported scalability claims.

## 7. Simulator behavior

Follow contracts/erp-api.yaml. Store idempotency outcomes and invoice updates
atomically in the ERP's own database. A replay with the same key and same payload
returns the stored result before evaluating a stale If-Match header.
The same key with a different payload is a conflict.
Resolve existing-key replay/conflict before synthetic fault injection; neither
case consumes a fault counter. Fault controls apply only to structurally valid
requests with an unseen key.

The update uses an expected invoice version. A stale version has no side effect;
the glue service refreshes and revalidates before a bounded retry.
Persisting a payment projection uses the PaymentIntent reference as a unique
business identity. A new idempotency key must not blindly append a second payment.

Fault controls are demo-only: fail before write for N requests, apply then delay
the response past the caller timeout, and produce a version conflict.
The apply-then-delay fault commits first, outside the artificial delay.
Support inspecting the state and reseeding only the local simulator.

## 8. Trade-offs and limits

- H2 on disk keeps setup small and survives normal process restart. It is not a
  production-HA or multi-instance datastore strategy. Use separate mounted data
  directories/volumes in containers; do not use create-drop schema settings.
- DB-backed work avoids a second DB/broker transaction boundary. Revisit RabbitMQ
  and an outbox when independent scaling or measured queue behavior warrants it.
- One worker simplifies per-payment ordering and exposes a throughput limit.
  Multiple instances need coordinated claiming, ownership recovery, and stale-writer
  protection; a broker alone would not solve these.
- Current-resource reads avoid arrival-order dependence, but cost API requests.
  They are observations, not an atomic snapshot across Stripe objects. A completed
  sync can need another pass if Stripe changes during processing.
- Reconciliation covers linked invoices and later changes. Its scan cost grows
  with their number and requires a revised selection/checkpoint strategy at scale.
- Spring Boot is chosen for the candidate's delivery productivity. Presentation
  emphasizes responsibilities and guarantees rather than framework internals.
- CodeGraph/MCP assists development and is optional. Runtime remains deterministic.

Retain these as concise decision records and amend them with evidence from the
implementation. No unanswered product-selection question blocks development.
