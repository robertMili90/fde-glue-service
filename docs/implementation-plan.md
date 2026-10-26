# Implementation milestones

The original expected effort is approximately 8-10 focused hours. Treat this as a
scope constraint, not a demand to invent evidence or skip correctness.
Implement in vertical slices; stop adding extensions when the core is complete.
The [testing report](testing-report.md) consolidates the latest executed suite
results, coverage measurement, reproduction commands and verification limits.

| Milestone | Scope | Exit evidence |
| --- | --- | --- |
| M0 | Inspect starter/existing repo, verify compatible dependencies, note assumptions | Recorded versions and a bounded plan; no unresolved core contract conflict. |
| M1 | Maven Wrapper; two Spring Boot apps; separate H2 files; migrations; health/OpenAPI; Compose | Both apps start; persistence survives a normal restart; basic build passes. |
| M2 | Stateful ERP invoice/projection API with idempotency and fault controls | Matching effect, replay, changed-payload rejection, and stale-version behavior verified. |
| M3 | Stripe adapter, signed webhook ingress, durable inbox/work request, normal sync | Matching payment reaches ERP through queued work; signed/invalid/duplicate cases verified. |
| M4 | Retry, timeout-after-commit, restart recovery, current-state refund handling | S04-S07 and the lost-wake-up condition verified. |
| M5 | Paginated ERP-reference reconciliation, operational APIs, basic metrics/logs | First missing webhook and old-invoice refund recovery verified. |
| M6 | Focused review, final verification, optional sandbox smoke, README/design/AI evidence | Accurate scenario matrix, actual check results, setup/demo instructions, known limits. |

M2 and M3 may be interleaved to get one real end-to-end slice working early.
The implementation agent can change sequencing when it reduces rework while
preserving the acceptance criteria.

## Working discipline

- Use the smallest implementation that can demonstrate the guarantees.
- Prefer existing Spring/JDK mechanisms and the official Stripe SDK.
- Add dependencies only for a concrete responsibility. Do not introduce a
  broker, cache, workflow platform, or LLM to make the diagram look richer.
- Pin actual compatible versions and record them after verifying official docs
  and resolving a build. Do not copy stale version numbers from a conversation.
- Keep progress and checks in the table below. Do not pre-populate success.
- If time runs short, cut optional presentation/features first. Explain unfinished
  core behavior explicitly rather than hiding it behind a passing happy path.

## Progress log

| Milestone | Status | Evidence / next step |
| --- | --- | --- |
| M0 | COMPLETE | Clean starter inspected; Java/Maven/Docker/optional tooling checked. Pinned choices and official sources recorded in references.md. No core contract conflict found. |
| M1 | LOCAL VERIFIED | Wrapper and both apps build; 9 tests pass; separate executable JVMs pass health/OpenAPI/Swagger and normal file-backed persistence restart. Compose configuration validates; container build/startup is unverified because the daemon is stopped. |
| M2 | VERIFIED OFFLINE | Real ERP HTTP tests cover absolute projection, replay/conflict ordering, faults, stale versions, unique associations, and integral amounts. File restart retains invoice and stored idempotency result. |
| M3 | VERIFIED OFFLINE | Official SDK current-resource reader; signed raw-body ingress; atomic receipt/work commit; concurrent deduplication; matching projection; invalid/live and persistence-failure checks. |
| M4 | VERIFIED OFFLINE | Persisted bounded retries/Retry-After, explicit requeue, timeout-after-commit, RUNNING/retry restart recovery, current paginated refunds, and distinct wake-up during remote work. |
| M5 | VERIFIED OFFLINE | ERP-reference scans recover first missing webhooks and a refund on work observed 180 days earlier; batch/failure cursor survives restart; operational APIs and metrics wired. |
| M6 | COMPLETE OFFLINE | Reliability self-review and fixes; latest clean reactor verification passes 45 tests plus formatting/import checks; separate-process demo passes. See the refactor evidence below. Docker runtime and real Stripe remain explicitly unverified. |

## Local baseline to establish

The agent should provide and verify ./mvnw verify and documented commands for:
starting each module in an IDE/terminal; starting the full local stack with
docker compose; configuring only sandbox Stripe credentials; forwarding
webhooks with Stripe CLI; seeding linked invoices; and exercising fault controls.

Default automated verification must work offline from Stripe. Failure-injection
controls and operational APIs are local exercise tooling, not a production
authorization design. Record that deployment limitation.

## Bootstrap verification — 2026-10-23

Historical M1 snapshot. Current end-to-end implementation evidence follows below.

