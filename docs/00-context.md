# 00 — Context

## Challenge Statement (enunciado)

**The CloudCart Chargeback Storm: Build a Dispute Intelligence API**

CloudCart (1,200+ merchants — Brazil, Mexico, Colombia) has seen chargebacks spike from 0.4% → 2.1%.
Their risk team receives 400+ dispute notifications/day in multiple formats, manually reviews them, and
often misses the 7–14 day contest window. Yuno's task: build a backend service that ingests chargeback
data from multiple processor formats, classifies disputes with win probability + recommended action +
urgency, and exposes a queryable API for prioritization.

Stack chosen: **Java 21 + Spring Boot 3.x (Maven)**, H2 in-memory, REST (not GraphQL).
Deployed to: **Railway** (not Vercel — Vercel does not host Spring Boot; the vercel.app URL in the form
is a placeholder example, not a requirement).

---

## Acceptance Criteria (AC) table

_Finalized by requirements-analyst._

### MUST (pass/fail — evaluated on every submission)

| ID | Criterion | How it's verified | Score area |
|----|-----------|-------------------|------------|
| AC-1 | `POST /api/v1/disputes/ingest/processor-a` accepts Processor A format (camelCase, amounts in dollars) and stores a normalized dispute | curl + unit test | Ingestion 20pts |
| AC-2 | `POST /api/v1/disputes/ingest/processor-b` accepts Processor B format (snake_case `@JsonProperty`, `amount_cents` integer converted ÷100) and normalizes to the same internal schema | curl + unit test | Ingestion 20pts |
| AC-3 | Both formats produce identical internal schema: id, merchantId, processorId, processorDisputeId, amount (BigDecimal), currency, reasonCode, reasonCategory, disputeDate, transactionDate, responseDeadline, customerEmail?, orderId?, status, winProbability, recommendedAction, urgencyLevel, createdAt | unit test on entity fields | Ingestion 20pts |
| AC-4 | Duplicate ingest (same processorId + processorDisputeId) returns the existing record (200 OK) without creating a second row — unique constraint enforced at DB level | idempotency integration test with concurrent latch | Ingestion 20pts |
| AC-5 | Missing required fields or invalid values (negative amount, unknown currency, unknown processorType) return 400 with structured `{ status, error, code, message, violations[] }` | validation integration test | API Design 20pts |
| AC-6 | Every dispute receives a `winProbability` (0–100) computed from: reason category base score, amount modifier, deadline modifier. Deterministic: same input = same output | table-driven unit test covering all 5 categories + boundaries | Intelligence 25pts |
| AC-7 | Every dispute receives a `recommendedAction`: ACCEPT if expired (daysUntilDeadline < 0), URGENT_REVIEW if ≤3 days AND prob≥40 (includes days==0 "today"), CONTEST if prob≥60 AND days>0, else ACCEPT | unit test covering each branch | Intelligence 25pts |
| AC-8 | Every dispute receives an `urgencyLevel`: HIGH if ≤3 days (or expired), MEDIUM if 4–10 days, LOW if >10 days | unit test | Intelligence 25pts |
| AC-9 | **Expired-deadline disputes are classified ACCEPT + urgency HIGH regardless of win probability** | unit test with past deadline, positive win prob | Intelligence 25pts |
| AC-10 | `GET /api/v1/disputes` returns paginated list; filters `merchantId`, `reasonCategory`, `recommendedAction`, `urgencyLevel`, `dateFrom`, `dateTo`, `minAmount` are all optional and combinable | curl with all filters combined + integration test | API Design 20pts |
| AC-11 | `GET /api/v1/disputes/{id}` returns a single dispute (200) or 404 with structured error if not found | curl + test | API Design 20pts |
| AC-12 | `GET /api/v1/merchants/{merchantId}/summary` returns: totalDisputeCount, totalAmountAtRisk, totalVolume, averageWinProbability, byReasonCategory (count+amount), byRecommendedAction (counts), byUrgencyLevel (counts) | curl + unit test for each aggregated field | API Design 20pts |
| AC-13 | Summary monetary totals broken down per currency (no cross-currency summing) | unit test | API Design 20pts |
| AC-14 | DataLoader seeds ≥150 disputes at startup: ≥5 merchants, USD+BRL+MXN+COP, all 5 reason categories, expired+urgent+comfortable deadlines, amounts $15–$3500, both processor formats | count query after startup | Ingestion 20pts |
| AC-15 | Swagger UI live at `/swagger-ui.html`, all endpoints documented | browser check | Documentation 15pts |
| AC-16 | `GET /actuator/health` returns `{"status":"UP"}` | curl | Documentation 15pts |
| AC-17 | `./mvnw clean verify` passes from a clean clone (Java 21, no external DB) | cold-start build | Code Quality 15pts |
| AC-18 | README: ≤5 setup steps, run/test instructions, design overview (2–4 paras with scoring logic explained), evaluator guide (exact curl commands), "What I'd improve" section | manual review | Documentation 15pts |
| AC-19 | No stack traces or internal schema details in error responses | curl against invalid inputs | Security |

