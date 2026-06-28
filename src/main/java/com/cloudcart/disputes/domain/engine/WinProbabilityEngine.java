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
    private static final int BASE_SUBSCRIPTION_CANCELLED = 40;
    private static final int BASE_PRODUCT_NOT_RECEIVED   = 50;
    private static final int BASE_PRODUCT_UNACCEPTABLE   = 55;
    private static final int BASE_DUPLICATE_PROCESSING   = 65;

    // ---- Step 2: Amount thresholds ----
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
     * @param amount          transaction amount in full currency units (e.g. 500.00)
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
        if (days <= 0) return 0;          // expired — no modifier; action handles this
        if (days >= DEADLINE_COMFORTABLE) return 10;
        if (days >= DEADLINE_CLOSE)       return 0;
        return -10;                        // 1–4 days: not enough time to build a case
    }

    private UrgencyLevel urgencyLevel(long days) {
        if (days <= DEADLINE_URGENT) return UrgencyLevel.HIGH;   // includes expired (≤ 0)
        if (days <= DEADLINE_COMFORTABLE) return UrgencyLevel.MEDIUM;
        return UrgencyLevel.LOW;
    }

    private RecommendedAction recommendedAction(int probability, BigDecimal amount, long days) {
        // Rule 1: expired → cannot contest, auto-accept
        if (days <= 0) return RecommendedAction.ACCEPT;

        // Rule 2: deadline imminent, but still winnable → flag for immediate human review
        if (days <= DEADLINE_URGENT && probability >= 40) return RecommendedAction.URGENT_REVIEW;

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
        if (days <= 0) return "expired";
        if (days >= DEADLINE_COMFORTABLE) return "≥10days";
        if (days >= DEADLINE_CLOSE) return "5-9days";
        return "1-4days";
    }
}
