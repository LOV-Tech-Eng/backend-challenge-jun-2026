# CloudCart Dispute Intelligence API

A backend service that ingests chargeback data from multiple payment processor formats,
scores each dispute with a win probability and recommended action, and exposes a queryable
REST API for CloudCart's risk team to prioritize dispute responses.

---

## Setup (< 5 steps)

**Requirements:** Java 21, Maven 3.8+ (or use the included `./mvnw` wrapper).

```bash
# 1. Clone the repo
git clone <repo-url> && cd backend-challenge-jun-2026

# 2. Build and run all tests
./mvnw clean verify

# 3. Start the service (auto-seeds 150+ disputes at startup)
./mvnw spring-boot:run

# 4. Open Swagger UI
open http://localhost:8080/swagger-ui.html

# 5. Check health
curl http://localhost:8080/actuator/health
```

The service starts with an in-memory H2 database pre-loaded with 150+ realistic disputes
across 5 merchants, 4 currencies, and 5 reason categories. No external database required.

---

## API Documentation

Full interactive docs: **`http://localhost:8080/swagger-ui.html`**

OpenAPI JSON: `http://localhost:8080/v3/api-docs`

### Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/v1/disputes/ingest/processor-a` | Ingest Processor A format (camelCase, dollar amounts) |
| `POST` | `/api/v1/disputes/ingest/processor-a/bulk` | Bulk ingest array of Processor A records |
| `POST` | `/api/v1/disputes/ingest/processor-b` | Ingest Processor B format (snake_case, cents) |
| `POST` | `/api/v1/disputes/ingest/processor-b/bulk` | Bulk ingest array of Processor B records |
| `GET`  | `/api/v1/disputes` | List disputes with optional filters + pagination |
| `GET`  | `/api/v1/disputes/{id}` | Get single dispute by ID |
| `GET`  | `/api/v1/disputes/summary` | Global summary statistics |
| `GET`  | `/api/v1/merchants/{merchantId}/summary` | Merchant-scoped summary statistics |
| `GET`  | `/actuator/health` | Health check |

---

## Evaluator Guide (curl examples)

### 1. Ingest a chargeback — Processor A format

```bash
curl -s -X POST http://localhost:8080/api/v1/disputes/ingest/processor-a \
  -H "Content-Type: application/json" \
  -d '{
    "disputeId": "PA-DEMO-001",
    "merchantId": "MERCHANT_DEMO",
    "transactionAmount": 1200.00,
    "currencyCode": "USD",
    "reasonCode": "13.1",
    "chargebackDate": "2026-06-20",
    "dueDate": "2026-07-08",
    "transactionDate": "2026-06-10",
    "customerEmail": "customer@example.com",
    "orderId": "ORDER-42"
  }' | jq .
```

Expected response (`201 Created`):
```json
{
  "id": "...",
  "processorId": "PROCESSOR_A",
  "reasonCategory": "PRODUCT_NOT_RECEIVED",
  "winProbability": 65,
  "recommendedAction": "CONTEST",
  "urgencyLevel": "MEDIUM",
  "scoringReason": "Base: PRODUCT_NOT_RECEIVED(50) + amount≥$500(+5) + 5-9days(+0) = 55 → ..."
}
```

### 2. Ingest — Processor B format (snake_case, amount in cents)

```bash
curl -s -X POST http://localhost:8080/api/v1/disputes/ingest/processor-b \
  -H "Content-Type: application/json" \
  -d '{
    "dispute_reference": "PB-DEMO-001",
    "merchant_ref": "MERCHANT_DEMO",
    "amount_cents": 350000,
    "currency": "BRL",
    "reason_code": "4837",
    "chargeback_dt": "2026-06-18",
    "response_deadline_dt": "2026-07-02",
    "txn_date": "2026-06-05",
    "email": "comprador@example.com.br"
  }' | jq .
```

Amount 350000 cents = **BRL 3500.00**. Note: same chargeback re-submitted returns `200 OK` (idempotent).

### 3. Query disputes with combined filters

```bash
# All HIGH urgency FRAUD disputes for MERCHANT_001 above $500
curl -s "http://localhost:8080/api/v1/disputes?merchantId=MERCHANT_001&reasonCategory=FRAUD&urgencyLevel=HIGH&minAmount=500" | jq .
```

```bash
# All disputes that should be contested, paginated
curl -s "http://localhost:8080/api/v1/disputes?recommendedAction=CONTEST&page=0&size=10" | jq .
```

```bash
# Disputes by date range
curl -s "http://localhost:8080/api/v1/disputes?dateFrom=2026-05-01&dateTo=2026-06-30&merchantId=MERCHANT_002" | jq .
```

### 4. Merchant summary statistics

