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