Executed on macOS arm64, Temurin 21.0.7+6-LTS:

| Check / command | Actual result |
| --- | --- |
| `java -version`, `mvn -version`, optional CLI lookup | Java 21 and installed Maven 3.9.6 available; Stripe CLI and CodeGraph absent from PATH; no CodeGraph MCP tool exposed. |
| `mvn -B -ntp org.apache.maven.plugins:maven-wrapper-plugin:3.3.4:wrapper -Dmaven=3.9.16 -Dtype=only-script` | Generated the official wrapper successfully. Distribution checksum added from the downloaded Maven Central ZIP. |
| `./mvnw --version` | Wrapper uses Maven 3.9.16 on Java 21.0.7. |
| First `./mvnw -B -ntp verify` | Failed on a test-only Flyway metadata assumption: history contains a table-creation marker as well as V1. HTTP checks and all seven credential cases passed. |
| Final `./mvnw -B -ntp verify` | BUILD SUCCESS, 9 tests, 0 failures/errors/skips; both executable JARs packaged. Corrected test compares history row count before/after restart and checks successful V1 exactly once. |
| Packaged-JAR process smoke (Python subprocess/HTTP/H2 harness) | Both apps ran simultaneously as separate JVMs, with random ports and separate temporary file-backed databases. Health, generated OpenAPI, and Swagger UI returned 200 in both rounds. SIGTERM/normal shutdown, SQL probe insertion while stopped, JVM restart, second normal shutdown, then SQL inspection retained each module's committed marker. All processes stopped and temporary data removed. |
| `docker version`, `docker compose version` | Client 28.3.2 and Compose v2.39.1-desktop.1; daemon connection failed because Docker Desktop is stopped. |
| `open -a Docker`, followed by bounded `docker info` checks | Docker Desktop is installed and launch returned successfully, but the daemon remained unavailable. No container runtime result is claimed. |
| `docker compose config --quiet` | Passed. Does not establish image build, volume permissions, or container startup. |
| `git diff --check` | Passed. |

Process smoke invoked `java -jar <absolute-built-jar>
--spring.main.banner-mode=off` for each module. The harness supplied module-specific
`GLUE_PORT`/`ERP_PORT` and `GLUE_DATA_DIR`/`ERP_DATA_DIR` variables, and empty Stripe
credentials. SQL probes used `java -cp <extracted-h2-jar> org.h2.tools.Shell` only
while each application was stopped. Automated application-context restart tests
also insert their marker while the application is running:
[glue bootstrap test](../glue-service/src/test/java/com/example/fde/glue/BootstrapTest.java)
and [ERP bootstrap test](../erp-simulator/src/test/java/com/example/fde/erp/BootstrapTest.java).
These tests establish normal bootstrap persistence, not S06 work recovery.

Flyway 11.7.2 and H2 2.3.232 resolved from the Boot dependency management.
Bootstrap migrations create separate application schemas; no domain behavior or
synthetic successful synchronization is exposed. The selected Stripe SDK version
is managed for M3 and is not yet included on the runtime classpath.

Not executed: Docker image builds/container restarts (daemon unavailable), IDE
launches, and real Stripe sandbox checks (M3 not implemented). No S01-S12 scenario
is claimed complete. Next slice: M2 stateful ERP API. Startup commands and optional
environment variables are in [README](../README.md).

## End-to-end verification — 2026-10-23

The main build prompt was read and executed after the bootstrap. The supplied
assignment PDF was also read during the final deliverable audit; no scope conflict
was found. JPA was added as
specified, while keeping dependency pins and separate application stores.
The simulator is a test-only dependency of glue, and the Boot plugin attaches
executable `-exec.jar` artifacts. Migration locations are module-specific.

