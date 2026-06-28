package com.cloudcart.disputes.api.dto;

import com.cloudcart.disputes.domain.model.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record DisputeResponse(
    UUID id,
    String processorId,
    String processorDisputeId,
    String merchantId,
    BigDecimal amount,
    Currency currency,
    String reasonCode,
    ReasonCategory reasonCategory,
    LocalDate disputeDate,
    LocalDate transactionDate,
    LocalDate responseDeadline,
    String customerEmail,
    String orderId,
    DisputeStatus status,
    Integer winProbability,
    RecommendedAction recommendedAction,
    UrgencyLevel urgencyLevel,
    String scoringReason,
    LocalDateTime createdAt
) {
    public static DisputeResponse from(Dispute dispute) {
        return new DisputeResponse(
            dispute.getId(),
            dispute.getProcessorId(),
            dispute.getProcessorDisputeId(),
            dispute.getMerchantId(),
            dispute.getAmount(),
            dispute.getCurrency(),
            dispute.getReasonCode(),
            dispute.getReasonCategory(),
            dispute.getDisputeDate(),
            dispute.getTransactionDate(),
            dispute.getResponseDeadline(),
            dispute.getCustomerEmail(),
            dispute.getOrderId(),
            dispute.getStatus(),
            dispute.getWinProbability(),
            dispute.getRecommendedAction(),
            dispute.getUrgencyLevel(),
            dispute.getScoringReason(),
            dispute.getCreatedAt()
        );
    }
}
