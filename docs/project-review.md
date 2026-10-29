# EDIFlow project-review guide

## Start with the problem (20 seconds)

“A retailer and a supplier use different business systems. They exchange EDI purchase orders and invoices. Manual re-entry creates mistakes, duplicate messages can create duplicate orders, and invoice differences need investigation. EDIFlow turns those documents into validated, traceable business records.”

The intended user is a B2B integration or operations analyst. The project demonstrates document ingestion, asynchronous processing, invoice-to-order total matching, and operational recovery. It does not place physical orders, deliver goods, charge cards, or pay invoices.

## Demonstrate the business scenario (2–3 minutes)

Open [the hosted demo](https://ediflowvaibhav7506.vercel.app) and use the **Project review / Guided use case** panel. Each step has expandable presenter notes. Use **Restart demo** to return to the initial state.

1. Explain the participants: Cedar Retail is the buyer; Northstar Supply is the supplier; an operations analyst monitors their document exchange. These are fictional businesses.
2. Choose **1. Receive purchase order**. Show `PO-REVIEW-01` in Orders. Choose **View**: 10 units at $75 plus 20 at $25 gives $1,250. Show the lines and the processing history. Close the detail dialog.
3. Choose **2. Check supplier invoice**. The Invoices tab opens on `INV-REVIEW-01`, worth $1,300. Point out `MATCH_EXCEPTION`: the supplier invoice is $50 above the agreed order. The purchase order remains unchanged. Explain that this makes the discrepancy visible to an operator; it is not a payment-blocking integration.
4. Choose **3. Submit corrected invoice**. Show the new `INV-REVIEW-02`, worth $1,250, with status `PERSISTED`. The original invoice remains an exception. This represents the supplier submitting a corrected document with a separate identity, not the system editing or erasing the original invoice.
5. Choose **4. Inspect audit evidence**. The Audit tab filters to the scenario. Point out the exact $50 mismatch reason, timestamps, entity IDs, and the successful corrected-invoice transition.

Close with: “The useful outcome is that an operator can find an incorrect invoice and explain the processing decision without manually comparing raw files.”

Optional extension: open Dead Letters and retry the sample transient failure, then open Partners and approve the pending sample partner. Inspect their audit events. These are separate operational examples, not steps that correct an invoice amount.

## Explain what runs where (30 seconds)

“The public Vercel site is a clearly labeled browser simulation. It is useful for demonstrating the workflow without credentials or infrastructure. Its tables do not prove that Kafka or Java services ran. The repository contains the implementation for those services; I run the local stack when demonstrating actual backend processing.”

The public walkthrough uses $1,250 and $1,300 for easy explanation. The existing backend fixtures use a different order and amounts; do not expect their IDs or totals to match the browser scenario.

## Prove the backend with the local stack

Before the review, follow the [README run instructions](../README.md#run) to start Docker Compose and wait for the services to become ready. Set `VITE_DASHBOARD_MODE=live` when building or starting a dashboard that should connect to the real gateway; configure the mTLS proxy described in the README. Do not present the public simulation as a live backend session.

Use the [README OAuth2/mTLS upload instructions](../README.md#verify-oauth2-and-mtls) to obtain a token, upload fixtures through the secured gateway, and poll their processing status. Upload the purchase order before either invoice, and wait for it to finish processing.

- `valid-850.edi`: `PO-100245`, total $1,687.50; the order reaches `PERSISTED` and its upload status becomes `PROCESSED`.
- `duplicate-850.edi`: resend the same interchange; status becomes `DUPLICATE`, with no second order.
- `mismatched-810.edi`: `INV-2002`, total $1,800; invoice reaches `MATCH_EXCEPTION` with a $112.50 difference. The upload itself is processed successfully; inspect the invoice status and audit reason for the business exception.
- `valid-810.edi`: `INV-2001`, total $1,687.50; invoice reaches `PERSISTED`.
- `malformed-850.edi`: demonstrate parsing failure and its recorded dead letter. Retrying an unchanged malformed file does not repair it.

Show API responses and the actual order, invoice, and audit results. The fixture files are in `ingestion-service/src/test/resources/fixtures`.

## Explain the engineering choices (1 minute)

- Java/Spring Boot and StAEDI parse X12 and implement business rules.
- Kafka separates document ingestion from downstream processing and supports retries.
- PostgreSQL stores business records, state history, and uniqueness constraints for duplicate protection.
- OAuth2 scopes and mTLS control access to the real APIs; the public demo does not bypass them.
- Redis backs gateway rate limits; Prometheus/Grafana support operational monitoring.

For implementation detail, show `InvoiceMatchRule.java`, `DocumentProcessor.java`, and `ProcessingStateMachine.java` under `order-service/src/main/java/com/vaibhav/ediflow/order`. See [the workflow guide](flow.md) and [testing guide](testing.md) for the complete architecture and verification commands.

## Scope questions to answer honestly

- This project compares invoice and purchase-order totals. It does not currently demonstrate goods-receipt matching or a complete ERP/accounting integration.
- The browser walkthrough and browser tests simulate data and API responses. Real backend evidence comes from the running stack and its tests.
- No measured customer savings or production adoption is claimed. The scenario illustrates where the system could reduce manual reconciliation work.
