# AI development record

## Existing preparation

An assistant helped turn the assignment and the candidate's decisions into this
planning starter, project-local skills, prompts, and a draft simulator contract.
The candidate chose Stripe plus a fictional ERP, Spring Boot, a small persistent
local setup, and AI tooling for development rather than runtime product features.

No application behavior is implemented or verified by the existence of this file.
The preparation date is 2026-10-22.

A separate agent performed a read-only design/contract review during preparation.
It found no blocking contradiction and identified two useful clarifications:
continuation after a reconciliation batch cap, and idempotent replay before fault
injection. Both were incorporated. Skill metadata, local links, contract references,
and JSON examples were also checked. These are preparation checks, not application
tests, dependency compatibility results, or a real Stripe sandbox demonstration.

## Record actual implementation work

Append a small number of useful examples as work occurs:

| Task / date | Context or prompt | AI contribution | Human decision/correction | Actual verification |
| --- | --- | --- | --- | --- |
| Bootstrap / 2026-10-23 | “initialialize repo” with the supplied repository instructions | Codex inspected the clean starter, read the bootstrap skill/design/contract, verified dependency choices against official documentation and Maven Central, and added the two-app build, migrations, configuration, Compose, and bootstrap tests. | Applied the agreed Java 21, two-process, H2 scope; no further human correction recorded. | Final ./mvnw -B -ntp verify passed 9 tests; two packaged-JAR processes passed HTTP/restart smoke; Compose config validated. Container runtime and Stripe sandbox checks were not run. See implementation-plan.md. |

Examples worth preserving: a proposed design that was simplified, a failure
discovered by testing, a corrected Stripe assumption, or impact analysis that
identified a second caller. Keep a short redacted excerpt or a commit reference
when useful. Do not manufacture an error/correction story for the interview.

## CodeGraph and review

Record where CodeGraph was actually used and where direct search was sufficient.
Describe the supported scope and limitations of static code analysis.
Claim time/token improvements only if measured comparably; otherwise state the
qualitative role in navigation and context gathering.

Record human ownership of assumptions, financial rules, and final acceptance.
An additional agent's review is evidence of a review step, not proof of correctness.

### Bootstrap tooling

CodeGraph was not exposed as an MCP tool and its CLI was not installed on PATH.
Direct source/configuration reads and targeted searches were used. No additional
agents were used for implementation or review. This bootstrap does not establish
any of the payment acceptance scenarios or a real Stripe sandbox demonstration.

The first executable suite found an incorrect AI-written Flyway test assumption:
automatically created history includes a table-creation marker. The test was
corrected to assert unchanged history across restart plus one successful V1,
while retaining the committed-data and HTTP assertions. The subsequent full
suite passed. No acceptance criteria were removed to obtain that result.

### End-to-end implementation — 2026-10-23

User requested START_HERE.md and the main build prompt. Codex implemented M2-M6
using the slice skill, existing contracts, and offline tests. JPA was added as
specified by that prompt; bootstrap dependency pins were preserved. CodeGraph
remained unavailable, so targeted source/configuration reads were used. No
additional agents were used. The human-selected financial scope was retained;
no further manual correction or final human acceptance is claimed here.

The first integrated suite exposed an AI-written JPA initialization defect: the
work row captured a null timestamp. The fix initialized fields before persist;
the suite still proves failed persistence returns non-2xx and rolls back receipt.
The review found an incompatible ERP status/projection tuple could be marked
complete by the no-op path. A policy guard and regression tests now hold it.
Development tracing also prompted idempotent local attempt completion and
unowned-claim recovery for ambiguous local commit responses.

Actual final verification: ./mvnw -B -ntp clean verify passed 33 tests; both
applications were exercised together over HTTP with an official SDK reader
against a local Stripe stub. The packaged demo passed actual scheduled processing
and normal JVM restart, with its latest 10-invoice burst measured at 10.225 seconds.
A self-review using fde-review-reliability is recorded in reliability-review.md;
it is supplementary evidence, not an independent review or proof of correctness.

No real Stripe sandbox call was made because test credentials were absent. Docker
configuration validated, but the daemon was unavailable for image/runtime checks.
The fixture tool's syntax/help and no-network input/credential rejection paths
were checked. The detailed executed-command record and remaining limits are in
implementation-plan.md. No runtime AI or custom MCP service was introduced.

