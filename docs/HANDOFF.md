# HANDOFF — single continuity doc

> Purpose: if I run out of tokens or switch account/AI mid-challenge, whoever continues reads
> THIS file + `00-context.md` and can pick up with zero context loss. Keep it short and current.

---

## Current state

- Plan approved. No code written yet.

---

## Next steps (ordered by priority)

1. **Slice 0** — Maven project scaffold, POM, application.yml, package skeleton
2. **Slice 1** — Domain enums + Dispute entity + ScoringResult record
3. **Slice 2** — WinProbabilityEngine (pure function) + DisputeScoringServiceTest (table-driven)
4. **Slice 3** — ProcessorA/B DTOs + ProcessorNormalizer + normalizer unit tests
5. **Slice 4** — DisputeRepository + DisputeIngestionService (idempotency) + validation DTOs
6. **Slice 5** — DisputeIngestionController (2 endpoints) + @RestControllerAdvice + validation tests
7. **Slice 6** — DisputeSpecifications + DisputeQueryController (GET /disputes, GET /disputes/{id}) + query tests
8. **Slice 7** — DisputeQueryService.getSummary() + MerchantSummaryController + summary tests
9. **Slice 8** — DataLoader CommandLineRunner (150+ disputes, 5 merchants, all currencies)
10. **Slice 9** — application.yml security settings, Swagger config, Actuator
11. **Slice 10** — README (setup, evaluator guide, design overview, curl commands)
12. **Audit** — `/audit` command; fix blockers
13. **Delivery** — README → commit+push → Railway deploy (time-permitting)

---

## Architecture & key decisions

### Stack

- Java 21 + Spring Boot 3.x (Maven)
- H2 in-memory (auto-creates schema from `@Entity`, seeded by `DataLoader`)
- Spring Data JPA + `JpaSpecificationExecutor` for dynamic filtering
- springdoc-openapi 2.x → Swagger at `/swagger-ui.html`
- spring-boot-starter-actuator → `/actuator/health`
- JUnit 5 + Mockito; no Lombok; no Testcontainers

### Package structure

```
com.cloudcart.disputes
├── api/
│   ├── controller/   DisputeIngestionController, DisputeQueryController, MerchantSummaryController
│   ├── dto/          ProcessorARequest, ProcessorBRequest, DisputeResponse, DisputeSummaryResponse, ErrorResponse
│   └── mapper/       ProcessorNormalizer (reason code → ReasonCategory map lives here)
├── domain/
│   ├── model/        Dispute (@Entity), enums: Currency, ReasonCategory, DisputeStatus, RecommendedAction, UrgencyLevel
│   ├── engine/       WinProbabilityEngine (pure fn, zero Spring deps), ScoringResult (record)
│   └── port/         DisputeRepository (extends JpaRepository + JpaSpecificationExecutor)
├── service/          DisputeIngestionService (@Transactional), DisputeQueryService (@Transactional readOnly)
└── infra/
    ├── config/       AppConfig, GlobalExceptionHandler (@RestControllerAdvice)
    └── DataLoader    CommandLineRunner — seeds 150+ disputes at startup
```

### Ingestion endpoints (two, not one)

```
POST /api/v1/disputes/ingest/processor-a   → ProcessorARequest (camelCase, BigDecimal dollars)
POST /api/v1/disputes/ingest/processor-b   → ProcessorBRequest (@JsonProperty snake_case, amount_cents ÷ 100)
```

Single endpoint with discriminator was rejected: no Jackson polymorphism complexity, clean typed Swagger schemas.

### Win probability scoring rules

```
Step 1 — Base score by ReasonCategory:
  FRAUD                  → 20
  SUBSCRIPTION_CANCELLED → 40
  PRODUCT_NOT_RECEIVED   → 50
  PRODUCT_UNACCEPTABLE   → 55
  DUPLICATE_PROCESSING   → 65

Step 2 — Amount modifier (mutually exclusive, first match):
  amount ≥ 1000 → +10
  amount ≥ 500  → +5
  else          → +0

Step 3 — Deadline modifier:
  days ≥ 10 → +10
  days 5–9  → +0
  days 1–4  → -10
  days ≤ 0  → +0  (expired; action handles this)

Step 4 — Clamp to [0, 100]

Step 5 — Urgency (independent):
  days ≤ 3  (including ≤ 0) → HIGH
  days 4–10                  → MEDIUM
  days > 10                  → LOW

Step 6 — Recommended action (first match wins):
  1. days ≤ 0                          → ACCEPT  (expired, cannot contest)
  2. days ≤ 3 AND prob ≥ 40            → URGENT_REVIEW
  3. prob < 40 OR amount < 50          → ACCEPT
  4. prob ≥ 60 AND days > 0            → CONTEST
  5. else                              → ACCEPT
```

### Key ADRs

| Decision | Why | Alternative rejected |
|----------|-----|----------------------|
| Two separate ingest endpoints | Clean typed schemas in Swagger; no Jackson `@JsonTypeInfo` hacks; adding processor = +1 endpoint | Single endpoint + `processorType` discriminator |
| BigDecimal dollars (not integer cents) | Challenge uses "$15–$3,500" everywhere; evaluators read responses; Processor B cents ÷ 100 at normalization | Integer minor units (correct for production ledger, confusing for evaluator) |
| DataLoader CommandLineRunner | Scoring computed by engine = consistent; tests the full ingestion pipeline at startup | `data.sql` — pre-computed scores drift from engine if rules change |
| JPA Specifications for filtering | Composable optional predicates; no combinatorial explosion of `findBy*` methods | Multiple `@Query` methods, QueryDSL |
| Status stored at ingest | Simple DB queries (`status = 'EXPIRED'` filter works); no background job for POC | Compute on read (accurate but requires translating filter params to `responseDeadline < now` in JPQL) |
| No Lombok | Zero annotation processing issues; readable for evaluators; Java Records for DTOs | Lombok — saves boilerplate but risky in unfamiliar Maven setups |

### Idempotency key

`(processorId, processorDisputeId)` — unique constraint at DB level.
First ingest → `201 Created`. Re-ingest same key → `200 OK`, return existing record.

### Error model

```json
{ "status": 400, "error": "BAD_REQUEST", "code": "VALIDATION_ERROR",
  "message": "...", "timestamp": "...", "violations": [{ "field": "...", "message": "..." }] }
```

No stack traces ever reach the caller (`server.error.include-stacktrace=never`).

### Security guardrails (in scope for POC)

- H2 console disabled
- Bean Validation on ingest DTOs (`@NotBlank`, `@Positive`, currency pattern, enum enforcement)
- `winProbability` / `recommendedAction` absent from ingest DTOs (computed server-side only)
- `customerEmail` masked in logs
- Actuator exposed: `health` only (no `env`, `beans`)
- `spring.jackson.deserialization.fail-on-unknown-properties=true`

---

## Review / audit findings

_Filled during `/audit`._

---

## How to run & deliver

- **Build:** `./mvnw clean verify`
- **Run:** `./mvnw spring-boot:run` — data loads automatically at startup
- **Swagger:** `http://localhost:8080/swagger-ui.html`
- **Health:** `http://localhost:8080/actuator/health`
- **Deploy:** Railway (`/release` command — time-permitting)
- **Submit:** repo URL + Railway URL + Swagger URL

### Known limitations (honest)

- In-memory H2: data lost on restart; seed reloads automatically
- No re-scoring on re-ingest (idempotent replay returns original score; if deadline changes, stale urgency)
- Summary monetary aggregates are per-currency (separate totals per USD/BRL/MXN/COP — no FX conversion)
- No auth on any endpoint (POC scope)
