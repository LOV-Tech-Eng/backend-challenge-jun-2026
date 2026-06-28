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
  1. days < 0                                              → ACCEPT  (expired, cannot contest)
  2. days ≤ 3 AND prob ≥ 40 AND amount ≥ $50              → URGENT_REVIEW
  3. prob < 40 OR amount < $50                             → ACCEPT
  4. prob ≥ 60                                             → CONTEST
  5. else                                                  → ACCEPT

  Note: amount guard on Rule 2 prevents URGENT_REVIEW for trivial amounts (<$50)
  even when the deadline is imminent and win probability is high.
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

_Audit date: 2026-06-28. Three agents: requirements-analyst · qa-resilience · security-compliance._

### AC Scoring Table

| ID | Criterion | Score | Evidence |
|----|-----------|-------|----------|
| AC-1  | POST /processor-a accepts Processor A (camelCase, dollars) | **PASS** | `DisputeIngestionController:34-46`; test `DisputeIngestionControllerTest:32-64` |
| AC-2  | POST /processor-b accepts Processor B (snake_case, amount_cents ÷ 100) | **PASS** | `DisputeIngestionController:66-78`; `ProcessorNormalizer:73-75`; test confirms 50099→500.99 |
| AC-3  | Both produce identical internal schema | **PASS** | `ProcessorNormalizer:55-91`; `ProcessorNormalizerTest:31-105` |
| AC-4  | Duplicate ingest → 200 OK, no second row, DB constraint | **PASS** | Unique constraint `Dispute.java:12-17`; app check `DisputeIngestionService:38-43`; controller returns 200 vs 201 |
| AC-5  | Invalid input → 400 with structured violations[] | **PASS** | `GlobalExceptionHandler:23-35`; `ErrorResponse.java`; 6 tests in `DisputeIngestionControllerTest:128-219` |
| AC-6  | winProbability 0–100, deterministic, all modifiers | **PASS** | `WinProbabilityEngine:58-87`; 46 unit tests; determinism test at `WinProbabilityEngineTest:247-263` |
| AC-7  | recommendedAction rules (ACCEPT/URGENT_REVIEW/CONTEST) | **PASS** | BUG-04 fixed: amount guard added to Rule 2 (`WinProbabilityEngine:134-138`). AC-7 updated in docs to include `amount≥$50` guard. Regression test `urgentDeadline_trivialAmount_isAcceptNotUrgentReview` proves $30 DUPLICATE_PROCESSING with 1 day left → ACCEPT. |
| AC-8  | urgencyLevel HIGH/MEDIUM/LOW boundaries | **PASS** | `WinProbabilityEngine:118-124`; boundary tests 0,1,2,3,4,5,9,10,11 in `WinProbabilityEngineTest:80-111` |
| AC-9  | Expired disputes → ACCEPT + HIGH regardless of probability | **PASS** | `WinProbabilityEngine:132`; 3 expired-case tests `WinProbabilityEngineTest:146-167`; integration test `DisputeIngestionControllerTest:222-243` |
| AC-10 | GET /disputes paginated + all 7 filters optional+combinable | **PASS** | `DisputeQueryController:31-72`; `DisputeSpecifications:20-61`; 12 filter/pagination tests |
| AC-11 | GET /disputes/{id} → 200 or 404 structured error | **PASS** | `DisputeQueryController:74-79`; tests `DisputeQueryControllerTest:109-125` |
| AC-12 | GET /merchants/{id}/summary → all required aggregate fields | **PASS** | `DisputeQueryController:114-124`; `DisputeQueryService.buildSummary`; `MerchantSummaryResponse` has all fields; tests `:136-148` |
| AC-13 | Summary monetary totals per-currency, no cross-currency summing | **PASS** | `MerchantSummaryResponse:18-19` `Map<Currency, BigDecimal>`; `DisputeQueryService:108-120` groups by currency |
| AC-14 | DataLoader ≥150 disputes, 5 merchants, 4 currencies, all 5 categories, both processors | **PASS** | 80 PA + 70 PB = 150; self-validation at startup throws `IllegalStateException` if any constraint fails |
| AC-15 | Swagger UI at /swagger-ui.html, all endpoints documented | **PASS** | springdoc 2.5.0 in pom; all controllers have `@Tag` + `@Operation` |
| AC-16 | GET /actuator/health → `{"status":"UP"}` | **PASS** | Actuator in pom; `show-details: never`; only health+info exposed |
| AC-17 | `mvn clean verify` passes from clean clone | **PASS** | 90/90 tests pass (confirmed during build); H2 embedded; `DataLoader @Profile("!test")` skips seed in test runs |
| AC-18 | README: ≤5 setup steps, design overview, evaluator guide, "What I'd improve" | **PASS** | All sections present with 7 curl examples, full scoring algorithm, 8-item "What I'd Improve" |
| AC-19 | No stack traces in error responses | **PASS** | `application.yml: include-stacktrace: never, include-exception: false`; catch-all returns fixed string |
| AC-20 | Both ingest endpoints accept JSON array | **PARTIAL** | Bulk at `/ingest/processor-a/bulk` and `/processor-b/bulk` (separate paths). AC implies same endpoint. Intentional design decision — separate URLs give typed Swagger schemas. |
| AC-21 | GET /disputes/{id} includes scoringReason field | **PASS** | `DisputeResponse:28`; `WinProbabilityEngine:78-84` generates human-readable string; test asserts non-empty |
| AC-22 | Pagination: size>100 / page<0 → 400 | **PASS** | `DisputeQueryController:56` now throws `InvalidPaginationException` for `size<1`, `size>100`, `page<0` → 400 `INVALID_PAGINATION`. Handler at `GlobalExceptionHandler`. 3 tests added. |
| AC-23 | customerEmail masked in log output | **PASS** | Email never written to any log (PASS on log side). Also fixed H-1: `DisputeResponse.maskEmail()` masks to `cu***@domain.tld` before returning in API responses. |
| AC-24 | notificationPayload in ingest response | **FAIL** | Not implemented (NICE-to-have, low priority) |