| Check / command | Actual result |
| --- | --- |
| `./mvnw -B -ntp -pl erp-simulator test` | ERP focused checks passed after correcting ambiguous test imports; the subsequent suites include them. |
| Early full `./mvnw -B -ntp clean verify` | Failed: a new WorkItem was persisted before its required updatedAt value was initialized. Webhook returned 503 and the transaction rolled back. Fixed initialization; kept failure/atomicity assertions. |
| Intermediate `./mvnw -B -ntp verify` runs | Passed after the persistence fix; later runs added boundary and review regressions. No failed assertion was removed to obtain success. |
| Final `./mvnw -B -ntp clean verify` | BUILD SUCCESS, 33 tests, zero failures/errors/skips. ERP: 5; glue: 28 (18 acceptance, 2 policy, 1 bootstrap, 7 credential cases). Both executable JARs packaged. |
| `python3 scripts/demo-offline.py` | Passed twice with packaged applications and real scheduling. Latest 10-invoice burst: 10.225 seconds at the default 500 ms poll (earlier run 10.260). First missing webhook, later refund, and persisted retry across normal glue JVM restart passed. Final state: 11 invoices, 11 projections, 12 applied updates. Own temporary processes/data cleaned up. |
| `python3 scripts/stripe-sandbox.py --help` and Python AST parsing | Passed. Three subprocess checks rejected live credentials, zero amount, and invalid PaymentIntent input before network use; supplied fixture keys were absent from output. Real fixture network behavior was not executed. |
| `docker compose config --quiet` | Passed after executable artifact/configuration changes. |
| `docker info --format '{{.ServerVersion}}'` | Failed: local daemon is still unavailable. No image build or container runtime pass is claimed. |
| YAML parsing with Ruby's YAML library | ERP contract and Compose parsed successfully. |
| Documentation local-link checks and `git diff --check` | Passed; Git emits an informational CRLF normalization warning for the generated Windows wrapper. |
| Packaged glue archive inspection | No simulator dependency in the runtime artifact. Resolved JPA 3.5.13, Hibernate 6.6.53.Final, H2 2.3.232, Flyway 11.7.2. |

The [scenario mapping](scenarios.md) links all S01-S12 to executed tests. The
[review report](reliability-review.md) records inspected boundaries, fixed findings,
and practical limits. The final clean build includes explicit old-notification
created times and a 180-day clock advance before a later refund scan.

Not executed: Docker image/volume/container startup and restart; IDE or
`spring-boot:run` launches; real Stripe API/CLI sandbox workflow. No test-mode
Stripe key was present in the process environment, and Stripe CLI/CodeGraph were
not installed on PATH. Application source, offline tests, and the packaged demo
are complete within scope; these optional/environment-dependent checks remain
unverified. Production authorization, data retention, distributed claims, and
runtime AI/broker/platform extensions remain outside scope.

Exact build/startup, sandbox fixture, forwarding, fault-control, metrics, and
operational API commands are maintained in [README](../README.md).

## Optional CodeGraph tooling — 2026-10-23

After the application verification above, the user explicitly requested CodeGraph
installation. Version 1.6.2 is now installed and configured locally for this
project. Initialization and current index status passed (49 files, 672 nodes,
1,126 edges). A Java symbol/route query and a direct stdio MCP initialize,
tools/list, and codegraph_explore call passed. This supersedes the earlier
CodeGraph availability limitation; it does not change historical build evidence.
The current Air session must reload tools to expose MCP through the agent.
Application source did not change; Maven was not rerun. See the
[tooling guide](agent-tooling.md) for exact setup and verification details.

## Clean-code refactor — 2026-10-23

The user requested prompt 08 on the existing working tree. Baseline verify passed
33 tests with no pre-existing failures. Feature packages, typed boundary models,
encapsulated workflow transitions, and managed lifecycle adapters are complete.
The ordered-invoice cursor assumption was separately corrected and documented;
the original migration/configuration/resource bytes and financial scope remain.

Final `./mvnw -B -ntp clean verify` passed 45 tests with no failures/errors/skips,
plus pinned formatting and explicit import/braces checks. The freshly packaged
`python3 scripts/demo-offline.py` passed the missing-first-webhook, late-refund,
and JVM-restart retry scenarios; its latest 10-invoice burst took 10.225 seconds.
Both application root packages contain only their entry points, and the glue
runtime archive has no simulator dependency. The [refactoring review](refactoring-review.md)
is the detailed record of package/transaction ownership, cursor correction,
new tests, iteration failures, actual commands, and remaining limitations.
No Docker runtime, real Stripe sandbox run, or independent review was added.

## Spring Data repository follow-up — 2026-10-23

Completed the user's requested persistence refactor across both applications.
Eight feature-scoped Spring Data repositories replace direct EntityManager access;
constructor injection, explicit caller-owned lock transactions, assigned-ID insert
semantics and managed entity transitions preserve the existing reliability rules.
Ordinary lookups/order/counts use derived queries; bounded eligibility and aggregate
queries remain explicit at the repository boundary. No schema, HTTP contract or
build dependency changes were required.

