# Reliability review — 2026-10-23

Codex performed a source/contract/evidence self-review using the
fde-review-reliability skill after the implementation slices. No additional agent
or independent human review was performed. Findings were fixed under the main
prompt's implementation authorization, then executable checks were rerun.

## Findings addressed, ordered by impact

1. **Incompatible ERP facts could pass the no-op path.** An ERP observation could
   report a PAID projection alongside UNPAID status, a different reference, or
   incompatible projection amounts/currency. Equality with the requested projection
   could then mark work DONE. [PaymentPolicy](../glue-service/src/main/java/com/example/fde/glue/work/PaymentPolicy.java)
   now validates the existing status/projection tuple before normalization/no-op.
   [SyncPolicyTest](../glue-service/src/test/java/com/example/fde/glue/work/SyncPolicyTest.java)
   exercises incompatible existing facts; adapter writes also validate committed
   response identity, projection, and invoice facts.
2. **An ambiguous local completion/claim response could strand work or count a
   retry twice.** Replaying completion after its transaction committed formerly
   found a non-RUNNING job; a claim whose commit response was lost could leave an
   ownerless RUNNING row until restart. [WorkStore](../glue-service/src/main/java/com/example/fde/glue/work/WorkStore.java)
   now makes completion idempotent by attempt identity, and the single worker
   recovers an orphan before claiming when it owns no pending attempt. A failed
   completion is retained in memory and retried before taking new work. Startup
   recovery remains persisted. The equivalent single-run reconciliation claim can
   resume RUNNING from its durable cursor. Acceptance tests repeat completion,
   simulate lost claim responses, and reopen file-backed databases.
3. **New-work insertion initially captured an unset required timestamp.** The first
   integrated suite received 503 and verified rollback instead of a false 2xx.
   Work initialization now completes before JPA persist. A separate forced DB
   constraint failure continues to assert 503, no inbox row, and safe redelivery.

Other focused improvements: logs report the committed final state after retry
exhaustion; failed reads do not attach a prior observation to the new attempt; pending-work age resets for a newly opened work cycle; URL-safe fixture
identifiers are validated before persistence so they remain addressable by the
contract's path endpoints. These protocol bounds are recorded in the ERP contract.

## Boundaries traced

- [AcceptStripeWebhook](../glue-service/src/main/java/com/example/fde/glue/webhook/application/AcceptStripeWebhook.java)
  delegates signature/envelope decoding to StripeWebhookDecoder, which verifies
  the exact body with the pinned SDK and rejects live/unknown event mode. The
  controller maps typed results and locally scoped errors. WorkStore's
  transaction stores the event payload and work generation before returning.
  The event primary key enforces deduplication; the short gate lock handles
  concurrent first receipt. Reconciliation can create work without an inbox row.
- [SyncWorker](../glue-service/src/main/java/com/example/fde/glue/work/SyncWorker.java)
  owns one synchronized processing loop. Claim and finish are separate short
  transactions; Stripe and ERP reads/writes execute between them without a local
  transaction. A new accepted event advances the requested generation during
  RUNNING; successful/waiting completion schedules a subsequent pass. Explicit
  conflict/exhaustion holds retain outstanding generations until operator requeue.
- [StripeReader](../glue-service/src/main/java/com/example/fde/glue/stripe/StripeReader.java)
  reads the current test PaymentIntent and all refund pages. Refund identity,
  payment association, currency, status, bounds, duplicate pages, and sum overflow
  are checked. Successful totals determine financial state; pending/failed refunds
  never count as completed. The current PaymentIntent's mode covers its refunds,
  whose resource schema does not expose a separate livemode flag.
- [ErpStore](../erp-simulator/src/main/java/com/example/fde/erp/invoice/ErpStore.java)
  commits invoice projection and stored result in one ERP transaction. The
  idempotency primary key and unique PaymentIntent association independently
  protect destination effects. Existing-key replay/conflict precedes stale-version
  and fault checks. The controller's deliberate delay runs after the transaction.
- Retry state, attempt outcome, next eligible time, and exhaustion are persisted.
  SDK network retries are disabled; 412 handling has a separate bounded refresh
  loop. Business/configuration failures are visible holds, not endless retries.
- [ReconciliationStore](../glue-service/src/main/java/com/example/fde/glue/reconciliation/ReconciliationStore.java)
  checkpoints each page and its work requests atomically. Enumeration includes
  every financial status. Batch caps are INCOMPLETE, errors are FAILED, cursor
  continuation survives restart, and only a final page records COMPLETE.

## Evidence and practical limits

The final suite maps to all S01-S12 in [scenarios.md](scenarios.md); the executed
command/result is in [implementation-plan.md](implementation-plan.md). It uses
real application HTTP servers and separate databases, with a local provider stub.
The packaged demo adds separate JVMs and an actual normal stop/start with a
persisted retry. ERP restart checks retain both a financial effect and its stored
idempotency result. Source review supplements these checks; it does not prove
all possible schedules, power-loss durability, or external provider behavior.

No outstanding scoped design violation was found after these fixes. Real Stripe
sandbox execution was skipped because test credentials were absent. Docker
configuration validates, but image builds/volume ownership/container restarts
were not executed because the local daemon is unavailable. IDE and terminal
`spring-boot:run` launches are documented but were not executed; packaged-JAR
startup was executed. The local exercise deliberately has no production API
authorization, multi-instance lease/claim protocol, or automated data retention.
These remain deployment/backlog concerns, not claims of production readiness.

## Clean-code refactor follow-up

The later [refactoring review](refactoring-review.md) documents package boundaries,
managed transaction/lifecycle review, the opaque-cursor correction and scan bound,
and executed regression evidence. Source links above point to the current owners;
the original findings and historical verification remain recorded here.
