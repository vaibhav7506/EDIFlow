# EDIFlow

EDIFlow is secured B2B integration middleware for ANSI X12 purchase orders and invoices. It parses partner EDI into canonical events, streams them through Kafka, applies deterministic and auditable business rules, and exposes the resulting operational state through an OAuth2/mTLS API Gateway and dashboard.

## Problem statement

Production B2B integrations must handle more than text parsing: delimiters are declared by each X12 interchange, messages can be duplicated, partner permissions differ, downstream decisions must be auditable, and failures need a recoverable dead-letter path. EDIFlow demonstrates that middleware pattern with Java 21, Spring Boot, StAEDI, Kafka in KRaft mode, PostgreSQL, Redis, Spring Authorization Server, Spring Cloud Gateway, Flyway, and React.

## Architecture

- `authorization-server` issues JWT access tokens with `client_credentials`. The bundled registrations demonstrate partner, read-only, and administrator clients.
- `gateway` is the only externally exposed API. It validates JWTs, enforces scopes, forwards trusted partner identity headers, and rate-limits by OAuth `client_id` using Redis.
- The Gateway requires a certificate signed by the local EDIFlow CA at the TLS handshake. This applies to every Gateway endpoint, which is stricter than requiring mTLS only for uploads.
- `ingestion-service` accepts 850/810 uploads only through the Gateway and uses the authenticated partner identity rather than a caller-supplied header.
- `order-service` owns idempotency, state transitions, business rules, persistence, invoice matching, DLQ records, and retry.
- The dashboard reaches APIs through the Gateway using its own mTLS client certificate and asks the operator for OAuth client credentials. No OAuth secret is built into the frontend.

Scopes:

| Scope | Access |
|---|---|
| `edi:write` | Upload X12 documents and poll their processing status |
| `edi:read` | Query orders, invoices, and interchanges |
| `admin` | Approve partners and retry dead letters |

## Run

```powershell
Copy-Item .env.example .env
docker compose up --build
```

The certificate-init container creates the development CA, Gateway keystore, truststore, and client certificates under the ignored `.certs` directory. Externally reachable services are:

- Dashboard: `http://localhost:5173`
- Secured Gateway: `https://localhost:8443`

Kafka, Redis, PostgreSQL, Authorization Server, ingestion, and order services are internal-only Docker services.

Prometheus is available at `http://localhost:9090`; the provisioned Grafana dashboard is at `http://localhost:3000`. Change the disposable Grafana credentials from `.env.example` outside local development.

## Job-description mapping

| Integration-engineering requirement | EDIFlow evidence |
|---|---|
| Java and Spring middleware | Java 21 Spring Boot services with REST, Kafka, JPA, validation, and Actuator |
| EDI/X12 integration | Delimiter-aware StAEDI parsing of 850 purchase orders and 810 invoices into canonical events |
| Message-oriented integration | Kafka KRaft topics, consumer idempotency, retries, and recoverable dead letters |
| Rules and reconciliation | Isolated R1–R5 rules plus invoice-to-PO three-way total matching |
| Security | OAuth2 client credentials, scoped JWTs, end-to-end mTLS, and Redis-backed rate limits |
| Auditability | Persisted state-machine transitions and a searchable operator Audit page |
| Testing | Unit, Testcontainers Kafka/PostgreSQL/Redis integration, and Playwright/WCAG suites required by CI |
| Operations | Micrometer metrics, Prometheus scraping, provisioned Grafana dashboard, Docker Compose, and k3s/GHCR deployment |

## Phase 4 deployment

The repository includes a conservative single-node k3s deployment and a `main`-branch GitHub Actions pipeline. CI runs the automated suite, publishes ARM64/AMD64 images to GHCR, deploys immutable commit-SHA tags from a self-hosted runner on the Oracle VM, waits for every rollout, and verifies the public mTLS health endpoint. Kubernetes Secrets are created from encrypted GitHub secrets and are not committed.

See [the k3s deployment runbook](docs/deployment.md) for VM bootstrap, DNS/certificate preparation, required GitHub settings, topology, verification, and rollback.

See [the architecture decision log](docs/decisions.md) for the security, messaging, persistence, retry, and deployment trade-offs. See [the application workflow](docs/flow.md) for end-to-end diagrams, the reason each stage exists, and a current competitor comparison.

## Hosted dashboard

