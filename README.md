# EDIFlow

EDIFlow is a B2B integration MVP that accepts ANSI X12 850 purchase orders, safely interprets the ISA-defined delimiters with StAEDI, persists a canonical purchase order, and exposes it through a small operational API and dashboard.

## Problem statement

B2B partners often exchange purchase orders in legacy-but-critical EDI formats. The practical engineering challenge is not merely splitting a text file: X12 delimiters come from the interchange envelope, structural errors must be explained clearly, and the resulting data has to become queryable operational state. EDIFlow demonstrates that foundation with Java 21, Spring Boot, PostgreSQL, Flyway, and StAEDI. Kafka, DLQ recovery, security gateway, and richer operations deliberately begin in later phases.

## Phase 1 capabilities

- `POST /api/v1/edi/upload` accepts an 850 as `multipart/form-data` with a `file` part.
- The partner is resolved from `ISA06`; an unknown sender becomes a `PENDING` partner.
- The interchange, order, and lines persist atomically. Invalid X12 / bad `SE01` counts return structured `422` responses.
- `GET /api/v1/orders` supports `partnerId`, `status`, `from`, `to`, `page`, and `size` filters.
- `GET /api/v1/orders/{id}` returns the order and line items.

## Run with Docker

```powershell
Copy-Item .env.example .env
docker compose up --build
```

The API is at `http://localhost:8080`; health is at `/actuator/health`. Upload `src/test/resources/fixtures/valid-850.edi` from the dashboard or with:

```powershell
curl.exe -F "file=@src/test/resources/fixtures/valid-850.edi" http://localhost:8080/api/v1/edi/upload
```

## Local checks

```powershell
.\gradlew.bat test
cd dashboard; npm.cmd install; npm.cmd run build
```

The checked-in fixtures include a valid 850 and an invalid transaction segment-count example. The 810 and duplicate fixtures are intentionally Phase 2 scope.
