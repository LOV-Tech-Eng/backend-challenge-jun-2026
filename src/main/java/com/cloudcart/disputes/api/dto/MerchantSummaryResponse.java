package com.cloudcart.disputes.api.dto;

import com.cloudcart.disputes.domain.model.Currency;
import com.cloudcart.disputes.domain.model.ReasonCategory;
import com.cloudcart.disputes.domain.model.RecommendedAction;
import com.cloudcart.disputes.domain.model.UrgencyLevel;

import java.math.BigDecimal;
import java.util.Map;

public record MerchantSummaryResponse(
    String merchantId,
    int totalDisputeCount,
    // Monetary totals are broken down per currency to avoid meaningless cross-currency sums
    // (USD + COP would be nonsensical without FX normalization).
    // TODO (production): expose a normalized total using FX rates, with the per-currency
    // breakdown retained for transparency.
    Map<Currency, BigDecimal> totalAmountAtRisk,
    Map<Currency, BigDecimal> totalVolume,
    double averageWinProbability,
    Map<ReasonCategory, CategoryStats> byReasonCategory,
    Map<RecommendedAction, Integer> byRecommendedAction,
    Map<UrgencyLevel, Integer> byUrgencyLevel
) {
    public record CategoryStats(int count, Map<Currency, BigDecimal> totalAmount) {}
}