Baseline verify passed 45 tests. The final `./mvnw -B -ntp clean verify` passed
51 tests with zero failures/errors/skips and both style gates. Six new regressions
cover assigned-ID overwrite protection, eligibility/order/limits/metrics, and ERP
projection/idempotency/fault-counter rollback with safe retry. All original test
sources and assertions are unchanged. The freshly packaged offline demo passed
first-missing-webhook, late-refund and actual JVM-restart retry recovery; its
ten-invoice burst took 10.545 seconds. CodeGraph sync/status/exploration and
source/archive/documentation checks also passed.

The [persistence follow-up](refactoring-review.md#spring-data-repository-follow-up)
records implementation choices, the corrected proxy-visibility iteration failure,
actual commands and remaining single-instance/Docker/Stripe verification limits.

## Review fixes — 2026-10-24

Implemented review findings 1-3 and HTTP error consistency: full ERP response
consumption has a cancelable deadline, endpoint OpenAPI describes actual statuses
and payloads, and logs preserve safe cause/stack/SQL diagnostics. Request advice
returns reasonCode/message plus field errors for malformed JSON, validation,
query conversion and missing required headers. Webhook errors add a message;
missing operational resources now have a typed 404 body.

New tests preserve queue progress and ERP idempotency when a response body stalls,
verify failed enumeration/checkpoint recovery, compare OpenAPI with HTTP behavior,
and inspect diagnostic logs for useful evidence without exception message values.
The first completed focused suite passed 44 tests. Final
`./mvnw -B -ntp clean verify` passed all 62 tests (ERP 9, glue 53), zero
failures/errors/skips, plus formatting and Checkstyle. The 51 existing tests
remain, with 11 new regression cases. `python3 scripts/demo-offline.py` passed
actual scheduled processing, missing-first-webhook recovery, late refund and retry
across a normal glue JVM restart. Its ten-invoice burst took 10.188 seconds;
final ERP counts were 11 invoices, 11 projections and 12 applied updates.
Documentation file links and `git diff --check` passed (the existing Windows-wrapper
line-ending warning remains). Docker runtime and real Stripe sandbox checks were
not run. No production-readiness or multi-instance claim is made.

## PaymentIntent status policy correction — 2026-10-24

Refined the original non-succeeded waiting rule at the user's request. The
[authoritative policy](design.md#4-financial-normalization) now distinguishes
recoverable waiting, terminal cancellation, successful collection/refund
normalization, and unsupported/unknown statuses. Cancellation finishes with
DONE/PAYMENT_CANCELED without an ERP write; reconciliation skips this observed
terminal result across restart, while new events and in-flight generations remain
preserved. Successful payments still participate in later-refund reconciliation.
Existing WAITING_PROVIDER rows adopt the new policy on their next observation.
No schema or HTTP contract change is required.

Actual verification:

- Before correction, `./mvnw -B -ntp -pl glue-service -am
  '-Dtest=AcceptanceTest#*PaymentStates*+canceledPayment*'
  -Dsurefire.failIfNoSpecifiedTests=false test` ran eight cases: four passed,
  four failed because cancellation and unsupported statuses observed WAITING_PROVIDER.
- `./mvnw -B -ntp spotless:apply` passed.
- After correction, `./mvnw -B -ntp -pl glue-service -am
  '-Dtest=AcceptanceTest#*PaymentStates*+canceledPayment*,WorkPersistenceTest'
  -Dsurefire.failIfNoSpecifiedTests=false test` passed all 13 focused cases.
- `./mvnw -B -ntp verify` passed all 71 tests (ERP 9, glue 62), zero
  failures/errors/skips, plus Spotless and Checkstyle. Both application archives
  were packaged. All 62 pre-existing cases remain, with nine additional cases.
- CodeGraph CLI status/exploration, index sync and final status were executed;
  the Java index is current. Direct source and configuration reads complemented it.
- `git diff --check` passed; the existing Windows-wrapper CRLF warning remains.

The regression suite includes waiting-to-success convergence for all four waiting
statuses, visible reasons and retained facts for unsupported states, no periodic
provider reads after cancellation across on-disk restart/reconciliation, and
preserved notifications during cancellation processing. The existing later-refund
and idempotency/retry/recovery acceptance cases also passed. See S13 in the
[scenario mapping](scenarios.md). Packaged demo, Docker runtime and real Stripe
sandbox checks were not rerun for this bounded policy change.

## Testing report — 2026-10-24

Added an English [testing report](testing-report.md) consolidating the latest
71-test verification and measured coverage, with reproducible commands and
explicit remaining limits. Cross-checked all suite rows and coverage counters
against existing XML evidence. The documented CLI-copy and all three combined-data
report commands passed; relative links/anchors and git diff --check passed.
This was a documentation-only change, so application tests were not rerun.