### CodeGraph installation — 2026-10-23

At the user's explicit request, Codex installed the official npm CLI at version
1.6.2, configured only this project's Codex MCP server, and initialized its local
index. The OpenAI Docs skill was used to check project-scoped MCP configuration
against official documentation. No additional agents were used and no global
agent settings were changed.

Actual checks: version, installer/init, current index status (49 files, 672 nodes,
1,126 edges), and a WebhookApi symbol/route query passed. A separate stdio MCP
client completed initialize, tools/list, and a codegraph_explore call covering
WebhookApi.receive and WorkStore.accept. This was installation verification;
CodeGraph was unavailable during the earlier application implementation. The
running Air agent still needs to reload its tools before MCP availability can be
claimed for that session. Application source was unchanged and Maven was not
rerun for this tooling-only change. Setup/evidence are in agent-tooling.md.

### Clean-code refactor — 2026-10-23

The user explicitly requested prompt 08 against the existing working tree. Codex
used the implementation-slice/reliability-review skills, inspected both complete
production trees and their relevant tests/contracts, and established a passing
33-test baseline before edits. CodeGraph CLI status/exploration/sync was used for
cross-component impact; MCP was not exposed in this running session. No additional
agents were used. Original financial/sandbox scope and uncommitted work were kept.

AI changes separated feature packages, extracted webhook decoding/acceptance,
encapsulated state transitions, typed HTTP/read models and workflow states, and
separated reconciliation orchestration/lifecycle/metrics from transactional work.
A separately explained defect fix removed undocumented ordered-invoice cursor
assumptions and added documented opaque progress plus a persisted scan budget.
No existing migration or operation fingerprint was changed.

After extraction, the 33 original tests still passed. The first new focused run
failed an AI-written characterization expectation for signed JSON null; the
existing response was LIVE_OR_UNKNOWN_MODE_EVENT, not INVALID_WEBHOOK. The new
assertion was corrected to preserve that behavior; no old assertion was removed.
The corrected focused suite passed 32 tests and full verify passed 45, including
format/import/braces checks. A temporary style probe was correctly rejected and
removed; its initial result parser expected different Checkstyle punctuation and
was corrected against the actual diagnostic output. The separate-JVM offline
demo passed (10-invoice burst 10.285 seconds) including persisted retry recovery.
The final clean verify also passed all 45 tests and style gates. Its clean
executable archives contain only the entry points in the root packages and no
simulator classes in glue. A second packaged smoke passed with a 10.225-second
burst. Resource/configuration/migration bytes match the pre-refactor snapshot.

The [refactoring review](refactoring-review.md) records boundaries, the cursor
correction, commands, results, and remaining limitations. No Docker runtime or
real Stripe check, independent review, or final human acceptance is claimed.

### Spring Data repository refactor — 2026-10-23

The user requested replacing direct EntityManager access with Spring Data while
retaining clean code. Codex read the implementation/reliability skills, design,
scenarios, ERP contract, affected sources/tests, and official Spring Data docs.
CodeGraph CLI confirmed a current index and supplied persistence impact exploration;
MCP tools are not exposed in this session. No additional agents are used.
Baseline `./mvnw -B -ntp verify` passed all 45 tests. The refactor adds focused
repositories, constructor injection, repository lock queries, and assigned-ID
new-entity detection while preserving the existing service transaction owners.
The first focused ERP run failed four HTTP fixture checks because public
repository proxies returned package-private entity types. Codex corrected type
visibility; existing assertions and response contracts were unchanged. The corrected
focused integration run passed 28 tests, and six new persistence regressions passed.
Final `./mvnw -B -ntp clean verify` passed all 51 tests (ERP 7, glue 44), zero
failures/errors/skips, plus pinned formatting and Checkstyle checks.
`python3 scripts/demo-offline.py` passed missing-first-webhook, late-refund and
actual JVM-restart retry recovery; the ten-invoice burst took 10.545 seconds.

