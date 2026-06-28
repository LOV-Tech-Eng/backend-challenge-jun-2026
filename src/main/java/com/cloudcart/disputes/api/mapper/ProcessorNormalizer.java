package com.cloudcart.disputes.api.mapper;

import com.cloudcart.disputes.api.dto.ProcessorARequest;
import com.cloudcart.disputes.api.dto.ProcessorBRequest;
import com.cloudcart.disputes.domain.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * Maps processor-specific ingest formats to the canonical Dispute domain model.
 * Each processor has different field names and conventions (see DTOs).
 * Unknown reason codes are mapped to FRAUD (lowest base score — conservative default).
 */
@Component
public class ProcessorNormalizer {

    private static final Logger log = LoggerFactory.getLogger(ProcessorNormalizer.class);

    static final String PROCESSOR_A_ID = "PROCESSOR_A";
    static final String PROCESSOR_B_ID = "PROCESSOR_B";

    /**
     * Reason code → ReasonCategory mapping covering Visa and Mastercard common codes.
     * Both processor formats use the same underlying codes, just potentially different subsets.
     */
    private static final Map<String, ReasonCategory> REASON_CODE_MAP = Map.ofEntries(
        // Fraud
        Map.entry("10.4",  ReasonCategory.FRAUD),
        Map.entry("4837",  ReasonCategory.FRAUD),
        Map.entry("10.1",  ReasonCategory.FRAUD),
        Map.entry("4863",  ReasonCategory.FRAUD),
        // Product Not Received
        Map.entry("13.1",  ReasonCategory.PRODUCT_NOT_RECEIVED),
        Map.entry("4855",  ReasonCategory.PRODUCT_NOT_RECEIVED),
        Map.entry("4850",  ReasonCategory.PRODUCT_NOT_RECEIVED),
        // Product Unacceptable / Not As Described
        Map.entry("13.3",  ReasonCategory.PRODUCT_UNACCEPTABLE),
        Map.entry("4853",  ReasonCategory.PRODUCT_UNACCEPTABLE),
        Map.entry("13.4",  ReasonCategory.PRODUCT_UNACCEPTABLE),
        // Duplicate Processing
        Map.entry("12.6",  ReasonCategory.DUPLICATE_PROCESSING),
        Map.entry("4834",  ReasonCategory.DUPLICATE_PROCESSING),
        Map.entry("12.1",  ReasonCategory.DUPLICATE_PROCESSING),
        // Subscription / Cancelled Recurring
        Map.entry("13.2",  ReasonCategory.SUBSCRIPTION_CANCELLED),
        Map.entry("4841",  ReasonCategory.SUBSCRIPTION_CANCELLED),
        Map.entry("4931",  ReasonCategory.SUBSCRIPTION_CANCELLED)
    );

    public Dispute fromProcessorA(ProcessorARequest req) {
        Dispute dispute = new Dispute();
        dispute.setProcessorId(PROCESSOR_A_ID);
        dispute.setProcessorDisputeId(req.disputeId());
        dispute.setMerchantId(req.merchantId());
        dispute.setAmount(req.transactionAmount().setScale(2, RoundingMode.HALF_UP));
        dispute.setCurrency(Currency.valueOf(req.currencyCode()));
        dispute.setReasonCode(req.reasonCode());
        dispute.setReasonCategory(mapReasonCode(req.reasonCode()));
        dispute.setDisputeDate(req.chargebackDate());
        dispute.setResponseDeadline(req.dueDate());
        dispute.setTransactionDate(req.transactionDate());
        dispute.setCustomerEmail(req.customerEmail());
        dispute.setOrderId(req.orderId());
        return dispute;
    }

    public Dispute fromProcessorB(ProcessorBRequest req) {
        // amount_cents is integer minor units — divide by 100 to get full currency units
        BigDecimal amount = BigDecimal.valueOf(req.amountCents())
            .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        Dispute dispute = new Dispute();
        dispute.setProcessorId(PROCESSOR_B_ID);
        dispute.setProcessorDisputeId(req.disputeReference());
        dispute.setMerchantId(req.merchantRef());
        dispute.setAmount(amount);
        dispute.setCurrency(Currency.valueOf(req.currency()));
        dispute.setReasonCode(req.reasonCode());
        dispute.setReasonCategory(mapReasonCode(req.reasonCode()));
        dispute.setDisputeDate(req.chargebackDt());
        dispute.setResponseDeadline(req.responseDeadlineDt());
        dispute.setTransactionDate(req.txnDate());
        dispute.setCustomerEmail(req.email());
        dispute.setOrderId(req.reference());
        return dispute;
    }

    public ReasonCategory mapReasonCode(String reasonCode) {
        ReasonCategory category = REASON_CODE_MAP.get(reasonCode);
        if (category == null) {
            log.warn("Unknown reason code '{}' — mapped to OTHER (base score 30)", reasonCode);
            return ReasonCategory.OTHER;
        }
        return category;
    }
}
