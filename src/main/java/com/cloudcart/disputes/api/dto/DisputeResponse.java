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
    String customerEmail,   // masked: local-part truncated, domain preserved (e.g. cu***@example.com)
    String orderId,
    DisputeStatus status,
    Integer winProbability,
    RecommendedAction recommendedAction,
    UrgencyLevel urgencyLevel,
    String scoringReason,
    LocalDateTime createdAt
) {
    /** Masks the email local-part, preserving the domain: "customer@example.com" → "cu***@example.com" */
    static String maskEmail(String email) {
        if (email == null) return null;
        int at = email.indexOf('@');
        if (at <= 0) return "***";
        String local = email.substring(0, at);
        String domain = email.substring(at); // includes '@'
        String visible = local.length() <= 2 ? local : local.substring(0, 2);
        return visible + "***" + domain;
    }

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
            maskEmail(dispute.getCustomerEmail()),
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
