package com.cloudcart.disputes.domain.engine;

import com.cloudcart.disputes.domain.model.ReasonCategory;
import com.cloudcart.disputes.domain.model.RecommendedAction;
import com.cloudcart.disputes.domain.model.UrgencyLevel;

import java.math.BigDecimal;

/**
 * Pure-function scoring engine. No Spring dependencies, no IO, no side effects.
 * Same inputs always produce the same output (deterministic).
 *
 * Scoring algorithm:
 *   1. Base score by reason category (how likely chargebacks of this type are won)
 *   2. Amount modifier (higher amounts justify more effort → slightly higher score)
 *   3. Deadline modifier (more time → better evidence gathering → higher score)
 *   4. Clamp result to [0, 100]
 *   5. Derive urgency level from days remaining
 *   6. Derive recommended action using precedence rules
 */
public class WinProbabilityEngine {

    // ---- Step 1: Base scores by reason category ----
    // Fraud is hardest to win (no proof the card wasn't stolen).
    // Duplicate processing is easiest (two identical charges are provable).
    private static final int BASE_FRAUD                  = 20;
    private static final int BASE_OTHER                  = 30; // unknown reason code — conservative, not as low as FRAUD
    private static final int BASE_SUBSCRIPTION_CANCELLED = 40;
    private static final int BASE_PRODUCT_NOT_RECEIVED   = 50;
    private static final int BASE_PRODUCT_UNACCEPTABLE   = 55;
    private static final int BASE_DUPLICATE_PROCESSING   = 65;

    // ---- Step 2: Amount thresholds ----
    // NOTE: amounts are evaluated in their original currency (no FX normalization).
    // A $500 USD dispute and a $500 COP dispute receive the same modifier even though
    // their real-world value differs significantly. For this POC the goal is to demonstrate
    // the classification logic, not financial accuracy.
    // TODO (production): normalize to a base currency using historical FX rates before scoring.
    private static final BigDecimal AMOUNT_HIGH   = new BigDecimal("1000");
    private static final BigDecimal AMOUNT_MEDIUM = new BigDecimal("500");
    private static final BigDecimal AMOUNT_MIN_TO_CONTEST = new BigDecimal("50");

    // ---- Step 3: Deadline buckets (days remaining) ----
    private static final long DEADLINE_COMFORTABLE = 10;
    private static final long DEADLINE_CLOSE       = 5;
    private static final long DEADLINE_URGENT      = 3;

    /**
     * Scores a dispute.
     *
     * @param reasonCategory  classified reason for the chargeback
     * @param amount          transaction amount in full currency units (e.g. 500.00), in the
     *                        dispute's original currency — no FX normalization is applied
     * @param daysUntilDeadline days remaining until response deadline;
     *                          negative or zero means the deadline has passed
     * @return ScoringResult with winProbability, recommendedAction, urgencyLevel, and human-readable reason
     */
    public ScoringResult score(ReasonCategory reasonCategory, BigDecimal amount, long daysUntilDeadline) {
        // Step 1 — base score
        int base = baseScore(reasonCategory);

        // Step 2 — amount modifier
        int amountMod = amountModifier(amount);

        // Step 3 — deadline modifier
        int deadlineMod = deadlineModifier(daysUntilDeadline);

        // Step 4 — clamp
        int probability = Math.max(0, Math.min(100, base + amountMod + deadlineMod));

        // Step 5 — urgency
        UrgencyLevel urgency = urgencyLevel(daysUntilDeadline);

        // Step 6 — recommended action (precedence order matters)
        RecommendedAction action = recommendedAction(probability, amount, daysUntilDeadline);

        // Human-readable explanation for evaluators and operators
        String reason = String.format(
            "Base: %s(%d) + amount%s(%+d) + deadline%s(%+d) = %d → %s",
            reasonCategory.name(), base,
            amountLabel(amount), amountMod,
            deadlineLabel(daysUntilDeadline), deadlineMod,
            probability, action.name()
        );

        return new ScoringResult(probability, action, urgency, reason);
    }

