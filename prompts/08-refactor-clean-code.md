# Refactor the existing implementation for clarity and maintainability

Use this prompt in the CURRENT project working directory. The application is
already being developed. Do not clone a new starter, restart from scratch, or
overwrite unrelated or uncommitted work.

Pause feature work and perform the refactoring below across both applications.
The result must be code that a senior engineer can read, explain, and safely
modify during an architecture interview. Complete the changes and verification;
do not stop after an assessment, a plan, or package moves.

Read AGENTS.md, docs/design.md, docs/scenarios.md, the ERP contract, and the actual
source/tests. Use the project slice/review skills when available. Use CodeGraph
for relevant impact questions if available; ordinary source inspection is enough
when it is unavailable. This task introduces no runtime AI or new product scope.

## 1. Establish the baseline

- Inspect the complete production source and relevant tests in glue-service and
  erp-simulator. The reported WebhookApi and ReconciliationStore are examples,
  not the only classes to review.
- Run the existing relevant checks and record actual failures before changing
  code. Distinguish pre-existing failures from regressions introduced here.
- Identify current HTTP contracts, transaction boundaries, lock ownership,
  idempotency rules, persisted state names, and database constraints.
- Preserve public behavior and stored data. Any independently discovered behavior
  defect needs an explicit explanation and focused regression verification.

## 2. Make these quality rules durable in AGENTS.md

Add a concise code-quality section, preserving the existing instructions:

- Organize by feature/responsibility. Keep HTTP handling, application orchestration,
  provider-specific decoding, and persistence responsibilities distinguishable.
  Do not keep every production class in the application root package.
- Use clear, specific names: stripeEventId, paymentIntentId, rawBody,
  reconciliationRun, and nextCursor rather than payment, raw, run, or generic
  abbreviations where their meaning is not immediately clear. Conventional short
  names are fine in small, obvious contexts.
- Use explicit imports. No wildcard imports or fully qualified type names used
  as a substitute for normal imports, except a genuine naming collision.
- Use braces for control-flow blocks, one statement per line, and consistent
  formatting. No compressed constructors, multi-assignment lines, or nested
  ternaries for business decisions. A simple ternary is fine when clearer.
- Give classes one cohesive responsibility. Extract a method when its name makes
  an important step clearer; avoid arbitrary method/class size limits and trivial
  wrappers that only increase navigation.
- Prefer explicit, typed request/response/read models at boundaries. Do not use
  ResponseEntity<?> and ad hoc Map bodies for a small, known response shape.
  Use records for appropriate immutable DTOs; do not use persistence entities as
  HTTP DTOs.
- Represent finite business/workflow states with appropriately scoped enums,
  preserving persisted names and JSON compatibility. Avoid repeated state-string
  comparisons. Keep reason codes stable and named; do not create one universal
  enum for every unrelated error in the system.
- Make valid state transitions explicit and keep timestamp/reason updates
  consistent. Encapsulate entity mutation behind meaningful operations where
  it clarifies an invariant; avoid public mutable fields throughout the code.
- Use constructor injection for application dependencies. Keep framework-managed
  persistence behavior correct; never create an EntityManager manually as a
  cosmetic replacement for managed injection.
- Avoid interface-per-class, abstract base services, generic frameworks,
  unnecessary mappers, and a catch-all common/utils package. Add a boundary or
  abstraction for an actual responsibility, external dependency, or variation.
- Use comments to explain a non-obvious invariant, trade-off, or transaction/lock
  decision. Do not narrate every line or generate boilerplate Javadoc.
- Preserve useful failure causes and identifiers in logs, without raw webhook
  payloads or credentials. Do not swallow failures or log them redundantly at
  every layer.
- Keep tests as readable as production code, with behavior-focused names and
  understandable setup/assertions.

Reuse an existing formatter/linter. If absent, add lightweight, pinned formatting
and import checks to the normal Maven verification path. Check their actual
coverage: a formatter alone may not reject wildcard imports. Do not introduce
an external quality platform or make default checks depend on network services.

## 3. Organize packages with the smallest useful structure

The following responsibilities are a guide, not a mandatory class inventory.
Retain the current base namespace unless changing it has a concrete benefit.

| Relative package | Responsibility |
| --- | --- |
| webhook.api | Stripe HTTP endpoint, typed HTTP response, relevant exception mapping |
| webhook.application | Accept/ignore/record a verified notification through the durable work path |
| stripe | Stripe SDK/JSON/signature decoding and current-resource adapter |
| work | Durable acceptance, scheduling, attempts, work states, and local persistence |
| reconciliation | Scan orchestration, continuation, run state, queries, and persistence |
| erp | Outbound ERP HTTP adapter and its transport models |
| config | Application wiring and configuration |
| observability | Metric/log adapters where there is enough responsibility to justify them |

Within larger features, use api/application/persistence/domain subpackages when
they help explain boundaries. Do not create empty layers. Apply the same
principles to invoice/projection/fault-control code in erp-simulator.

