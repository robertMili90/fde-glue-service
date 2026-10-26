# Clean-code refactor — 2026-10-23

Executed the existing project's prompt 08 using the implementation-slice and
reliability-review skills. This was a local refactor of the current working tree;
no starter was cloned, unrelated staged changes reset, or repository published.
The baseline source, contracts, tests, and migrations were inspected before edits.
CodeGraph CLI 1.6.2 supplied cross-component exploration before and after moves;
no CodeGraph MCP tool was exposed in this running Air session. Graph results were
complemented by source/configuration/contract inspection. No additional agents or
independent human reviewer were used.

## Responsibility map

The application entry points retain their original base namespaces. The glue
application uses these feature packages:

| Package | Owner / boundary |
| --- | --- |
| webhook.api | StripeWebhookController; typed receipt/problem bodies; scoped HTTP exception mapping |
| webhook.application | AcceptStripeWebhook coordinates decoding and atomic acceptance |
| stripe | StripeWebhookDecoder verifies/decodes signed envelopes; StripeReader retrieves current resources and all refund pages |
| work | Durable receipt/work/attempt persistence, guarded entity transitions, worker, payment policy and observations |
| reconciliation | Scan orchestration, lifecycle adapter, transactional checkpoint store, typed run state/read model |
| erp | Outbound HTTP adapter and explicit invoice/projection/page models |
| config | Boot wiring, validated settings, sandbox credentials and OpenAPI |
| operations | Typed work/attempt/run HTTP reads and enqueue/requeue operations |
| observability | Reconciliation outcome encoding at the metric boundary |

The ERP application separates invoice persistence/projection policy and boundary
models in invoice, its HTTP controller in invoice.api, fault-control/lifecycle
state and demo endpoints in demo, and OpenAPI wiring in config. Each entity has
private fields and meaningful mutation operations; cross-package code receives
read models rather than mutable JPA entities. No interface-per-service, generic
repository framework, abstract base service, or catch-all utility layer was added.
The one sealed InvoiceResponse interface represents the actual invoice/problem
HTTP response alternatives.

Financial policy consumes current PaymentFacts and ERP read models without
Stripe SDK or HTTP request types. External status text remains raw in transport
models so unknown provider values retain their existing explicit conflict path.
Work, attempt, reconciliation, ERP financial and demo-fault states use scoped
enums. Enum columns explicitly retain VARCHAR mappings and original stored names;
all existing migrations are byte-for-byte unchanged.

## Webhook transaction and HTTP behavior

StripeWebhookController receives rawBody/signature and returns a typed receipt.
StripeWebhookDecoder owns SDK signature verification and envelope/reference
validation, including expanded refund PaymentIntent references. AcceptStripeWebhook
calls the managed WorkStore only after verification/decoding. Its short transaction
still records the unique event and work generation together before returning.

The coarse WorkLock gate serializes concurrent first receipts, and received_event's
primary key remains the database uniqueness backstop. There is no independent
inbox/enqueue commit. A rollback returns a retryable 503. The HTTP characterization
suite checks exact existing receipt/problem fields for recorded, duplicate,
ignored, invalid signature/body/reference/mode, missing configuration, live
resource, and unavailable persistence. Unexpected programming failures retain
the server-error path rather than becoming client errors.

## Reconciliation transitions, ownership and recovery

Reconciler owns the synchronized batch orchestration and remote calls.
ReconciliationLifecycle delegates startup recovery, periodic scan requests and
batch scheduling to managed beans. ReconciliationStore owns short transactions,
run lookup/current-owner checks, page validation and checkpointing. Enqueues join
the page transaction; a failed second enqueue rolls back the first and its cursor.
ReconciliationRun encapsulates valid state/timestamp transitions. The HTTP layer
uses ReconciliationRunView, and ReconciliationMetrics alone converts typed scan
state to the existing 1/-1/0 metric values.

The single-worker and shared-gate scheme is retained. WorkRecovery and
ReconciliationLifecycle call public methods on injected transactional stores;
neither relies on transactional self-invocation. WorkLock/DemoControls mutation
helpers require an existing transaction. Remote Stripe/ERP calls and artificial
HTTP response delays remain outside database transactions. A lost work completion
response can still replay by attempt identity; accepted triggers during RUNNING
still advance the next generation. Holds and retry budgets retain their behavior.

## Separately corrected behavior: opaque ERP cursors

Before this change, a page was rejected unless nextCursor equaled its last invoice
ID and both IDs/cursors increased lexicographically. The supplied string-cursor
contract did not promise either property. The simulator happens to use ordered
invoice IDs internally, which hid the client defect.

