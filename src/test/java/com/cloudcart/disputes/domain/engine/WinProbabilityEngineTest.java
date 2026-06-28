package com.cloudcart.disputes.domain.engine;

import com.cloudcart.disputes.domain.model.ReasonCategory;
import com.cloudcart.disputes.domain.model.RecommendedAction;
import com.cloudcart.disputes.domain.model.UrgencyLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class WinProbabilityEngineTest {

    private WinProbabilityEngine engine;

    @BeforeEach
    void setUp() {
        engine = new WinProbabilityEngine();
    }

    // ---- Base score by reason category (neutral: $200, 10 days) ----

    static Stream<Arguments> baseCategoryScores() {
        BigDecimal amt = new BigDecimal("200.00");
        long days = 10L;
        // 10 days is within the 4-10 day MEDIUM bucket (>10 days = LOW)
        return Stream.of(
            // category, amount, days, expectedProbability, expectedAction, expectedUrgency
            Arguments.of(ReasonCategory.FRAUD,                  amt, days, 30,  RecommendedAction.ACCEPT,        UrgencyLevel.MEDIUM),
            Arguments.of(ReasonCategory.SUBSCRIPTION_CANCELLED, amt, days, 50,  RecommendedAction.ACCEPT,        UrgencyLevel.MEDIUM),
            Arguments.of(ReasonCategory.PRODUCT_NOT_RECEIVED,   amt, days, 60,  RecommendedAction.CONTEST,       UrgencyLevel.MEDIUM),
            Arguments.of(ReasonCategory.PRODUCT_UNACCEPTABLE,   amt, days, 65,  RecommendedAction.CONTEST,       UrgencyLevel.MEDIUM),
            Arguments.of(ReasonCategory.DUPLICATE_PROCESSING,   amt, days, 75,  RecommendedAction.CONTEST,       UrgencyLevel.MEDIUM)
        );
    }

    @ParameterizedTest(name = "{0}: prob={3}, action={4}, urgency={5}")
    @MethodSource("baseCategoryScores")
    void baseScoreByCategory(ReasonCategory category, BigDecimal amount, long days,
                             int expectedProb, RecommendedAction expectedAction, UrgencyLevel expectedUrgency) {
        ScoringResult result = engine.score(category, amount, days);
        assertThat(result.winProbability()).isEqualTo(expectedProb);
        assertThat(result.recommendedAction()).isEqualTo(expectedAction);
        assertThat(result.urgencyLevel()).isEqualTo(expectedUrgency);
    }

    // ---- Amount modifier boundaries (PRODUCT_NOT_RECEIVED base=50, 10 days = +10 deadline mod) ----

    static Stream<Arguments> amountModifiers() {
        ReasonCategory cat = ReasonCategory.PRODUCT_NOT_RECEIVED; // base=50
        long days = 10L; // +10 deadline mod → base score = 60 before amount
        return Stream.of(
            // amount, expectedProbability
            Arguments.of(new BigDecimal("15.00"),   60),  // <$500: +0
            Arguments.of(new BigDecimal("49.99"),   60),  // just below $50 (still <$500 bucket, +0)
            Arguments.of(new BigDecimal("50.00"),   60),  // at $50, <$500: +0
            Arguments.of(new BigDecimal("499.99"),  60),  // just below $500: +0
            Arguments.of(new BigDecimal("500.00"),  65),  // exactly $500: +5
            Arguments.of(new BigDecimal("750.00"),  65),  // $500-$999: +5
            Arguments.of(new BigDecimal("999.99"),  65),  // just below $1000: +5
            Arguments.of(new BigDecimal("1000.00"), 70),  // exactly $1000: +10
            Arguments.of(new BigDecimal("3500.00"), 70)   // max test data: +10
        );
    }

    @ParameterizedTest(name = "amount=${0} → prob={1}")
    @MethodSource("amountModifiers")
    void amountModifierBoundaries(BigDecimal amount, int expectedProb) {
        ScoringResult result = engine.score(ReasonCategory.PRODUCT_NOT_RECEIVED, amount, 10L);
        assertThat(result.winProbability()).isEqualTo(expectedProb);
    }

    // ---- Deadline modifier + urgency (PRODUCT_NOT_RECEIVED base=50, $200 amount +0) ----

    static Stream<Arguments> deadlineModifiers() {
        ReasonCategory cat = ReasonCategory.PRODUCT_NOT_RECEIVED; // base=50
        BigDecimal amt = new BigDecimal("200.00"); // +0 amount mod
        return Stream.of(
            // days, expectedProb, expectedUrgency, expectedAction
            Arguments.of(-5L,  50, UrgencyLevel.HIGH,   RecommendedAction.ACCEPT),        // days < 0: expired → ACCEPT
            Arguments.of(-1L,  50, UrgencyLevel.HIGH,   RecommendedAction.ACCEPT),        // days < 0: expired → ACCEPT
            // days == 0: deadline is TODAY — not expired, merchant still has the day.
            // Modifier: 0 falls into the 0-4 day bucket → -10. prob = 50-10 = 40. URGENT_REVIEW.
            Arguments.of(0L,   40, UrgencyLevel.HIGH,   RecommendedAction.URGENT_REVIEW), // today: OPEN, HIGH, URGENT_REVIEW
            Arguments.of(1L,   40, UrgencyLevel.HIGH,   RecommendedAction.URGENT_REVIEW), // 1 day: -10 → 40, prob>=40 → URGENT
            Arguments.of(2L,   40, UrgencyLevel.HIGH,   RecommendedAction.URGENT_REVIEW),
            Arguments.of(3L,   40, UrgencyLevel.HIGH,   RecommendedAction.URGENT_REVIEW), // boundary HIGH
            Arguments.of(4L,   40, UrgencyLevel.MEDIUM, RecommendedAction.ACCEPT),        // 4 days: -10 → 40, not urgent (>3), <60 → ACCEPT
            Arguments.of(5L,   50, UrgencyLevel.MEDIUM, RecommendedAction.ACCEPT),        // 5 days: +0 → 50, <60 → ACCEPT
            Arguments.of(9L,   50, UrgencyLevel.MEDIUM, RecommendedAction.ACCEPT),        // boundary MEDIUM
            Arguments.of(10L,  60, UrgencyLevel.MEDIUM, RecommendedAction.CONTEST),       // 10 days: 4-10 = MEDIUM, +10 → 60 → CONTEST
            Arguments.of(11L,  60, UrgencyLevel.LOW,    RecommendedAction.CONTEST),       // 11 days: >10 = LOW, +10 → 60 → CONTEST
            Arguments.of(20L,  60, UrgencyLevel.LOW,    RecommendedAction.CONTEST)
        );
    }

    @ParameterizedTest(name = "days={0} → prob={1}, urgency={2}, action={3}")
    @MethodSource("deadlineModifiers")
    void deadlineModifierAndUrgency(long days, int expectedProb, UrgencyLevel expectedUrgency,
                                    RecommendedAction expectedAction) {
        ScoringResult result = engine.score(ReasonCategory.PRODUCT_NOT_RECEIVED,
                                            new BigDecimal("200.00"), days);
        assertThat(result.winProbability()).isEqualTo(expectedProb);
        assertThat(result.urgencyLevel()).isEqualTo(expectedUrgency);
        assertThat(result.recommendedAction()).isEqualTo(expectedAction);
    }

    // ---- Recommended action coverage: all three actions must be reachable ----

    @ParameterizedTest(name = "{0}: amount=${1}, days={2} → {3}")
    @MethodSource("recommendedActionCases")
    void recommendedActionCoverage(String description, BigDecimal amount, long days,
                                   ReasonCategory category, RecommendedAction expectedAction) {
        ScoringResult result = engine.score(category, amount, days);
        assertThat(result.recommendedAction())
            .as(description)
            .isEqualTo(expectedAction);
    }

    static Stream<Arguments> recommendedActionCases() {
        return Stream.of(
            Arguments.of("CONTEST: high prob, enough time",
                new BigDecimal("500.00"), 15L, ReasonCategory.PRODUCT_NOT_RECEIVED, RecommendedAction.CONTEST),
            Arguments.of("ACCEPT: fraud, low amount, expired",
                new BigDecimal("30.00"), -1L, ReasonCategory.FRAUD, RecommendedAction.ACCEPT),
            Arguments.of("ACCEPT: fraud, high amount, plenty of time (prob still low)",
                new BigDecimal("3500.00"), 15L, ReasonCategory.FRAUD, RecommendedAction.ACCEPT),
            Arguments.of("URGENT_REVIEW: 2 days left, prob>=40",
                new BigDecimal("200.00"), 2L, ReasonCategory.PRODUCT_NOT_RECEIVED, RecommendedAction.URGENT_REVIEW),
            Arguments.of("URGENT_REVIEW: 3 days (boundary), prob>=40",
                new BigDecimal("200.00"), 3L, ReasonCategory.PRODUCT_NOT_RECEIVED, RecommendedAction.URGENT_REVIEW),
            Arguments.of("ACCEPT: amount below $50 even with good prob",
                new BigDecimal("30.00"), 15L, ReasonCategory.DUPLICATE_PROCESSING, RecommendedAction.ACCEPT),
            Arguments.of("ACCEPT: subscription, 12 days, $200 (prob=50, <60)",
                new BigDecimal("200.00"), 12L, ReasonCategory.SUBSCRIPTION_CANCELLED, RecommendedAction.ACCEPT)
        );
    }

    // ---- Expired-deadline trap: must be ACCEPT + HIGH regardless of probability ----

    @ParameterizedTest(name = "Expired: {0}, days={2} → ACCEPT + HIGH")
    @MethodSource("expiredCases")
    void expiredDisputeAlwaysAcceptHighUrgency(String description, ReasonCategory category,
                                               long days, BigDecimal amount) {
        ScoringResult result = engine.score(category, amount, days);
        assertThat(result.recommendedAction())
            .as("expired dispute must always be ACCEPT")
            .isEqualTo(RecommendedAction.ACCEPT);
        assertThat(result.urgencyLevel())
            .as("expired dispute must always be HIGH urgency")
            .isEqualTo(UrgencyLevel.HIGH);
    }

    static Stream<Arguments> expiredCases() {
        // Only days < 0 are truly expired (cannot contest).
        // days == 0 (today) is NOT in this list — it's OPEN with URGENT_REVIEW.
        return Stream.of(
            Arguments.of("Duplicate, expired yesterday", ReasonCategory.DUPLICATE_PROCESSING, -1L, new BigDecimal("3500.00")),
            Arguments.of("Fraud, long expired", ReasonCategory.FRAUD, -30L, new BigDecimal("100.00")),
            Arguments.of("Product, expired 2 days ago", ReasonCategory.PRODUCT_NOT_RECEIVED, -2L, new BigDecimal("1500.00"))
        );
    }

    // ---- Composite / interaction: multiple factors at once ----

    @ParameterizedTest(name = "{0}")
    @MethodSource("compositeCases")
    void compositeScenarios(String description, ReasonCategory category, BigDecimal amount,
                            long days, int expectedProb, RecommendedAction expectedAction,
                            UrgencyLevel expectedUrgency) {
        ScoringResult result = engine.score(category, amount, days);
        assertThat(result.winProbability()).as("probability for: " + description).isEqualTo(expectedProb);
        assertThat(result.recommendedAction()).as("action for: " + description).isEqualTo(expectedAction);
        assertThat(result.urgencyLevel()).as("urgency for: " + description).isEqualTo(expectedUrgency);
    }

    static Stream<Arguments> compositeCases() {
        return Stream.of(
            // Duplicate $500 exactly, 5 days exactly: base=65 + amount>=500(+5) + 5-9days(+0) = 70, 5days→MEDIUM
            Arguments.of("Duplicate $500 5days (all boundaries)",
                ReasonCategory.DUPLICATE_PROCESSING, new BigDecimal("500.00"), 5L,
                70, RecommendedAction.CONTEST, UrgencyLevel.MEDIUM),
            // Fraud $3500 15 days: base=20 + +10 + +10 = 40, prob<40? No, prob=40. action: days>3, prob<60 → ACCEPT
            Arguments.of("Fraud $3500 15days: high amount can't save FRAUD",
                ReasonCategory.FRAUD, new BigDecimal("3500.00"), 15L,
                40, RecommendedAction.ACCEPT, UrgencyLevel.LOW),
            // Product-not-received $1000 1day: base=50 + +10 + -10 = 50, urgency=HIGH, days=1<=3 prob>=40 → URGENT_REVIEW
            Arguments.of("ProductNotReceived $1000 1day: urgency overrides",
                ReasonCategory.PRODUCT_NOT_RECEIVED, new BigDecimal("1000.00"), 1L,
                50, RecommendedAction.URGENT_REVIEW, UrgencyLevel.HIGH),
            // Subscription $600 3 days: base=40 + +5 + -10 = 35, prob<40 → ACCEPT, HIGH urgency
            Arguments.of("Subscription $600 3days: low prob despite urgency",
                ReasonCategory.SUBSCRIPTION_CANCELLED, new BigDecimal("600.00"), 3L,
                35, RecommendedAction.ACCEPT, UrgencyLevel.HIGH)
        );
    }

    // ---- Boundary: days == 0 (today) vs days < 0 (yesterday / expired) ----
    // Decision: ChronoUnit.DAYS.between(today, deadline) returns 0 when deadline == today.
    // 0 means the window has NOT closed — the merchant still has the day. It is OPEN + URGENT_REVIEW.
    // -1 (or less) means the deadline has passed. It is EXPIRED + ACCEPT.

    @Test
    void deadlineToday_isOpenAndUrgentReview_notExpired() {
        // PRODUCT_NOT_RECEIVED, $200, days=0: base=50 + amount=+0 + today=-10 = 40
        // 0 <= DEADLINE_URGENT(3) AND prob=40 >= 40 → URGENT_REVIEW, HIGH, NOT expired
        ScoringResult result = engine.score(ReasonCategory.PRODUCT_NOT_RECEIVED,
                                            new BigDecimal("200.00"), 0L);
        assertThat(result.recommendedAction())
            .as("deadline today should be URGENT_REVIEW, not ACCEPT")
            .isEqualTo(RecommendedAction.URGENT_REVIEW);
        assertThat(result.urgencyLevel()).isEqualTo(UrgencyLevel.HIGH);
        assertThat(result.winProbability()).isEqualTo(40);
    }

    @Test
    void deadlineYesterday_isExpiredAndAccept() {
        // days == -1: clearly past deadline, cannot contest regardless of category
        ScoringResult result = engine.score(ReasonCategory.DUPLICATE_PROCESSING,
                                            new BigDecimal("3500.00"), -1L);
        assertThat(result.recommendedAction())
            .as("deadline yesterday must be ACCEPT (cannot contest)")
            .isEqualTo(RecommendedAction.ACCEPT);
        assertThat(result.urgencyLevel()).isEqualTo(UrgencyLevel.HIGH);
    }

    // ---- OTHER category: unknown reason code, conservative scoring ----

    @Test
    void other_baseScore_isConservativeNotFraud() {
        // OTHER (base=30) must be distinct from FRAUD (base=20) —
        // an unknown code is not evidence of fraud.
        ScoringResult other = engine.score(ReasonCategory.OTHER, new BigDecimal("200.00"), 15L);
        ScoringResult fraud = engine.score(ReasonCategory.FRAUD, new BigDecimal("200.00"), 15L);
        assertThat(other.winProbability()).isEqualTo(40); // 30 + 0 + 10 = 40
        assertThat(fraud.winProbability()).isEqualTo(30); // 20 + 0 + 10 = 30
        assertThat(other.winProbability()).isGreaterThan(fraud.winProbability());
    }

    // ---- Determinism: same input always yields same result ----

    @ParameterizedTest(name = "Deterministic call #{0}")
    @MethodSource("deterministicInputs")
    void deterministicOutputs(int callNumber, ReasonCategory category, BigDecimal amount, long days) {
        ScoringResult r1 = engine.score(category, amount, days);
        ScoringResult r2 = engine.score(category, amount, days);
        assertThat(r1.winProbability()).isEqualTo(r2.winProbability());
        assertThat(r1.recommendedAction()).isEqualTo(r2.recommendedAction());
        assertThat(r1.urgencyLevel()).isEqualTo(r2.urgencyLevel());
    }

    static Stream<Arguments> deterministicInputs() {
        return Stream.of(
            Arguments.of(1, ReasonCategory.FRAUD, new BigDecimal("500.00"), 7L),
            Arguments.of(2, ReasonCategory.DUPLICATE_PROCESSING, new BigDecimal("1000.00"), 0L),
            Arguments.of(3, ReasonCategory.PRODUCT_UNACCEPTABLE, new BigDecimal("250.00"), 10L)
        );
    }
}