```bash
curl -s http://localhost:8080/api/v1/merchants/MERCHANT_001/summary | jq .
```

Response includes: `totalDisputeCount`, `totalAmountAtRisk` (per currency), `totalVolume` (per currency),
`averageWinProbability`, `byReasonCategory` (count + amount), `byRecommendedAction`, `byUrgencyLevel`.

### 5. Global summary

```bash
curl -s http://localhost:8080/api/v1/disputes/summary | jq .
```

### 6. Idempotency demonstration

```bash
# Send the same payload twice — second call returns 200 (not 201), same body
curl -s -o /dev/null -w "%{http_code}" -X POST http://localhost:8080/api/v1/disputes/ingest/processor-a \
  -H "Content-Type: application/json" \
  -d '{"disputeId":"IDEM-TEST","merchantId":"M1","transactionAmount":100,"currencyCode":"USD","reasonCode":"10.4","chargebackDate":"2026-06-01","dueDate":"2026-07-01"}'
# → 201

curl -s -o /dev/null -w "%{http_code}" -X POST http://localhost:8080/api/v1/disputes/ingest/processor-a \
  -H "Content-Type: application/json" \
  -d '{"disputeId":"IDEM-TEST","merchantId":"M1","transactionAmount":100,"currencyCode":"USD","reasonCode":"10.4","chargebackDate":"2026-06-01","dueDate":"2026-07-01"}'
# → 200 (no duplicate created)
```

### 7. Validation error demonstration

```bash
curl -s -X POST http://localhost:8080/api/v1/disputes/ingest/processor-a \
  -H "Content-Type: application/json" \
  -d '{"disputeId":"","transactionAmount":-50,"currencyCode":"XYZ"}' | jq .
# → 400 with violations array listing each field failure
```

---

## Design Overview

### Architecture

The service follows a layered package structure under `com.cloudcart.disputes`:

- **`api/`** — REST controllers and DTOs. Two separate ingest endpoints (one per processor format) with
  typed schemas, validated with Bean Validation. A `@RestControllerAdvice` maps all exceptions to a
  consistent JSON error structure — no stack traces ever reach the caller.

- **`domain/`** — Core business logic with zero Spring dependencies. `WinProbabilityEngine` is a pure
  function: given reason category, amount, and days remaining, it returns a deterministic score plus a
  human-readable explanation. `Dispute` is the JPA entity and domain model in one (pragmatic for a POC).

- **`service/`** — `DisputeIngestionService` orchestrates: normalize → score → check idempotency →
  persist. `DisputeQueryService` handles filtering via JPA Specifications (composable predicates) and
  aggregation for summary statistics.

- **`infra/`** — `DataLoader` seeds 150+ disputes at startup by running them through the full ingestion
  pipeline, proving the pipeline handles realistic load and ensuring scoring consistency.

### Win Probability Scoring

The scoring algorithm is deterministic and rule-based (no ML required for this POC). Given the same
inputs it always returns the same score, making it auditable and testable:

```
Step 1: Base score by reason category
  FRAUD = 20  |  SUBSCRIPTION_CANCELLED = 40  |  PRODUCT_NOT_RECEIVED = 50
  PRODUCT_UNACCEPTABLE = 55  |  DUPLICATE_PROCESSING = 65

Step 2: Amount modifier (mutually exclusive, pick highest)
  amount ≥ $1000 → +10  |  amount ≥ $500 → +5  |  else → +0

Step 3: Deadline modifier
  ≥ 10 days → +10  |  5–9 days → +0  |  1–4 days → -10  |  expired → +0

Step 4: Clamp result to [0, 100]

Step 5: Urgency — ≤ 3 days (or expired) → HIGH | 4–10 days → MEDIUM | > 10 days → LOW

Step 6: Recommended action (first match wins)
  days < 0 (past deadline)   → ACCEPT      (cannot contest — window is closed)
  days == 0 (today)          → falls through to URGENT_REVIEW if prob ≥ 40
  ≤ 3 days AND prob ≥ 40     → URGENT_REVIEW
  prob < 40 OR amount < $50  → ACCEPT
  prob ≥ 60                  → CONTEST
  else                       → ACCEPT

Note: expired disputes (deadline passed) are classified as urgencyLevel=HIGH and
recommendedAction=ACCEPT. HIGH urgency signals that the merchant's attention is still
needed (acknowledge the loss, update records, investigate root cause). ACCEPT means
"do not attempt to contest" — the card network window has closed, not that the
dispute is unimportant.
```

Each response includes a `scoringReason` field with a human-readable breakdown
(e.g. `"Base: DUPLICATE_PROCESSING(65) + amount≥$1000(+10) + ≥10days(+10) = 85 → CONTEST"`).

### Two Processor Formats