The production Vite assets are deployed at [dashboard-sigma-swart-17.vercel.app](https://dashboard-sigma-swart-17.vercel.app). This is the Vercel-hosted dashboard surface; the complete runtime still belongs on the documented k3s target because Kafka, PostgreSQL, Redis, long-running JVM consumers, persistent volumes, and the mTLS Gateway are not static frontend workloads. Remote login and API operations require a public Gateway plus a server-side proxy that can present the dashboard client certificate. Never embed that private key in the browser bundle.

## Verify OAuth2 and mTLS

Get a partner token:

```powershell
$password = ConvertTo-SecureString "changeit" -AsPlainText -Force
$clientCertificate = Get-PfxCertificate -FilePath .certs/partner-client.p12 -Password $password
$basic = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("partner-client:change-me-partner"))
$tokenResponse = Invoke-RestMethod -SkipCertificateCheck -Certificate $clientCertificate -Method Post -Uri https://localhost:8443/oauth2/token -Headers @{ Authorization = "Basic $basic" } -ContentType "application/x-www-form-urlencoded" -Body @{ grant_type = "client_credentials"; scope = "edi:read edi:write" }
$accessToken = $tokenResponse.access_token
```

Upload through the secured Gateway:

```powershell
$accepted = Invoke-RestMethod -SkipCertificateCheck -Certificate $clientCertificate -Method Post -Uri https://localhost:8443/api/v1/edi/upload -Headers @{ Authorization = "Bearer $accessToken" } -Form @{ file = Get-Item ingestion-service/src/test/resources/fixtures/valid-850.edi }
$accepted
Invoke-RestMethod -SkipCertificateCheck -Certificate $clientCertificate -Method Get -Uri "https://localhost:8443/api/v1/edi/status/$($accepted.eventId)" -Headers @{ Authorization = "Bearer $accessToken" }
```

The upload endpoint deliberately returns `202 Accepted` because Kafka processing is asynchronous. Its body is stable JSON shaped like `{"eventId":"...","documentType":"PO_850","status":"QUEUED"}`. Poll `GET /api/v1/edi/status/{eventId}` until it returns the terminal processing status. A brief `404` means the consumer has not recorded the event yet and can be retried.

`-SkipCertificateCheck` is appropriate only for this generated local-development CA. Trust `.certs/ca.crt` in the client environment instead for non-local deployments.

Security expectations:

- No client certificate: TLS handshake is rejected.
- Certificate without a bearer token: `401`.
- Read-only token used for upload: `403`.
- More than the configured client burst: `429`.
- The ingestion and order-service host ports are not published.

The credentials in `.env.example` are disposable local-development placeholders. Replace them in the ignored `.env` file before using the stack outside a local machine.

## Phase 2 processing fixtures

Fixtures live under `ingestion-service/src/test/resources/fixtures`. Upload the valid 850 and its duplicate first, followed by the matching and mismatched 810 documents. `CTT01` is interpreted as the PO1 line-item count; a mismatch is rejected with `LINE_COUNT_MISMATCH`, while `CTT02` is never interpreted as a monetary total. The duplicate creates no second interchange or order and its event status becomes `DUPLICATE`; the matching invoice becomes `PERSISTED`; the mismatched invoice becomes `MATCH_EXCEPTION`; the malformed document returns `422` and is persisted in the DLQ.

For frontend development, generate `.certs` with the Compose certificate service and start the secured Gateway before running `npm.cmd run dev` in `dashboard`. Vite proxies `/api`, `/oauth2`, and `/.well-known` through that mTLS Gateway using the dashboard client certificate, so the client-credentials login exercises the real authorization and routing path.

## Verification commands

```powershell
.\gradlew.bat --no-daemon test
.\gradlew.bat --no-daemon :integration-tests:integrationTest :gateway-integration-tests:integrationTest
.\gradlew.bat --no-daemon :authorization-server:bootJar :gateway:bootJar :ingestion-service:bootJar :order-service:bootJar
Set-Location dashboard
npm.cmd ci
npm.cmd run build
npm.cmd run playwright:install
npm.cmd run test:e2e
```

See [the testing and observability guide](docs/testing.md) for the risk-based test layers, critical scenarios, and metrics.

## Resume bullets

- Built an EDI X12 (850/810) event-driven middleware pipeline in Java/Spring Boot, with Kafka-based decoupling, dead-letter recovery, and an OAuth2/mTLS-secured API Gateway.
- Implemented a deterministic business-rule and three-way-match engine with fully auditable state transitions.
- Delivered Testcontainers-backed integration tests, Playwright E2E and accessibility coverage, and a Prometheus/Grafana observability stack.

Flyway migrations remain append-only: Phase 1 is `V1__init.sql`; Phase 2 additions are in `V2__streaming_and_invoices.sql`; `V3__enforce_interchange_idempotency.sql` adds database-enforced control-number uniqueness; and `V4__add_document_processing_status.sql` adds event-status polling.