The [ERP contract](../contracts/erp-api.yaml) now explicitly owns opaque-token
progress rules. The client echoes tokens without interpreting their order or
identity. Validation retains bounded pages, valid/distinct references within a
page, a nonempty bounded advancing token on non-final pages, and atomic enqueue /
checkpoint commits. A configurable total scan budget uses the persisted page
count to bound cycles even across batches/restarts. Its default and operator
recovery action are maintained in [README](../README.md). The ordinary per-batch
cap still yields INCOMPLETE, and the next batch resumes beyond that cap.

Regression evidence includes descending opaque tokens and invoice IDs, a repeated
token, an alternating token cycle across restart with a small scan budget, and
rollback before checkpoint advancement. Missing-first-webhook and old-paid-invoice
late-refund scenarios remain intact. No invoice facts, destination idempotency
fingerprints, endpoint response fields, existing state names, or stored schema
were changed. Signed JSON null retains the original LIVE_OR_UNKNOWN_MODE_EVENT
response; an initial new test incorrectly expected INVALID_WEBHOOK and was fixed
to characterize the existing behavior.

## Executed verification

| Command / check | Actual result |
| --- | --- |
| Baseline ./mvnw -B -ntp verify | Passed 33 tests; no pre-existing failures. |
| ./mvnw -B -ntp -DskipTests test-compile | Passed after package extraction; no test pass claimed for this command. |
| First ./mvnw -B -ntp test after structural extraction | All 33 existing tests passed. |
| First new focused suite | Failed one new characterization assertion: signed JSON null correctly returned LIVE_OR_UNKNOWN_MODE_EVENT. Corrected the expectation without weakening existing assertions. |
| Focused AcceptanceTest, StripeWebhookControllerTest, SyncPolicyTest, ReconciliationPersistenceTest | Passed 32 tests. |
| ./mvnw -B -ntp -DskipTests verify | Build/style checks passed; tests were explicitly skipped. |
| ./mvnw -B -ntp verify | Passed 45 tests, zero failures/errors/skips; both executable artifacts packaged; formatting and Checkstyle passed. ERP 5; glue 40. |
| ./mvnw -B -ntp clean verify | BUILD SUCCESS; 45 tests, zero failures/errors/skips; formatting and Checkstyle passed. Cleanly rebuilt both executable artifacts. |
| python3 scripts/demo-offline.py | Passed separate-process startup, first missing webhook, late refund, and persisted retry across normal JVM restart. Latest 10-invoice burst: 10.225 seconds (earlier refactor smoke: 10.285). Final ERP: 11 invoices, 11 projections, 12 applied updates. |
| Temporary test-source probe with ./mvnw -B -ntp -pl glue-service checkstyle:check | Expected build failure: both regular/static wildcard imports and missing braces were rejected. Probe removed. The initial log-parsing assertion used the wrong diagnostic punctuation; checking the actual diagnostics confirmed the violations. |
| Resource/migration SHA-256 comparison against the pre-refactor snapshot | Every application resource/configuration/migration is byte-for-byte unchanged. |
| Clean executable ZIP inspection | Only the application entry point remains in each root package; no stale classes or simulator library in the glue executable. |
| Documentation relative-link checks and git diff --check | Passed; Git reports the existing Windows-wrapper line-ending normalization warning. |
| CodeGraph CLI status/explore/sync | Initial index current; explored webhook/work/reconciliation/operations before editing and the extracted transaction/lifecycle owners afterwards. Static graph results are supplementary evidence. |

Formatting is pinned to Spotless 2.43.0 / google-java-format 1.24.0. The normal
Maven verify path also runs maven-checkstyle-plugin 3.6.0 / Checkstyle 10.21.4
against main and test Java sources for regular/static wildcard imports, braces,
and one statement per line. Those tools supplement the manual responsibility and
transaction review; they do not prove maintainability or business correctness.

## Remaining limits and next actions

No outstanding scoped defect was found in this self-review after the cursor fix.
The scan budget bounds pathological pagination rather than proving an ERP's
complete enumeration; validate provider contracts again before replacing the
simulator. Static CodeGraph edges cannot establish Spring proxy behavior or
cross-process HTTP semantics; the integration/restart tests supply that evidence.

Real Stripe sandbox execution and Docker image/container startup were not run in
this refactor. The default suite remains independent of their availability.
Power-loss recovery, multiple glue instances, production authorization and data
retention remain outside the agreed scope; use the existing backlog before a
production deployment. Human acceptance and independent review remain outstanding.

## Spring Data repository follow-up