### SHOULD

| ID | Criterion | How it's verified | Score area |
|----|-----------|-------------------|------------|
| AC-20 | Both ingestion endpoints accept a JSON array for bulk ingest (not just single records) | curl with array payload | Polish 5pts |
| AC-21 | `GET /api/v1/disputes/{id}` includes `scoringReason` field (human-readable explanation of the score, e.g. "Base: FRAUD(20) + amount≥$500(+5) + 2 days(-10) = 15 → ACCEPT") | curl | Code Quality |
| AC-22 | Pagination params validated: size≤100, page≥0; violations return 400 | test | Code Quality |
| AC-23 | `customerEmail` masked in all log output | code review | Security |

### NICE (stretch — only if core is complete)

| ID | Criterion | Score area |
|----|-----------|------------|
| AC-24 | S-C: `POST /api/v1/disputes/ingest/processor-a` (and B) also returns/publishes a `notificationPayload` JSON for the affected merchant in the response or as a sub-field | Stretch 5pts |

---

## Assumptions & resolved ambiguities

| # | Ambiguity | Assumption | Why safe |
|---|-----------|------------|----------|
| A1 | REST or GraphQL? | REST | Simpler, faster, evaluator-friendly (curl examples) |
| A2 | Single ingest endpoint with discriminator vs two separate endpoints | Two separate endpoints: `/ingest/processor-a`, `/ingest/processor-b` | Typed schemas per endpoint, clean Swagger, no Jackson polymorphism complexity |
| A3 | Amount storage: integer cents vs BigDecimal dollars | BigDecimal dollars (e.g. 500.00) | Challenge uses dollar amounts throughout; evaluators read responses without mental conversion; Processor B `amount_cents` divided by 100 at normalization |
| A4 | Historical merchant win rate input | Default to 50% win rate when no history; stored per-merchant in seed data for demo | Cold-start behavior documented |
| A5 | Expired deadline action | ACCEPT + urgency HIGH | Cannot contest after deadline; URGENT_REVIEW reserved for 1-3 days remaining |
| A6 | Summary stats: cross-currency totals | Break down monetary totals by currency; count is global | Prevents nonsensical USD+BRL sums |
| A7 | Dispute "at risk" definition | Sum of amounts for OPEN disputes with recommendedAction = CONTEST or URGENT_REVIEW | ACCEPT disputes are already conceded losses |
| A8 | Data loader mechanism | `CommandLineRunner` (`DataLoader.java`) that calls `DisputeIngestionService` | Guarantees scoring consistency; proves pipeline handles 150+ records at startup |
| A9 | Status computed when? | Computed at ingest time, stored in DB | Simple queries; no background job needed for POC |
| A10 | Reason code mapping for unknown codes | Map to `OTHER` category (base score 30, above FRAUD=20) + log WARNING | Safe conservative default; `OTHER` is scored higher than `FRAUD` because fraud disputes are genuinely hard to win; unknown codes shouldn't inherit that pessimism but shouldn't get a high base either |
| A11 | Stretch goals priority | S-C only if core complete; S-A and S-B are out of scope | File handling and time-series add complexity without demonstrating core competency |
| A12 | Deadline expiration boundary: `days == 0` (today) — EXPIRED or OPEN? | **OPEN** (actionable). Only `days < 0` (deadline was yesterday or earlier) → EXPIRED. | **Business rationale (intentional decision):** The challenge defines deadlines as `LocalDate` values, not timestamps — "today" is inherently ambiguous (the contest window may still be open depending on the card network's cutoff time). A risk-aware dispute management system should always favor preserving the merchant's opportunity to contest over prematurely classifying a dispute as lost. `days == 0` → `OPEN + HIGH urgency + URGENT_REVIEW` (if winnable). Trade-off accepted: a dispute due at midnight tonight may be too late to act on, but flagging it for human review is preferable to silently dropping a potentially recoverable dispute. |

---

## Out of scope (intentional)

- Stretch S-A (evidence file attachment)
- Stretch S-B (weekly historical trends)
- Authentication / authorization
- Currency FX conversion
- Real notification delivery (email, webhook)
- Persistent database (PostgreSQL, etc.)
- Rate limiting / throttling
- PCI-DSS compliance documentation
- Vercel deployment (incompatible with Spring Boot)
