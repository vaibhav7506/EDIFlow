# EDIFlow architecture decisions

This log records the decisions that materially shape security, data correctness, operations, and deployment. It explains the current implementation, not an immutable roadmap. Revisit a decision when its assumptions change.

## Decision summary

| ID | Decision | Status |
|---|---|---|
| ADR-001 | Split the MVP into independently owned services | Accepted |
| ADR-002 | Use Kafka and asynchronous `202 Accepted` ingestion | Accepted |
| ADR-003 | Parse X12 with StAEDI into versioned canonical events | Accepted |
| ADR-004 | Enforce idempotency in PostgreSQL, not only in application code | Accepted |
| ADR-005 | Derive partner identity from OAuth instead of caller headers | Accepted |
| ADR-006 | Require both mTLS and scoped OAuth2 tokens at the Gateway | Accepted |
| ADR-007 | Rate-limit by OAuth client through Redis | Accepted |
| ADR-008 | Keep business rules deterministic, ordered, and auditable | Accepted |
| ADR-009 | Persist dead letters and make retries explicit | Accepted |
| ADR-010 | Treat poison-message retry as one attempt | Accepted |
| ADR-011 | Make operational state and metrics first-class features | Accepted |
| ADR-012 | Use append-only Flyway migrations | Accepted |
| ADR-013 | Package the stack with containers and deploy the backend to k3s | Accepted |
| ADR-014 | Use Vercel only for the static dashboard surface | Accepted with constraint |
| ADR-015 | Keep secrets, keys, and generated certificates out of Git | Accepted |
| ADR-016 | Require unit, infrastructure integration, browser, and accessibility tests | Accepted |

## ADR-001: Split the MVP into independently owned services

**Context.** The original application combined ingress, parsing, business decisions, storage, and HTTP queries. That made security boundaries and failure ownership ambiguous.

**Decision.** Use `authorization-server`, `gateway`, `ingestion-service`, and `order-service`, with a shared canonical-event package. The order service owns PostgreSQL state; the ingestion service does not write business entities.

**Why.** Each service has one reason to change, can be scaled independently, and exposes a clear failure boundary. The trade-off is more deployment and local-environment complexity.

## ADR-002: Kafka and asynchronous ingestion

**Decision.** A successful upload returns `202 Accepted` with a stable event ID. Kafka separates parsing from business processing, and `GET /api/v1/edi/status/{eventId}` reports the durable result.

**Why.** EDI processing can be slow or temporarily unavailable downstream. A synchronous request would couple client latency and availability to every service. Eventual consistency requires clients to poll and makes correlation IDs mandatory.

## ADR-003: StAEDI and canonical events

**Decision.** Use StAEDI for delimiter-aware ANSI X12 parsing. Convert 850 and 810 transactions into a typed canonical event before publishing them.

**Why.** X12 separators are declared by the interchange and cannot be parsed safely with fixed string splits. A canonical contract keeps Kafka consumers independent from parser details. Schema evolution must remain backward-compatible.

## ADR-004: Database-enforced idempotency

**Decision.** Identify an interchange by partner ID plus ISA and ST control numbers. Enforce that tuple with `uq_edi_interchange_control_numbers` and translate the losing concurrent insert into `DUPLICATE`.

**Why.** An application-level read-before-write check races under concurrency. PostgreSQL is the final authority and protects correctness across replicas and restarts.

## ADR-005: Authenticated partner identity

**Decision.** The Gateway derives the trusted partner from the OAuth client and replaces inbound identity headers. Ingestion ignores a caller-supplied `X-Partner-Id`.

**Why.** Business data must not be able to impersonate another trading partner. This also provides a stable tenant key for persistence, Kafka partitioning, auditing, and rate limits.

## ADR-006: mTLS plus OAuth2 scopes

**Decision.** Require a CA-signed client certificate for every Gateway connection and a JWT for protected APIs. Use `edi:write`, `edi:read`, and `admin` scopes.