Snapshot comparison confirmed every pre-existing test, resource/configuration,
migration, HTTP contract and Maven declaration is unchanged. Source/archive checks
confirmed all eight repositories are packaged and direct EntityManager access is
removed from production sources. CodeGraph was synchronized and its current status
and repository exploration checked. Documentation links and git diff --check passed.
The [persistence follow-up](refactoring-review.md#spring-data-repository-follow-up)
records choices, failure/retry evidence and limits. No Docker runtime, real Stripe
sandbox execution, independent agent review or publish/push was performed.

### Review fixes — 2026-10-24

The user requested fixes for the full ERP response timeout, generated OpenAPI,
failure diagnostics and consistent HTTP errors, with tests and full regressions.
Codex used fde-implement-slice and inspected the existing design, scenarios,
contracts and affected source/tests. No sub-agents were used for these fixes.
CodeGraph was not exposed and its CLI was unavailable; source/configuration reads
and targeted searches supplied the call paths.

Changes add a complete-response deadline with cancellation, explicit API metadata,
typed safe field errors, and bounded structural diagnostics that omit exception
messages. New tests cover post-header stalls, queue progress, commit/retry safety,
contract/status accuracy and troubleshooting evidence without private values.
The first HTTP test exposed local generic exception handlers intercepting a query
conversion cause; moving request handlers into advice preserved the field detail.
New test compilation mistakes were corrected without removing assertions.
The first completed focused run passed 44 tests. Final
`./mvnw -B -ntp clean verify` passed 62 tests (ERP 9, glue 53), zero
failures/errors/skips, and both style gates. `python3 scripts/demo-offline.py`
passed the missing-first-webhook, late-refund and actual JVM-restart retry checks;
its ten-invoice burst took 10.188 seconds. Documentation file links and
`git diff --check` passed, with the existing Windows-wrapper line-ending warning.
Docker runtime and real Stripe sandbox checks were not run. No publish/push or
user-global configuration change was performed.

### PaymentIntent status policy correction — 2026-10-24

The user identified the original design rule as too broad: all non-succeeded
PaymentIntents, including terminal cancellations, entered provider polling.
Codex used fde-debug-sync, read the design/scenarios/ERP contract and affected
sources, and checked the official Stripe lifecycle, object and cancel docs.
CodeGraph CLI was available; status exposed a stale index, exploration was
complemented with direct source reads, and sync refreshed 20 changed files.
No sub-agents were used. New offline acceptance cases cover recoverable statuses,
terminal cancellation across restart/reconciliation, and held unsupported statuses.
The pre-fix focused run executed eight cases: four waiting cases passed and
cancellation plus three unsupported cases failed as expected (all observed
WAITING_PROVIDER). Codex added explicit status policy, DONE/PAYMENT_CANCELED
completion, and durable reconciliation suppression for observed cancellations.
The requested-generation mechanism still preserves new webhook requests.
`./mvnw -B -ntp spotless:apply` passed. The focused acceptance/persistence run
passed 13 tests. Final `./mvnw -B -ntp verify` passed 71 tests (ERP 9, glue 62),
zero failures/errors/skips, plus Spotless and Checkstyle. Nine new cases cover
status decisions and preservation of notifications during cancellation processing.
CodeGraph sync/status confirmed the final Java index is current. `git diff --check`
passed with the existing Windows-wrapper line-ending warning. The packaged demo,
Docker runtime and real Stripe sandbox were not rerun. No publish/push or
global configuration changes were performed.

### Coverage measurement — 2026-10-24

The user asked for current code coverage. No coverage plugin/configuration or
existing report was present. Codex consulted the official JaCoCo agent/CLI docs,
then ran `./mvnw -B -ntp org.jacoco:jacoco-maven-plugin:0.8.12:prepare-agent test
org.jacoco:jacoco-maven-plugin:0.8.12:report`: all 71 tests passed, no failures,
errors or skips. No POM or production/test source change was made.
The JaCoCo 0.8.12 CLI was fetched through Maven dependency:get and used to report
both execution files together against production class/source directories only.
This includes ERP execution inside glue acceptance tests rather than averaging
module percentages or omitting cross-module execution. No report exclusions were
configured. Generated HTML/XML reports are under ignored build output at
`target/site/jacoco-combined/{all,erp-simulator,glue-service}`.
Combined coverage: 1,220/1,347 lines (90.57%), 406/545 branches (74.50%),
5,566/6,079 instructions (91.56%). ERP: lines 237/258 (91.86%), branches 67/86
(77.91%). Glue: lines 983/1,089 (90.27%), branches 339/459 (73.86%).
SyncEngine: lines 93.18%, branches 94.44%; PaymentPolicy: lines 84.21%,
branches 68.66%. These are one-off measurements; default verify still has no
coverage report or threshold. No additional tests were added, no source changes
or real Stripe calls were made, and no sub-agents were used.

### Repository testing report — 2026-10-24

The user requested an English testing report in the repository, with Romanian
reserved for conversation. Codex added [the testing report](testing-report.md)
and linked it from README, the acceptance scenarios and the implementation plan.
Its 11-suite inventory and coverage tables were checked against the existing
Surefire and JaCoCo XML, not inferred from test source counts. The report records
actual verification results, the instrumented run, cross-module aggregation,
remaining coverage gaps and checks not executed. It links authoritative business
rules/scenario mappings instead of duplicating their specifications.

The documented Maven dependency:copy command passed, and all three JaCoCo CLI
report commands were executed against the existing execution data. Regenerated
counters match the report: 71 tests, 90.57% line coverage, 74.50% branch coverage.
Relative file links and heading anchors passed, as did git diff --check (the
existing Windows-wrapper line-ending warning remains). Application tests were
not rerun for these documentation-only edits; the report explicitly identifies
which earlier runs supply its evidence. No Java, test, POM or build configuration
was changed. No sub-agents, real Stripe calls, publish/push or global setting
changes were involved.

### Public repository preparation — 2026-10-26

The user asked about publishing the project to a public GitHub repository.
Codex inspected Git status, the existing local-bundle remote, ignore rules,
tracked configuration and recruiter context. A targeted pattern scan checked
146 current files and 273 historical blobs for Stripe/GitHub/AWS credentials
and private-key headers; it found no matching credentials. This limited scan
does not establish the absence of every possible secret. The original assignment
PDF and recruiter clarifications are tracked and require a publication-scope
decision, including history if they are excluded. GitHub CLI was unavailable.
No repository was created, no commit or push was made, and no application tests
were rerun for this preparation. No sub-agents or global configuration changes
were involved.

### Commit preparation — 2026-10-26

The user explicitly requested one commit containing all uncommitted changes
and chose to retain the original assignment PDF and recruiter clarifications.
That resolves the publication-scope question above; both source files remain
tracked. Codex inspected the staged and working-tree changes and prepared the
complete implementation, tests, documentation and tooling for a single commit.
Actual verification: `./mvnw -B -ntp verify` passed all 71 tests with no failures,
errors or skips, including Spotless and Checkstyle. Both staged and working-tree
`git diff --check` passed with the existing Windows-wrapper line-ending warning.
No application behavior was changed during commit preparation. No GitHub
repository creation, push, sub-agents or global configuration changes occurred.

### Public GitHub repository setup — 2026-10-26

The user requested public publication with the original assignment PDF and
recruiter clarifications included, explicitly outside the FintechOS organization.
Codex completed the browser-based GitHub CLI authentication flow and verified
that the authenticated account `robertMili90` has type `User`. It created
[robertMili90/fde-glue-service](https://github.com/robertMili90/fde-glue-service)
with that explicit personal owner and verified `isPrivate: false` through GitHub.
The local bundle remote was preserved as `starter`; `origin` now targets the
personal GitHub repository. The implementation commit is `081ab88`.
No application source or test behavior changed during publication setup.
The earlier commit-preparation verification passed 71 tests and style checks;
application tests were not rerun for this documentation and Git configuration
step. No sub-agents or global agent configuration changes were involved.

### Postman localhost demo — 2026-10-26

The user requested a localhost Postman collection for demonstrations. Codex read
the application configuration, HTTP controllers, ERP contract, scenario mapping
and existing offline/sandbox fixtures, then added 40 requests, a localhost
environment with an empty signing secret, and an English walkthrough linked from
README. Generated IDs/ETags support repeatable ERP-only runs; signed sandbox
notifications preserve duplicate event IDs and sign exact resolved request bytes.
The manual ERP reset skips execution unless explicitly enabled locally.

Actual verification used `npm exec --yes --package=newman@6.2.1 -- newman` through
a temporary Python harness, starting both existing executable JARs on temporary
ports with isolated H2 databases and reusing the existing offline Stripe stub.
An initial run exposed a collision with the sandbox's built-in CryptoJS identifier;
the signing script now uses a distinct local name. A reset-guard harness assertion
was corrected to expect a successfully skipped request rather than a script error.
The final run passed 35 HTTP requests/54 assertions, checked recorded/duplicate
receipts and current-fact PAID/REFUNDED convergence, and observed one projection,
two updates and no unapproved reset. Temporary processes/data were cleaned up.
JSON parsing, local documentation links and `git diff --check` passed. No real
Stripe, desktop import, optional mutation-control run or Maven verification was
performed for these artifact/documentation-only edits. The unrelated existing
Java working-tree edit was preserved. No sub-agents, push/publish or global agent
configuration changes were involved.

### Swagger endpoint documentation — 2026-10-26

The user requested explicit endpoint documentation and request/response payload
examples in Swagger. Codex used the local fde-implement-slice skill, read the
design/scenarios, ERP HTTP contract, controllers, response models, persistence
behavior and existing contract tests. CodeGraph tools/CLI were unavailable in
this session; targeted source/configuration reads were used instead.

Both generated Swagger pages now include a walkthrough, endpoint descriptions,
parameter/header examples, named success/error payloads, pagination guidance,
bodyless-request/204 semantics, webhook signing instructions, asynchronous
completion guidance and local fault/reset behavior. Examples are owned by each
application's OpenAPI configuration and referenced by endpoint annotations.
The existing HTTP contract tests now execute generated ERP example payloads
through create/paid/replay/refunded and sign/deliver both webhook examples.
No synchronization rules, dependencies or runtime endpoint behavior changed.

Initial formatting exposed annotation parentheses mistakes, which were fixed.
Initial generated-document assertions exposed unnamed examples being serialized
as a singular example; all response examples now have explicit names. The focused
ERP contract/example test passed. Browser inspection then exposed Springdoc
stripping the quotes from the If-Match parameter example. Codex corrected its
encoded annotation value and extended the test to send the generated header
unchanged, comparing it with the created invoice's quoted ETag.

Final `./mvnw -B -ntp spotless:apply` and `./mvnw -B -ntp verify` passed: 71 tests,
zero failures/errors/skips, plus Spotless and Checkstyle. The ERP Swagger UI was
inspected through Air using the packaged executable with an isolated in-memory
database: walkthrough, descriptions and named request/response selectors rendered,
with no captured console errors. The rebuilt packaged document retained the
quoted If-Match example. The glue UI was also inspected with scheduled work disabled
and an isolated in-memory database; the workflow and endpoint groups rendered.
The webhook operation did not expand during the final browser interaction, so
its examples were verified through the HTTP suite rather than browser selectors.
Temporary preview processes were stopped.
No real Stripe/Docker checks were run. No sub-agents,
publication or global agent configuration changes were involved.

### Swagger documentation extraction — 2026-10-26

The user requested readable controllers with Swagger documentation elsewhere.
Codex continued with the fde-implement-slice workflow, inspected the existing
annotations/configuration and HTTP contract, and captured the generated OpenAPI
from both pre-refactor executable JARs using temporary isolated databases and
ports. CodeGraph CLI was available this time: status initially showed stale
changes, so Codex synchronized and verified the index, and complemented graph
exploration with direct source/configuration reads.

Descriptions, parameters, request/response examples, status codes and Swagger
walkthroughs moved into module-local YAML resources. Both OpenAPI configurations
load these resources and apply documentation only to discovered endpoints. DTO
schemas remain generated from Java; concrete polymorphic/error response models
are explicitly registered. A stale documented path/method is rejected instead
of silently advertised. Controllers retain Spring HTTP/validation annotations
and behavior, and the two Java example-constant holders were removed.

The initial two focused HTTP contract/example tests passed with their existing
assertions intact. Packaged-document comparison then exposed Springdoc pruning
hidden response models before customization. Registration moved after applying
the documentation and uses the generated document's OpenAPI dialect. Reference
checks added to both existing HTTP tests also caught a missing nested
FieldViolation schema; registration now includes referenced models with readAll.
No assertions were removed or weakened. Final `./mvnw -B -ntp spotless:apply`
and `./mvnw -B -ntp verify` passed all 71 tests with zero failures/errors/skips,
plus Spotless and Checkstyle. The two focused contract/example tests also passed.
A temporary Python harness ran both rebuilt executable JARs against isolated
in-memory databases and compared their generated OpenAPI with the pre-refactor
snapshots. Paths, methods, operation metadata, parameters, response codes,
payload examples and every pre-existing DTO schema matched, normalizing only
prose's trailing newlines. ERP additionally infers an unused InvoiceResponse
interface schema; endpoint response schemas still reference the concrete types.
All temporary JVMs stopped; no existing application processes were changed.
CodeGraph was resynchronized and reported a current index; git diff --check
passed. No browser/Docker or real Stripe checks were rerun for this extraction.
No sub-agents, new dependencies, publication or global agent configuration
changes were involved.

## Persistent Swagger fixtures — 2026-10-26

The user requested populated databases and valid IDs for Swagger examples.
Codex inspected the running applications read-only: only an earlier unrelated
fixture existed, and glue work reported STRIPE_NOT_CONFIGURED. The existing
offline demo, HTTP contracts, provider adapter, reconciliation and persistence
paths were inspected; CodeGraph status/exploration complemented direct reads.
The fde-implement-slice skill guided this local demo slice.

Implemented a repeatable persistent demo launcher using separate file databases
and ports, public HTTP APIs for additive seeding, and the real worker/reconciler
against a shared read-only local Stripe fixture. Added a guarded Swagger-only
profile that selects an existing reconciliation ID and signs synthetic webhook
requests in the browser. Demo-specific prose/examples remain in YAML/JS resources.
Reserved distinct creation IDs so prepopulation does not immediately conflict
with POST /invoices; retained duplicate-creation 409 and normal ETag/idempotency
semantics. Existing application processes/databases remain untouched.

Verification and any corrections are recorded below after execution. No
sub-agents, real Stripe access, new dependencies, publication or global settings
changes are involved.

Final Maven formatting/verification passed 73 tests (9 ERP, 64 glue), with zero
failures/errors/skips and clean Spotless/Checkstyle gates. The two new tests check
profile credential/provider restrictions; the existing default-profile HTTP
contract test verifies the browser signer is absent. The packaged Swagger check
passed every endpoint plus actual response-example run/attempt ID validation,
signature rejection/deduplication, reseeding, file restart, manual-edit
preservation and reset recovery. Its first attempt caught a count snapshot
before the newly-created invoice converged; it now waits for that invoice's
DONE state before asserting stable counts. Restoring a missing fixture after ERP
reset also explicitly requeues its held work, while existing edited invoices
remain held. No acceptance assertion was removed.

The existing offline packaged demo passed with the shared strict provider stub:
10 invoices in 10.844 seconds, missed-first-webhook/late-refund/restart recovery
passed, final counts 11 invoices, 11 projections and 12 effects. A Node.js VM
executed the actual generated Swagger initializer/WebCrypto hook and delivered
its exact formatted body to the running demo: recorded/200, duplicate/200;
external destinations were left unsigned. The existing Air preview rejected
navigation to another port, so no rendered-browser success is claimed.

Stopped/restarted only this turn's persistent demo processes on the same
database files/ports. An immediate restart exposed TIME_WAIT in the preliminary
port probe; using SO_REUSEADDR to match Tomcat fixed that false rejection. An
occupied-port invocation still failed without starting children. The running
demo retained four invoices, two projections and two financial updates. Initial
application processes were never targeted for shutdown or their data reset.
Python compilation, final CodeGraph sync/current status and git diff --check
passed. Temporary checks cleaned up their own data/processes; the persistent
demo remains running for the user. No real Stripe or Docker checks were run.

## Commit and push requested by the user — 2026-10-26

The user explicitly authorized committing and pushing the Swagger documentation
and seeded demo updates to main. Codex checked the working tree and fetched
origin/main before preparing the commit. Reviewed the existing successful
verification evidence: 73 Maven tests, formatting/import/braces gates, the
packaged Swagger endpoint/restart check and the offline synchronization demo.
No application code changed during publication, so those suites were not rerun.
Checked the final diff and excluded ignored local databases, logs and credentials
from the commit. Push completion is reported after the remote operation succeeds.