Package moves must leave a sensible dependency direction: transport and
provider-specific details should not leak into business policy or state models.
Keep DTOs near their owning boundary and share only genuinely common concepts.
Make cross-package visibility intentional rather than making every field public
to get the build working.

## 4. Refactor the webhook flow as one complete slice

WebhookApi currently mixes transport, signature verification, JSON traversal,
identifier extraction, validation, persistence, and error translation.

Create a clear flow with a thin StripeWebhookController and a named acceptance
use case. A cohesive StripeWebhookDecoder can own verification plus envelope/
resource decoding and return a typed result. Separate verification and decoding
further only if the actual code justifies it.

- The controller receives the raw request and signature, calls the use case,
  and maps the result to a typed response. It does not traverse JsonNode trees,
  invoke Stripe signature APIs, or manipulate repositories.
- Provider-specific parsing stays in the Stripe adapter. Decode payment-intent
  and refund references in named, readable steps instead of a nested ternary.
  Keep signature verification against the exact raw body, before acceptance.
- Verification/decoding happen before the short persistence transaction. Preserve
  the single atomic transaction that records a unique event and requests work.
  Do not split inbox acceptance and work enqueue into independently committed
  calls during extraction.
- Preserve ignored, duplicate, recorded, invalid-signature, test/live-mode,
  missing-configuration, and unavailable-persistence behavior and response fields.
- Use narrow, deliberate HTTP exception mapping, locally or in scoped advice.
  Do not turn every RuntimeException into a client error or expose internals.
- Keep the valid-duplicate path safe under concurrent receipt. Inspect the
  database uniqueness/rollback behavior instead of trusting a pre-insert lookup.

## 5. Refactor reconciliation without weakening its guarantees

ReconciliationStore currently combines locking, run lifecycle, page validation,
work enqueue, queries, startup recovery, view mapping, and metric encoding.

- Separate orchestration and lifecycle adapters from transactional persistence
  operations. Keep the number of classes proportional to the responsibilities.
- Replace repeated run-state strings with a typed ReconciliationState and
  readable, valid transitions. Preserve existing database state values.
- Give run lookup, locking, page validation, and state updates clear names.
  Handle impossible/missing state deliberately; do not scatter incidental
  NullPointerExceptions or broadly catch them as normal business outcomes.
- Put a reconciliation read model in its owning application/query boundary,
  rather than making the HTTP API depend on a nested persistence-store record.
- Encode COMPLETE/FAILED/in-progress into metric numbers at the metric boundary;
  avoid using a double as the application representation of a scan outcome.
- Preserve the transaction that enqueues a page's references and advances its
  checkpoint. A refactor must not commit the cursor after losing its work, or
  commit work while claiming an unprocessed page has completed.
- Understand and retain the current locking/ownership scheme unless a concrete
  defect is established. Do not remove coarse locks simply to make code shorter.
- Ensure startup and scheduler adapters invoke the intended managed transaction
  boundary. Check actual proxy/self-invocation behavior after extraction.
- Keep all remote HTTP calls outside database transactions.

Review one possible contract mismatch explicitly: the reported implementation
compares cursor strings lexicographically and requires nextCursor to equal the
last invoice ID. The supplied ERP contract specifies a string cursor, without
those guarantees. Inspect the actual repository contract and both implementations.
Do not silently invent cursor semantics or remove safety checks. Use documented
progress rules and bounded scans; make any correction a separately explained,
verified behavior change. Preserve continuation beyond a batch cap and recovery
of old linked invoices, including old paid invoices with later refunds.

## 6. Preserve and verify the observable behavior

Keep existing useful tests and fixtures. Add focused characterization/regression
checks where extraction affects an unverified boundary. Verify, at minimum, the
relevant existing scenarios for:

- Signed/invalid/unsupported/duplicate webhook receipt and persistence failure.
- Atomic durable acceptance, concurrent deduplication, and a trigger during work.
- ERP idempotency after timeout-after-commit, including replay before version and
  synthetic fault checks.
- Bounded retries, held/exhausted work, restart recovery, and no lost wake-ups.
- Full refund/current-state handling and visible financial conflicts.
- Paginated scan continuation, incomplete/failed outcomes, and atomic checkpoints.

Do not fabricate passes, delete tests to make the refactor green, reset local
data, silently change API responses, or add real Stripe access to the default
suite. Do not add tests that merely restate getters or private method structure.

Run focused checks while changing one slice, then ./mvnw verify and the relevant
documented local smoke path. Inspect the final diff for accidental behavior,
configuration, migration, API, or dependency changes. Formatting is a check;
review the resulting design and readability separately.

## 7. Finish with concrete evidence

Update the actual AI-use record with this review/refactor and real corrections.
Report:

1. The package/responsibility structure and why it is easy to navigate.
2. What changed in the webhook and reconciliation flows.
3. Any behavior defects fixed separately from structural changes.
4. Commands actually executed, results, and skipped checks.
5. Remaining material issues, with locations and next actions.

Do not push or publish the repository. Complete the local refactor and verification
within the agreed project scope before resuming feature development.
