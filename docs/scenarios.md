# Observable acceptance scenarios

These define behavior independently of implementation classes. Link implemented
tests and demo commands here as they become available; do not label them passed
until actually run. Keep the automated suite independent of Stripe credentials.
Executed suite counts, measured coverage and reproduction commands are in the
[testing report](testing-report.md).

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
| S13 | Observe each supported PaymentIntent status and an unknown status | Apply the explicit [status decisions](design.md#4-financial-normalization); cancellation stops periodic refresh across restart and reconciliation, waiting states can converge, unsupported states are held without financial writes. |

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

## Implemented verification mapping — 2026-10-23

Run `./mvnw verify`. The real ERP and glue application servers use separate
stores; Stripe is an HTTP stub. Scheduler time is controlled by an injected Clock.
The [acceptance suite](../glue-service/src/test/java/com/example/fde/glue/AcceptanceTest.java)
contains these named behaviors:

| Scenario | Test / evidence |
| --- | --- |
| S01 | signedConcurrentDuplicatesAndEquivalentEventsHaveOneFinancialEffect; persistenceFailureRollsBackEventAndReturnsRetryableResponse |
| S02 | signedConcurrentDuplicatesAndEquivalentEventsHaveOneFinancialEffect (six concurrent deliveries; one persisted event/generation/effect) |
| S03 | signedConcurrentDuplicatesAndEquivalentEventsHaveOneFinancialEffect (distinct accepted event, identical operation key, one update) |
| S04 | transientFailurePersistsBackoffAndEventuallyConverges; exhaustedRetriesStayHeldUntilExplicitRequeueAndStaleVersionRevalidates |
| S05 | timeoutAfterCommitConvergesWithoutASecondProjection; ERP commitsBeforeDelayedResponseAndReplaysOneProjection |
| S06 | restartRecoversRunningWorkAndRetainsNotifications; retryScheduleAndBudgetSurviveRestart; packaged demo's persisted-retry JVM restart |
| S07 | currentRefundPagesWinOverOldPaymentNotification (two refund pages; PaymentIntent remains succeeded) |
| S08 | missingFirstWebhookAndLaterRefundOnPaidInvoiceConvergeAcrossBatchesAndRestart (no received-event rows) |
| S09 | conflictsAreHeldAndPendingFailedRefundsDoNotCountAsSuccessful; incompatibleAmountCurrencyAndMissingAssociationAreVisibleWithoutWrites; refundNotificationsRefreshCurrentFactsAndFailedRefundEventuallyLeavesWaiting; SyncPolicyTest |
| S10 | badSignatureAndLiveEventsOrResourcesProduceNoFinancialEffect; SandboxConfigurationTest |
| S11 | distinctTriggerDuringRemoteCallPreservesAnotherPass (a distinct event commits while provider HTTP is blocked) |
| S12 | missingFirstWebhookAndLaterRefundOnPaidInvoiceConvergeAcrossBatchesAndRestart (one-page batches; page failure remains incomplete; retained cursor across restart; later refund beyond first page) |
| S13 | recoverablePaymentStatesWaitWithoutWritingAndCanConverge; canceledPaymentStopsPollingAndReconciliationAcrossRestartButRetainsNewEvents; unsupportedPaymentStatesAreHeldWithFactsWithoutPollingOrWriting; WorkPersistenceTest.cancellationCompletionPreservesRequestsReceivedDuringWork |

The [ERP HTTP suite](../erp-simulator/src/test/java/com/example/fde/erp/ErpApiTest.java)
also verifies replay before stale If-Match/faults, changed-payload rejection, a
single projection after a new equivalent key, stale-version behavior, no refunded
regression, integral JSON validation, unique associations, and status-independent
pagination. Its [file restart test](../erp-simulator/src/test/java/com/example/fde/erp/BootstrapTest.java)
retains a paid invoice and its idempotency result together.

Additional cases cover immediate authentication failure without attaching stale facts to its attempt, SDK 429 Retry-After without SDK retries, idempotent local
completion after an ambiguous commit, recovery of an unowned RUNNING claim, and
wide integral minor units without assuming currency decimals.

The offline [packaged demo](../scripts/demo-offline.py) verifies actual scheduled
work, a 10-invoice burst, first-missing-webhook recovery, a late refund, and normal
JVM restart. These are offline passes, not evidence of a real Stripe sandbox run,
Docker startup, crash/power-loss recovery, or multi-instance safety. See the
[milestone log](implementation-plan.md) for final executed results and skipped checks.

## Refactoring characterization and regressions

The same S01-S12 tests are retained. [SyncPolicyTest](../glue-service/src/test/java/com/example/fde/glue/work/SyncPolicyTest.java)
now exercises the policy directly from its feature package. The
[webhook HTTP characterization suite](../glue-service/src/test/java/com/example/fde/glue/webhook/api/StripeWebhookControllerTest.java)
preserves exact receipt/problem fields, exact signed body forwarding, expanded
refund references, unsupported-event handling, configuration/mode/reference
rejections, retryable persistence failure, and narrow exception translation.

AcceptanceTest additionally covers opaque cursors with descending invoice IDs,
non-advancing cursors, a cyclic scan bounded across restart, page checkpoint
rollback on DB failure, reconciliation RUNNING recovery, and outcome metric
encoding. [ReconciliationPersistenceTest](../glue-service/src/test/java/com/example/fde/glue/reconciliation/ReconciliationPersistenceTest.java)
forces the second enqueue in a two-reference page to fail, then proves neither
reference nor the checkpoint commits and the same page can be replayed safely.
Exact executed results are in the [refactoring review](refactoring-review.md).

The Spring Data repository follow-up adds focused persistence regressions:
[WorkPersistenceTest](../glue-service/src/test/java/com/example/fde/glue/work/WorkPersistenceTest.java)
checks assigned event identity protection, due-work ordering, limited keyset/history
reads, and pending-age/state metrics.
[ErpPersistenceTest](../erp-simulator/src/test/java/com/example/fde/erp/invoice/ErpPersistenceTest.java)
checks immutable operation-key identity and rollback of the invoice projection,
idempotency result, consumed fault, and update counter when the idempotency insert
fails; retry/replay then succeeds safely. Existing acceptance assertions remain
unchanged. Executed results are in the
[persistence follow-up](refactoring-review.md#spring-data-repository-follow-up).

## Review follow-up regressions — 2026-10-24

AcceptanceTest additionally exercises ERP responses that send headers and one JSON
byte, then remain blocked. GET lookup and PUT-after-commit both release the worker,
persist a retry, allow another payment to finish, and converge without a duplicate
projection/update. Enumeration-body stalls fail visibly without advancing the
checkpoint and resume through the existing reconciliation request path.

The ERP and glue HTTP suites assert generated OpenAPI response codes/schemas,
queued 202 responses, the webhook object body, and consistent safe field errors
for malformed JSON, validation, query conversion and missing headers. Existing
financial/idempotency/restart assertions are retained. Webhook characterization
expectations deliberately add the newly documented problem message.

FailureDiagnosticsTest captures worker/reconciliation logs to assert cause types,
source locations and SQL codes survive without private exception messages. It also
checks retained worker observations and preserves original reconciliation failure
evidence when recording that failure encounters a second persistence exception.
Executed results are recorded in the implementation plan.
