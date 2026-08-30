# Testing and observability

EDIFlow uses a risk-based testing pyramid. Fast unit tests isolate parsing, rules, and state transitions; Docker-backed integration tests exercise infrastructure boundaries; Playwright covers the two operator journeys most likely to hide cross-layer regressions.

## Test layers

| Layer | What it proves | Command |
|---|---|---|
| Unit | X12 segment mapping, R1–R5 rules, valid/invalid state transitions, JWT authorization | `.\gradlew.bat --no-daemon test` |
| Integration | Real Kafka/PostgreSQL upload-to-persistence, duplicate handling, DLQ, invoice matching; real Redis rate limiting | `.\gradlew.bat --no-daemon :integration-tests:integrationTest :gateway-integration-tests:integrationTest` |
| Browser E2E | Dashboard upload, admin DLQ retry, serious/critical WCAG 2 A/AA violations | `cd dashboard; npm.cmd run test:e2e` |

The integration layer requires a running Docker engine. CI runs it on GitHub's Ubuntu runner. Browser tests intercept the external API boundary with deterministic fixtures, leaving Kafka/PostgreSQL correctness to the lower integration layer and avoiding duplicate slow scenarios.

## Critical scenarios

- A valid `CTT*2` 850 is uploaded, published, consumed, line-count validated, totalled, and persisted.
- A malformed segment is recorded in the DLQ and returned by its API.
- A CTT01/PO1 mismatch is rejected with `LINE_COUNT_MISMATCH`; CTT02 is not treated as money.
- Two concurrent uses of the same partner/ISA/ST tuple produce one interchange and one order; the losing event records `DUPLICATE`.
- An accepted upload returns `202` and a stable event ID whose terminal result is available from the status API.
- An 810 whose total differs from its PO becomes `MATCH_EXCEPTION`; a matching invoice persists.
- Redis-backed limits emit `429`; absent, invalid, and under-scoped JWTs emit `401`/`403`.
- R1–R5 are tested independently, including boundaries, missing references, and negative paths.

## Metrics

Prometheus scrapes both Spring Boot services every ten seconds. Grafana provisions an `EDIFlow Operations` dashboard with ingestion rate, parse-error rate, Kafka lag, p95 processing latency, and open DLQ depth. Locally, Prometheus is at `http://localhost:9090` and Grafana at `http://localhost:3000`.

Metric names:

- `ediflow_documents_ingested_total{documentType}`
- `ediflow_parse_errors_total`
- `ediflow_kafka_consumer_lag`
- `ediflow_processing_latency_seconds` (histogram)
- `ediflow_dead_letter_queue_depth`