**Why.** mTLS authenticates the connecting system; OAuth scopes authorize the requested action. Either mechanism alone leaves a gap. Certificate issuance and rotation add operational work, which is documented in the deployment runbook.

## ADR-007: Distributed rate limiting

**Decision.** Store token-bucket state in Redis and key limits by OAuth client ID.

**Why.** IP addresses are unreliable behind proxies and do not represent a B2B client. Redis keeps limits consistent across Gateway replicas. Redis availability therefore becomes part of the ingress availability budget.

## ADR-008: Deterministic and auditable rules

**Decision.** Implement partner approval, line count, line value, invoice reference, and invoice total matching as ordered rules. Persist every state transition and its reason.

**Why.** Business rejection must be reproducible and explainable. The rule engine favors explicit code and tests over opaque runtime configuration; changing rules currently requires a release.

## ADR-009: Persisted dead letters and explicit retry

**Decision.** Store parse and processing failures in PostgreSQL, expose them to administrators, and publish a retry only after an explicit operator action.

**Why.** Kafka retention alone is not an operator workflow. Persisted records make errors searchable and auditable. Retrying cannot guarantee success when the payload or rule condition is unchanged.

## ADR-010: One poison-message retry attempt

**Decision.** When a retried raw document remains malformed, `IngestionService` publishes one replacement DLQ event and `RetryConsumer` acknowledges the retry record instead of rethrowing the parse exception.

**Why.** Rethrowing invoked Spring Kafka's listener retries and amplified one operator action into ten DLQ records. Unexpected infrastructure exceptions still escape and retain framework retry behavior.

## ADR-011: Operational state and metrics

**Decision.** Export ingestion count, parse errors, consumer lag, processing latency, duplicate count, and DLQ depth through Micrometer. Scrape with Prometheus and provision a Grafana dashboard.

**Why.** A message pipeline can accept traffic while silently falling behind. Business status APIs explain individual documents; metrics reveal systemic health.

## ADR-012: Append-only Flyway migrations

**Decision.** Never rewrite an applied migration. Correct schemas with a new versioned migration.

**Why.** Rewriting history makes existing environments diverge from clean installs. A rollback of application code does not automatically roll back data, so deployment planning must treat migrations as forward-only.

## ADR-013: Containers and k3s for the full stack

**Decision.** Build multi-architecture OCI images in GitHub Actions and run the stateful backend on k3s. Keep Kafka, PostgreSQL, Redis, and internal services off public host ports.

**Why.** The system needs long-running JVM consumers, stable networking, volumes, Kafka, and PostgreSQL. A single-node k3s target is cost-conscious and reproducible, but it is not highly available.

## ADR-014: Vercel is a dashboard deployment target, not the backend

**Decision.** Vercel may host the Vite static assets. The complete EDIFlow runtime remains on k3s. A functional remote dashboard additionally requires a public Gateway and a trusted server-side proxy that presents the dashboard client certificate; the certificate must never be shipped to browser JavaScript.

**Why.** Vercel supports Vite deployments, but EDIFlow's JVM consumers, Kafka, PostgreSQL, Redis, persistent volumes, and inbound client-certificate policy are not a static frontend workload. Deploying only the assets is useful as a UI preview, not proof of full-stack production readiness.

## ADR-015: Secret and certificate hygiene

**Decision.** Ignore `.env`, `.certs`, `.deployment-certs`, and Vercel's local project metadata. Commit only examples and secret schemas. Inject real material through local ignored files, GitHub secrets, Kubernetes Secrets, or Vercel environment variables.

**Why.** Repository history is effectively permanent. A secret committed and later deleted must still be rotated.

## ADR-016: Layered verification

**Decision.** Require unit tests, Testcontainers-backed Kafka/PostgreSQL/Redis integration tests, Playwright operator journeys, WCAG checks, container health checks, and an external post-deployment mTLS probe.

**Why.** Parser tests cannot prove broker delivery, and HTTP smoke tests cannot prove business invariants or UI accessibility. Layering keeps most feedback fast while exercising real infrastructure at the boundaries where defects are expensive.