- **Processor A** (`/ingest/processor-a`): camelCase JSON, amounts as decimal dollars.
  Simulates Stripe-style chargeback notifications.
- **Processor B** (`/ingest/processor-b`): snake_case JSON (via `@JsonProperty`), `amount_cents`
  as integer minor units (divided by 100 during normalization). Simulates Adyen-style notifications.

Both formats are normalized to the same internal `Dispute` entity. Adding a third processor requires
one new endpoint + DTO + normalizer method — no existing logic changes.

### Idempotency

The natural key `(processorId, processorDisputeId)` enforces deduplication at both the application
layer (check-then-insert) and database layer (unique constraint). First ingest → `201 Created`.
Re-ingest of the same key → `200 OK` with the existing record. No duplicate disputes are ever created.

### Trade-offs made for the 2-hour scope

- **H2 in-memory**: No external database dependency. Data is ephemeral — the DataLoader re-seeds on
  every restart, which is fine for a demo. In production, replace with PostgreSQL.
- **Status computed at ingest**: A dispute ingested as OPEN stays OPEN in the DB until re-queried.
  Production would run a background job to flip OPEN → EXPIRED as deadlines pass.
- **No re-scoring on re-ingest**: The idempotent replay returns the original score. If the processor
  corrects a deadline, the score doesn't update. Production would support explicit PATCH to re-score.
- **No authentication**: The POC has no API key or JWT auth. Production would require merchant-scoped
  access control so merchants can only see their own disputes.

---

## Test Data

150+ disputes are loaded at startup via `DataLoader.java`. Distribution:

- **5 merchants**: `MERCHANT_001` (BRL, FRAUD-heavy) through `MERCHANT_005` (multi-currency, all categories)
- **4 currencies**: USD, BRL, MXN, COP
- **5 reason categories**: FRAUD, PRODUCT_NOT_RECEIVED, PRODUCT_UNACCEPTABLE, DUPLICATE_PROCESSING, SUBSCRIPTION_CANCELLED
- **Deadline variety**: ~20% expired, ~15% urgent (1–3 days), ~25% medium (4–10 days), ~40% comfortable (11+ days)
- **Amount range**: $15 to $3,500
- **Both processor formats**: PROCESSOR_A (camelCase) and PROCESSOR_B (cents) records per merchant

No manual data loading step needed — just start the service.

---

## Running Tests

```bash
./mvnw test
```

Test coverage:
- `WinProbabilityEngineTest` — 44 table-driven unit tests covering all categories, amount boundaries,
  deadline boundaries, all three recommended actions, the expired-deadline trap, and determinism
- `ProcessorNormalizerTest` — 17 tests covering field mapping and the cents-to-decimal conversion
- `DisputeIngestionControllerTest` — 9 integration tests covering happy paths, idempotency, and validation
- `DisputeQueryControllerTest` — 14 integration tests covering all filter combinations, pagination, 404s

---

## What I'd Improve With More Time

1. **Persistent database (PostgreSQL)**: Replace H2 with PostgreSQL via Docker Compose for realistic
   multi-instance deployment. The JPA/Specification layer is already DB-agnostic.

2. **Background deadline expiry job**: A `@Scheduled` task that runs hourly to flip OPEN disputes
   to EXPIRED as their `responseDeadline` passes. Currently, status is only computed at ingest time.

3. **Merchant historical win rate**: The scoring formula has a slot for merchant win rate (`if available`
   per the spec). Currently defaults to the base score. With real data, each merchant's past CONTEST
   outcomes would be tracked and factored in as a +/− modifier.

4. **Re-scoring on re-ingest**: Allow processors to resubmit with corrected deadlines. Currently the
   idempotent replay returns the original score. An explicit `/recalculate` sub-resource or upsert
   mode would handle this.

5. **Authentication and multi-tenancy**: Each merchant should only see their own disputes. A JWT-based
   auth layer with `merchantId` extracted from the token would enforce this at the service layer.

6. **Stretch Goal S-B (Historical Trends)**: Weekly dispute volume + reason breakdown per merchant.
   The data model already supports time-series queries; `DisputeQueryService` would need a
   `GROUP BY WEEK(disputeDate)` query with JPA's `@Query`.

7. **Observability**: Structured JSON logging (Logback JSON layout), Micrometer metrics for
   ingest rate and score distribution, and distributed tracing (OpenTelemetry) for production debugging.

8. **Docker Compose**: A `docker-compose.yml` with PostgreSQL + the Spring Boot service for
   one-command local setup without JDK 21 requirement.

---

## Links

- **Repository**: _(submitted GitHub URL)_
- **Live API**: _(Railway deploy URL — time-permitting)_
- **Swagger UI**: `http://localhost:8080/swagger-ui.html` (local) or `<railway-url>/swagger-ui.html`