The user subsequently requested replacing direct EntityManager access with Spring
Data JPA. Eight feature-scoped repository interfaces now cover received events,
work items, attempts, the work gate, reconciliation runs, invoices, idempotency
results, and demo controls. They extend Spring Data's Repository and expose only
the operations their callers need; the standard Spring Data implementation supplies
the persistence behavior. There is no custom repository base implementation or
new dependency. Constructors inject repositories into the existing stores/lock
components, and production services contain no direct EntityManager access or JPQL.

WorkStore, ReconciliationStore, ErpStore, and DemoControls remain the transaction
owners. WorkLock joins their transactions. Gate queries use
@Lock(PESSIMISTIC_WRITE) and require an existing transaction, preserving the same
lock ordering. Demo-only bulk deletion also requires the existing reset transaction.
Managed entity operations retain JPA dirty checking; updates are not separate
repository commits. No transaction spans HTTP or response delays. The existing
concurrent-receipt, enqueue-during-work, rollback, completion-replay, retry-budget,
reconciliation and file-restart scenarios continue to exercise these boundaries.

Derived queries replace ordinary identity, state, association, count and ordered
history lookups. Typed enum parameters replace workflow-state string literals.
Explicit JPQL remains only in repository interfaces for eligibility, minimum
pending age, locked gate lookup and demo reset. Bounded List queries use Pageable
while retaining the existing keyset/cursor behavior and deterministic ordering.
The HTTP layer still receives immutable read models, never repository entities.

New entities use assigned IDs, so each insertable entity implements Persistable
with a transient new-instance flag and PostPersist/PostLoad callbacks. This keeps
repository save on the insert path for a new instance even when its ID is already
set; a duplicate cannot silently merge over the existing receipt or stored
idempotency result. The state is not a new database column. Existing loaded
entities retain normal managed updates. Invoice's existing business version remains
unchanged. Invoice and IdempotencyResult are public so public repository proxies
can return them; their fields and domain mutation methods remain encapsulated.
Other entities and repositories remain package-local where their callers allow it.

The first focused run exposed Java proxy visibility errors because those two
public repositories initially returned package-private entity types. All four ERP
HTTP tests failed during fixture creation. Making the entity types accessible
corrected the error; no existing assertion or response contract was changed.
The corrected focused AcceptanceTest/ReconciliationPersistenceTest/ErpApiTest run
passed 28 tests. The six new WorkPersistenceTest/ErpPersistenceTest regressions
also passed. Their deliberate constraint failures are expected test evidence,
not unresolved build errors.

The implementation follows the pinned Spring Data JPA 3.5 guidance for
[assigned identifiers](https://docs.spring.io/spring-data/jpa/reference/3.5/jpa/entity-persistence.html),
[repository locks](https://docs.spring.io/spring-data/jpa/reference/3.5/jpa/locking.html),
and [service transaction boundaries](https://docs.spring.io/spring-data/jpa/reference/3.5/jpa/transactions.html).
No schema, application configuration, ERP contract, dependency version, operation
fingerprint or existing test source changed from the pre-follow-up snapshot.
AGENTS.md now directs future persistence work toward repositories while preserving
service transaction boundaries and assigned-ID semantics.

Final follow-up verification:

| Command / check | Actual result |
| --- | --- |
| Baseline ./mvnw -B -ntp verify | Passed all 45 existing tests. |
| Corrected focused AcceptanceTest, ReconciliationPersistenceTest, ErpApiTest | Passed 28 tests after fixing repository proxy visibility. |
| Focused WorkPersistenceTest, ErpPersistenceTest | Passed all six new regressions. |
| ./mvnw -B -ntp spotless:apply | Applied the pinned Java formatter. |
| ./mvnw -B -ntp clean verify | BUILD SUCCESS; 51 tests (ERP 7, glue 44), zero failures/errors/skips; formatting and Checkstyle passed. |
| python3 scripts/demo-offline.py | Passed first missing webhook, late refund and retry across an actual glue JVM restart. Ten-invoice burst: 10.545 seconds; final ERP: 11 invoices, 11 projections, 12 applied updates. |
| Snapshot comparisons | Existing tests, application resources/configuration, migrations, HTTP contract and Maven declarations are byte-identical to the pre-follow-up snapshot. |
| Source/archive checks | No direct EntityManager/PersistenceContext access remains in production source; five glue and three ERP repositories packaged, with no simulator library in the glue executable. |
| CodeGraph CLI sync/status/explore | Current index: 98 files, 1,499 nodes, 2,550 edges; inspected repository/transaction owners. Static edges do not resolve all Spring proxy calls. |
| Documentation relative links and git diff --check | Passed; Git retains its pre-existing Windows wrapper line-ending warning. |

The single-instance boundary remains. Docker runtime and real Stripe sandbox
checks were not run for this follow-up; offline/default checks require neither.
No independent review or production-readiness claim was added.
