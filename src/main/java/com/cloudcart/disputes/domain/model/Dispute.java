package com.cloudcart.disputes.domain.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
    name = "disputes",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_processor_dispute",
            columnNames = {"processor_id", "processor_dispute_id"}
        )
    },
    indexes = {
        @Index(name = "idx_merchant_id", columnList = "merchant_id"),
        @Index(name = "idx_reason_category", columnList = "reason_category"),
        @Index(name = "idx_urgency_action", columnList = "urgency_level,recommended_action")
    }
)
public class Dispute {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "processor_id", nullable = false, length = 50)
    private String processorId;

    @Column(name = "processor_dispute_id", nullable = false, length = 100)
    private String processorDisputeId;

    @Column(name = "merchant_id", nullable = false, length = 100)
    private String merchantId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    private Currency currency;

    @Column(name = "reason_code", nullable = false, length = 20)
    private String reasonCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason_category", nullable = false, length = 30)
    private ReasonCategory reasonCategory;

    @Column(name = "dispute_date", nullable = false)
    private LocalDate disputeDate;

    @Column(name = "transaction_date")
    private LocalDate transactionDate;

    @Column(name = "response_deadline", nullable = false)
    private LocalDate responseDeadline;

    @Column(name = "customer_email", length = 254)
    private String customerEmail;

    @Column(name = "order_id", length = 100)
    private String orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DisputeStatus status;

    @Column(name = "win_probability", nullable = false)
    private Integer winProbability;

    @Enumerated(EnumType.STRING)
    @Column(name = "recommended_action", nullable = false, length = 20)
    private RecommendedAction recommendedAction;

    @Enumerated(EnumType.STRING)
    @Column(name = "urgency_level", nullable = false, length = 10)
    private UrgencyLevel urgencyLevel;

    @Column(name = "scoring_reason", length = 500)
    private String scoringReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    // Getters and setters

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getProcessorId() { return processorId; }
    public void setProcessorId(String processorId) { this.processorId = processorId; }

    public String getProcessorDisputeId() { return processorDisputeId; }
    public void setProcessorDisputeId(String processorDisputeId) { this.processorDisputeId = processorDisputeId; }

    public String getMerchantId() { return merchantId; }
    public void setMerchantId(String merchantId) { this.merchantId = merchantId; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public Currency getCurrency() { return currency; }
    public void setCurrency(Currency currency) { this.currency = currency; }

    public String getReasonCode() { return reasonCode; }
    public void setReasonCode(String reasonCode) { this.reasonCode = reasonCode; }

    public ReasonCategory getReasonCategory() { return reasonCategory; }
    public void setReasonCategory(ReasonCategory reasonCategory) { this.reasonCategory = reasonCategory; }

    public LocalDate getDisputeDate() { return disputeDate; }
    public void setDisputeDate(LocalDate disputeDate) { this.disputeDate = disputeDate; }

    public LocalDate getTransactionDate() { return transactionDate; }
    public void setTransactionDate(LocalDate transactionDate) { this.transactionDate = transactionDate; }

    public LocalDate getResponseDeadline() { return responseDeadline; }
    public void setResponseDeadline(LocalDate responseDeadline) { this.responseDeadline = responseDeadline; }

    public String getCustomerEmail() { return customerEmail; }
    public void setCustomerEmail(String customerEmail) { this.customerEmail = customerEmail; }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public DisputeStatus getStatus() { return status; }
    public void setStatus(DisputeStatus status) { this.status = status; }

    public Integer getWinProbability() { return winProbability; }
    public void setWinProbability(Integer winProbability) { this.winProbability = winProbability; }

    public RecommendedAction getRecommendedAction() { return recommendedAction; }
    public void setRecommendedAction(RecommendedAction recommendedAction) { this.recommendedAction = recommendedAction; }

    public UrgencyLevel getUrgencyLevel() { return urgencyLevel; }
    public void setUrgencyLevel(UrgencyLevel urgencyLevel) { this.urgencyLevel = urgencyLevel; }

    public String getScoringReason() { return scoringReason; }
    public void setScoringReason(String scoringReason) { this.scoringReason = scoringReason; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
