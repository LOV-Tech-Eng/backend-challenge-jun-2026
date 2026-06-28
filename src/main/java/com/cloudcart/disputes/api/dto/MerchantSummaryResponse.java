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
    Map<Currency, BigDecimal> totalAmountAtRisk,
    Map<Currency, BigDecimal> totalVolume,
    double averageWinProbability,
    Map<ReasonCategory, CategoryStats> byReasonCategory,
    Map<RecommendedAction, Integer> byRecommendedAction,
    Map<UrgencyLevel, Integer> byUrgencyLevel
) {
    public record CategoryStats(int count, Map<Currency, BigDecimal> totalAmount) {}
}