    // ---- Private helpers ----

    private int baseScore(ReasonCategory category) {
        return switch (category) {
            case FRAUD                  -> BASE_FRAUD;
            case OTHER                  -> BASE_OTHER;
            case SUBSCRIPTION_CANCELLED -> BASE_SUBSCRIPTION_CANCELLED;
            case PRODUCT_NOT_RECEIVED   -> BASE_PRODUCT_NOT_RECEIVED;
            case PRODUCT_UNACCEPTABLE   -> BASE_PRODUCT_UNACCEPTABLE;
            case DUPLICATE_PROCESSING   -> BASE_DUPLICATE_PROCESSING;
        };
    }

    private int amountModifier(BigDecimal amount) {
        if (amount.compareTo(AMOUNT_HIGH) >= 0) return 10;
        if (amount.compareTo(AMOUNT_MEDIUM) >= 0) return 5;
        return 0;
    }

    private int deadlineModifier(long days) {
        // days < 0  → clearly past deadline; no modifier, action forces ACCEPT
        // days == 0 → deadline is today; treated as 1-4 day bucket (-10) — same urgency pressure,
        //             merchant technically still has the day to respond but time is critical
        if (days < 0)                    return 0;
        if (days >= DEADLINE_COMFORTABLE) return 10;
        if (days >= DEADLINE_CLOSE)       return 0;
        return -10;  // 0–4 days remaining: insufficient time to build a strong case
    }

    private UrgencyLevel urgencyLevel(long days) {
        // days == 0 (today) and days < 0 (expired) both land in HIGH —
        // the distinction between "today" and "yesterday" is irrelevant for urgency.
        if (days <= DEADLINE_URGENT) return UrgencyLevel.HIGH;
        if (days <= DEADLINE_COMFORTABLE) return UrgencyLevel.MEDIUM;
        return UrgencyLevel.LOW;
    }

    private RecommendedAction recommendedAction(int probability, BigDecimal amount, long days) {
        // Rule 1: clearly past deadline (days < 0) → cannot contest, auto-accept.
        // days == 0 (today is the deadline) is NOT expired — the merchant technically still has
        // the day to respond. It falls through to URGENT_REVIEW if probability justifies it.
        // Decision rationale: ChronoUnit.DAYS.between(today, deadline) returns 0 when
        // deadline == today, meaning the window has not yet closed.
        if (days < 0) return RecommendedAction.ACCEPT;

        // Rule 2: deadline imminent, still winnable, and amount worth contesting → urgent human review.
        // Amount guard is required here: without it, a $30 dispute with high base score (e.g.
        // DUPLICATE_PROCESSING=65) and 1 day left would escalate to URGENT_REVIEW, bypassing the
        // trivial-amount check in Rule 3 entirely. The amount guard must precede the prob check
        // to ensure both conditions are evaluated.
        if (days <= DEADLINE_URGENT
                && probability >= 40
                && amount.compareTo(AMOUNT_MIN_TO_CONTEST) >= 0) return RecommendedAction.URGENT_REVIEW;

        // Rule 3: low probability or trivial amount → not worth contesting
        if (probability < 40 || amount.compareTo(AMOUNT_MIN_TO_CONTEST) < 0) return RecommendedAction.ACCEPT;

        // Rule 4: strong case with enough time → contest
        if (probability >= 60) return RecommendedAction.CONTEST;

        // Rule 5: mid-range probability → accept (not strong enough to commit resources)
        return RecommendedAction.ACCEPT;
    }

    private String amountLabel(BigDecimal amount) {
        if (amount.compareTo(AMOUNT_HIGH) >= 0) return "≥$1000";
        if (amount.compareTo(AMOUNT_MEDIUM) >= 0) return "≥$500";
        return "<$500";
    }

    private String deadlineLabel(long days) {
        if (days < 0) return "expired";
        if (days == 0) return "due-today";
        if (days >= DEADLINE_COMFORTABLE) return "≥10days";
        if (days >= DEADLINE_CLOSE) return "5-9days";
        return "1-4days";
    }
}
