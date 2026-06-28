package com.cloudcart.disputes.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;

import java.time.LocalDate;

/**
 * Ingest format for Processor B (snake_case fields, amount in integer cents).
 * Simulates an Adyen-style chargeback notification.
 * Amount is in minor units (cents) and will be divided by 100 during normalization.
 */
public record ProcessorBRequest(

    @JsonProperty("dispute_reference")
    @NotBlank(message = "dispute_reference is required")
    @Size(max = 100, message = "dispute_reference must not exceed 100 characters")
    String disputeReference,

    @JsonProperty("merchant_ref")
    @NotBlank(message = "merchant_ref is required")
    @Size(max = 100, message = "merchant_ref must not exceed 100 characters")
    String merchantRef,

    @JsonProperty("amount_cents")
    @NotNull(message = "amount_cents is required")
    @Positive(message = "amount_cents must be positive")
    @Max(value = 99999999, message = "amount_cents exceeds maximum allowed")
    Integer amountCents,

    @JsonProperty("currency")
    @NotBlank(message = "currency is required")
    @Pattern(regexp = "USD|BRL|MXN|COP", message = "currency must be one of: USD, BRL, MXN, COP")
    String currency,

    @JsonProperty("reason_code")
    @NotBlank(message = "reason_code is required")
    @Size(max = 20, message = "reason_code must not exceed 20 characters")
    String reasonCode,

    @JsonProperty("chargeback_dt")
    @NotNull(message = "chargeback_dt is required")
    LocalDate chargebackDt,

    @JsonProperty("response_deadline_dt")
    @NotNull(message = "response_deadline_dt is required")
    LocalDate responseDeadlineDt,

    @JsonProperty("txn_date")
    LocalDate txnDate,

    @JsonProperty("email")
    @Email(message = "email must be a valid email address")
    @Size(max = 254, message = "email must not exceed 254 characters")
    String email,

    @JsonProperty("reference")
    @Size(max = 100, message = "reference must not exceed 100 characters")
    String reference
) {}
