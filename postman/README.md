# Localhost Postman demo

Import both files into Postman and select **FDE localhost**:

- [Collection](fde-localhost.postman_collection.json)
- [Environment](localhost.postman_environment.json)

Default URLs: glue `http://localhost:8080`, ERP `http://localhost:8081`.
Change `glueBaseUrl` / `erpBaseUrl` in the selected environment for other ports.
Run individual folders/requests: the entire collection includes alternative fault
controls and an explicitly guarded reset. Collection requests only target localhost.

## ERP demo without Stripe

Build with `./mvnw verify`, then start the ERP from the repository root:

```sh
java -jar erp-simulator/target/erp-simulator-0.1.0-SNAPSHOT-exec.jar
```

Keep glue stopped for this demo, or use a separate ERP instance/database. The demo
creates synthetic PaymentIntent references; a running glue reconciliation would
otherwise discover them and attempt provider reads. No Stripe configuration is
needed. This demonstrates the ERP contract directly; it does not prove integration.

Run folder **01 - ERP demo - no Stripe required** in order (Postman Runner works):

1. Clear active simulator faults and create a fresh UNPAID invoice for 1200 minor
   units in USD. Each run generates new invoice/payment/idempotency identifiers.
2. Read the ETag, apply PAID, and replay the same key using the original ETag.
   The replay returns the same committed version.
3. Observe expected 409 (changed payload), 412 (stale version), and 422 (amount
   mismatch) responses with specific reason codes.
4. Apply a full REFUNDED projection and inspect the resulting invoice and counts.

The collection saves IDs and ETags automatically. Existing invoices are retained;
only the active simulator fault setting is cleared. `amountMinor`,
`mismatchedAmountMinor` and `currency` are collection variables; amounts must be
positive integers and the mismatch value must differ from the invoice amount.

Optional CLI equivalent with Node.js/npm:

```sh
npm exec --yes --package=newman@6.2.1 -- newman run \
  postman/fde-localhost.postman_collection.json \
  -e postman/localhost.postman_environment.json \
  --folder '01 - ERP demo - no Stripe required'
```

## Stripe-to-ERP sandbox integration

Follow the authoritative [startup and sandbox instructions](../README.md#optional-stripe-sandbox-workflow)
for both applications, test credentials, Stripe CLI forwarding and fixtures.
The fixture command creates, links and confirms a test payment:

```sh
python3 scripts/stripe-sandbox.py pay --invoice inv_postman_sandbox_001 --amount 1200 --currency usd
```

Set these values locally in the selected Postman environment:

- `stripePaymentIntentId`: the PaymentIntent ID printed by the fixture.
- `stripeWebhookSecret`: the same value configured as the glue application's
  `STRIPE_WEBHOOK_SECRET`. Keep it local; the committed environment value is empty.

Use folder **02 - Glue integration - Stripe sandbox required** manually:

1. Inspect the linked invoice, send a new signed notification, then resend it.
   Expect `recorded` followed by `duplicate`.
2. Inspect work, attempts and the invoice until synchronization completes.
   Acceptance is asynchronous; an HTTP 200 receipt does not imply PAID.
3. Request reconciliation and inspect the saved run ID. For a missing-first-webhook
   demo, disable CLI forwarding before fixture confirmation and request a scan
   before sending any Postman event. The default periodic scan may discover it first.
4. Create a full refund using the existing fixture tool:

   ```sh
   python3 scripts/stripe-sandbox.py refund pi_REPLACE_WITH_FIXTURE_ID
   ```

   After the refund succeeds, send **09 - Old success notification after refund**.
   Inspect the invoice again: current Stripe resources should yield REFUNDED.

The pre-request script uses Postman's bundled `crypto-js` module to sign the exact
resolved body with a fresh timestamp. Replaying preserves the event ID while
refreshing the signature. The worker reads real current Stripe resources; changing
the event JSON alone does not create a payment or refund. Postman never requires
the Stripe API key. Keep credentials in the local process environment as described
in the main README.

## Operations and fault controls

Folder **00** checks health and generated OpenAPI; **03** exposes work, scans,
invoice pages, counts and metrics. Some metrics appear only after the first attempt.

In folder **04**, choose one fault, then produce a new sandbox payment/refund that
requires an ERP write. Inspect work/attempts to observe retries. A 3000 ms response
delay exceeds the default glue deadline; VERSION_CONFLICT exercises revalidation.
Existing idempotency keys and already-correct observations can bypass these faults.
Requeue held work only after fixing its cause. See the authoritative
[ERP contract](../contracts/erp-api.yaml) for fault and version semantics.

The reset request requires local environment variable `allowErpReset=yes`. It clears
all ERP simulator data, including other local demo invoices, but retains glue and
Stripe state. Clear the opt-in value afterwards. No reset is needed for folder 01.
