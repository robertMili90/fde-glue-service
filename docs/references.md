# Sources and official reference documentation

## Assignment authority

- sources/FDE_Assignment_Glue_Service.pdf: supplied original, copied unchanged.
- sources/recruiter-clarifications.md: English rendering of the candidate-supplied
  recruiter response. It permits real/fictitious systems, local simulators, and
  a justified unidirectional flow; no Adobe environment will be provided.

The invoice/PaymentIntent model and operational policies in docs/design.md are
our selected exercise assumptions. They were not mandated by the recruiter.

## Verify implementation details against primary sources

Links checked during preparation on 2026-10-22; releases and SDKs can change.

- Spring Boot requirements: https://docs.spring.io/spring-boot/system-requirements.html
- Spring scheduling: https://docs.spring.io/spring-framework/reference/integration/scheduling.html
- Springdoc compatibility: https://springdoc.org/
- H2 persistence modes: https://www.h2database.com/html/features.html
- Stripe Java SDK: https://github.com/stripe/stripe-java
- Stripe webhooks: https://docs.stripe.com/webhooks
- PaymentIntent fields: https://docs.stripe.com/api/payment_intents/object
- Refund fields: https://docs.stripe.com/api/refunds/object
- Refund filtering/pagination: https://docs.stripe.com/api/refunds/list
- Snapshot event types: https://docs.stripe.com/api/events/types
- Sandbox testing: https://docs.stripe.com/sandboxes
- CodeGraph setup/tools/language support: https://github.com/colbymchenry/codegraph
- Repository agent instructions: https://developers.openai.com/codex/guides/agents-md
- Repository skills: https://developers.openai.com/codex/skills

Record the actual Spring Boot, springdoc, Stripe SDK/API, JDK, Maven Wrapper, and
container versions selected during bootstrap. Prefer stable compatible releases.
Do not infer that a moving documentation link pins the runtime dependency.

## Bootstrap dependency selection (2026-10-23)

| Component | Choice / evidence |
| --- | --- |
| JDK | Java 21; local Temurin 21.0.7+6-LTS |
| Spring Boot | 3.5.16; [3.5 requirements](https://docs.spring.io/spring-boot/3.5/system-requirements.html) support Java 21 and Maven >=3.6.3 |
| springdoc | 2.8.17; [official compatibility matrix](https://springdoc.org/v2/#what-is-the-compatibility-matrix-of-springdoc-openapi-with-spring-boot) lists Boot 3.5 with 2.8.x |
| Stripe Java | 34.0.0 pinned and used by the runtime reader adapter; [official SDK](https://github.com/stripe/stripe-java/tree/v34.0.0) supports Java 21 |
| Stripe API | 2026-09-30.endive, from the pinned SDK [ApiVersion.java](https://github.com/stripe/stripe-java/blob/v34.0.0/src/main/java/com/stripe/ApiVersion.java); event names and routing/resource fields verified during M3 implementation |
| Maven | Wrapper 3.3.4 (only-script), distribution 3.9.16; ZIP checksum pinned; [official wrapper docs](https://maven.apache.org/tools/wrapper/) |
| H2, Flyway, test/build plugins | JPA 3.5.13, Hibernate 6.6.53.Final, H2 2.3.232, Flyway 11.7.2 resolved; managed by the pinned Spring Boot BOM/parent, with no independent overrides |
| Docker | Local client 28.3.2, Compose v2.39.1-desktop.1; daemon stopped |
| Container images | eclipse-temurin:21-jdk-jammy and 21-jre-jammy, multi-architecture digests pinned in Dockerfile using registry manifest responses; runtime not executed locally |

Stable Maven Central metadata was checked before selecting exact artifacts.
Boot 3.5 was chosen as a stable line with the documented springdoc 2.8 pairing;
no requirement calls for a Boot 4 migration. Maven 3.9.16 was chosen from the
established 3.9 line. Dependency resolution and executable verification are
recorded in [the milestone log](implementation-plan.md).

### Stripe integration verification

On 2026-10-23, official [snapshot event types](https://docs.stripe.com/api/events/types)
confirmed payment_intent.succeeded, payment_intent.payment_failed, refund.created,
refund.updated, and refund.failed. [Refund list filtering/pagination](https://docs.stripe.com/api/refunds/list)
confirmed payment_intent, limit, and starting_after. The pinned SDK's source and
resolved classes confirmed the v1 client services, PaymentIntent mode and capture
fields, Refund association/status fields, SDK signature verification, per-client
retry/timeout options, and response headers. Signature verification uses the SDK
on the raw body, followed by minimal JSON routing; current resources, rather than
snapshot deserialization or arrival time, drive financial decisions.

[Stripe testing](https://docs.stripe.com/testing?testing-method=payment-methods)
and the [CLI reference](https://docs.stripe.com/cli) informed the optional fixture
and forwarding commands. The fixture pins the SDK's API version. No real sandbox
request was made; fixture network behavior remains an unexecuted optional check.


### Refactoring tools — 2026-10-23

Formatting/import checks are build-only plugins, not application dependencies:
Spotless 2.43.0 with google-java-format 1.24.0, and maven-checkstyle-plugin 3.6.0
with Checkstyle 10.21.4. Compatible versions were pinned and resolved locally.
The [Spotless Maven guide](https://github.com/diffplug/spotless/blob/main/plugin-maven/README.md)
documents main/test Java formatting and verify binding. The
[Checkstyle plugin goal](https://maven.apache.org/plugins/maven-checkstyle-plugin/check-mojo.html)
and [AvoidStarImport check](https://checkstyle.org/checks/imports/avoidstarimport.html)
were consulted; actual temporary-source probes verified both regular and static
wildcard rejection rather than inferring it from formatter success.
