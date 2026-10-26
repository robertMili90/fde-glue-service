# Testing report

Evidence date: 2026-10-24. This report covers the local working tree after the
PaymentIntent status policy correction. It is a measured snapshot, not a CI
result or a claim about subsequent changes.

The full Maven verification and a separate JaCoCo-instrumented run both passed
all **71 tests**, with **zero failures, errors or skipped tests**. Combined
production coverage is **90.57% of lines** and **74.50% of branches**.

## Environment and test boundaries

- Eclipse Temurin JDK 21.0.7, Maven Wrapper 3.9.16, macOS on aarch64.
- Two Spring Boot applications with separate stores. Acceptance tests use real
  application servers and ERP HTTP calls, with a deterministic local Stripe HTTP
  stub and the official Stripe SDK.
- Controlled scheduling time avoids waiting for provider polling and retry delays.
  File-backed H2 tests exercise application-context restart and durable recovery;
  focused persistence tests also use isolated in-memory H2 databases.
- Tests require no Stripe credentials, real Stripe API access, Docker daemon or
  optional CodeGraph tooling. Initial dependency downloads require Maven Central.

Business requirements and named test mappings remain authoritative in
[the acceptance scenarios](scenarios.md). Payment status decisions are defined
in [the financial normalization policy](design.md#4-financial-normalization).

## Executed checks

| Check | Actual result |
| --- | --- |
| `./mvnw -B -ntp verify` | BUILD SUCCESS; 71 tests; both application archives packaged; Spotless and Checkstyle passed. Completed at 19:48:38 +03:00 in 39.996 seconds. |
| JaCoCo 0.8.12 `prepare-agent test report` run | BUILD SUCCESS; the same 71 tests passed with instrumentation. Completed at 19:51:11 +03:00 in 42.988 seconds. |
| JaCoCo CLI reports using both modules' execution data | Generated combined and per-module HTML/XML reports without class exclusions. |

Durations describe these local runs, not a performance target. The coverage
command runs tests and reporting; it does not execute the `verify` style gates.
Those gates passed separately in the full verification above.

## Test inventory

Counts are JUnit executions reported by Maven Surefire, including individual
parameterized cases. Every suite below has zero failures, errors and skips.

| Module | Suite | Tests |
| --- | --- | ---: |
| erp-simulator | BootstrapTest | 1 |
| erp-simulator | ErpApiTest | 6 |
| erp-simulator | ErpPersistenceTest | 2 |
| glue-service | AcceptanceTest | 36 |
| glue-service | BootstrapTest | 1 |
| glue-service | SandboxConfigurationTest | 7 |
| glue-service | FailureDiagnosticsTest | 4 |
| glue-service | ReconciliationPersistenceTest | 1 |
| glue-service | StripeWebhookControllerTest | 6 |
| glue-service | SyncPolicyTest | 2 |
| glue-service | WorkPersistenceTest | 5 |
| **Total** | **ERP: 9; glue: 62** | **71** |

The suite exercises scenarios S01-S13: verified durable webhook acceptance,
deduplication and destination idempotency, persisted retries and exhaustion,
timeout after ERP commit, restart recovery, refund ordering, first-missing-webhook
recovery, held financial conflicts, test-mode enforcement, notifications received
during work, paginated reconciliation and explicit PaymentIntent status decisions.
The [scenario mapping](scenarios.md#implemented-verification-mapping--2026-10-23)
identifies the tests; additional sections there describe HTTP body stalls,
OpenAPI/error contracts, transaction rollback and safe diagnostic logging.

The latest policy correction added nine executions: four recoverable status
cases, one cancellation/restart/reconciliation case, three unsupported status
cases, and one persistence case preserving a notification received during
cancellation processing. The focused post-fix run passed 13 tests, including
the existing persistence checks. Before the fix, the new status tests reproduced
four incorrect WAITING_PROVIDER outcomes; see the
[milestone evidence](implementation-plan.md#paymentintent-status-policy-correction--2026-10-24).

## Coverage results

JaCoCo 0.8.12 measured compiled production classes only. Test classes are not part
of the reported denominator, and no application classes were explicitly excluded.
Both execution files were supplied to every final report. This includes ERP code
executed by glue acceptance tests, rather than treating each module's tests as
its only coverage source. Total percentages use summed counters, not averages
of module percentages.

| Scope | Lines covered / total | Line coverage | Branches covered / total | Branch coverage | Instruction coverage |
| --- | ---: | ---: | ---: | ---: | ---: |
| **Combined** | **1,220 / 1,347** | **90.57%** | **406 / 545** | **74.50%** | **91.56%** |
| glue-service | 983 / 1,089 | 90.27% | 339 / 459 | 73.86% | 91.41% |
| erp-simulator | 237 / 258 | 91.86% | 67 / 86 | 77.91% | 92.15% |

Selected production classes:

| Class | Line coverage | Branch coverage |
| --- | ---: | ---: |
| SyncEngine | 93.18% | 94.44% |
| WorkItem | 93.41% | 84.62% |
| PaymentPolicy | 84.21% | 68.66% |
| StripeReader | 89.16% | 61.11% |
| RetryAfter | 23.08% | 100.00% |

Coverage is execution evidence, not proof of assertion quality or correctness.
JaCoCo does not count exception handlers as branches: RetryAfter's 100% branch
figure coexists with uncovered HTTP-date parsing and invalid-header fallback
lines. Other gaps include defensive invoice/provider validation, HTTP error
handling and ERP response decoding. New tests should address meaningful behavior
and failure recovery rather than merely raise these percentages.

Coverage was measured on demand. The POM files do not configure automatic JaCoCo
execution or a minimum coverage threshold; default `./mvnw verify` does not
produce a coverage report.

## Reproduce verification and coverage

Run from the repository root with JDK 21. Standard verification:

```sh
./mvnw -B -ntp verify
```

The instrumented test command used for this measurement was:

```sh
./mvnw -B -ntp \
  org.jacoco:jacoco-maven-plugin:0.8.12:prepare-agent \
  test \
  org.jacoco:jacoco-maven-plugin:0.8.12:report
```

For a new measurement after code changes, prepend `clean` to that command so
stale class files and previous execution data cannot contribute to the result.
Keep reporting against the same compiled classes that produced the execution data.

Generate reports that combine execution across modules. This portable CLI-copy
command is equivalent to the Maven-cache CLI download used in the measured run:

```sh
./mvnw -B -ntp -N dependency:copy \
  -Dartifact=org.jacoco:org.jacoco.cli:0.8.12:jar:nodeps \
  -DoutputDirectory=target/coverage-tools

java -jar target/coverage-tools/org.jacoco.cli-0.8.12-nodeps.jar report \
  erp-simulator/target/jacoco.exec glue-service/target/jacoco.exec \
  --classfiles erp-simulator/target/classes \
  --classfiles glue-service/target/classes \
  --sourcefiles erp-simulator/src/main/java \
  --sourcefiles glue-service/src/main/java \
  --name all \
  --html target/site/jacoco-combined/all \
  --xml target/site/jacoco-combined/all/jacoco.xml

for module in erp-simulator glue-service; do
  java -jar target/coverage-tools/org.jacoco.cli-0.8.12-nodeps.jar report \
    erp-simulator/target/jacoco.exec glue-service/target/jacoco.exec \
    --classfiles "$module/target/classes" \
    --sourcefiles "$module/src/main/java" \
    --name "$module" \
    --html "target/site/jacoco-combined/$module" \
    --xml "target/site/jacoco-combined/$module/jacoco.xml"
done
```

Surefire XML/text evidence is generated under each module's
`target/surefire-reports`. Combined HTML starts at
`target/site/jacoco-combined/all/index.html`; per-module reports are siblings
under `target/site/jacoco-combined`. Inspect these combined-data reports for the
table above: the Maven goal's initial module report omits ERP execution from
the glue tests. All generated artifacts stay in ignored build directories;
this Markdown report is the repository record.

## Checks outside the latest runs

The previously executed packaged offline demo passed actual scheduled work,
first-missing-webhook recovery, a later refund and persisted retry recovery across
a normal glue JVM restart. Its recorded ten-invoice burst was 10.188 seconds.
It was not rerun after the status policy correction, and its execution is not
included in the coverage counters. See the
[earlier demo evidence](implementation-plan.md#review-fixes--2026-10-24).

Docker image/container startup and volume restart, the real Stripe sandbox/CLI
workflow, and IDE launch checks were not executed in the latest verification.
They are not skipped JUnit cases and therefore do not appear in Surefire's zero
skip count. Tests establish the documented single-instance behavior and normal
restart recovery, not production readiness, multi-instance safety or power-loss
durability. Performance/load and security audits are outside this report.