---

### Phased Fix List

#### Phase 1 — Blockers (hard fails or correctness bugs in core domain)

| # | Finding | File:Line | Fix |
|---|---------|-----------|-----|
| P1-1 | **BUG-04 (HIGH)**: URGENT_REVIEW fires before amount<$50 guard. `days≤3 AND prob≥40` in Rule 2 doesn't check amount — a $30 dispute with DUPLICATE_PROCESSING and 1 day left gets URGENT_REVIEW, not ACCEPT. | `WinProbabilityEngine.java:134-135` | Add `&& amount.compareTo(AMOUNT_MIN_TO_CONTEST) >= 0` to Rule 2 condition, so amount<$50 still forces ACCEPT even when deadline is urgent. |
| P1-2 | **BUG-03 (HIGH)**: Test fixture builder uses `<= 0` for EXPIRED, real service uses `< 0`. A fixture seeded with `days=0` would be tagged EXPIRED in tests but OPEN in production — directly contradicts the boundary decision. | `DisputeQueryControllerTest.java:197` | Change `days <= 0` → `days < 0` to mirror `DisputeIngestionService:66` exactly. |
| P1-3 | **H-1 (HIGH)**: customerEmail returned unmasked in every API response. Cardholder PII leaks to any unauthenticated caller. | `DisputeResponse.java:44` | Remove `customerEmail` from `DisputeResponse`, or mask it in `DisputeResponse.from()` (e.g., `cu***@domain.tld`). |
| P1-4 | **AC-22 / MV-02 (FAIL)**: `size=0` → `PageRequest.of(0,0)` → `IllegalArgumentException` → 500. And `size>100` / `page<0` are silently clamped instead of returning 400. | `DisputeQueryController.java:56-57` | Add guard: `if (size < 1) size = 1;` to prevent 500. For strict AC-22, return 400 for `size > 100` or `page < 0`. |

#### Phase 2 — High-impact (moves PARTIAL → PASS, removes dead code, security polish)

