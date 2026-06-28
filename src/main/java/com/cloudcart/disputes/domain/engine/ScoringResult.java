package com.cloudcart.disputes.domain.engine;

import com.cloudcart.disputes.domain.model.RecommendedAction;
import com.cloudcart.disputes.domain.model.UrgencyLevel;

public record ScoringResult(
    int winProbability,
    RecommendedAction recommendedAction,
    UrgencyLevel urgencyLevel,
    String reason
) {}
