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