| # | Finding | File:Line | Fix |
|---|---------|-----------|-----|
| P2-1 | **DC-03**: Unused `fraudCodes`, `productCodes`, `subCodes`, `dupCodes` arrays declared but never read. | `DataLoader.java:127-130` | Delete the four array declarations. |
| P2-2 | **DC-01**: Dead method `findMerchantIds` — never called; semantically broken query (always returns ≤1 result). | `DisputeRepository.java:19-21` | Delete the method. |
| P2-3 | **M-3**: `sanitize(ex.getMessage())` passes Jackson internal message content to callers (class names, field paths in deserialization errors). | `GlobalExceptionHandler.java:39` | Replace `sanitize(ex.getMessage())` with a fixed string: `"Malformed JSON or unrecognisable field value."` Log the exception server-side. |
| P2-4 | **UT-07**: `minAmount=500` test doesn't assert count — `everyItem(>=500)` is vacuously true if filter returns 0 results (would pass even if predicate were `>` instead of `>=`). | `DisputeQueryControllerTest.java:85-88` | Add `jsonPath("$.totalElements").value(N)` where N is the known count of disputes with `amount >= 500`. |
| P2-5 | **AC-7 alignment**: Document the `amount < $50 → ACCEPT` rule that exists in the engine but isn't in the AC. After fixing P1-1 (reordering), update `docs/00-context.md` AC-7 and HANDOFF scoring rules to include Rule 3 explicitly. | `docs/00-context.md:33` | Add the amount guard to the AC-7 description and HANDOFF scoring table. |

#### Phase 3 — Polish

| # | Finding | File:Line | Fix |
|---|---------|-----------|-----|
| P3-1 | **BUG-01/BUG-02**: Test comments incorrectly label 10 days as "4-10 day (+0) bucket" when engine returns `+10` for `days >= DEADLINE_COMFORTABLE(10)`. | `WinProbabilityEngineTest.java:31,96` | Correct comments to `"≥10days bucket (+10)"`. |
| P3-2 | **UT-08**: No tests for `dateFrom` / `dateTo` filter params. | `DisputeQueryControllerTest.java` | Add: `dateFrom=tomorrow → empty`, `dateTo=yesterday → empty`, `dateFrom=dateTo=today → matching disputes`. |
| P3-3 | **UT-09/UT-10**: No test for non-UUID path variable → 400, or invalid enum param → 400. Handler exists (`GlobalExceptionHandler:45`) but is untested. | `DisputeQueryControllerTest.java` | Add two tests: `GET /api/v1/disputes/not-a-uuid` → 400 `INVALID_PARAMETER`; `GET /api/v1/disputes?reasonCategory=GARBAGE` → 400. |
| P3-4 | **application.yml**: `include-binding-errors: on_param` is dead config (handler intercepts before `/error`). | `application.yml:47` | Set to `never`. |
| P3-5 | **L-2**: Bulk ingest has no list-size guard — 10,000-record payload is valid. | `DisputeIngestionController.java:56,88` | Add `@Size(max = 500)` to list parameters (requires `@Validated` on controller) or manual guard at handler entry. |

---

### Do Not Fix (document as known limitations)

- **H-2 (No auth)**: Intentional POC scope. Documented in README and HANDOFF known limitations. A real deploy would need JWT/API-key with merchantId scoping.
- **RG-01 (Idempotency race condition)**: Under concurrent load, two threads can both pass the check-then-act and hit the unique constraint → 500. Mitigation: catch `DataIntegrityViolationException` and retry-read. Noted as production risk.
- **RG-03 (Unbounded heap in buildSummary)**: `repository.findAll(spec)` loads all matching disputes. Fine for 150 records; needs aggregate pushdown (`COUNT`, `SUM` queries) for production scale.
- **M-4 (CORS)**: No CORS config — browser clients get no `Access-Control-Allow-Origin`. Not relevant for a curl-testable POC. Would need explicit allowlist in production.
- **AC-20 (Bulk at separate paths)**: `/processor-a/bulk` vs same URL. Intentional — keeps Swagger schemas clean. Acceptable interpretation for SHOULD criterion.

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
