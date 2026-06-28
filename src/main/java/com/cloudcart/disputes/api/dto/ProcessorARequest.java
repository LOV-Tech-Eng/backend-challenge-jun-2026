package com.cloudcart.disputes.api.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Ingest format for Processor A (camelCase fields, amounts in full currency units).
 * Simulates a Stripe-style chargeback notification.
 */
public record ProcessorARequest(

    @NotBlank(message = "disputeId is required")
    @Size(max = 100, message = "disputeId must not exceed 100 characters")
    String disputeId,

    @NotBlank(message = "merchantId is required")
    @Size(max = 100, message = "merchantId must not exceed 100 characters")
    String merchantId,

    @NotNull(message = "transactionAmount is required")
    @Positive(message = "transactionAmount must be positive")
    @DecimalMax(value = "999999.99", message = "transactionAmount exceeds maximum allowed")
    BigDecimal transactionAmount,

    @NotBlank(message = "currencyCode is required")
    @Pattern(regexp = "USD|BRL|MXN|COP", message = "currencyCode must be one of: USD, BRL, MXN, COP")
    String currencyCode,

    @NotBlank(message = "reasonCode is required")
    @Size(max = 20, message = "reasonCode must not exceed 20 characters")
    String reasonCode,

    @NotNull(message = "chargebackDate is required")
    LocalDate chargebackDate,

    @NotNull(message = "dueDate is required")
    LocalDate dueDate,

    LocalDate transactionDate,

    @Email(message = "customerEmail must be a valid email address")
    @Size(max = 254, message = "customerEmail must not exceed 254 characters")
    String customerEmail,

    @Size(max = 100, message = "orderId must not exceed 100 characters")
    String orderId
) {}
